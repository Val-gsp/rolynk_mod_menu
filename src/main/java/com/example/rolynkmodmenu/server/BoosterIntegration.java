package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Intégration optionnelle avec le mod rolynk_cards (cartes à collectionner).
 *
 * Tout passe par réflexion — aucun import direct de rolynk_cards. Si le mod
 * est absent (ou son API incompatible), toutes les méthodes retournent
 * silencieusement : la money du palier est quand même créditée, seuls les
 * boosters sont ignorés. Même pattern défensif que {@link OPCServerIntegration}.
 *
 * API ciblée (côté rolynk_cards) :
 *   RolynkCardsApi.giveBooster(ServerPlayer, RolynkCardsApi.BoosterTier, int)
 *
 * À appeler depuis le MAIN THREAD serveur uniquement (agit sur l'inventaire).
 */
public final class BoosterIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Paliers de booster — mappés sur l'enum BoosterTier de rolynk_cards. */
    public enum Tier { BASIC, IMPROVED, SUPREME }

    private static final String MOD_ID   = "rolynk_cards";
    private static final String API_CLS  = "com.example.rolynkcards.api.RolynkCardsApi";
    private static final String TIER_CLS = "com.example.rolynkcards.api.RolynkCardsApi$BoosterTier";

    // null = pas encore testé, true = disponible, false = absent / incompatible
    private static Boolean available = null;
    private static Method  giveBoosterMethod;                 // (ServerPlayer, BoosterTier, int)
    private static Object  tierBasic, tierImproved, tierSupreme;

    private BoosterIntegration() {}

    // ── Détection + résolution réflexive (une seule fois) ───────────────────

    private static synchronized boolean ensureLoaded() {
        if (available != null) return available;

        if (!ModList.get().isLoaded(MOD_ID)) {
            LOGGER.info("[Rolynk] rolynk_cards absent — boosters de récompense désactivés.");
            available = false;
            return false;
        }
        try {
            Class<?> api  = Class.forName(API_CLS);
            Class<?> tier = Class.forName(TIER_CLS);

            for (Object c : tier.getEnumConstants()) {
                switch (((Enum<?>) c).name()) {
                    case "BASIC"    -> tierBasic    = c;
                    case "IMPROVED" -> tierImproved = c;
                    case "SUPREME"  -> tierSupreme  = c;
                    default -> { /* tier inconnu — ignoré */ }
                }
            }

            for (Method m : api.getMethods()) {
                if (m.getName().equals("giveBooster") && m.getParameterCount() == 3) {
                    giveBoosterMethod = m;
                    break;
                }
            }
            if (giveBoosterMethod == null) throw new NoSuchMethodException("giveBooster/3");

            available = true;
            LOGGER.info("[Rolynk] rolynk_cards détecté — boosters de récompense activés.");
        } catch (Throwable e) {
            LOGGER.warn("[Rolynk] rolynk_cards présent mais API incompatible : {}", e.getMessage());
            available = false;
        }
        return available;
    }

    private static Object tierObj(Tier t) {
        return switch (t) {
            case BASIC    -> tierBasic;
            case IMPROVED -> tierImproved;
            case SUPREME  -> tierSupreme;
        };
    }

    // ── Opération publique ──────────────────────────────────────────────────

    /**
     * Donne {@code count} boosters du tier indiqué au joueur.
     * No-op si rolynk_cards est absent ou si {@code count <= 0}.
     * MAIN THREAD uniquement.
     */
    public static void giveBooster(ServerPlayer sp, Tier tier, int count) {
        if (count <= 0 || sp == null || !ensureLoaded()) return;
        Object tierEnum = tierObj(tier);
        if (tierEnum == null) return; // ce tier n'existe pas dans la version installée
        try {
            giveBoosterMethod.invoke(null, sp, tierEnum, count);
        } catch (Throwable e) {
            LOGGER.warn("[Rolynk] giveBooster({} x{}) a échoué : {}", tier, count, e.getMessage());
        }
    }
}
