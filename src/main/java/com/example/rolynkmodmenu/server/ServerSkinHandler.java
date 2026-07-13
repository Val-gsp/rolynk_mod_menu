package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.SkinApplyPayload;
import com.example.rolynkmodmenu.network.SkinResultPayload;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion du skin personnalisé : réception du choix client, signature via
 * MineSkin, stockage, application au GameProfile et rafraîchissement en direct.
 *
 * SÉCURITÉ / robustesse :
 *  – rate-limiting par joueur ;
 *  – appels réseau/DB sur DB_EXECUTOR (jamais le main thread) ;
 *  – toute l'application de skin est défensive : un échec ne peut pas crasher
 *    le serveur (au pire, le skin par défaut est conservé).
 */
public final class ServerSkinHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEX = "textures";
    private static final long APPLY_CD_MS = 4_000L;

    private static final ConcurrentHashMap<UUID, Long> APPLY_CD = new ConcurrentHashMap<>();

    private ServerSkinHandler() {}

    public static void onPlayerLogout(UUID uuid) { APPLY_CD.remove(uuid); }

    private static boolean throttle(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = APPLY_CD.get(uuid);
        if (last != null && now - last < APPLY_CD_MS) return true;
        APPLY_CD.put(uuid, now);
        return false;
    }

    // ── Réception du choix (C2S) ──────────────────────────────────────────

    public static void onApply(SkinApplyPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(sp.getUUID())) return;

        String url = payload.url() == null ? "" : payload.url().trim();

        // URL vide : le joueur garde son skin par défaut → simple accusé.
        if (url.isEmpty()) {
            PacketDistributor.sendToPlayer(sp, new SkinResultPayload(true, "Skin par défaut conservé."));
            return;
        }
        if (!SkinService.isValidUrl(url)) {
            PacketDistributor.sendToPlayer(sp, new SkinResultPayload(false,
                    "URL invalide (doit commencer par http et pointer une image)."));
            return;
        }

        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            SkinService.Resultat res = SkinService.generateFromUrl(url);
            if (!res.isOk()) {
                sp.getServer().execute(() -> PacketDistributor.sendToPlayer(sp,
                        new SkinResultPayload(false, messageErreur(res.erreur()))));
                return;
            }
            SkinService.SignedTexture tex = res.texture();
            SkinRepository.upsert(uuid, url, tex.value(), tex.signature());
            sp.getServer().execute(() -> {
                boolean applied = applyToProfile(sp, tex.value(), tex.signature());
                PacketDistributor.sendToPlayer(sp, new SkinResultPayload(applied,
                        applied ? "Skin appliqué !" : "Skin enregistré — visible à la reconnexion."));
                LOGGER.info("[Skin] {} a défini un skin personnalisé", sp.getGameProfile().getName());
            });
        }, BaliseStore.DB_EXECUTOR);
    }

    /** Message clair selon la cause de l'échec. */
    private static String messageErreur(SkinService.Erreur e) {
        return switch (e) {
            case URL            -> "URL invalide (doit commencer par http).";
            case TELECHARGEMENT -> "Image inaccessible — vérifie le lien (lien Discord expiré ?).";
            case FORMAT         -> "L'image doit être un skin PNG 64×64.";
            case MINESKIN       -> "Service de skin indisponible — réessaie dans un instant.";
        };
    }

    // ── Application au login (ré-applique le skin stocké) ─────────────────

    /** Charge le skin stocké et l'applique. À appeler depuis DB_EXECUTOR au login. */
    public static void applyStoredSkin(ServerPlayer sp) {
        SkinRepository.StoredSkin skin = SkinRepository.fetch(sp.getStringUUID());
        if (skin == null) return;
        sp.getServer().execute(() -> applyToProfile(sp, skin.value(), skin.signature()));
    }

    // ── Cœur : pose la texture sur le GameProfile + rafraîchit l'affichage ──

    /**
     * Remplace la propriété "textures" du GameProfile et rafraîchit le rendu
     * pour tous les joueurs (retrait + ré-ajout de l'entrée player-info).
     * Doit tourner sur le MAIN THREAD. Entièrement défensif.
     * @return true si l'application (et le broadcast) a réussi.
     */
    private static boolean applyToProfile(ServerPlayer sp, String value, String signature) {
        try {
            GameProfile profile = sp.getGameProfile();
            PropertyMap props = profile.getProperties();
            props.removeAll(TEX);
            props.put(TEX, new Property(TEX, value, signature));

            var players = sp.getServer().getPlayerList();
            // Retire puis ré-ajoute l'entrée du joueur : les clients rechargent
            // sa texture (tab list + modèle de l'entité).
            players.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(sp.getUUID())));
            players.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(sp)));
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[Skin] Application live échouée pour {} : {} (skin visible à la reconnexion)",
                    sp.getGameProfile().getName(), t.toString());
            return false;
        }
    }
}
