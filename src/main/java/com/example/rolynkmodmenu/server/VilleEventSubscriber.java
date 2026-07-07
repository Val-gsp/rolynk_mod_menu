package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import com.example.rolynkmodmenu.network.VilleProfilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'événements serveur pour le système de villes et de profil.
 * - Init/arrêt du pool DB et chargement des caches au démarrage
 * - Protection des chunks claimés (casse / pose / interactions / explosions)
 * - Affichage de l'actionbar lors du changement de chunk
 * - Enregistrement des commandes /ville
 * - Suivi du login/logout pour le profil (statut, heures de jeu)
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class VilleEventSubscriber {

    private VilleEventSubscriber() {}

    /** Timestamps de login pour calculer les heures de session à la déconnexion. */
    private static final ConcurrentHashMap<String, Long> LOGIN_TIMES = new ConcurrentHashMap<>();

    // ── Cycle de vie serveur ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Config + pool initialisés explicitement ici (aucun bloc static {} dans le mod)
        RolynkConfig.load();
        Database.init();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!Database.isReady()) return;
        CompletableFuture.runAsync(() -> {
            VilleStore.loadAllCaches();
            JoueurRepository.resetAllStatus();
            // Synchronise OPC sur le main thread une fois les caches prêts
            event.getServer().execute(() ->
                    OPCServerIntegration.syncAllOnStartup(event.getServer()));
        }, BaliseStore.DB_EXECUTOR);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Point unique d'arrêt : draine l'executor (30 s max) puis ferme le pool partagé.
        Database.shutdown();
    }

    // ── Commandes ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        VilleCommandHandler.register(event.getDispatcher());
    }

    // ── Joueur connexion / déconnexion ────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid   = sp.getStringUUID();
        String pseudo = sp.getGameProfile().getName();

        LOGIN_TIMES.put(uuid, System.currentTimeMillis());

        CompletableFuture.runAsync(() -> {
            // Upsert joueur + marquer online + premiere_connexion
            BaliseStore.upsertJoueur(uuid, pseudo);
            JoueurRepository.onLogin(uuid);

            // Recharge l'appartenance ville depuis la DB : les caches sont par-backend,
            // un quitter/rejoindre fait sur un autre serveur doit être répercuté ici.
            VilleStore.refreshMembership(uuid);

            // Envoi du nom de ville au client
            String villeNom = VilleStore.fetchVilleNomForPlayer(uuid);
            sp.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(sp, new VilleProfilePayload(villeNom)));

            // Envoi du profil complet au client (version login = met à jour le status)
            ServerProfileHandler.sendProfileOnLogin(sp);
        }, BaliseStore.DB_EXECUTOR);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String uuid = sp.getStringUUID();

        VilleCommandHandler.onPlayerLogout(sp);
        ServerVilleHandler.onPlayerLogout(sp.getUUID());
        ServerProfileHandler.onPlayerLogout(sp.getUUID());
        ServerBaliseHandler.onPlayerLogout(sp.getUUID());
        ServerRecompenseHandler.onPlayerLogout(sp.getUUID());
        VilleStore.LAST_CHUNK.remove(uuid);

        // Calcul des heures de session
        Long loginTime = LOGIN_TIMES.remove(uuid);
        double sessionHours = loginTime != null
                ? (System.currentTimeMillis() - loginTime) / 3_600_000.0 : 0.0;

        ServerProfileHandler.invalidateCache(uuid);
        CompletableFuture.runAsync(() ->
                JoueurRepository.onLogout(uuid, sessionHours), BaliseStore.DB_EXECUTOR);
    }

    // ── Protection des chunks claimés ─────────────────────────────────────

    /**
     * @return l'ID de la ville propriétaire du chunk si le joueur N'EN est PAS
     *         membre (action à bloquer), ou -1 si l'action est autorisée.
     * Lecture 100 % cache mémoire — aucun hit DB sur le chemin chaud.
     */
    private static int villeBloquante(ServerPlayer sp, BlockPos pos) {
        // Les opérateurs (niveau 2+) ne sont jamais bloqués : ils peuvent
        // utiliser WorldEdit et bâtir n'importe où, même dans un claim.
        if (sp.hasPermissions(2)) return -1;
        int villeId = VilleStore.getVilleOfChunk(
                RolynkConfig.serverName(), pos.getX() >> 4, pos.getZ() >> 4);
        if (villeId == -1) return -1;
        return VilleStore.getVilleIdByUuid(sp.getStringUUID()) == villeId ? -1 : villeId;
    }

    private static void refuser(ServerPlayer sp, int villeId, String action) {
        sp.sendSystemMessage(Component.literal(
                "§cCe chunk appartient à §e" + VilleStore.getVilleNom(villeId)
                + "§c. Tu ne peux pas " + action + " ici."));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        int villeId = villeBloquante(sp, event.getPos());
        if (villeId == -1) return;
        event.setCanceled(true);
        refuser(sp, villeId, "casser de blocs");
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        int villeId = villeBloquante(sp, event.getPos());
        if (villeId == -1) return;
        event.setCanceled(true);
        refuser(sp, villeId, "poser de blocs");
    }

    /**
     * Bloque les interactions des non-membres dans un chunk claimé :
     * coffres, portes, leviers, boutons, seaux (vidage contre un bloc),
     * briquets, trappes... C'était le contournement n°1 de la protection
     * limitée à casse/pose.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        int villeId = villeBloquante(sp, event.getPos());
        if (villeId == -1) return;
        event.setCanceled(true);
        refuser(sp, villeId, "interagir");
    }

    /** Empêche le piétinement des cultures (saut sur terre labourée) par les non-membres. */
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (villeBloquante(sp, event.getPos()) == -1) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        String monde = RolynkConfig.serverName();
        event.getAffectedBlocks().removeIf(pos -> {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            return VilleStore.getVilleOfChunk(monde, cx, cz) != -1;
        });
    }

    // ── Actionbar au changement de chunk ──────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 5 != 0) return;

        int cx = sp.getBlockX() >> 4;
        int cz = sp.getBlockZ() >> 4;
        long packed = ((long) cx << 32) | (cz & 0xFFFFFFFFL);

        String uuid = sp.getStringUUID();
        Long last = VilleStore.LAST_CHUNK.get(uuid);
        if (last != null && last == packed) return;

        VilleStore.LAST_CHUNK.put(uuid, packed);

        String monde = RolynkConfig.serverName();
        int villeId = VilleStore.getVilleOfChunk(monde, cx, cz);
        if (villeId != -1) {
            String nom = VilleStore.getVilleNom(villeId);
            sp.displayClientMessage(Component.literal("§6⚑ §e" + nom), true);
        }
    }
}
