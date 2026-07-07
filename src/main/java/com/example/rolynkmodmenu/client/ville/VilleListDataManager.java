package com.example.rolynkmodmenu.client.ville;

import com.example.rolynkmodmenu.network.VilleListPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache client — liste de toutes les villes reçues via VilleListPayload.
 */
public final class VilleListDataManager {

    private static List<VilleListPayload.VilleEntry> villes = new ArrayList<>();

    private VilleListDataManager() {}

    public static List<VilleListPayload.VilleEntry> getVilles() {
        return Collections.unmodifiableList(villes);
    }

    public static void setVilles(List<VilleListPayload.VilleEntry> list) {
        villes = new ArrayList<>(list);
    }

    /** Retourne l'entrée dont l'id correspond, ou null. */
    public static VilleListPayload.VilleEntry getById(int id) {
        return villes.stream().filter(v -> v.id() == id).findFirst().orElse(null);
    }

    /** Retourne l'entrée dont le nom correspond (insensible à la casse), ou null. */
    public static VilleListPayload.VilleEntry getByNom(String nom) {
        return villes.stream()
                .filter(v -> v.nom().equalsIgnoreCase(nom))
                .findFirst().orElse(null);
    }

    public static void clear() {
        villes.clear();
    }
}
