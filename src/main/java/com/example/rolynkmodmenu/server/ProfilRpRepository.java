package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Accès MySQL au profil RP (table `joueurs_profil_rp`).
 * Toutes les méthodes sont BLOQUANTES — toujours appeler depuis DB_EXECUTOR, jamais le main thread.
 *
 * Le profil est créé UNE SEULE FOIS par joueur (première connexion) ; toute
 * tentative de recréation est refusée par {@link #insert}.
 */
public final class ProfilRpRepository {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Nouvelle ville imposée par le serveur — non modifiable par le client. */
    public static final String NOUVELLE_VILLE = "Rolynk";

    private ProfilRpRepository() {}

    public record RpData(
            String nom,
            String prenom,
            String sexe,
            String taille,
            String ancienneVille,
            String nouvelleVille,
            String metier,
            String description
    ) {}

    /** @return le profil RP du joueur, ou null s'il n'en a pas encore créé. */
    public static RpData fetch(String uuid) {
        String sql = """
                SELECT nom, prenom, sexe, taille, ancienne_ville,
                       nouvelle_ville, metier, description
                FROM joueurs_profil_rp WHERE uuid = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RpData(
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("sexe"),
                        rs.getString("taille"),
                        rs.getString("ancienne_ville"),
                        rs.getString("nouvelle_ville"),
                        rs.getString("metier"),
                        rs.getString("description")
                );
            }
        } catch (SQLException e) {
            LOGGER.error("[ProfilRpRepository] fetch({}): {}", uuid, e.getMessage());
        }
        return null;
    }

    /**
     * Crée le profil RP du joueur. INSERT IGNORE : si le profil existe déjà
     * (double clic, deux backends), l'insertion est silencieusement ignorée.
     * @return true si une ligne a été créée.
     */
    public static boolean insert(String uuid, String nom, String prenom, String sexe,
                                 String taille, String ancienneVille, String metier,
                                 String description) {
        String sql = """
                INSERT IGNORE INTO joueurs_profil_rp
                    (uuid, nom, prenom, sexe, taille, ancienne_ville,
                     nouvelle_ville, metier, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, nom);
            ps.setString(3, prenom);
            ps.setString(4, sexe);
            ps.setString(5, taille);
            ps.setString(6, ancienneVille);
            ps.setString(7, NOUVELLE_VILLE);
            ps.setString(8, metier);
            ps.setString(9, description);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("[ProfilRpRepository] insert({}): {}", uuid, e.getMessage());
            return false;
        }
    }
}
