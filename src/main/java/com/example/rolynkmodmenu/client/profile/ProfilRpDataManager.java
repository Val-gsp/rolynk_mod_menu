package com.example.rolynkmodmenu.client.profile;

import com.example.rolynkmodmenu.network.ProfilRpPayload;

/**
 * Cache client du profil RP du joueur connecté.
 *
 * Pattern snapshot volatile : le thread de rendu lit toujours un état cohérent.
 * {@code creationRequise} est levé quand le serveur signale qu'aucun profil
 * n'existe (première connexion) — consommé par ClientGameEvents qui ouvre
 * l'écran « Création Profil RP » dès que le joueur est en jeu.
 */
public final class ProfilRpDataManager {

    private record Snapshot(
            boolean recu, boolean existe,
            String nom, String prenom, String sexe, String taille,
            String ancienneVille, String nouvelleVille, String metier, String description
    ) {}

    private static volatile Snapshot data =
            new Snapshot(false, false, "", "", "", "", "", "", "", "");

    /** true tant que l'écran de création doit être ouvert (première connexion). */
    private static volatile boolean creationRequise = false;

    private ProfilRpDataManager() {}

    /** Appelé sur le client thread quand ProfilRpPayload est reçu. */
    public static void handleS2C(ProfilRpPayload p) {
        data = new Snapshot(true, p.existe(),
                p.nom(), p.prenom(), p.sexe(), p.taille(),
                p.ancienneVille(), p.nouvelleVille(), p.metier(), p.description());
        if (!p.existe()) creationRequise = true;
        else             creationRequise = false;
    }

    /** Réinitialise le cache (déconnexion du serveur). */
    public static void reset() {
        data = new Snapshot(false, false, "", "", "", "", "", "", "", "");
        creationRequise = false;
    }

    // ── Ouverture automatique de l'écran de création ──────────────────────

    public static boolean isCreationRequise()   { return creationRequise; }
    public static void clearCreationRequise()   { creationRequise = false; }

    // ── Accesseurs ────────────────────────────────────────────────────────

    public static boolean isRecu()          { return data.recu(); }
    public static boolean isExiste()        { return data.existe(); }
    public static String getNom()           { return data.nom(); }
    public static String getPrenom()        { return data.prenom(); }
    public static String getSexe()          { return data.sexe(); }
    public static String getTaille()        { return data.taille(); }
    public static String getAncienneVille() { return data.ancienneVille(); }
    public static String getNouvelleVille() { return data.nouvelleVille(); }
    public static String getMetier()        { return data.metier(); }
    public static String getDescription()   { return data.description(); }
}
