package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Accès MySQL pour les données de profil joueur (table `joueurs`).
 * Toutes les méthodes sont BLOQUANTES — toujours appeler depuis DB_EXECUTOR, jamais le main thread.
 */
public final class JoueurRepository {

    private static final Logger LOGGER = LogUtils.getLogger();

    private JoueurRepository() {}

    // ── Record de données profil ──────────────────────────────────────────

    public record ProfileData(
            String uuid,
            String status,
            String pseudo,
            String grade,
            String money,
            String cristaux,
            String heuresDeJeu,
            String premiereConnexion,
            String villeNom
    ) {}

    // ── Événements de session ─────────────────────────────────────────────

    /**
     * Appelé au login (et à chaque ProfileRequest) : marque le joueur en ligne.
     * Les deux requêtes sont séparées : si premiere_connexion n'existe pas encore,
     * le status est quand même mis à jour.
     */
    public static void onLogin(String uuid) {
        // Requête critique — status doit toujours être mis à jour
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE joueurs SET status = 'online' WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[JoueurRepository] onLogin status({}): {}", uuid, e.getMessage());
        }

        // Requête optionnelle — premiere_connexion (peut échouer si colonne absente)
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE joueurs SET premiere_connexion = COALESCE(premiere_connexion, NOW()) WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("[JoueurRepository] onLogin premiere_connexion({}): {}", uuid, e.getMessage());
        }
    }

    /**
     * Appelé au logout : marque le joueur hors ligne et ajoute les heures de session.
     * @param sessionHours durée de la session en heures (ex: 0.5 = 30 minutes)
     */
    public static void onLogout(String uuid, double sessionHours) {
        String sql = "UPDATE joueurs SET status = 'offline', " +
                     "heures_de_jeu = heures_de_jeu + ? " +
                     "WHERE uuid = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, BigDecimal.valueOf(sessionHours));
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[JoueurRepository] onLogout({}): {}", uuid, e.getMessage());
        }
    }

    /**
     * Réinitialise tous les statuts à 'offline'.
     * Appelé au démarrage du serveur pour corriger les statuts résiduels après un crash.
     */
    public static void resetAllStatus() {
        String sql = "UPDATE joueurs SET status = 'offline'";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
            LOGGER.info("[JoueurRepository] Statuts réinitialisés à 'offline'.");
        } catch (SQLException e) {
            LOGGER.error("[JoueurRepository] resetAllStatus(): {}", e.getMessage());
        }
    }

    // ── Lecture profil ────────────────────────────────────────────────────

    /**
     * Récupère le profil complet d'un joueur avec le nom de sa ville via LEFT JOIN.
     * @return ProfileData ou null si le joueur est introuvable en base.
     */
    public static ProfileData fetchProfile(String uuid) {
        String sql = """
                SELECT j.uuid, j.status, j.pseudo, j.grade, j.money, j.cristaux,
                       j.heures_de_jeu, j.premiere_connexion,
                       COALESCE(v.nom, '') AS ville_nom
                FROM joueurs j
                LEFT JOIN villes_membres vm ON vm.uuid = j.uuid
                LEFT JOIN villes v ON v.id = vm.ville_id
                WHERE j.uuid = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                BigDecimal money  = rs.getBigDecimal("money");
                BigDecimal heures = rs.getBigDecimal("heures_de_jeu");
                Timestamp  pc     = rs.getTimestamp("premiere_connexion");

                String moneyStr    = money  != null ? money.toPlainString()  : "0";
                String cristauxStr = Long.toString(rs.getLong("cristaux"));
                String heuresStr = heures != null
                        ? String.format("%.1f h", heures.doubleValue()) : "0.0 h";
                String pcStr     = pc != null
                        ? pc.toString().substring(0, 16).replace("T", " ") : "—";

                return new ProfileData(
                        rs.getString("uuid"),
                        rs.getString("status"),
                        rs.getString("pseudo"),
                        rs.getString("grade"),
                        moneyStr,
                        cristauxStr,
                        heuresStr,
                        pcStr,
                        rs.getString("ville_nom")
                );
            }
        } catch (SQLException e) {
            LOGGER.error("[JoueurRepository] fetchProfile({}): {}", uuid, e.getMessage());
        }
        return null;
    }
}
