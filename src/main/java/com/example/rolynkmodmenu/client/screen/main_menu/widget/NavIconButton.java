package com.example.rolynkmodmenu.client.screen.main_menu.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Icône de navigation du bandeau sous le header (retour / maison), dessinée en
 * pixel-art procédural — présente sur toutes les fenêtres via BaseMenuScreen.
 * Gris atténué au repos, vert vif au survol (palette du menu).
 */
public class NavIconButton extends AbstractButton {

    public static final String[] ICON_BACK = {
            "    #      ",
            "   ##      ",
            "  #########",
            " ##########",
            "  #########",
            "   ##      ",
            "    #      ",
    };
    public static final String[] ICON_HOME = {
            "    ##     ",
            "   ####    ",
            "  ######   ",
            " ######## ",
            " ##    ##  ",
            " ##  # ##  ",
            " ##  # ##  ",
    };

    private static final int COLOR_IDLE  = 0xFF607080;
    private static final int COLOR_HOVER = 0xFF3DD96A;

    private final String[] icon;
    private final Runnable onPress;

    public NavIconButton(int x, int y, String[] icon, String label, Runnable onPress) {
        super(x, y, 15, 11, Component.literal(label));
        this.icon = icon;
        this.onPress = onPress;
    }

    @Override public void onPress() { onPress.run(); }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int color = isHoveredOrFocused() ? COLOR_HOVER : COLOR_IDLE;
        int x = getX() + 2, y = getY() + 2;
        for (int r = 0; r < icon.length; r++)
            for (int c = 0; c < icon[r].length(); c++)
                if (icon[r].charAt(c) == '#')
                    gfx.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
