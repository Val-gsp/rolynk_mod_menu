package com.example.rolynkmodmenu.client.grade;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Cache client des grades joueurs.
 *
 * Alimenté depuis les payloads de profil (ProfilePayload / ProfilJoueurPayload)
 * reçus du serveur via la base de données.
 *
 * SÉCURITÉ / THREAD-SAFETY :
 *  – ConcurrentHashMap pour les grades (lus depuis render thread, écrits depuis packet thread)
 *  – volatile pour monGrade (même raison)
 *  – Valeur par défaut "default" si grade inconnu (jamais null)
 */
public final class GradeCache {

    /**
     * username (casse exacte Mojang) → grade ("default" | "helper" | "staff")
     * ConcurrentHashMap : safe pour lectures et écritures concurrentes.
     */
    private static final Map<String, String> GRADES = new ConcurrentHashMap<>();

    /**
     * Grade du joueur local — volatile car écrit depuis le packet handler
     * et lu depuis le render thread sans synchronisation explicite.
     */
    private static volatile String monGrade = "default";

    private GradeCache() {}

    // ── Setters (appelés depuis les handlers de payload) ───────────────────────

    /** Met à jour le grade du joueur local (depuis ProfilePayload). */
    public static void setMonGrade(String grade) {
        monGrade = (grade != null && !grade.isEmpty()) ? grade : "default";
    }

    /** Enregistre le grade d'un autre joueur (depuis ProfilJoueurPayload). */
    public static void setGrade(String username, String grade) {
        if (username != null && !username.isEmpty() && grade != null && !grade.isEmpty())
            GRADES.put(username, grade);
    }

    // ── Accesseurs ─────────────────────────────────────────────────────────────

    /** Grade du joueur local. Jamais null. */
    public static String getMonGrade() { return monGrade; }

    /** Grade d'un joueur par pseudo. Retourne "default" si inconnu. */
    public static String getGrade(String username) {
        return GRADES.getOrDefault(username, "default");
    }

    // ── Helpers d'affichage ────────────────────────────────────────────────────

    /** Label coloré pour affichage GUI. */
    public static String gradeLabel(String grade) {
        if (grade == null) return "§7Citoyen";
        return switch (grade) {
            case "staff"  -> "§cStaff";
            case "helper" -> "§9Helper";
            default       -> "§7Citoyen";
        };
    }
}
