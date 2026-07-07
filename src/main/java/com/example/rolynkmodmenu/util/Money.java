package com.example.rolynkmodmenu.util;

/**
 * Symbole et formatage de la monnaie du serveur.
 *
 * IMPORTANT : ne jamais utiliser '§' comme symbole monétaire — c'est le
 * caractère de code couleur Minecraft, le renderer l'avale avec le caractère
 * suivant et le texte s'affiche corrompu. D'où ce symbole dédié.
 *
 * Classe commune (aucun import Minecraft) — utilisable côté client ET serveur.
 */
public final class Money {

    /** Symbole affiché après les montants. Rendu garanti par la police vanilla. */
    public static final String SYMBOL = "$";

    private Money() {}

    /** 1500.0 → "1500.00 $" — montant exact à 2 décimales (séparateur '.' quelle que soit la locale JVM). */
    public static String exact(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f %s", v, SYMBOL);
    }

    /** 500.0 → "500 $" — montant entier (coûts fixes). */
    public static String entier(double v) {
        return (long) v + " " + SYMBOL;
    }
}
