package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comptabilise le temps de jeu quotidien pour les récompenses.
 *
 * Principe : on mémorise par joueur l'instant du dernier "flush", et on
 * pousse périodiquement le temps écoulé vers la DB (compteur du jour).
 * Le flush a lieu :
 *   – toutes les 2 minutes de jeu (tick joueur) — résistance aux crashs,
 *   – à la déconnexion (reliquat de session),
 *   – juste avant chaque lecture/claim côté handler (précision exacte
 *     au moment où le joueur regarde l'écran ou réclame).
 *
 * Le temps est cumulé en DB sur la ligne (uuid, jour) — tous les serveurs
 * du réseau Velocity alimentent donc le même compteur quotidien.
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RecompenseEventSubscriber {

    private RecompenseEventSubscriber() {}

    /** uuid → timestamp (ms) du dernier flush de temps de jeu. */
    private static final ConcurrentHashMap<String, Long> LAST_FLUSH = new ConcurrentHashMap<>();

    /** Intervalle du flush périodique : 2 min (2400 ticks). */
    private static final int FLUSH_INTERVAL_TICKS = 20 * 120;

    /** Garde-fou : jamais plus de 10 min créditées d'un coup (saut d'horloge, lag extrême). */
    private static final long MAX_FLUSH_SECONDES = 600;

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!Database.isReady()) return;
        CompletableFuture.runAsync(RecompenseStore::purgerAnciennes, Database.EXECUTOR);
        CompletableFuture.runAsync(VoteVilleStore::purgerAnciens, Database.EXECUTOR);
        CompletableFuture.runAsync(ExplorationStore::purgerAnciennes, Database.EXECUTOR);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        LAST_FLUSH.put(sp.getStringUUID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            flushNow(uuid);
            LAST_FLUSH.remove(uuid);
        }, Database.EXECUTOR);
    }

    // ── Flush périodique ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % FLUSH_INTERVAL_TICKS != 0) return;
        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> flushNow(uuid), Database.EXECUTOR);
    }

    /**
     * Pousse en DB le temps écoulé depuis le dernier flush de ce joueur.
     * BLOQUANT — à appeler depuis Database.EXECUTOR uniquement.
     *
     * Thread-safe : le replace() en CAS garantit qu'un intervalle de temps
     * donné n'est crédité qu'une seule fois, même si plusieurs flushes
     * (tick + requête écran + claim) se déclenchent en parallèle.
     */
    public static void flushNow(String uuid) {
        Long last = LAST_FLUSH.get(uuid);
        if (last == null) return;                       // joueur déconnecté entre-temps
        long now = System.currentTimeMillis();
        long elapsedMs = now - last;
        if (elapsedMs < 1_000) return;                  // rien de significatif à créditer
        if (!LAST_FLUSH.replace(uuid, last, now)) return; // un flush concurrent a gagné
        int secondes = (int) Math.min(elapsedMs / 1_000, MAX_FLUSH_SECONDES);
        RecompenseStore.ajouterTempsJeu(uuid, secondes);
    }
}
