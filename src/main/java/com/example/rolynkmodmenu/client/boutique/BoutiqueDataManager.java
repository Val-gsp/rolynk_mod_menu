package com.example.rolynkmodmenu.client.boutique;

import com.example.rolynkmodmenu.network.BoutiqueCatalogPayload;

import java.util.List;

/**
 * Cache client du catalogue de rachat de la Boutique Jeux.
 * Le catalogue vient exclusivement du serveur ; le client n'a aucun prix en dur.
 */
public final class BoutiqueDataManager {

    private static volatile List<BoutiqueCatalogPayload.Offre> offres = null;

    private BoutiqueDataManager() {}

    public static void set(List<BoutiqueCatalogPayload.Offre> list) {
        offres = List.copyOf(list);
    }

    public static void clear() {
        offres = null;
    }

    public static boolean isLoaded() {
        return offres != null;
    }

    public static List<BoutiqueCatalogPayload.Offre> offres() {
        List<BoutiqueCatalogPayload.Offre> local = offres;
        return local == null ? List.of() : local;
    }
}
