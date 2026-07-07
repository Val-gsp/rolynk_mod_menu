package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.util.Money;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDate;

/**
 * Accès MySQL pour les récompenses quotidiennes (temps de jeu).
 *
 * Table requise (voir docs/schema.sql) :
 *
 * CREATE TABLE IF NOT EXISTS recompenses_quotidiennes (
 *   uuid               VARCHAR(36) NOT NULL,
 *   jour               DATE NOT NULL,
 *   temps_jeu_secondes INT NOT NULL DEFAULT 0,
 *   palier_30m         TINYINT(1) NOT NULL DEFAULT 0,
 *   palier_2h          TINYINT(1) NOT NULL DEFAULT 0,
 *   palier_4h          TINYINT(1) NOT NULL DEFAULT 0,
 *   PRIMARY KEY (uuid, jour)
 * );
 *
 * Le "reset à minuit" est structurel : la clé contient la date du jour
 * (heure locale de la machine serveur via LocalDate.now()) — au passage de
 * minuit, les lectures/écritures portent sur une nouvelle ligne vierge.
 * Aucun cron nécessaire ; les vieilles lignes sont purgées au démarrage.
 *
 * Toutes les méthodes sont BLOQUANTES — appeler depuis Database.EXECUTOR uniquement.
 */
public final class RecompenseStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Seuils des paliers en secondes : 30 min, 2 h, 4 h. */
    public static final int[] SEUILS_SECONDES = {30 * 60, 2 * 3600, 4 * 3600};

    /** Colonnes DB des paliers — indexées comme SEUILS_SECONDES (jamais de SQL dynamique au-delà). */
    private static final String[] PALIER_COLS = {"palier_30m", "palier_2h", "palier_4h"};

    public static final int NB_PALIERS = SEUILS_SECONDES.length;

    /** État du jour courant pour un joueur. */
    public record EtatJour(int tempsSecondes, boolean[] recuperes) {}

    private RecompenseStore() {}

    private static Connection getConn() throws SQLException {
        return Database.getConnection();
    }

    /** Date du jour, heure locale de la machine serveur (le réseau partage la même). */
    private static Date aujourdHui() {
        return Date.valueOf(LocalDate.now());
    }

    // ── Temps de jeu ──────────────────────────────────────────────────────

    /** Ajoute du temps de jeu au compteur du jour (upsert). */
    public static void ajouterTempsJeu(String uuid, int secondes) {
        if (secondes <= 0) return;
        String sql = "INSERT INTO recompenses_quotidiennes (uuid, jour, temps_jeu_secondes) "
                   + "VALUES (?,?,?) "
                   + "ON DUPLICATE KEY UPDATE temps_jeu_secondes = temps_jeu_secondes + VALUES(temps_jeu_secondes)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDate(2, aujourdHui());
            ps.setInt(3, secondes);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[RecompenseStore] ajouterTempsJeu({}): {}", uuid, e.getMessage());
        }
    }

    // ── Lecture de l'état ─────────────────────────────────────────────────

    /** État du jour : temps cumulé + paliers déjà récupérés. Ligne absente = tout à zéro. */
    public static EtatJour getEtat(String uuid) {
        String sql = "SELECT temps_jeu_secondes, palier_30m, palier_2h, palier_4h "
                   + "FROM recompenses_quotidiennes WHERE uuid=? AND jour=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDate(2, aujourdHui());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EtatJour(rs.getInt("temps_jeu_secondes"), new boolean[]{
                            rs.getBoolean("palier_30m"),
                            rs.getBoolean("palier_2h"),
                            rs.getBoolean("palier_4h")});
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[RecompenseStore] getEtat({}): {}", uuid, e.getMessage());
        }
        return new EtatJour(0, new boolean[NB_PALIERS]);
    }

    // ── Réclamation ───────────────────────────────────────────────────────

    /**
     * Réclame un palier — TOUT est dans une seule transaction :
     * lecture verrouillée (FOR UPDATE, anti double-claim concurrent),
     * marquage du palier et crédit du joueur. Le montant vient de la config
     * serveur, jamais du client.
     *
     * @param palier index 0..2 (déjà validé par le handler)
     * @return null si succès, message d'erreur coloré sinon.
     */
    public static String reclamer(String uuid, int palier, double montant) {
        int seuil = SEUILS_SECONDES[palier];
        String col = PALIER_COLS[palier];

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 1. Lecture verrouillée de la ligne du jour
                int temps = 0;
                boolean dejaRecupere = false;
                boolean ligneExiste = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT temps_jeu_secondes, " + col + " AS p "
                        + "FROM recompenses_quotidiennes WHERE uuid=? AND jour=? FOR UPDATE")) {
                    ps.setString(1, uuid);
                    ps.setDate(2, aujourdHui());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ligneExiste   = true;
                            temps         = rs.getInt("temps_jeu_secondes");
                            dejaRecupere  = rs.getBoolean("p");
                        }
                    }
                }
                if (dejaRecupere) {
                    c.rollback();
                    return "§eTu as déjà récupéré cette récompense aujourd'hui.";
                }
                if (!ligneExiste || temps < seuil) {
                    c.rollback();
                    return "§cTemps de jeu insuffisant pour ce palier.";
                }

                // 2. Marquage du palier (condition =0 conservée : ceinture + bretelles)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE recompenses_quotidiennes SET " + col + "=1 "
                        + "WHERE uuid=? AND jour=? AND " + col + "=0")) {
                    ps.setString(1, uuid);
                    ps.setDate(2, aujourdHui());
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return "§eTu as déjà récupéré cette récompense aujourd'hui.";
                    }
                }

                // 3. Crédit du joueur
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
            LOGGER.error("[RecompenseStore] reclamer({}, {}): {}", uuid, palier, e.getMessage());
            return "§cErreur lors de la récupération de la récompense.";
        }
    }

    // ── Maintenance ───────────────────────────────────────────────────────

    /** Purge les lignes de plus de 2 jours (appelé au démarrage serveur). */
    public static void purgerAnciennes() {
        String sql = "DELETE FROM recompenses_quotidiennes WHERE jour < ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(2)));
            int n = ps.executeUpdate();
            if (n > 0) LOGGER.info("[RecompenseStore] {} lignes journalières purgées.", n);
        } catch (SQLException e) {
            LOGGER.error("[RecompenseStore] purgerAnciennes: {}", e.getMessage());
        }
    }

    /** Libellé lisible d'un montant pour les messages de succès. */
    public static String libelleMontant(double montant) {
        return Money.entier(montant);
    }
}
