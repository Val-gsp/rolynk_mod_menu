package com.example.rolynkmodmenu.client.screen.main_menu.widget;

import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class MenuButton extends AbstractButton {

    private static final int COLOR_BG       = 0xA0141C26;
    private static final int COLOR_BG_HOVER = 0xC01A2840;
    private static final int COLOR_BORDER   = 0xFF1C2C3C;
    private static final int COLOR_TITLE    = 0xFFEEF2F5;
    private static final int COLOR_TITLE_H  = 0xFF3DD96A;
    private static final int COLOR_SUBTITLE = 0xFF607080;

    private final Runnable  onPress;
    private final String    subtitle;
    private final String    truncatedSubtitle;
    private final ItemStack icon;

    /** Constructeur principal : label + sous-titre + icône item. */
    public MenuButton(int x, int y, int w, int h,
                      String label, String subtitle, ItemStack icon,
                      Runnable onPress) {
        super(x, y, w, h, Component.literal(label));
        this.onPress  = onPress;
        this.subtitle = subtitle != null ? subtitle : "";
        this.icon     = icon;
        // Pré-calcule le sous-titre tronqué une seule fois (w est fixe après construction)
        if (this.subtitle.isEmpty()) {
            this.truncatedSubtitle = "";
        } else {
            var f = Minecraft.getInstance().font;
            float subScale = 0.80f;
            int maxPx = (int)((w - 14) / subScale);
            String sub = this.subtitle;
            while (f.width(sub) > maxPx && sub.length() > 6) sub = sub.substring(0, sub.length() - 4) + "...";
            this.truncatedSubtitle = sub;
        }
    }

    /** Rétro-compat : sans icône ni sous-titre. */
    public MenuButton(int x, int y, int w, int h, String label, Runnable onPress) {
        this(x, y, w, h, label, "", null, onPress);
    }

    /** Rétro-compat : ancien constructeur avec texture (ignorée dans ce design). */
    public MenuButton(int x, int y, int w, int h, String label,
                      ResourceLocation texture, int imgW, int imgH,
                      Runnable onPress) {
        this(x, y, w, h, label, "", null, onPress);
    }

    @Override public void onPress() { onPress.run(); }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        boolean hov = isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        var font = Minecraft.getInstance().font;

        // Fond de la carte (coins biseautés)
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 6, hov ? COLOR_BG_HOVER : COLOR_BG);

        if (hov) {
            // Glow extérieur (offset de 1 à 3px, opacité croissante vers l'intérieur)
            GuiUtils.drawChamferedBorder(gfx, x - 3, y - 3, w + 6, h + 6, 9, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, w + 4, h + 4, 8, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 7, 1, 0x552ECC60);
            // Pas de bordure uniforme — drawNeonEdge dessine tout (coins + côtés)
            GuiUtils.drawNeonEdge(gfx, x, y, w, h, 6);
        } else {
            GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 6, 1, COLOR_BORDER);
        }

        // Coins décoratifs (bracket corners) — top-left et top-right
        drawCornerBrackets(gfx, x, y, w, h, hov);

        // Icône item (2× — 32×32) centrée dans la moitié haute
        if (icon != null) {
            float scale  = 2.0f;
            int   iconPx = (int)(16 * scale);
            int   iconX  = x + (w - iconPx) / 2;
            int   iconY  = y + (h / 2 - iconPx) / 2 + 4;
            gfx.pose().pushPose();
            gfx.pose().translate(iconX, iconY, 0);
            gfx.pose().scale(scale, scale, scale);
            gfx.renderItem(icon, 0, 0);
            gfx.pose().popPose();
        }

        // Titre (gras, centré dans la moitié basse)
        int halfY  = y + h / 2;
        int titleY = icon != null ? halfY + 4 : halfY - font.lineHeight;
        Component title = Component.literal(getMessage().getString()).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, title, x + w / 2, titleY, hov ? COLOR_TITLE_H : COLOR_TITLE);

        // Sous-titre (0.8× scale, couleur atténuée) — texte pré-tronqué dans le constructeur
        if (!truncatedSubtitle.isEmpty()) {
            int   subY     = titleY + font.lineHeight + 2;
            float subScale = 0.80f;
            gfx.pose().pushPose();
            gfx.pose().translate(x + w / 2f, subY, 0);
            gfx.pose().scale(subScale, subScale, 1f);
            gfx.drawCenteredString(font, truncatedSubtitle, 0, 0, COLOR_SUBTITLE);
            gfx.pose().popPose();
        }
    }

    private void drawCornerBrackets(GuiGraphics gfx, int x, int y, int w, int h, boolean hov) {
        int arm   = 5;
        int thick = 1;
        int off   = 9;
        int colorNormal = 0x553DDE6A;
        int colorHover  = 0xCC3DDE6A;

        int lx = x + off;
        int rx = x + w - off;
        int ty = y + off;
        int by = y + h - off;

        // ┌ haut-gauche
        gfx.fill(lx,         ty,         lx + arm,   ty + thick, colorNormal);
        gfx.fill(lx,         ty,         lx + thick, ty + arm,   colorNormal);

        // ┐ haut-droit
        gfx.fill(rx - arm,   ty,         rx,         ty + thick, colorNormal);
        gfx.fill(rx - thick, ty,         rx,         ty + arm,   colorNormal);

        if (hov) {
            // Coins du haut passent en vif
            gfx.fill(lx,         ty,         lx + arm,   ty + thick, colorHover);
            gfx.fill(lx,         ty,         lx + thick, ty + arm,   colorHover);
            gfx.fill(rx - arm,   ty,         rx,         ty + thick, colorHover);
            gfx.fill(rx - thick, ty,         rx,         ty + arm,   colorHover);

            // └ bas-gauche
            gfx.fill(lx,         by - thick, lx + arm,   by,         colorHover);
            gfx.fill(lx,         by - arm,   lx + thick, by,         colorHover);

            // ┘ bas-droit
            gfx.fill(rx - arm,   by - thick, rx,         by,         colorHover);
            gfx.fill(rx - thick, by - arm,   rx,         by,         colorHover);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
