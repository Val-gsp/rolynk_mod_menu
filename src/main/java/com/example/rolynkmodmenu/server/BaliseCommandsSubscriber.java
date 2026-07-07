package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Enregistre les commandes /balise et gère les événements de connexion joueur.
 * L'arrêt des pools DB est centralisé dans VilleEventSubscriber.onServerStopping().
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class BaliseCommandsSubscriber {

    private BaliseCommandsSubscriber() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BaliseCommandHandler.register(event.getDispatcher());
    }

    /** Pending teleport à la connexion (upsert joueur géré par VilleEventSubscriber). */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getUUID().toString();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // Pending teleport cross-serveur
            int[] pending = BaliseStore.getPendingTeleport(uuid);
            if (pending == null) return;

            sp.getServer().execute(() -> {
                sp.teleportTo(pending[0] + 0.5, pending[1], pending[2] + 0.5);
                sp.sendSystemMessage(Component.literal(
                        "§aTéléporté à ta balise ("
                        + pending[0] + ", " + pending[1] + ", " + pending[2] + ")"));
                java.util.concurrent.CompletableFuture.runAsync(
                        () -> BaliseStore.clearPendingTeleport(uuid), BaliseStore.DB_EXECUTOR);
            });
        }, BaliseStore.DB_EXECUTOR);
    }
}
