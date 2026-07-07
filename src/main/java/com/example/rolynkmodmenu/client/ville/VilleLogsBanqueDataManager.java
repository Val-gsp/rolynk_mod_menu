package com.example.rolynkmodmenu.client.ville;

import com.example.rolynkmodmenu.network.VilleLogsBanquePayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache client — historique des transactions bancaires d'une ville.
 */
public final class VilleLogsBanqueDataManager {

    private static List<VilleLogsBanquePayload.LogEntry> logs = new ArrayList<>();

    private VilleLogsBanqueDataManager() {}

    public static List<VilleLogsBanquePayload.LogEntry> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    public static void setLogs(List<VilleLogsBanquePayload.LogEntry> list) {
        logs = new ArrayList<>(list);
    }

    public static void clear() {
        logs.clear();
    }
}
