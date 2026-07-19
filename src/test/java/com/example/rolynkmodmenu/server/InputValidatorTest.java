package com.example.rolynkmodmenu.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche de validation des entrées réseau — la première ligne
 * de défense contre les payloads forgés par un client modifié.
 */
class InputValidatorTest {

    @Test
    void uuid_formatStandardUniquement() {
        assertTrue(InputValidator.isValidUuid("550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(InputValidator.isValidUuid("550E8400-E29B-41D4-A716-446655440000")); // normalisé
        assertFalse(InputValidator.isValidUuid(null));
        assertFalse(InputValidator.isValidUuid(""));
        assertFalse(InputValidator.isValidUuid("550e8400e29b41d4a716446655440000"));    // sans tirets
        assertFalse(InputValidator.isValidUuid("550e8400-e29b-41d4-a716-44665544000")); // trop court
        assertFalse(InputValidator.isValidUuid("zzze8400-e29b-41d4-a716-446655440000")); // hors hex
    }

    // ── Whitelists ────────────────────────────────────────────────────────

    @Test
    void montant_bornesEtValeursSpeciales() {
        assertTrue(InputValidator.isValidMontant(0.01));
        assertTrue(InputValidator.isValidMontant(InputValidator.MAX_MONTANT));
        assertFalse(InputValidator.isValidMontant(0));
        assertFalse(InputValidator.isValidMontant(-5));
        assertFalse(InputValidator.isValidMontant(InputValidator.MAX_MONTANT + 1));
        assertFalse(InputValidator.isValidMontant(Double.NaN));
        assertFalse(InputValidator.isValidMontant(Double.POSITIVE_INFINITY));
        assertFalse(InputValidator.isValidMontant(Double.NEGATIVE_INFINITY));
    }

    @Test
    void palierRecompense_index0a2Uniquement() {
        assertTrue(InputValidator.isValidPalierRecompense(0));
        assertTrue(InputValidator.isValidPalierRecompense(1));
        assertTrue(InputValidator.isValidPalierRecompense(2));
        assertFalse(InputValidator.isValidPalierRecompense(-1));
        assertFalse(InputValidator.isValidPalierRecompense(3));
        assertFalse(InputValidator.isValidPalierRecompense(Integer.MAX_VALUE));
    }

    @Test
    void serverName_aucunCaractereSpecial() {
        assertTrue(InputValidator.isValidServerName("ville"));
        assertTrue(InputValidator.isValidServerName("lobby-2_test"));
        assertFalse(InputValidator.isValidServerName("ville; rm -rf"));
        assertFalse(InputValidator.isValidServerName("a b"));
        assertFalse(InputValidator.isValidServerName(""));
    }
}
