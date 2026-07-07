package com.example.rolynkmodmenu.client.recompense;

import com.example.rolynkmodmenu.network.RecompensesPayload;

import java.util.List;

/**
 * Cache client de l'état des récompenses quotidiennes.
 *
 * Le temps de jeu affiché est EXTRAPOLÉ : le serveur envoie un instantané,
 * et comme le joueur est en train de jouer, on ajoute localement le temps
 * écoulé depuis la réception — la barre de progression avance en direct.
 *
 * La distance d'exploration n'est PAS extrapolée (taux de déplacement imprévisible) :
 * elle affiche la valeur du dernier snapshot, laquelle est exacte à l'ouverture
 * de l'écran (le serveur flushe avant d'envoyer).
 */
public final class RecompensesDataManager {

    private static volatile RecompensesPayload data = null;
    private static volatile long receivedAtMs = 0;

    private RecompensesDataManager() {}

    public static void set(RecompensesPayload payload) {
        data = payload;
        receivedAtMs = System.currentTimeMillis();
    }

    public static void clear() {
        data = null;
        receivedAtMs = 0;
    }

    public static boolean isLoaded() {
        return data != null;
    }

    // ── Play time ─────────────────────────────────────────────────────────

    /** Les paliers (seuil, montant, récupéré) tels qu'envoyés par le serveur. */
    public static List<RecompensesPayload.PalierEntry> paliers() {
        RecompensesPayload d = data;
        return d == null ? List.of() : d.paliers();
    }

    /** Temps de jeu du jour, extrapolé depuis la réception du dernier snapshot. */
    public static int tempsActuelSecondes() {
        RecompensesPayload d = data;
        if (d == null) return 0;
        long extra = (System.currentTimeMillis() - receivedAtMs) / 1_000;
        return d.tempsJeuSecondes() + (int) extra;
    }

    // ── Vote ville ────────────────────────────────────────────────────────

    /** {@code true} si le joueur a déjà voté pour une ville aujourd'hui. */
    public static boolean isVoteVilleEffectue() {
        RecompensesPayload d = data;
        return d != null && d.voteVilleEffectue();
    }

    /** Nom de la ville votée aujourd'hui, "" si le joueur n'a pas encore voté. */
    public static String getVilleVoteeNom() {
        RecompensesPayload d = data;
        return d == null ? "" : d.villeVoteeNom();
    }

    /**
     * Nom de la ville dont le joueur est membre, "" s'il est sans ville.
     * Utilisé par VoteVilleScreen pour griser la propre ville du joueur.
     */
    public static String getMaVilleNom() {
        RecompensesPayload d = data;
        return d == null ? "" : d.maVilleNom();
    }

    /** Montant de la récompense vote de ville (config serveur). */
    public static double getMontantVoteVille() {
        RecompensesPayload d = data;
        return d == null ? 0 : d.montantVoteVille();
    }

    // ── Exploration ───────────────────────────────────────────────────────

    /**
     * Blocs XZ parcourus aujourd'hui, tels qu'envoyés par le serveur
     * (flush effectué juste avant l'envoi → valeur exacte à l'ouverture).
     */
    public static int getBlocsParcourus() {
        RecompensesPayload d = data;
        return d == null ? 0 : d.blocsParcourus();
    }

    /** {@code true} si la récompense d'exploration a déjà été réclamée aujourd'hui. */
    public static boolean isExplorationRecue() {
        RecompensesPayload d = data;
        return d != null && d.explorationRecue();
    }

    /** Seuil de blocs à atteindre pour la récompense (config serveur). */
    public static int getSeuilBlocs() {
        RecompensesPayload d = data;
        return d == null ? 20_000 : d.seuilBlocs();
    }

    /** Montant de la récompense d'exploration (config serveur). */
    public static double getMontantExploration() {
        RecompensesPayload d = data;
        return d == null ? 0 : d.montantExploration();
    }
}
