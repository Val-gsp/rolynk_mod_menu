package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Accès DB de la Boutique Jeux. Une seule opération : créditer une vente.
 * Comme partout : jamais appelé depuis le main thread (Database.EXECUTOR).
 */
public final class BoutiqueStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    private BoutiqueStore() {}

    /**
     * Crédite {@code montant} au joueur suite à une vente.
     * @return true si le crédit a été appliqué (ligne joueur trouvée).
     */
    public static boolean crediterVente(String uuid, double montant, String detail) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE joueurs SET money = money + ? WHERE uuid = ?")) {
            ps.setDouble(1, montant);
            ps.setString(2, uuid);
            if (ps.executeUpdate() == 0) {
                LOGGER.warn("[Boutique] Vente non créditée, joueur inconnu en DB : {} ({})", uuid, detail);
                return false;
            }
            LOGGER.info("[Boutique] Vente créditée : {} +{} ({})", uuid, montant, detail);
            return true;
        } catch (SQLException e) {
            LOGGER.error("[Boutique] Erreur SQL au crédit de {} ({})", uuid, detail, e);
            return false;
        }
    }
}
