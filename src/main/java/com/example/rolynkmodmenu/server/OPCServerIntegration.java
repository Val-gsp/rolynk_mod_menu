package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intégration OPC (Open Parties and Claims) pour la visualisation Xaero Minimap.
 * Tout passe par réflexion Java — aucun import direct d'OPC.
 * Si OPC n'est pas installé, toutes les méthodes retournent silencieusement.
 * Toutes les méthodes publiques doivent être appelées depuis le main thread.
 */
public final class OPCServerIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    // null = pas encore testé, true = disponible, false = absent
    private static Boolean available = null;

    private OPCServerIntegration() {}

    // ── Détection ─────────────────────────────────────────────────────────

    public static boolean isAvailable() {
        if (available == null) {
            available = ModList.get().isLoaded("openpartiesandclaims");
            if (available) {
                LOGGER.info("[Rolynk] Open Parties and Claims détecté — minimap activée.");
            } else {
                LOGGER.info("[Rolynk] Open Parties and Claims absent — minimap désactivée.");
            }
        }
        return available;
    }

    // ── UUID et couleur déterministes par ville ────────────────────────────

    private static UUID villeUUID(int villeId) {
        return UUID.nameUUIDFromBytes(
                ("rolynk_ville_" + villeId).getBytes(StandardCharsets.UTF_8));
    }

    /** Distribution HSV via golden ratio angle — IDs consécutifs = teintes très distinctes. */
    static int colorForVille(int villeId) {
        float hue = (villeId * 137.508f) % 360f;
        float s = 0.65f, v = 0.85f;
        float h = hue / 60f;
        int i = (int) h;
        float f = h - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    // ── Accès à l'API OPC ─────────────────────────────────────────────────

    private static Object getAPI(MinecraftServer server) throws Exception {
        Class<?> cls = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        Method m = findMethod(cls, "get");
        return m.invoke(null, server);
    }

    // ── Opérations publiques ──────────────────────────────────────────────

    /** Enregistre un chunk claimé dans OPC. Main thread uniquement. */
    public static void registerClaim(MinecraftServer server, int villeId, String villeNom, int cx, int cz) {
        if (!isAvailable()) return;
        try {
            Object api = getAPI(server);
            Object mgr = call0(api, "getServerClaimsManager");
            ResourceLocation dim = ResourceLocation.withDefaultNamespace("overworld");
            UUID uid = villeUUID(villeId);
            call(mgr, "claim", dim, uid, 0, cx, cz, false);
            applyConfig(api, uid, villeNom, villeId);
        } catch (Exception e) {
            LOGGER.warn("[Rolynk] OPC registerClaim failed: {}", e.getMessage());
        }
    }

    /** Retire un chunk d'OPC. Main thread uniquement. */
    public static void unregisterClaim(MinecraftServer server, int cx, int cz) {
        if (!isAvailable()) return;
        try {
            Object api = getAPI(server);
            Object mgr = call0(api, "getServerClaimsManager");
            ResourceLocation dim = ResourceLocation.withDefaultNamespace("overworld");
            call(mgr, "unclaim", dim, cx, cz);
        } catch (Exception e) {
            LOGGER.warn("[Rolynk] OPC unregisterClaim failed: {}", e.getMessage());
        }
    }

    /** Retire tous les chunks d'une ville dissoute d'OPC. Main thread uniquement. */
    public static void unregisterAll(MinecraftServer server, List<int[]> claims) {
        if (!isAvailable() || claims.isEmpty()) return;
        try {
            Object api = getAPI(server);
            Object mgr = call0(api, "getServerClaimsManager");
            ResourceLocation dim = ResourceLocation.withDefaultNamespace("overworld");
            for (int[] pos : claims) {
                try { call(mgr, "unclaim", dim, pos[0], pos[1]); }
                catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warn("[Rolynk] OPC unregisterAll failed: {}", e.getMessage());
        }
    }

    /**
     * Synchronise tous les claims Rolynk dans OPC au démarrage.
     * À appeler APRÈS loadAllCaches() — lit uniquement depuis les caches mémoire,
     * pas de requête DB. Main thread uniquement.
     */
    public static void syncAllOnStartup(MinecraftServer server) {
        if (!isAvailable()) return;
        try {
            Object api = getAPI(server);
            Object mgr = call0(api, "getServerClaimsManager");
            ResourceLocation dim = ResourceLocation.withDefaultNamespace("overworld");
            int count = 0;

            for (Map.Entry<Integer, String> villeEntry : VilleStore.VILLE_NOM_CACHE.entrySet()) {
                int villeId  = villeEntry.getKey();
                String nom   = villeEntry.getValue();
                UUID uid     = villeUUID(villeId);
                for (int[] pos : VilleStore.getVilleClaims(villeId)) {
                    try {
                        call(mgr, "claim", dim, uid, 0, pos[0], pos[1], false);
                        count++;
                    } catch (Exception ignored) {}
                }
                try { applyConfig(api, uid, nom, villeId); }
                catch (Exception ignored) {}
            }
            LOGGER.info("[Rolynk] OPC sync démarrage : {} chunks enregistrés.", count);
        } catch (Exception e) {
            LOGGER.warn("[Rolynk] OPC syncAllOnStartup failed: {}", e.getMessage());
        }
    }

    // ── Configuration OPC de la ville (nom, couleur, notifs) ─────────────

    private static void applyConfig(Object api, UUID uid, String villeNom, int villeId) throws Exception {
        Object configs = call0(api, "getPlayerConfigs");
        Object config  = call1(configs, "getLoadedConfig", UUID.class, uid);
        if (config == null) return;

        Class<?> optsCls   = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimOptionAPI");
        Method   tryToSet  = findMethod(config.getClass(), "tryToSet");

        tryToSet.invoke(config, optsCls.getField("CLAIMS_NAME").get(null),  villeNom);
        tryToSet.invoke(config, optsCls.getField("CLAIMS_COLOR").get(null), colorForVille(villeId));

        // Désactiver les notifs OPC (le mod affiche sa propre actionbar)
        for (String optName : new String[]{
                "ENTER_CLAIM_NOTIFICATIONS", "EXIT_CLAIM_NOTIFICATIONS",
                "ENTER_SUBCLAIM_NOTIFICATIONS", "EXIT_SUBCLAIM_NOTIFICATIONS"}) {
            try { tryToSet.invoke(config, optsCls.getField(optName).get(null), false); }
            catch (Exception ignored) {}
        }
    }

    // ── Helpers de réflexion ──────────────────────────────────────────────

    private static Method findMethod(Class<?> cls, String name) throws NoSuchMethodException {
        for (Method m : cls.getMethods())
            if (m.getName().equals(name)) return m;
        throw new NoSuchMethodException(cls.getSimpleName() + "." + name);
    }

    private static Object call0(Object obj, String name) throws Exception {
        for (Method m : obj.getClass().getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == 0)
                return m.invoke(obj);
        throw new NoSuchMethodException(obj.getClass().getSimpleName() + "." + name + "()");
    }

    private static Object call1(Object obj, String name, Class<?> type, Object arg) throws Exception {
        for (Method m : obj.getClass().getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(type))
                return m.invoke(obj, arg);
        throw new NoSuchMethodException(obj.getClass().getSimpleName() + "." + name);
    }

    private static Object call(Object obj, String name, Object... args) throws Exception {
        for (Method m : obj.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            try { return m.invoke(obj, args); }
            catch (IllegalArgumentException ignored) {}
        }
        throw new NoSuchMethodException(obj.getClass().getSimpleName() + "." + name + "/" + args.length);
    }
}
