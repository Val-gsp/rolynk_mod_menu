package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fenêtre d'une catégorie de la Boutique — MENU > BOUTIQUE > [catégorie]
 * Écran générique « à venir » : chaque catégorie affichera son contenu quand
 * elle ouvrira (les 6 boutons de {@link BoutiqueScreen} pointent ici).
 */
public class BoutiqueCategorieScreen extends BaseMenuScreen {

    private final String categorie;

    public BoutiqueCategorieScreen(String categorie) {
        super("BOUTIQUE", categorie);
        this.categorie = categorie;
    }

    @Override
    protected void initContent() { }

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        GuiUtils.renderComingSoon(gfx, font, this,
                "BOUTIQUE " + categorie, "Cette rubrique ouvrira bientôt !");
    }
}
