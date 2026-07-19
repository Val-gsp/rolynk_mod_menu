package com.example.rolynkmodmenu.client.network;

import com.example.rolynkmodmenu.client.balise.BaliseDataManager;
import com.example.rolynkmodmenu.client.grade.GradeCache;
import com.example.rolynkmodmenu.client.profile.ProfileDataManager;
import com.example.rolynkmodmenu.client.profile.ProfilJoueurDataManager;
import com.example.rolynkmodmenu.client.profile.ProfilRpDataManager;
import com.example.rolynkmodmenu.client.recompense.RecompensesDataManager;
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

    /**
     * Profil RP du joueur connecté. Si absent (première connexion), le data
     * manager lève {@code creationRequise} — ClientGameEvents ouvrira l'écran
     * « Création Profil RP » dès que le joueur sera en jeu.
     */
    public static void onProfilRp(ProfilRpPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProfilRpDataManager.handleS2C(payload));
    }

    /** Résultat de la création du profil RP → transmis à l'écran s'il est ouvert. */
    public static void onProfilRpResult(ProfilRpResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen
                    instanceof com.example.rolynkmodmenu.client.screen.profile.CreationProfilScreen s) {
                s.onResultat(payload.ok(), payload.message());
            }
        });
    }

    /** Résultat de l'application du skin → transmis à l'écran de skin s'il est ouvert. */
    public static void onSkinResult(SkinResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen
                    instanceof com.example.rolynkmodmenu.client.screen.profile.SkinScreen s) {
                s.onResultat(payload.ok(), payload.message());
            }
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
