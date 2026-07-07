package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.BaliseListPayload;
import com.example.rolynkmodmenu.network.BaliseListRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler C2S pour la liste de balises.
 *
 * SÉCURITÉ :
 *  – Rate-limiting : 5 s entre deux demandes de liste du même joueur
 *  – L'UUID utilisé est toujours celui fourni par le serveur (jamais le payload)
 */
public final class ServerBaliseHandler {

    private ServerBaliseHandler() {}

    // ── Rate-limiting ──────────────────────────────────────────────────────────

    private static final long LIST_CD_MS = 5_000L;

    private static final ConcurrentHashMap<UUID, Long> BALISE_LIST_CD = new ConcurrentHashMap<>();

    private static boolean throttle(UUID uuid) {
        long now  = System.currentTimeMillis();
        Long last = BALISE_LIST_CD.get(uuid);
        if (last != null && now - last < LIST_CD_MS) return true;
        BALISE_LIST_CD.put(uuid, now);
        return false;
    }

    /** Nettoyage à la déconnexion. */
    public static void onPlayerLogout(UUID uuid) {
        BALISE_LIST_CD.remove(uuid);
    }

    // ── Handler ────────────────────────────────────────────────────────────────

    public static void onListRequest(BaliseListRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(sp.getUUID())) return;

        // UUID fourni par le serveur — jamais depuis le payload
        String uuid = sp.getUUID().toString();

        CompletableFuture.runAsync(() -> {
            List<BaliseStore.BaliseEntry> entries = BaliseStore.getBalises(uuid);
            int max = BaliseStore.getMaxBalises(uuid);
            List<BaliseListPayload.BaliseEntry> net = entries.stream()
                    .map(e -> new BaliseListPayload.BaliseEntry(
                            e.nom(), e.monde(), e.x(), e.y(), e.z()))
                    .toList();
            ctx.enqueueWork(() ->
                    PacketDistributor.sendToPlayer(sp, new BaliseListPayload(net, max)));
        }, BaliseStore.DB_EXECUTOR);
    }
}
