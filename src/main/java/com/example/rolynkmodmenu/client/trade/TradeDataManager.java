package com.example.rolynkmodmenu.client.trade;

import com.example.rolynkmodmenu.network.TradeListPayload;
import com.example.rolynkmodmenu.network.TradeStatePayload;

import java.util.List;

/**
 * Cache client du système de trade : liste des joueurs/demandes et état de
 * la session en cours. Tout vient du serveur ; le client ne fait qu'afficher.
 */
public final class TradeDataManager {

    private static volatile List<String> joueurs  = null;
    private static volatile List<String> demandes = List.of();
    private static volatile TradeStatePayload state = null;

    private TradeDataManager() {}

    // ── Liste joueurs / demandes ──────────────────────────────────────────

    public static void setListe(List<String> j, List<String> d) {
        joueurs  = List.copyOf(j);
        demandes = List.copyOf(d);
    }

    public static boolean isListeLoaded() { return joueurs != null; }

    public static List<String> joueurs()  { List<String> l = joueurs;  return l == null ? List.of() : l; }
    public static List<String> demandes() { return demandes; }

    // ── Session ───────────────────────────────────────────────────────────

    public static void setState(TradeStatePayload s) { state = s; }
    public static TradeStatePayload state()          { return state; }

    public static void clear() {
        joueurs = null;
        demandes = List.of();
        state = null;
    }

    // ── Gestion d'écran ───────────────────────────────────────────────────
    // IMPORTANT sided-safety : ces méthodes mentionnent des classes Screen.
    // Elles vivent ICI (et pas dans ClientPayloadHandlers) car la
    // VÉRIFICATION bytecode d'un instanceof/cast force le chargement de la
    // classe cible : dans ClientPayloadHandlers (chargée par la classe @Mod
    // à l'enregistrement des payloads), cela ferait charger Screen sur le
    // serveur dédié → crash RuntimeDistCleaner. TradeDataManager n'est
    // chargée qu'à l'EXÉCUTION d'un handler S2C, donc jamais côté serveur.

    /** Demande acceptée : ouvre l'écran de trade. */
    public static void ouvrirEcran(String partenaire) {
        state = null;
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.example.rolynkmodmenu.client.screen.trade.TradeScreen(partenaire));
    }

    /** Session terminée côté serveur : ferme l'écran de trade s'il est ouvert. */
    public static void fermerEcran() {
        state = null;
        if (net.minecraft.client.Minecraft.getInstance().screen
                instanceof com.example.rolynkmodmenu.client.screen.trade.TradeScreen ts) {
            ts.closeFromServer();
        }
    }
}
