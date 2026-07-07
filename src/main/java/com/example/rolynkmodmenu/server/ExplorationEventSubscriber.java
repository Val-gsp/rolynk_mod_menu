package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suivi du déplacement horizontal (XZ) des joueurs pour la récompense d'exploration.
 *
 * Principe : on compare la position XZ du joueur à chaque tick et on accumule
 * la distance en mémoire. Un flush en DB se produit :
 *   – toutes les 2 minutes (résistance aux crashs),
 *   – à la déconnexion (reliquat de session),
 *   – juste avant la lecture de l'état ou le claim (précision maximale).
 *
 * Seul l'axe XZ est compté (déplacement horizontal) : cela mesure la surface
 * effectivement explorée et évite de favoriser les ascenseurs ou puits verticaux.
 *
 * Anti-triche : le déplacement est plafonné à {@link #MAX_BLOCS_PAR_TICK}
 * par tick. Toute valeur supérieure (téléportation, lag spike extrême) est
 * simplement ignorée — la position est quand même mise à jour pour que le tick
 * suivant reparte du bon endroit.
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ExplorationEventSubscriber {

    private ExplorationEventSubscriber() {}

    /**
     * uuid → [x, z] dernière position connue.
     * Stocke uniquement l'axe horizontal (XZ).
     */
    private static final ConcurrentHashMap<String, double[]> LAST_POS     = new ConcurrentHashMap<>();

    /**
     * uuid → blocs XZ accumulés non encore flushés en DB.
     * Mis à jour via ConcurrentHashMap.merge() (atomique).
     */
    private static final ConcurrentHashMap<String, Double>   PENDING_BLOCS = new ConcurrentHashMap<>();

    /**
     * Plafond par tick : sprint sur glace + haste ≈ 3 blocs/tick,
     * élytra + feu d'artifice ≈ 4-5 blocs/tick.
     * Un cap à 8 blocs/tick élimine les téléportations sans pénaliser le jeu légitime.
     */
    private static final double MAX_BLOCS_PAR_TICK = 8.0;

    /** Flush périodique : 2 min (2400 ticks). */
    private static final int FLUSH_INTERVAL_TICKS = 20 * 120;

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getStringUUID();
        // Enregistrer la position initiale — le 1er tick ne comptera pas de distance
        LAST_POS.put(uuid, new double[]{sp.getX(), sp.getZ()});
        PENDING_BLOCS.put(uuid, 0.0);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            flushNow(uuid);
            LAST_POS.remove(uuid);
            PENDING_BLOCS.remove(uuid);
        }, Database.EXECUTOR);
    }

    // ── Tick de mouvement ─────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid  = sp.getStringUUID();
        double curX  = sp.getX();
        double curZ  = sp.getZ();

        double[] last = LAST_POS.get(uuid);
        if (last != null) {
            double dx   = curX - last[0];
            double dz   = curZ - last[1];
            double dist = Math.sqrt(dx * dx + dz * dz);

            // On accumule seulement si la distance est plausible
            if (dist > 0 && dist <= MAX_BLOCS_PAR_TICK) {
                PENDING_BLOCS.merge(uuid, dist, Double::sum);
            }
            // Si dist > MAX : téléportation / lag spike — on ignore le delta
            // mais on met quand même la position à jour ci-dessous.
        }

        LAST_POS.put(uuid, new double[]{curX, curZ});

        // Flush périodique (tous les FLUSH_INTERVAL_TICKS ticks)
        if (sp.tickCount % FLUSH_INTERVAL_TICKS == 0) {
            CompletableFuture.runAsync(() -> flushNow(uuid), Database.EXECUTOR);
        }
    }

    // ── Flush ─────────────────────────────────────────────────────────────

    /**
     * Pousse en DB les blocs accumulés depuis le dernier flush.
     * BLOQUANT — à appeler depuis Database.EXECUTOR uniquement.
     *
     * Thread-safe : le replace() en CAS garantit qu'un intervalle donné
     * n'est crédité qu'une seule fois, même si deux flushes (tick + claim)
     * se déclenchent en parallèle.
     */
    public static void flushNow(String uuid) {
        Double pending = PENDING_BLOCS.get(uuid);
        if (pending == null || pending < 1.0) return;        // rien de significatif
        if (!PENDING_BLOCS.replace(uuid, pending, 0.0)) return; // flush concurrent a gagné

        int blocs = (int) Math.floor(pending);
        ExplorationStore.ajouterBlocs(uuid, blocs);
    }
}
