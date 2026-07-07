package com.example.rolynkmodmenu.client.ville;

import com.example.rolynkmodmenu.network.VilleDemandesPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache client — demandes d'adhésion en attente pour une ville.
 */
public final class VilleDemandesDataManager {

    private static List<VilleDemandesPayload.DemandeEntry> demandes = new ArrayList<>();

    private VilleDemandesDataManager() {}

    public static List<VilleDemandesPayload.DemandeEntry> getDemandes() {
        return Collections.unmodifiableList(demandes);
    }

    public static void setDemandes(List<VilleDemandesPayload.DemandeEntry> list) {
        demandes = new ArrayList<>(list);
    }

    public static void clear() {
        demandes.clear();
    }
}
