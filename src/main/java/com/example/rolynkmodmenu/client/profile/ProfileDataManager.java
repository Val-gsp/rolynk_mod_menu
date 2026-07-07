package com.example.rolynkmodmenu.client.profile;

import com.example.rolynkmodmenu.network.ProfilePayload;

/**
 * Cache client du profil du joueur connecté.
 *
 * Pattern snapshot volatile : le thread de rendu lit toujours un état cohérent
 * — jamais un état partiellement écrit. handleS2C() remplace le snapshot en entier.
 */
public final class ProfileDataManager {

    private record Snapshot(
            String uuid, String status, String pseudo, String grade,
            String money, String cristaux, String heuresDeJeu, String premiereConnexion,
            String villeNom
    ) {}

    private static volatile Snapshot data = new Snapshot(
            null, null, null, null, null, null, null, null, "");

    private ProfileDataManager() {}

    /** Appelé sur le client thread quand ProfilePayload est reçu. */
    public static void handleS2C(ProfilePayload p) {
        data = new Snapshot(
                p.uuid(), p.status(), p.pseudo(), p.grade(),
                p.money(), p.cristaux(), p.heuresDeJeu(), p.premiereConnexion(),
                p.villeNom()
        );
    }

    // ── Accesseurs ────────────────────────────────────────────────────────

    public static String getUuid()              { return data.uuid(); }
    public static String getStatus()            { return data.status(); }
    public static String getPseudo()            { return data.pseudo(); }
    public static String getGrade()             { return data.grade(); }
    public static String getMoney()             { return data.money(); }
    public static String getCristaux()          { return data.cristaux(); }
    public static String getHeuresDeJeu()       { return data.heuresDeJeu(); }
    public static String getPremiereConnexion() { return data.premiereConnexion(); }
    public static String getVilleNom()          { return data.villeNom(); }

    /** @return true si le joueur appartient à une ville. */
    public static boolean hasVille() {
        return data.villeNom() != null && !data.villeNom().isEmpty();
    }
}
