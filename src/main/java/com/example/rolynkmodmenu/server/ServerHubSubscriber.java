package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hub d'événements serveur du mod (hors système de villes, retiré en v1.7.0).
 *
 * Responsabilités :
 *  - init/arrêt du pool DB partagé (ServerAboutToStart / ServerStopping) ;
 *  - remise à zéro des statuts au démarrage ;
 *  - orchestration login/logout pour profil, profil RP, skin, balises,
 *    récompenses, boutique et trade (statut, heures de jeu).
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerHubSubscriber {

    private ServerHubSubscriber() {}

    /** Timestamps de login pour calculer les heures de session à la déconnexion. */
    private static final ConcurrentHashMap<String, Long> LOGIN_TIMES = new ConcurrentHashMap<>();

    // ── Cycle de vie serveur ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RolynkConfig.load();
        Database.init();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!Database.isReady()) return;
        CompletableFuture.runAsync(JoueurRepository::resetAllStatus, BaliseStore.DB_EXECUTOR);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Database.shutdown();
    }

    // ── Joueur connexion / déconnexion ────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid   = sp.getStringUUID();
        String pseudo = sp.getGameProfile().getName();

        LOGIN_TIMES.put(uuid, System.currentTimeMillis());

        CompletableFuture.runAsync(() -> {
            BaliseStore.upsertJoueur(uuid, pseudo);
            JoueurRepository.onLogin(uuid);

            // Profil complet (met à jour le statut)
            ServerProfileHandler.sendProfileOnLogin(sp);

            // Profil RP — si absent (1re connexion), le client ouvre la création.
            ServerProfilRpHandler.sendProfilRp(sp);

            // Ré-applique le skin personnalisé stocké (si présent).
            ServerSkinHandler.applyStoredSkin(sp);
        }, BaliseStore.DB_EXECUTOR);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getStringUUID();

        ServerProfileHandler.onPlayerLogout(sp.getUUID());
        ServerProfilRpHandler.onPlayerLogout(sp.getUUID());
        ServerSkinHandler.onPlayerLogout(sp.getUUID());
        ServerBaliseHandler.onPlayerLogout(sp.getUUID());
        ServerRecompenseHandler.onPlayerLogout(sp.getUUID());
        ServerBoutiqueHandler.onPlayerLogout(sp.getUUID());
        ServerTradeHandler.onPlayerLogout(sp.getUUID());

        Long loginTime = LOGIN_TIMES.remove(uuid);
        double sessionHours = loginTime != null
                ? (System.currentTimeMillis() - loginTime) / 3_600_000.0 : 0.0;

        ServerProfileHandler.invalidateCache(uuid);
        CompletableFuture.runAsync(() ->
                JoueurRepository.onLogout(uuid, sessionHours), BaliseStore.DB_EXECUTOR);
    }
}
