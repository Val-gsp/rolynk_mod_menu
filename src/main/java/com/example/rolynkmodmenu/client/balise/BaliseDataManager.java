package com.example.rolynkmodmenu.client.balise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache client : liste des balises + limite de grade reçues du serveur.
 */
public final class BaliseDataManager {

    public record Balise(String nom, String monde, int x, int y, int z) {}

    private static List<Balise> balises   = new ArrayList<>();
    private static int          maxBalises = 2; // valeur par défaut conservative

    private BaliseDataManager() {}

    public static List<Balise> getBalises()    { return Collections.unmodifiableList(balises); }
    public static int          getMaxBalises() { return maxBalises; }

    public static void setBalises(List<Balise> list, int max) {
        balises    = new ArrayList<>(list);
        maxBalises = max;
    }

    public static void clear() {
        balises.clear();
        maxBalises = 2;
    }
}
