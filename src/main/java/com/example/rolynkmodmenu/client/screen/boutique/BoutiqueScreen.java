package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fenêtre Boutique — MENU > BOUTIQUE
 * Module pas encore ouvert : affiche un état "en construction" explicite
 * plutôt que des boutons factices qui ne font rien.
 */
public class BoutiqueScreen extends BaseMenuScreen {

    public BoutiqueScreen() { super("MENU", "BOUTIQUE"); }

    @Override
    protected void initContent() { }

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        GuiUtils.renderComingSoon(gfx, font, this,
                "BOUTIQUE", "La boutique du serveur ouvrira bientôt !");
    }

    @Override
    public void onClose() { minecraft.setScreen(new MainMenuScreen()); }
}
