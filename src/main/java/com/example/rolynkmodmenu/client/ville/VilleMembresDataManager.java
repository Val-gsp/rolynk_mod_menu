package com.example.rolynkmodmenu.client.ville;

import com.example.rolynkmodmenu.network.VilleMembresPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache client — liste des membres d'une ville + grade du joueur courant.
 */
public final class VilleMembresDataManager {

    private static List<VilleMembresPayload.MembreEntry> membres = new ArrayList<>();
    private static String myGrade     = "";
    private static double playerMoney = -1;

    private VilleMembresDataManager() {}

    public static List<VilleMembresPayload.MembreEntry> getMembres() {
        return Collections.unmodifiableList(membres);
    }

    public static String getMyGrade()    { return myGrade; }
    /** Solde personnel du joueur, ou -1 si pas encore reçu. */
    public static double getPlayerMoney() { return playerMoney; }

    public static boolean isChef()    { return "Chef".equals(myGrade); }
    public static boolean isAdjoint() { return "Adjoint".equals(myGrade); }

    public static void set(List<VilleMembresPayload.MembreEntry> list, String grade, double money) {
        membres = new ArrayList<>(list);
        membres.sort((a, b) -> {
            int ra = gradeRank(a.grade()), rb = gradeRank(b.grade());
            if (ra != rb) return ra - rb;
            return a.pseudo().compareToIgnoreCase(b.pseudo());
        });
        myGrade     = grade == null ? "" : grade;
        playerMoney = money;
    }

    private static int gradeRank(String grade) {
        return switch (grade) {
            case "Chef"    -> 0;
            case "Adjoint" -> 1;
            default        -> 2;
        };
    }

    /** Met à jour uniquement la money personnelle (après dépôt/retrait). */
    public static void setPlayerMoney(double money) {
        playerMoney = money;
    }

    public static void clear() {
        membres.clear();
        myGrade     = "";
        playerMoney = -1;
    }
}
