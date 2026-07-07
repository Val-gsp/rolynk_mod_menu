package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDate;

/**
 * Accès MySQL pour l'exploration quotidienne.
 *
 * Table requise (voir docs/schema.sql) :
 *
 * CREATE TABLE IF NOT EXISTS exploration_quotidienne (
 *   uuid             VARCHAR(36)  NOT NULL,
 *   jour             DATE         NOT NULL,
 *   blocs_parcourus  INT          NOT NULL DEFAULT 0,
 *   recompense_recue TINYINT(1)   NOT NULL DEFAULT 0,
 *   PRIMARY KEY (uuid, jour)
 * );
 *
 * La distance est comptée en blocs sur l'axe XZ uniquement (mouvement horizontal).
 * Le reset à minuit est structurel (la clé contient la date), aucun cron.
 * Purge automatique des lignes > 2 jours au démarrage.
 *
 * Toutes les méthodes sont BLOQUANTES — appeler depuis Database.EXECUTOR uniquement.
 */
public final class ExplorationStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** État du jour courant pour un joueur. */
    public record EtatExploration(int blocsParcourus, boolean recompenseRecue) {}

    private ExplorationStore() {}

    private static Connection getConn() throws SQLException {
        return Database.getConnection();
    }

    private static Date aujourdHui() {
        return Date.valueOf(LocalDate.now());
    }

    // ── Écriture ──────────────────────────────────────────────────────────

    /** Ajoute des blocs parcourus au compteur du jour (upsert). */
    public static void ajouterBlocs(String uuid, int blocs) {
        if (blocs <= 0) return;
        String sql = "INSERT INTO exploration_quotidienne (uuid, jour, blocs_parcourus) "
                   + "VALUES (?,?,?) "
                   + "ON DUPLICATE KEY UPDATE blocs_parcourus = blocs_parcourus + VALUES(blocs_parcourus)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDate(2, aujourdHui());
            ps.setInt(3, blocs);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[ExplorationStore] ajouterBlocs({}): {}", uuid, e.getMessage());
        }
    }

    // ── Lecture ───────────────────────────────────────────────────────────

    /** État du jour : blocs parcourus + récompense déjà récupérée. Ligne absente = tout à zéro. */
    public static EtatExploration getEtat(String uuid) {
        String sql = "SELECT blocs_parcourus, recompense_recue "
                   + "FROM exploration_quotidienne WHERE uuid=? AND jour=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDate(2, aujourdHui());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EtatExploration(
                            rs.getInt("blocs_parcourus"), rs.getBoolean("recompense_recue"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[ExplorationStore] getEtat({}): {}", uuid, e.getMessage());
        }
        return new EtatExploration(0, false);
    }

    // ── Réclamation ───────────────────────────────────────────────────────

    /**
     * Réclame la récompense d'exploration — TOUT dans une seule transaction :
     * lecture verrouillée (FOR UPDATE), vérification du seuil, marquage,
     * crédit du joueur. Montant et seuil viennent de RolynkConfig, jamais du client.
     *
     * @return null si succès, message d'erreur coloré sinon.
     */
    public static String reclamer(String uuid, int seuil, double montant) {
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 1. Lecture verrouillée
                int  blocs       = 0;
                boolean dejaRecue  = false;
                boolean ligneExiste = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT blocs_parcourus, recompense_recue "
                        + "FROM exploration_quotidienne WHERE uuid=? AND jour=? FOR UPDATE")) {
                    ps.setString(1, uuid);
                    ps.setDate(2, aujourdHui());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ligneExiste = true;
                            blocs       = rs.getInt("blocs_parcourus");
                            dejaRecue   = rs.getBoolean("recompense_recue");
                        }
                    }
                }
                if (dejaRecue) {
                    c.rollback();
                    return "§eTu as déjà récupéré ta récompense d'exploration aujourd'hui.";
                }
                if (!ligneExiste || blocs < seuil) {
                    c.rollback();
                    return "§cDistance insuffisante. ("
                            + blocs + " / " + seuil + " blocs)";
                }

                // 2. Marquer la récompense (condition =0 : ceinture + bretelles)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE exploration_quotidienne SET recompense_recue=1 "
                        + "WHERE uuid=? AND jour=? AND recompense_recue=0")) {
                    ps.setString(1, uuid);
                    ps.setDate(2, aujourdHui());
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return "§eTu as déjà récupéré ta récompense d'exploration aujourd'hui.";
                    }
                }

                // 3. Créditer le joueur
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
                return null;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[ExplorationStore] reclamer({}): {}", uuid, e.getMessage());
            return "§cErreur lors de la récupération de la récompense.";
        }
    }

    // ── Maintenance ───────────────────────────────────────────────────────

    /** Purge les lignes de plus de 2 jours (appelé au démarrage serveur). */
    public static void purgerAnciennes() {
        String sql = "DELETE FROM exploration_quotidienne WHERE jour < ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(2)));
            int n = ps.executeUpdate();
            if (n > 0) LOGGER.info("[ExplorationStore] {} lignes journalières purgées.", n);
        } catch (SQLException e) {
            LOGGER.error("[ExplorationStore] purgerAnciennes: {}", e.getMessage());
        }
    }
}
