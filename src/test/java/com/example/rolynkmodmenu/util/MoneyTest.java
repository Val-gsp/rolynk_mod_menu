package com.example.rolynkmodmenu.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void symboleJamaisLeCaractereSection() {
        // '§' est le code couleur Minecraft : utilisé comme symbole monétaire,
        // il corrompt le rendu du texte. Ce test verrouille la règle.
        assertFalse(Money.SYMBOL.contains("§"));
        assertFalse(Money.SYMBOL.isBlank());
    }

    @Test
    void formatageExactEtEntier() {
        assertEquals("1500.00 " + Money.SYMBOL, Money.exact(1500));
        assertEquals("0.50 " + Money.SYMBOL,    Money.exact(0.5));
        assertEquals("500 " + Money.SYMBOL,     Money.entier(500.0));
        assertEquals("500 " + Money.SYMBOL,     Money.entier(500.9)); // tronqué, pas arrondi
    }
}
