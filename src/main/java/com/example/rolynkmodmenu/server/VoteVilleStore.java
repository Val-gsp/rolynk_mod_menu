package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDate;

/**
 * Accès MySQL pour les votes de villes quotidiens.
 *
 * Table requise (voir docs/schema.sql) :
 *
 * CREATE TABLE IF NOT EXISTS votes_villes (
 *   uuid     VARCHAR(36) NOT NULL,
 *   ville_id INT         NOT NULL,
 *   jour     DATE        NOT NULL,
 *   PRIMARY KEY (uuid, jour),
 *   KEY idx_votes_ville (ville_id, jour),
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * Règles métier :
 *   – Un joueur = un vote par jour. La clé (uuid, jour) l'assure structurellement.
 *   – Un joueur ne peut pas voter pour sa propre ville (vérifié dans la transaction).
 *   – Le reset est structurel (date du jour dans la clé), aucun cron nécessaire.
 *   – Purge automatique des entrées de plus de 40 jours au démarrage serveur
 *     (garde un mois complet + 10 jours de marge pour les requêtes Discord).
 *
 * Classement mensuel (à lancer en fin de mois pour le Discord) :
 *   SELECT v.nom, COUNT(*) AS votes
 *   FROM votes_villes vv JOIN villes v ON v.id = vv.ville_id
 *   WHERE vv.jour >= DATE_FORMAT(NOW(), '%Y-%m-01')
 *   GROUP BY vv.ville_id, v.nom ORDER BY votes DESC;
 *
 * Toutes les méthodes sont BLOQUANTES — appeler depuis Database.EXECUTOR uniquement.
 */
public final class VoteVilleStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** État du vote du joueur pour aujourd'hui. */
    public record EtatVote(boolean aVoteAujourdhui, String nomVilleVotee) {}

    private VoteVilleStore() {}

    private static Connection getConn() throws SQLException {
        return Database.getConnection();
    }

    private static Date aujourdHui() {
        return Date.valueOf(LocalDate.now());
    }

    // ── Lecture ───────────────────────────────────────────────────────────

    /**
     * Renvoie l'état du vote du joueur pour aujourd'hui.
     * Si le joueur n'a pas voté : EtatVote(false, "").
     * Si la ville votée a depuis été dissoute, nomVilleVotee = "(ville supprimée)".
     */
    public static EtatVote getEtat(String uuid) {
        String sql = "SELECT COALESCE(v.nom, '(ville supprimée)') AS nom "
                   + "FROM votes_villes vv "
                   + "LEFT JOIN villes v ON v.id = vv.ville_id "
                   + "WHERE vv.uuid = ? AND vv.jour = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDate(2, aujourdHui());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new EtatVote(true, rs.getString("nom"));
            }
        } catch (SQLException e) {
            LOGGER.error("[VoteVilleStore] getEtat({}): {}", uuid, e.getMessage());
        }
        return new EtatVote(false, "");
    }

    /**
     * Renvoie le nom de la ville dont le joueur est membre, ou "" s'il est sans ville.
     * BLOQUANT — Database.EXECUTOR uniquement.
     */
    public static String getVilleJoueur(String uuid) {
        String sql = "SELECT v.nom FROM villes_membres vm "
                   + "JOIN villes v ON v.id = vm.ville_id "
                   + "WHERE vm.uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nom");
            }
        } catch (SQLException e) {
            LOGGER.error("[VoteVilleStore] getVilleJoueur({}): {}", uuid, e.getMessage());
        }
        return "";
    }

    // ── Action ────────────────────────────────────────────────────────────

    /**
     * Enregistre un vote pour une ville — TOUT est dans une seule transaction :
     *
     *   1. Verrou sur (uuid, aujourd'hui) — anti double-vote concurrent (FOR UPDATE).
     *   2. Vérification que la ville existe.
     *   3. Vérification que le joueur n'en est pas membre.
     *   4. Insertion de la ligne de vote.
     *   5. Crédit de la récompense au joueur.
     *
     * Le montant vient de RolynkConfig, jamais du client.
     *
     * @return null si succès, message d'erreur coloré sinon.
     */
    public static String voter(String uuid, int villeId, double montant) {
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 1. Vérifier que le joueur n'a pas déjà voté aujourd'hui (verrou)
                //    On verrouille via une pseudo-ligne dans une table temporaire ?
                //    Non : on utilise un SELECT ... FOR UPDATE sur votes_villes.
                //    Si la ligne n'existe pas, InnoDB ne posera pas de verrou gap
                //    sur PRIMARY KEY — on se protège par l'unicité de la clé (uuid, jour)
                //    + l'INSERT qui échouera si un concurrent a gagné entre les deux.
                boolean dejaVote = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM votes_villes WHERE uuid=? AND jour=? FOR UPDATE")) {
                    ps.setString(1, uuid);
                    ps.setDate(2, aujourdHui());
                    try (ResultSet rs = ps.executeQuery()) { dejaVote = rs.next(); }
                }
                if (dejaVote) {
                    c.rollback();
                    return "§eTu as déjà voté pour une ville aujourd'hui.";
                }

                // 2. Vérifier que la ville existe
                String nomVille = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT nom FROM villes WHERE id=?")) {
                    ps.setInt(1, villeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) nomVille = rs.getString("nom");
                    }
                }
                if (nomVille == null) {
                    c.rollback();
                    return "§cCette ville n'existe plus.";
                }

                // 3. Vérifier que le joueur n'est pas membre de cette ville
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM villes_membres WHERE uuid=? AND ville_id=?")) {
                    ps.setString(1, uuid);
                    ps.setInt(2, villeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            c.rollback();
                            return "§cTu ne peux pas voter pour ta propre ville.";
                        }
                    }
                }

                // 4. Insérer le vote
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO votes_villes (uuid, ville_id, jour) VALUES (?,?,?)")) {
                    ps.setString(1, uuid);
                    ps.setInt(2, villeId);
                    ps.setDate(3, aujourdHui());
                    ps.executeUpdate();
                }

                // 5. Créditer le joueur
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE joueurs SET money = money + ? WHERE uuid=?")) {
                    ps.setDouble(1, montant);
                    ps.setString(2, uuid);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return "§cProfil joueur introuvable.";
                    }
                }

                c.commit();
                return null; // succès
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[VoteVilleStore] voter({}, {}): {}", uuid, villeId, e.getMessage());
            return "§cErreur lors de l'enregistrement du vote.";
        }
    }

    // ── Maintenance ───────────────────────────────────────────────────────

    /**
     * Purge les votes de plus de 40 jours (garde un mois complet + marge Discord).
     * Appelé au démarrage serveur.
     */
    public static void purgerAnciens() {
        String sql = "DELETE FROM votes_villes WHERE jour < ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(40)));
            int n = ps.executeUpdate();
            if (n > 0) LOGGER.info("[VoteVilleStore] {} votes anciens purgés.", n);
        } catch (SQLException e) {
            LOGGER.error("[VoteVilleStore] purgerAnciens: {}", e.getMessage());
        }
    }
}
