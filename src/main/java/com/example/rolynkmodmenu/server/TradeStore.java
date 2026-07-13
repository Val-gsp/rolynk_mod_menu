package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Accès DB du système de trade : échange d'argent ATOMIQUE entre deux joueurs.
 *
 * Toute l'opération se fait dans UNE transaction avec garde de solde
 * ({@code money >= ?}) : impossible de passer en négatif, impossible de
 * dupliquer — soit tout passe, soit rien.
 *
 * Jamais appelé depuis le main thread (Database.EXECUTOR).
 */
public final class TradeStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeStore() {}

    /**
     * A donne {@code deA} à B, B donne {@code deB} à A (montants ≥ 0, déjà arrondis).
     * @return null si OK, sinon message d'erreur (§c inclus) identifiant le manque.
     */
    public static String echangerArgent(String uuidA, String pseudoA, double deA,
                                        String uuidB, String pseudoB, double deB) {
        if (deA <= 0 && deB <= 0) return null; // pas d'argent en jeu

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (deA > 0 && !debiter(c, uuidA, deA)) {
                    c.rollback();
                    return "§c" + pseudoA + " n'a pas assez d'argent.";
                }
                if (deB > 0 && !debiter(c, uuidB, deB)) {
                    c.rollback();
                    return "§c" + pseudoB + " n'a pas assez d'argent.";
                }
                if (deA > 0) crediter(c, uuidB, deA);
                if (deB > 0) crediter(c, uuidA, deB);
                c.commit();
                LOGGER.info("[Trade] Argent échangé : {} -{} / {} -{}", pseudoA, deA, pseudoB, deB);
                return null;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[Trade] Erreur SQL échange {} <-> {}", pseudoA, pseudoB, e);
            return "§cErreur interne, trade annulé.";
        }
    }

    /** Compensation après échec post-transfert : refait l'échange en sens inverse. */
    public static void compenser(String uuidA, String pseudoA, double deA,
                                 String uuidB, String pseudoB, double deB) {
        String err = echangerArgent(uuidB, pseudoB, deA, uuidA, pseudoA, deB);
        if (err != null) {
            LOGGER.error("[Trade] COMPENSATION IMPOSSIBLE ({} {} / {} {}) : {} — intervention manuelle requise !",
                    pseudoA, deA, pseudoB, deB, err);
        }
    }

    /**
     * Lit les soldes des deux joueurs (affichage dans l'écran de trade).
     * @return {soldeA, soldeB}, -1 pour un joueur introuvable / en erreur.
     */
    public static double[] lireSoldes(String uuidA, String uuidB) {
        double[] out = {-1, -1};
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, money FROM joueurs WHERE uuid IN (?, ?)")) {
            ps.setString(1, uuidA);
            ps.setString(2, uuidB);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    double m = rs.getDouble("money");
                    if (uuidA.equals(rs.getString("uuid"))) out[0] = m;
                    else out[1] = m;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[Trade] Lecture des soldes impossible", e);
        }
        return out;
    }

    private static boolean debiter(Connection c, String uuid, double montant) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE joueurs SET money = money - ? WHERE uuid = ? AND money >= ?")) {
            ps.setDouble(1, montant);
            ps.setString(2, uuid);
            ps.setDouble(3, montant);
            return ps.executeUpdate() == 1;
        }
    }

    private static void crediter(Connection c, String uuid, double montant) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE joueurs SET money = money + ? WHERE uuid = ?")) {
            ps.setDouble(1, montant);
            ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }
}
