package com.example.rolynkmodmenu.client.network;

import com.example.rolynkmodmenu.client.balise.BaliseDataManager;
import com.example.rolynkmodmenu.client.boutique.BoutiqueDataManager;
import com.example.rolynkmodmenu.client.grade.GradeCache;
import com.example.rolynkmodmenu.client.profile.ProfileDataManager;
import com.example.rolynkmodmenu.client.profile.ProfilJoueurDataManager;
import com.example.rolynkmodmenu.client.recompense.RecompensesDataManager;
import com.example.rolynkmodmenu.client.screen.ville.GestionVilleScreen;
import com.example.rolynkmodmenu.client.ville.*;
import com.example.rolynkmodmenu.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handlers des payloads S2C — classe UNIQUEMENT chargée côté client.
 *
 * La classe @Mod ({@code RolynkModMenu}) ne référence ces méthodes que par
 * method reference : sur un serveur dédié, cette classe n'est jamais
 * initialisée (les handlers S2C n'y sont jamais invoqués), donc aucune
 * classe client (Minecraft, screens, data managers) n'y est chargée.
 * C'est le pattern sided-safe recommandé par la doc NeoForge.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {}

    // ── Profil ────────────────────────────────────────────────────────────

    /** Profil du joueur connecté → alimente aussi GradeCache. */
    public static void onProfile(ProfilePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ProfileDataManager.handleS2C(payload);
            GradeCache.setMonGrade(payload.grade());
        });
    }

    /** Profil d'un autre joueur → alimente aussi GradeCache pour ce joueur. */
    public static void onProfilJoueur(ProfilJoueurPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ProfilJoueurDataManager.handleS2C(payload);
            GradeCache.setGrade(payload.pseudo(), payload.grade());
        });
    }

    // ── Balises ───────────────────────────────────────────────────────────

    public static void onBaliseList(BaliseListPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                BaliseDataManager.setBalises(
                        payload.entries().stream()
                                .map(e -> new BaliseDataManager.Balise(
                                        e.nom(), e.monde(), e.x(), e.y(), e.z()))
                                .toList(),
                        payload.maxBalises()));
    }

    /**
     * Ordre de changer de serveur (téléportation cross-serveur).
     * SÉCURITÉ : le nom de serveur est filtré avant d'être injecté dans la
     * commande — un serveur malveillant ne peut pas faire exécuter autre
     * chose que "/server <nom-sain>" au client.
     */
    public static void onSwitchServer(SwitchServerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return;
            String safeName = payload.serverName().replaceAll("[^a-zA-Z0-9_\\-]", "");
            if (!safeName.isEmpty()) {
                mc.getConnection().sendCommand("server " + safeName);
            }
        });
    }

    // ── Récompenses quotidiennes ──────────────────────────────────────────

    public static void onRecompenses(RecompensesPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RecompensesDataManager.set(payload));
    }

    // ── Villes ────────────────────────────────────────────────────────────

    public static void onVilleList(VilleListPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> VilleListDataManager.setVilles(payload.entries()));
    }

    public static void onVilleMembres(VilleMembresPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                VilleMembresDataManager.set(payload.membres(), payload.myGrade(), payload.playerMoney()));
    }

    public static void onVilleActionResult(VilleActionResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Met à jour la money personnelle si le serveur l'a renvoyée
            if (payload.newPlayerMoney() >= 0)
                VilleMembresDataManager.setPlayerMoney(payload.newPlayerMoney());
            if (GestionVilleScreen.current != null)
                GestionVilleScreen.current.handleActionResult(payload);
        });
    }

    public static void onVilleLogs(VilleLogsBanquePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> VilleLogsBanqueDataManager.setLogs(payload.entries()));
    }

    public static void onVilleDemandes(VilleDemandesPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> VilleDemandesDataManager.setDemandes(payload.demandes()));
    }

    public static void onVilleProfile(VilleProfilePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> VilleProfileDataManager.setVilleNom(payload.villeNom()));
    }

    // ── Boutique ──────────────────────────────────────────────────────────

    public static void onBoutiqueCatalog(BoutiqueCatalogPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> BoutiqueDataManager.set(payload.offres()));
    }

    // ── Trade ─────────────────────────────────────────────────────────────

    public static void onTradeList(TradeListPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.example.rolynkmodmenu.client.trade.TradeDataManager.setListe(
                        payload.joueurs(), payload.demandes()));
    }

    /** Demande acceptée : ouvre l'écran de trade des deux côtés.
     *  (délégué à TradeDataManager — sided-safety, voir commentaire là-bas) */
    public static void onTradeOpen(TradeOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.example.rolynkmodmenu.client.trade.TradeDataManager.ouvrirEcran(payload.partenaire()));
    }

    public static void onTradeState(TradeStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.example.rolynkmodmenu.client.trade.TradeDataManager.setState(payload));
    }

    /** Session terminée côté serveur : ferme l'écran sans renvoyer de CANCEL. */
    public static void onTradeClose(TradeClosePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.example.rolynkmodmenu.client.trade.TradeDataManager.fermerEcran());
    }
}
