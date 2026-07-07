package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Accès MySQL pour le système de balises.
 * Toutes les méthodes sont BLOQUANTES — toujours appeler depuis {@link Database#EXECUTOR},
 * jamais le main thread. Le pool de connexions est partagé via {@link Database}.
 */
public final class BaliseStore {

    public record BaliseEntry(String nom, String monde, int x, int y, int z) {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Alias historique — voir {@link Database#EXECUTOR}. */
    public static final ExecutorService DB_EXECUTOR = Database.EXECUTOR;

    private BaliseStore() {}

    private static Connection getConn() throws SQLException {
        return Database.getConnection();
    }

    // ── Balises ───────────────────────────────────────────────────────────

    /** Retourne toutes les balises d'un joueur, triées par id ASC. */
    public static List<BaliseEntry> getBalises(String uuid) {
        List<BaliseEntry> list = new ArrayList<>();
        String sql = "SELECT nom, monde, x, y, z FROM balises WHERE joueur_uuid = ? ORDER BY id ASC";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BaliseEntry(
                            rs.getString("nom"), rs.getString("monde"),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] getBalises({}): {}", uuid, e.getMessage());
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Ajoute une balise en une seule requête atomique.
     * La contrainte UNIQUE KEY (joueur_uuid, nom) gère les doublons côté DB.
     * @param maxBalises limite de grade (lue via LuckPerms, transmise par le handler)
     * @return true si créée, false si quota atteint ou nom déjà existant.
     */
    public static boolean addBalise(String uuid, String nom, String monde, int x, int y, int z, int maxBalises) {
        String sql = "INSERT INTO balises (joueur_uuid, nom, monde, x, y, z) "
                   + "SELECT ?, ?, ?, ?, ?, ? FROM DUAL "
                   + "WHERE (SELECT COUNT(*) FROM balises WHERE joueur_uuid = ?) < ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, nom);
            ps.setString(3, monde);
            ps.setInt(4, x);
            ps.setInt(5, y);
            ps.setInt(6, z);
            ps.setString(7, uuid);
            ps.setInt(8, maxBalises);
            return ps.executeUpdate() > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false; // Nom déjà utilisé (UNIQUE KEY violation)
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] addBalise({}, {}): {}", uuid, nom, e.getMessage());
            return false;
        }
    }

    /** @return true si supprimée, false si introuvable. */
    public static boolean removeBalise(String uuid, String nom) {
        String sql = "DELETE FROM balises WHERE joueur_uuid = ? AND nom = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, nom);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] removeBalise({}, {}): {}", uuid, nom, e.getMessage());
            return false;
        }
    }

    /** @return l'entrée ou null si introuvable. */
    public static BaliseEntry getBalise(String uuid, String nom) {
        String sql = "SELECT nom, monde, x, y, z FROM balises WHERE joueur_uuid = ? AND nom = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, nom);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new BaliseEntry(
                        rs.getString("nom"), rs.getString("monde"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
            }
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] getBalise({}, {}): {}", uuid, nom, e.getMessage());
        }
        return null;
    }

    // ── Joueurs & grades ─────────────────────────────────────────────────

    /**
     * Upsert du joueur à la connexion : crée la ligne si absente, met à jour le pseudo.
     * Le grade reste inchangé s'il existe déjà (géré manuellement via SQL).
     */
    public static void upsertJoueur(String uuid, String pseudo) {
        String sql = "INSERT INTO joueurs (uuid, pseudo, grade) VALUES (?, ?, 'default') "
                   + "ON DUPLICATE KEY UPDATE pseudo = VALUES(pseudo)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, pseudo);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] upsertJoueur({}, {}): {}", uuid, pseudo, e.getMessage());
        }
    }

    /**
     * Retourne le max_balises du joueur via JOIN joueurs → grade_limits.
     * Fallback 2 si joueur inconnu ou grade non configuré.
     */
    public static int getMaxBalises(String uuid) {
        String sql = "SELECT gl.max_balises FROM joueurs j "
                   + "JOIN grade_limits gl ON gl.grade = j.grade "
                   + "WHERE j.uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("max_balises");
            }
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] getMaxBalises({}): {}", uuid, e.getMessage());
        }
        return 2;
    }

    // ── Pending teleports ─────────────────────────────────────────────────

    public static void storePendingTeleport(String uuid, int x, int y, int z) {
        String sql = "INSERT INTO pending_teleports (uuid, x, y, z) VALUES (?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE x = VALUES(x), y = VALUES(y), z = VALUES(z)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] storePendingTeleport({}): {}", uuid, e.getMessage());
        }
    }

    /** @return int[]{x,y,z} ou null. */
    public static int[] getPendingTeleport(String uuid) {
        String sql = "SELECT x, y, z FROM pending_teleports WHERE uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new int[]{rs.getInt("x"), rs.getInt("y"), rs.getInt("z")};
            }
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] getPendingTeleport({}): {}", uuid, e.getMessage());
        }
        return null;
    }

    public static void clearPendingTeleport(String uuid) {
        String sql = "DELETE FROM pending_teleports WHERE uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[BaliseStore] clearPendingTeleport({}): {}", uuid, e.getMessage());
        }
    }

}
