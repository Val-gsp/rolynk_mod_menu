package com.example.rolynkmodmenu.client.profile;

import com.example.rolynkmodmenu.network.ProfilJoueurPayload;

/**
 * Cache client du profil d'un autre joueur (consultation temporaire).
 *
 * Contrairement à ProfileDataManager, ce cache est effacé (clear()) à chaque ouverture
 * de ProfilJoueurScreen afin d'éviter d'afficher les données du joueur précédemment consulté.
 */
public final class ProfilJoueurDataManager {

    private static String uuid              = "";
    private static String status            = "";
    private static String pseudo            = "";
    private static String grade             = "";
    private static String money             = "";
    private static String cristaux          = "";
    private static String heuresDeJeu       = "";
    private static String premiereConnexion = "";
    private static String villeNom          = "";

    private ProfilJoueurDataManager() {}

    /** Réinitialise tous les champs — appelé dans ProfilJoueurScreen.init(). */
    public static void clear() {
        uuid = ""; status = ""; pseudo = ""; grade = "";
        money = ""; cristaux = ""; heuresDeJeu = ""; premiereConnexion = "";
        villeNom = "";
    }

    /** Appelé sur le client thread quand ProfilJoueurPayload est reçu. */
    public static void handleS2C(ProfilJoueurPayload p) {
        uuid              = p.uuid();
        status            = p.status();
        pseudo            = p.pseudo();
        grade             = p.grade();
        money             = p.money();
        cristaux          = p.cristaux();
        heuresDeJeu       = p.heuresDeJeu();
        premiereConnexion = p.premiereConnexion();
        villeNom          = p.villeNom();
    }

    // ── Accesseurs ────────────────────────────────────────────────────────

    public static String getUuid()              { return uuid; }
    public static String getStatus()            { return status; }
    public static String getPseudo()            { return pseudo; }
    public static String getGrade()             { return grade; }
    public static String getMoney()             { return money; }
    public static String getCristaux()          { return cristaux; }
    public static String getHeuresDeJeu()       { return heuresDeJeu; }
    public static String getPremiereConnexion() { return premiereConnexion; }
    public static String getVilleNom()          { return villeNom; }
}
