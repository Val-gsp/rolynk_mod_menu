package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.TradeActionPayload;
import com.example.rolynkmodmenu.network.TradeListPayload;
import com.example.rolynkmodmenu.network.TradeListRequestPayload;
import com.example.rolynkmodmenu.network.TradeRequestPayload;
import com.example.rolynkmodmenu.network.TradeRespondPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlers C2S du système de trade.
 *
 * SÉCURITÉ — comme partout dans le mod :
 *   – identité = UUID de la connexion, jamais le payload ;
 *   – pseudos/slots/montants validés (InputValidator) avant usage ;
 *   – TOUTES les mutations de session passent par le main thread
 *     (ctx.enqueueWork) — voir TradeManager pour les garanties anti-dupe ;
 *   – rate-limiting sur toutes les actions.
 */
public final class ServerTradeHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerTradeHandler() {}

    // ── Rate-limiting ─────────────────────────────────────────────────────

    private static final long LIST_CD_MS    = 1_000L;
    private static final long REQUEST_CD_MS = 3_000L;
    private static final long ACTION_CD_MS  =   150L;

    private static final ConcurrentHashMap<UUID, Long> LIST_CD    = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> REQUEST_CD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> ACTION_CD  = new ConcurrentHashMap<>();

    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /** Nettoyage à la déconnexion (+ annulation de session dans TradeManager). */
    public static void onPlayerLogout(UUID uuid) {
        LIST_CD.remove(uuid);
        REQUEST_CD.remove(uuid);
        ACTION_CD.remove(uuid);
        TradeManager.onPlayerLogout(uuid);
    }

    // ── TradeListRequestPayload ───────────────────────────────────────────

    public static void onListRequest(TradeListRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(LIST_CD, sp.getUUID(), LIST_CD_MS)) return;

        ctx.enqueueWork(() -> {
            List<String> joueurs = sp.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> !p.getUUID().equals(sp.getUUID()))
                    .map(p -> p.getGameProfile().getName())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            PacketDistributor.sendToPlayer(sp,
                    new TradeListPayload(joueurs, TradeManager.demandesPour(sp)));
        });
    }

    // ── TradeRequestPayload ───────────────────────────────────────────────

    public static void onRequest(TradeRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(REQUEST_CD, sp.getUUID(), REQUEST_CD_MS)) return;

        String cible = payload.cible();
        if (!InputValidator.isValidPseudo(cible)) {
            LOGGER.warn("[Security] TradeRequest: {} a envoyé un pseudo invalide", sp.getStringUUID());
            return;
        }
        if (RolynkConfig.isLobby()) {
            ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                    "§cLe trade n'est pas disponible dans le lobby.")));
            return;
        }

        ctx.enqueueWork(() -> {
            ServerPlayer target = sp.getServer().getPlayerList().getPlayerByName(cible);
            if (target == null) {
                sp.sendSystemMessage(Component.literal(
                        "§c" + cible + " n'est pas sur ce serveur (le trade exige d'être sur le même monde)."));
                return;
            }
            String err = TradeManager.demander(sp, target);
            if (err != null) sp.sendSystemMessage(Component.literal(err));
            else sp.sendSystemMessage(Component.literal(
                    "§aDemande envoyée à §e" + cible + "§a (expire dans 60s)."));
        });
    }

    // ── TradeRespondPayload ───────────────────────────────────────────────

    public static void onRespond(TradeRespondPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(ACTION_CD, sp.getUUID(), ACTION_CD_MS)) return;

        String demandeur = payload.demandeur();
        if (!InputValidator.isValidPseudo(demandeur)) return;

        ctx.enqueueWork(() -> {
            ServerPlayer requester = sp.getServer().getPlayerList().getPlayerByName(demandeur);
            if (requester == null) {
                sp.sendSystemMessage(Component.literal("§c" + demandeur + " s'est déconnecté."));
                return;
            }
            TradeManager.repondre(sp, requester, payload.accepte());
        });
    }

    // ── TradeActionPayload ────────────────────────────────────────────────

    public static void onAction(TradeActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(ACTION_CD, sp.getUUID(), ACTION_CD_MS)) return;

        int action = payload.action();
        int iv     = payload.intValue();
        int iv2    = payload.intValue2();
        double dv  = payload.doubleValue();

        // Validation structurelle AVANT le main thread
        switch (action) {
            case TradeActionPayload.ADD_ITEM -> {
                if (!InputValidator.isValidSlotInventaire(iv) || iv2 < 1 || iv2 > 3456) {
                    LOGGER.warn("[Security] TradeAction: {} slot/quantité invalide {}/{}",
                            sp.getStringUUID(), iv, iv2);
                    return;
                }
            }
            case TradeActionPayload.REMOVE_ITEM -> {
                if (iv < 0 || iv >= TradeManager.MAX_OFFRE) return;
            }
            case TradeActionPayload.SET_MONEY -> {
                if (!InputValidator.isValidMontantTrade(dv)) {
                    LOGGER.warn("[Security] TradeAction: {} montant invalide {}", sp.getStringUUID(), dv);
                    return;
                }
            }
            case TradeActionPayload.CONFIRM, TradeActionPayload.UNCONFIRM, TradeActionPayload.CANCEL -> { }
            default -> { return; }
        }

        ctx.enqueueWork(() -> {
            switch (action) {
                case TradeActionPayload.ADD_ITEM    -> TradeManager.ajouterItem(sp, iv, iv2);
                case TradeActionPayload.REMOVE_ITEM -> TradeManager.retirerItem(sp, iv);
                case TradeActionPayload.SET_MONEY   -> TradeManager.setArgent(sp, InputValidator.roundMontant(dv));
                case TradeActionPayload.CONFIRM     -> TradeManager.confirmer(sp, true);
                case TradeActionPayload.UNCONFIRM   -> TradeManager.confirmer(sp, false);
                case TradeActionPayload.CANCEL      -> TradeManager.annuler(sp, "§7Trade annulé.");
            }
        });
    }
}
