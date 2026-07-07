package com.example.rolynkmodmenu.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche de validation des entrées réseau — la première ligne
 * de défense contre les payloads forgés par un client modifié.
 */
class InputValidatorTest {

    // ── Noms de ville ──────────────────────────────────────────────────────

    @Test
    void villeName_accepteLettresChiffresAccents() {
        assertTrue(InputValidator.isValidVilleName("Mon_Village"));
        assertTrue(InputValidator.isValidVilleName("Lyon-2"));
        assertTrue(InputValidator.isValidVilleName("Évreux"));
        assertTrue(InputValidator.isValidVilleName("abc"));            // min 3
        assertTrue(InputValidator.isValidVilleName("a".repeat(32)));   // max 32
    }

    @Test
    void villeName_refuseInjectionsEtFormatsInvalides() {
        assertFalse(InputValidator.isValidVilleName(null));
        assertFalse(InputValidator.isValidVilleName(""));
        assertFalse(InputValidator.isValidVilleName("ab"));                // trop court
        assertFalse(InputValidator.isValidVilleName("a".repeat(33)));      // trop long
        assertFalse(InputValidator.isValidVilleName("Mon Village"));       // espace
        assertFalse(InputValidator.isValidVilleName("§cRouge"));           // code couleur
        assertFalse(InputValidator.isValidVilleName("ville'--"));          // quote SQL
        assertFalse(InputValidator.isValidVilleName("<script>alert</script>"));
        assertFalse(InputValidator.isValidVilleName("nom\nmultiligne"));
    }

    // ── UUID ───────────────────────────────────────────────────────────────

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
    void actions_seulesLesActionsConnuesPassent() {
        for (String a : InputValidator.VALID_ACTIONS) assertTrue(InputValidator.isValidAction(a));
        assertFalse(InputValidator.isValidAction(null));
        assertFalse(InputValidator.isValidAction("drop_table"));
        assertFalse(InputValidator.isValidAction("KICK"));   // sensible à la casse
        assertFalse(InputValidator.isValidAction("toggle_rec")); // ancienne action supprimée
    }

    @Test
    void grades_etRecrutement_whitelistes() {
        assertTrue(InputValidator.isValidGrade("Chef"));
        assertTrue(InputValidator.isValidGrade("Adjoint"));
        assertTrue(InputValidator.isValidGrade("Membre"));
        assertFalse(InputValidator.isValidGrade("chef"));
        assertFalse(InputValidator.isValidGrade("Admin"));

        assertTrue(InputValidator.isValidRecrutement("ouvert"));
        assertTrue(InputValidator.isValidRecrutement("sur_demande"));
        assertTrue(InputValidator.isValidRecrutement("ferme"));
        assertFalse(InputValidator.isValidRecrutement("OUVERT"));
        assertFalse(InputValidator.isValidRecrutement(""));
    }

    // ── Montants ──────────────────────────────────────────────────────────

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
    void montant_arrondiADeuxDecimales() {
        assertEquals(10.01, InputValidator.roundMontant(10.005), 1e-9);
        assertEquals(2.00,  InputValidator.roundMontant(1.9999999), 1e-9);
        assertEquals(0.10,  InputValidator.roundMontant(0.1), 1e-9);
    }

    // ── Divers ────────────────────────────────────────────────────────────

    @Test
    void villeId_strictementPositif() {
        assertTrue(InputValidator.isValidVilleId(1));
        assertFalse(InputValidator.isValidVilleId(0));
        assertFalse(InputValidator.isValidVilleId(-42));
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
