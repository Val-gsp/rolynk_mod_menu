package com.example.rolynkmodmenu.client.ville;

/**
 * Cache client — nom de la ville du joueur courant.
 * Mis à jour via VilleProfilePayload à chaque changement d'appartenance.
 */
public final class VilleProfileDataManager {

    private static String villeNom = "";

    private VilleProfileDataManager() {}

    public static String getVilleNom() { return villeNom; }

    public static boolean hasVille() {
        return villeNom != null && !villeNom.isEmpty();
    }

    public static void setVilleNom(String nom) {
        villeNom = nom == null ? "" : nom;
    }

    public static void clear() {
        villeNom = "";
    }
}
