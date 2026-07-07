package com.example.rolynkmodmenu.server;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation centralisée de TOUTES les entrées joueur côté serveur.
 *
 * RÈGLE : aucune donnée venant du réseau (payload C2S) ne peut être
 * utilisée sans passer par l'une de ces méthodes en premier.
 *
 * Thread-safe — toutes les méthodes sont stateless (pas d'état mutable).
 */
public final class InputValidator {

    // ── Patterns de validation ─────────────────────────────────────────────────

    /**
     * Noms de ville : 3–32 caractères.
     * Autorisés : lettres Unicode (y compris accentuées), chiffres, tirets, underscores.
     * Refusés   : espaces, codes §, caractères de contrôle, balises HTML, guillemets.
     */
    private static final Pattern VILLE_NAME =
            Pattern.compile("^[\\p{L}0-9_\\-]{3,32}$");

    /**
     * Noms de balise : 1–32 caractères.
     * Plus permissif : les espaces sont autorisés (noms composés).
     * Refusés : codes §, caractères de contrôle, guillemets.
     */
    private static final Pattern BALISE_NAME =
            Pattern.compile("^[\\p{L}0-9_\\- ]{1,32}$");

    /**
     * UUID Minecraft au format standard : lowercase + tirets.
     * Exemple : 550e8400-e29b-41d4-a716-446655440000
     */
    private static final Pattern UUID_FMT =
            Pattern.compile(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
            );

    /**
     * Nom de serveur Velocity : lettres, chiffres, tirets, underscores, 1–64 chars.
     * Évite les injections de commande via SwitchServerPayload.
     */
    private static final Pattern SERVER_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

    // ── Ensembles de valeurs autorisées ────────────────────────────────────────

    /**
     * Actions autorisées dans {@code VilleActionPayload}.
     * Tout autre string est rejeté avant traitement.
     */
    public static final Set<String> VALID_ACTIONS = Set.of(
            "kick", "deposer", "retirer",
            "acc_dem", "ref_dem",
            "grade", "dissoudre", "quitter", "set_rec"
    );

    /** Grades de ville valides. */
    public static final Set<String> VALID_GRADES = Set.of("Membre", "Adjoint", "Chef");

    /** Modes de recrutement valides (doit correspondre aux constantes VilleStore). */
    public static final Set<String> VALID_REC = Set.of(
            VilleStore.RECRUTEMENT_OUVERT,
            VilleStore.RECRUTEMENT_SUR_DEMANDE,
            VilleStore.RECRUTEMENT_FERME
    );

    // ── Limites numériques ─────────────────────────────────────────────────────

    /** Montant maximum autorisé par transaction (évite overflow, DoS, abus). */
    public static final double MAX_MONTANT = 999_999_999.99;

    private InputValidator() {}

    // ── Méthodes de validation ─────────────────────────────────────────────────

    /** Nom de ville valide (regex + longueur). */
    public static boolean isValidVilleName(String s) {
        return s != null && VILLE_NAME.matcher(s).matches();
    }

    /** Nom de balise valide (regex + longueur). */
    public static boolean isValidBaliseName(String s) {
        return s != null && BALISE_NAME.matcher(s).matches();
    }

    /**
     * UUID Minecraft au format lowercase avec tirets.
     * Accepte aussi les UUIDs en majuscules (normalisés en lowercase avant test).
     */
    public static boolean isValidUuid(String s) {
        return s != null && !s.isEmpty()
                && UUID_FMT.matcher(s.toLowerCase()).matches();
    }

    /** Nom de serveur Velocity — aucun caractère spécial autorisé. */
    public static boolean isValidServerName(String s) {
        return s != null && SERVER_NAME_PATTERN.matcher(s).matches();
    }

    /** Action ville : doit figurer dans la whitelist {@code VALID_ACTIONS}. */
    public static boolean isValidAction(String s) {
        return s != null && VALID_ACTIONS.contains(s);
    }

    /** Grade ville : "Membre", "Adjoint" ou "Chef" uniquement. */
    public static boolean isValidGrade(String s) {
        return s != null && VALID_GRADES.contains(s);
    }

    /** Mode de recrutement : "ouvert", "sur_demande" ou "ferme" uniquement. */
    public static boolean isValidRecrutement(String s) {
        return s != null && VALID_REC.contains(s);
    }

    /**
     * Montant financier valide :
     * – fini (pas NaN, pas ±Infinity)
     * – strictement positif
     * – dans les limites définies par {@link #MAX_MONTANT}
     */
    public static boolean isValidMontant(double v) {
        return Double.isFinite(v) && v > 0 && v <= MAX_MONTANT;
    }

    /**
     * Arrondit un montant à 2 décimales.
     * Élimine les imprécisions de la virgule flottante transmises par le client.
     * Exemple : 10.005 → 10.01 ; 1.9999999 → 2.00
     */
    public static double roundMontant(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Vérifie qu'un villeId est structurellement valide (> 0).
     * Ne garantit pas l'existence en base — à combiner avec VilleStore.
     */
    public static boolean isValidVilleId(int id) {
        return id > 0;
    }

    /** Nombre de paliers de récompense temps de jeu (30 min, 2 h, 4 h). */
    public static final int NB_PALIERS_RECOMPENSE = 3;

    /** Palier de récompense temps de jeu : index 0..2 uniquement. */
    public static boolean isValidPalierRecompense(int palier) {
        return palier >= 0 && palier < NB_PALIERS_RECOMPENSE;
    }
}
