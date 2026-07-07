package com.example.rolynkmodmenu;

import com.example.rolynkmodmenu.client.network.ClientPayloadHandlers;
import com.example.rolynkmodmenu.network.*;
import com.example.rolynkmodmenu.server.ServerBaliseHandler;
import com.example.rolynkmodmenu.server.ServerProfileHandler;
import com.example.rolynkmodmenu.server.ServerRecompenseHandler;
import com.example.rolynkmodmenu.server.ServerVilleHandler;
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
        registrar.playToServer(VoteVilleActionPayload.TYPE,
                VoteVilleActionPayload.STREAM_CODEC,
                ServerRecompenseHandler::onVoter);
        registrar.playToServer(ExplorationClaimPayload.TYPE,
                ExplorationClaimPayload.STREAM_CODEC,
                ServerRecompenseHandler::onExplorationClaim);
        registrar.playToClient(RecompensesPayload.TYPE,
                RecompensesPayload.STREAM_CODEC,
                ClientPayloadHandlers::onRecompenses);

        // ── Villes — C2S ──────────────────────────────────────────────────
        registrar.playToServer(VilleListRequestPayload.TYPE,
                VilleListRequestPayload.STREAM_CODEC,
                ServerVilleHandler::onListRequest);
        registrar.playToServer(VilleMembresRequestPayload.TYPE,
                VilleMembresRequestPayload.STREAM_CODEC,
                ServerVilleHandler::onMembresRequest);
        registrar.playToServer(VilleActionPayload.TYPE,
                VilleActionPayload.STREAM_CODEC,
                ServerVilleHandler::onAction);
        registrar.playToServer(VilleLogsRequestPayload.TYPE,
                VilleLogsRequestPayload.STREAM_CODEC,
                ServerVilleHandler::onLogsRequest);
        registrar.playToServer(VilleDemandesRequestPayload.TYPE,
                VilleDemandesRequestPayload.STREAM_CODEC,
                ServerVilleHandler::onDemandesRequest);

        // ── Villes — S2C ──────────────────────────────────────────────────
        registrar.playToClient(VilleListPayload.TYPE,
                VilleListPayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleList);
        registrar.playToClient(VilleMembresPayload.TYPE,
                VilleMembresPayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleMembres);
        registrar.playToClient(VilleActionResultPayload.TYPE,
                VilleActionResultPayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleActionResult);
        registrar.playToClient(VilleLogsBanquePayload.TYPE,
                VilleLogsBanquePayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleLogs);
        registrar.playToClient(VilleDemandesPayload.TYPE,
                VilleDemandesPayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleDemandes);
        registrar.playToClient(VilleProfilePayload.TYPE,
                VilleProfilePayload.STREAM_CODEC,
                ClientPayloadHandlers::onVilleProfile);
    }
}
