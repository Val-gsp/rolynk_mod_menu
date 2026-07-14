package com.example.rolynkmodmenu;

import com.example.rolynkmodmenu.client.network.ClientPayloadHandlers;
import com.example.rolynkmodmenu.network.*;
import com.example.rolynkmodmenu.server.ServerBaliseHandler;
import com.example.rolynkmodmenu.server.ServerBoutiqueHandler;
import com.example.rolynkmodmenu.server.ServerTradeHandler;
import com.example.rolynkmodmenu.server.ServerProfilRpHandler;
import com.example.rolynkmodmenu.server.ServerProfileHandler;
import com.example.rolynkmodmenu.server.ServerSkinHandler;
import com.example.rolynkmodmenu.server.ServerRecompenseHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Point d'entrée du mod Rolynk Mod Menu.
 *
 * Cette classe est chargée des deux côtés : elle ne contient AUCUN code
 * client ni serveur — uniquement l'enregistrement des payloads, qui pointe
 * par method reference vers {@link ClientPayloadHandlers} (S2C, jamais
 * initialisé sur serveur dédié) et vers les handlers du package server
 * (C2S, jamais initialisés sur un client pur).
 */
@Mod(RolynkModMenu.MOD_ID)
public class RolynkModMenu {

    public static final String MOD_ID = "rolynk_mod_menu";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RolynkModMenu(IEventBus modEventBus) {
        LOGGER.info("[RolynkModMenu] Initialisation...");
        modEventBus.addListener(RolynkModMenu::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ── Profil ────────────────────────────────────────────────────────
        registrar.playToServer(ProfileRequestPayload.TYPE,
                ProfileRequestPayload.STREAM_CODEC,
                ServerProfileHandler::onProfileRequest);
        registrar.playToClient(ProfilePayload.TYPE,
                ProfilePayload.STREAM_CODEC,
                ClientPayloadHandlers::onProfile);
        registrar.playToServer(ProfilJoueurRequestPayload.TYPE,
                ProfilJoueurRequestPayload.STREAM_CODEC,
                ServerProfileHandler::onProfilJoueurRequest);
        registrar.playToClient(ProfilJoueurPayload.TYPE,
                ProfilJoueurPayload.STREAM_CODEC,
                ClientPayloadHandlers::onProfilJoueur);

        // ── Profil RP (création première connexion + consultation) ───────
        registrar.playToServer(ProfilRpRequestPayload.TYPE,
                ProfilRpRequestPayload.STREAM_CODEC,
                ServerProfilRpHandler::onRequest);
        registrar.playToServer(ProfilRpCreatePayload.TYPE,
                ProfilRpCreatePayload.STREAM_CODEC,
                ServerProfilRpHandler::onCreate);
        registrar.playToClient(ProfilRpPayload.TYPE,
                ProfilRpPayload.STREAM_CODEC,
                ClientPayloadHandlers::onProfilRp);
        registrar.playToClient(ProfilRpResultPayload.TYPE,
                ProfilRpResultPayload.STREAM_CODEC,
                ClientPayloadHandlers::onProfilRpResult);

        // ── Skin personnalisé ─────────────────────────────────────────────
        registrar.playToServer(SkinApplyPayload.TYPE,
                SkinApplyPayload.STREAM_CODEC,
                ServerSkinHandler::onApply);
        registrar.playToClient(SkinResultPayload.TYPE,
                SkinResultPayload.STREAM_CODEC,
                ClientPayloadHandlers::onSkinResult);

        // ── Balises ───────────────────────────────────────────────────────
        registrar.playToServer(BaliseListRequestPayload.TYPE,
                BaliseListRequestPayload.STREAM_CODEC,
                ServerBaliseHandler::onListRequest);
        registrar.optional().playToClient(SwitchServerPayload.TYPE,
                SwitchServerPayload.STREAM_CODEC,
                ClientPayloadHandlers::onSwitchServer);
        registrar.playToClient(BaliseListPayload.TYPE,
                BaliseListPayload.STREAM_CODEC,
                ClientPayloadHandlers::onBaliseList);

        // ── Récompenses quotidiennes ──────────────────────────────────────
        registrar.playToServer(RecompensesRequestPayload.TYPE,
                RecompensesRequestPayload.STREAM_CODEC,
                ServerRecompenseHandler::onRequest);
        registrar.playToServer(RecompenseClaimPayload.TYPE,
                RecompenseClaimPayload.STREAM_CODEC,
                ServerRecompenseHandler::onClaim);
        registrar.playToServer(ExplorationClaimPayload.TYPE,
                ExplorationClaimPayload.STREAM_CODEC,
                ServerRecompenseHandler::onExplorationClaim);
        registrar.playToClient(RecompensesPayload.TYPE,
                RecompensesPayload.STREAM_CODEC,
                ClientPayloadHandlers::onRecompenses);

        // ── Boutique Jeux ─────────────────────────────────────────────────
        registrar.playToServer(BoutiqueCatalogRequestPayload.TYPE,
                BoutiqueCatalogRequestPayload.STREAM_CODEC,
                ServerBoutiqueHandler::onCatalogRequest);
        registrar.playToServer(BoutiqueVendrePayload.TYPE,
                BoutiqueVendrePayload.STREAM_CODEC,
                ServerBoutiqueHandler::onVendre);
        registrar.playToClient(BoutiqueCatalogPayload.TYPE,
                BoutiqueCatalogPayload.STREAM_CODEC,
                ClientPayloadHandlers::onBoutiqueCatalog);

        // ── Trade — C2S ───────────────────────────────────────────────────
        registrar.playToServer(TradeListRequestPayload.TYPE,
                TradeListRequestPayload.STREAM_CODEC,
                ServerTradeHandler::onListRequest);
        registrar.playToServer(TradeRequestPayload.TYPE,
                TradeRequestPayload.STREAM_CODEC,
                ServerTradeHandler::onRequest);
        registrar.playToServer(TradeRespondPayload.TYPE,
                TradeRespondPayload.STREAM_CODEC,
                ServerTradeHandler::onRespond);
        registrar.playToServer(TradeActionPayload.TYPE,
                TradeActionPayload.STREAM_CODEC,
                ServerTradeHandler::onAction);

        // ── Trade — S2C ───────────────────────────────────────────────────
        registrar.playToClient(TradeListPayload.TYPE,
                TradeListPayload.STREAM_CODEC,
                ClientPayloadHandlers::onTradeList);
        registrar.playToClient(TradeOpenPayload.TYPE,
                TradeOpenPayload.STREAM_CODEC,
                ClientPayloadHandlers::onTradeOpen);
        registrar.playToClient(TradeStatePayload.TYPE,
                TradeStatePayload.STREAM_CODEC,
                ClientPayloadHandlers::onTradeState);
        registrar.playToClient(TradeClosePayload.TYPE,
                TradeClosePayload.STREAM_CODEC,
                ClientPayloadHandlers::onTradeClose);

    }
}
