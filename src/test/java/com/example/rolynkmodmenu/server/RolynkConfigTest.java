package com.example.rolynkmodmenu.server;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du parsing de la configuration gameplay — valeurs invalides,
 * listes de mondes, priorité de la propriété JVM.
 */
class RolynkConfigTest {

    @Test
    void parsePositiveDouble_valeursValides() {
        assertEquals(250.0, RolynkConfig.parsePositiveDouble("250", 500, "k"), 1e-9);
        assertEquals(0.0,   RolynkConfig.parsePositiveDouble("0", 500, "k"), 1e-9);   // gratuit autorisé
        assertEquals(12.5,  RolynkConfig.parsePositiveDouble(" 12.5 ", 500, "k"), 1e-9);
    }

    @Test
    void parsePositiveDouble_retombeSurLeDefaut() {
        assertEquals(500.0, RolynkConfig.parsePositiveDouble(null, 500, "k"), 1e-9);
        assertEquals(500.0, RolynkConfig.parsePositiveDouble("", 500, "k"), 1e-9);
        assertEquals(500.0, RolynkConfig.parsePositiveDouble("abc", 500, "k"), 1e-9);
        assertEquals(500.0, RolynkConfig.parsePositiveDouble("-10", 500, "k"), 1e-9);  // négatif refusé
        assertEquals(500.0, RolynkConfig.parsePositiveDouble("NaN", 500, "k"), 1e-9);
        assertEquals(500.0, RolynkConfig.parsePositiveDouble("Infinity", 500, "k"), 1e-9);
    }

    @Test
    void parseWorldList_normaliseEtFiltre() {
        assertEquals(Set.of("ville"), RolynkConfig.parseWorldList("ville"));
        assertEquals(Set.of("ville", "capitale"), RolynkConfig.parseWorldList("Ville, CAPITALE"));
        assertEquals(Set.of("a", "b"), RolynkConfig.parseWorldList(" a ,, b , "));
        // Liste vide → on retombe sur le monde par défaut (jamais de Set vide)
        assertEquals(Set.of(RolynkConfig.DEFAULT_MONDES_CLAIM), RolynkConfig.parseWorldList(" , "));
    }

    @Test
    void resolveServerName_prioriteJvmPuisFichierPuisDefaut() {
        String old = System.getProperty("rolynk.server.name");
        try {
            System.clearProperty("rolynk.server.name");
            assertEquals("fichier", RolynkConfig.resolveServerName("fichier"));
            assertEquals(RolynkConfig.DEFAULT_SERVER_NAME, RolynkConfig.resolveServerName(null));
            assertEquals(RolynkConfig.DEFAULT_SERVER_NAME, RolynkConfig.resolveServerName("  "));

            System.setProperty("rolynk.server.name", "jvm");
            assertEquals("jvm", RolynkConfig.resolveServerName("fichier")); // la JVM gagne
        } finally {
            if (old != null) System.setProperty("rolynk.server.name", old);
            else System.clearProperty("rolynk.server.name");
        }
    }
}
