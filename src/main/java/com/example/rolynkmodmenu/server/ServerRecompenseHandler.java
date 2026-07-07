package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.ExplorationClaimPayload;
import com.example.rolynkmodmenu.network.RecompenseClaimPayload;
import com.example.rolynkmodmenu.network.RecompensesPayload;
import com.example.rolynkmodmenu.network.RecompensesRequestPayload;
import com.example.rolynkmodmenu.network.VoteVilleActionPayload;
import com.example.rolynkmodmenu.util.Money;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlers C2S des récompenses quotidiennes (play time, vote de ville, exploration).
 *
 * SÉCURITÉ — comme partout dans le mod :
 *   – identité = UUID de la connexion, jamais le payload ;
 *   – palier/villeId validés par whitelist (InputValidator) ;
 *   – tous les montants et seuils viennent de RolynkConfig, jamais du client ;
 *   – réclamations atomiques en DB (FOR UPDATE) ;
 *   – rate-limiting sur toutes les actions.
 */
public final class ServerRecompenseHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerRecompenseHandler() {}

    // ── Rate-limiting ─────────────────────────────────────────────────────

    private static final long REQUEST_CD_MS = 2_000L;
    private static final long CLAIM_CD_MS   =   750L;
    private static final long VOTE_CD_MS    = 2_000L;
    private static final long EXPLO_CD_MS   =   750L;

    private static final ConcurrentHashMap<UUID, Long> REQUEST_CD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> CLAIM_CD   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> VOTE_CD    = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> EXPLO_CD   = new ConcurrentHashMap<>();

    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /**
     * Refuse toute réclamation de récompense lorsque le joueur est sur le lobby.
     * @return true si la réclamation est bloquée (un message a été envoyé au joueur).
     */
    private static boolean rejectIfLobby(ServerPlayer sp, IPayloadContext ctx) {
        if (!RolynkConfig.isLobby()) return false;
        ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                "§cVous ne pouvez pas récupérer vos récompenses lorsque vous êtes dans le lobby.")));
        return true;
    }

    /** Nettoyage à la déconnexion. */
    public static void onPlayerLogout(UUID uuid) {
        REQUEST_CD.remove(uuid);
        CLAIM_CD.remove(uuid);
        VOTE_CD.remove(uuid);
        EXPLO_CD.remove(uuid);
    }

    // ── RecompensesRequestPayload ─────────────────────────────────────────

    public static void onRequest(RecompensesRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(REQUEST_CD, sp.getUUID(), REQUEST_CD_MS)) return;

        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            // Flush avant lecture : temps de jeu et distance exacts à la seconde/au bloc près
            RecompenseEventSubscriber.flushNow(uuid);
            ExplorationEventSubscriber.flushNow(uuid);
            sendEtat(sp);
        }, Database.EXECUTOR);
    }

    // ── RecompenseClaimPayload (play time) ────────────────────────────────

    public static void onClaim(RecompenseClaimPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(CLAIM_CD, sp.getUUID(), CLAIM_CD_MS)) return;
        if (rejectIfLobby(sp, ctx)) return;

        int palier  = payload.palier();
        String uuid = sp.getStringUUID();

        if (!InputValidator.isValidPalierRecompense(palier)) {
            LOGGER.warn("[Security] RecompenseClaim: {} a envoyé un palier invalide {}", uuid, palier);
            return;
        }

        CompletableFuture.runAsync(() -> {
            RecompenseEventSubscriber.flushNow(uuid);
            double montant = RolynkConfig.recompensePlaytime(palier);
            String err = RecompenseStore.reclamer(uuid, palier, montant);
            if (err == null) ServerProfileHandler.invalidateCache(uuid); // argent crédité

            // Crédit DB réussi → distribution des boosters sur le main thread
            // (rolynk_cards agit sur l'inventaire). Si le mod cards est absent,
            // giveBoosters() est silencieusement no-op : la money reste créditée.
            ctx.enqueueWork(() -> {
                if (err == null) {
                    giveBoosters(sp, palier);
                    sp.sendSystemMessage(Component.literal(
                            "§aRécompense récupérée : §e+" + Money.entier(montant)
                            + " §aet " + boosterLabel(palier) + " §a!"));
                } else {
                    sp.sendSystemMessage(Component.literal(err));
                }
            });
            sendEtat(sp);
        }, Database.EXECUTOR);
    }

    // ── Boosters rolynk_cards par palier ──────────────────────────────────
    //   30 min → 1 basique
    //   2 h    → 1 amélioré + 1 basique
    //   4 h    → 2 améliorés + 1 basique

    /** Distribue les boosters du palier. Main thread uniquement. No-op si rolynk_cards absent. */
    private static void giveBoosters(ServerPlayer sp, int palier) {
        switch (palier) {
            case 0 -> BoosterIntegration.giveBooster(sp, BoosterIntegration.Tier.BASIC, 1);
            case 1 -> {
                BoosterIntegration.giveBooster(sp, BoosterIntegration.Tier.IMPROVED, 1);
                BoosterIntegration.giveBooster(sp, BoosterIntegration.Tier.BASIC, 1);
            }
            case 2 -> {
                BoosterIntegration.giveBooster(sp, BoosterIntegration.Tier.IMPROVED, 2);
                BoosterIntegration.giveBooster(sp, BoosterIntegration.Tier.BASIC, 1);
            }
            default -> { /* palier déjà validé en amont */ }
        }
    }

    /** Libellé des boosters du palier pour le message de succès. */
    private static String boosterLabel(int palier) {
        return switch (palier) {
            case 0 -> "§b1 booster basique";
            case 1 -> "§b1 booster amélioré §aet §b1 booster basique";
            case 2 -> "§b2 boosters améliorés §aet §b1 booster basique";
            default -> "";
        };
    }

    // ── VoteVilleActionPayload ────────────────────────────────────────────

    public static void onVoter(VoteVilleActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(VOTE_CD, sp.getUUID(), VOTE_CD_MS)) return;
        if (rejectIfLobby(sp, ctx)) return;

        int villeId = payload.villeId();
        String uuid = sp.getStringUUID();

        if (!InputValidator.isValidVilleId(villeId)) {
            LOGGER.warn("[Security] VoteVilleAction: {} a envoyé un villeId invalide {}", uuid, villeId);
            return;
        }

        CompletableFuture.runAsync(() -> {
            double montant = RolynkConfig.recompenseVoteVille();
            String err = VoteVilleStore.voter(uuid, villeId, montant);
            if (err == null) ServerProfileHandler.invalidateCache(uuid); // argent crédité

            ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                    err == null
                            ? "§aVote enregistré ! §e+" + Money.entier(montant) + " §a!"
                            : err)));
            sendEtat(sp);
        }, Database.EXECUTOR);
    }

    // ── ExplorationClaimPayload ───────────────────────────────────────────

    public static void onExplorationClaim(ExplorationClaimPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(EXPLO_CD, sp.getUUID(), EXPLO_CD_MS)) return;
        if (rejectIfLobby(sp, ctx)) return;

        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            // Flush avant claim : le seuil est évalué sur la distance réelle
            ExplorationEventSubscriber.flushNow(uuid);

            int    seuil   = RolynkConfig.explorationSeuilBlocs();
            double montant = RolynkConfig.recompenseExploration();
            String err     = ExplorationStore.reclamer(uuid, seuil, montant);
            if (err == null) ServerProfileHandler.invalidateCache(uuid); // argent crédité

            ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                    err == null
                            ? "§aRécompense d'exploration récupérée : §e+" + Money.entier(montant) + " §a!"
                            : err)));
            sendEtat(sp);
        }, Database.EXECUTOR);
    }

    // ── Envoi de l'état ───────────────────────────────────────────────────

    /**
     * Lit l'état complet du jour (play time + vote ville + exploration) et l'envoie.
     * BLOQUANT — Database.EXECUTOR uniquement.
     */
    static void sendEtat(ServerPlayer sp) {
        String uuid = sp.getStringUUID();

        RecompenseStore.EtatJour          etat       = RecompenseStore.getEtat(uuid);
        VoteVilleStore.EtatVote           etatVote   = VoteVilleStore.getEtat(uuid);
        String                            maVilleNom = VoteVilleStore.getVilleJoueur(uuid);
        ExplorationStore.EtatExploration  etatExplo  = ExplorationStore.getEtat(uuid);

        List<RecompensesPayload.PalierEntry> paliers = new ArrayList<>(RecompenseStore.NB_PALIERS);
        for (int i = 0; i < RecompenseStore.NB_PALIERS; i++) {
            paliers.add(new RecompensesPayload.PalierEntry(
                    RecompenseStore.SEUILS_SECONDES[i],
                    RolynkConfig.recompensePlaytime(i),
                    etat.recuperes()[i]));
        }

        RecompensesPayload out = new RecompensesPayload(
                etat.tempsSecondes(),
                paliers,
                etatVote.aVoteAujourdhui(),
                etatVote.nomVilleVotee(),
                maVilleNom,
                RolynkConfig.recompenseVoteVille(),
                etatExplo.blocsParcourus(),
                etatExplo.recompenseRecue(),
                RolynkConfig.explorationSeuilBlocs(),
                RolynkConfig.recompenseExploration());

        sp.getServer().execute(() -> PacketDistributor.sendToPlayer(sp, out));
    }
}
