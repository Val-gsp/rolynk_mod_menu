package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fenêtre Boutique Grade — MENU > BOUTIQUE > GRADE
 * En construction : affiche l'état "à venir" explicite.
 */
public class BoutiqueGradeScreen extends BaseMenuScreen {

    public BoutiqueGradeScreen() { super("BOUTIQUE", "GRADE"); }

    @Override
    protected void initContent() { }

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        GuiUtils.renderComingSoon(gfx, font, this,
                "BOUTIQUE GRADE", "La boutique des grades ouvrira bientôt !");
    }

    @Override
    public void onClose() { minecraft.setScreen(new BoutiqueScreen()); }
}
