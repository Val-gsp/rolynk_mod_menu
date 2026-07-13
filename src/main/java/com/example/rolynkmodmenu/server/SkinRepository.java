package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Persistance du skin personnalisé (table `joueurs_skin`).
 * Stocke la texture SIGNÉE afin de la ré-appliquer à chaque connexion sans
 * re-solliciter MineSkin. BLOQUANT — appeler depuis DB_EXECUTOR.
 */
public final class SkinRepository {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SkinRepository() {}

    public record StoredSkin(String url, String value, String signature) {}

    /** @return le skin stocké du joueur, ou null s'il n'en a pas. */
    public static StoredSkin fetch(String uuid) {
        String sql = "SELECT skin_url, texture_value, texture_signature "
                   + "FROM joueurs_skin WHERE uuid = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String value = rs.getString("texture_value");
                if (value == null || value.isEmpty()) return null;
                return new StoredSkin(rs.getString("skin_url"), value,
                        rs.getString("texture_signature"));
            }
        } catch (SQLException e) {
            LOGGER.error("[SkinRepository] fetch({}): {}", uuid, e.getMessage());
        }
        return null;
    }

    /** Crée ou remplace le skin du joueur. */
    public static void upsert(String uuid, String url, String value, String signature) {
        String sql = """
                INSERT INTO joueurs_skin (uuid, skin_url, texture_value, texture_signature)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    skin_url = VALUES(skin_url),
                    texture_value = VALUES(texture_value),
                    texture_signature = VALUES(texture_signature)
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, url);
            ps.setString(3, value);
            ps.setString(4, signature);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[SkinRepository] upsert({}): {}", uuid, e.getMessage());
        }
    }
}
