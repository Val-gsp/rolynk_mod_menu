package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Fenêtre Boutique — MENU > BOUTIQUE
 *
 * Hub à deux cartes :
 *   [ BOUTIQUE GRADE ]  ← en construction (ouvre l'écran "à venir")
 *   [ BOUTIQUE JEUX  ]  ← rachat d'objets par le serveur (fonctionnel)
 */
public class BoutiqueScreen extends BaseMenuScreen {

    private static final int C_BORDER    = 0xFF1E2D3D;
    private static final int C_CARD_BG   = 0xC0141C26;
    private static final int C_CARD_HOV  = 0xC01A2840;
    private static final int C_TITLE_HOT = 0xFF3DD96A;
    private static final int C_GREY      = 0xFF607080;
    private static final int GAP = 10;

    // Zones cliquables (calculées au rendu)
    private int gradeX, gradeY, jeuxX, jeuxY, cardW, cardH;

    public BoutiqueScreen() { super("MENU", "BOUTIQUE"); }

    @Override
    protected void initContent() { }

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = panelX() + PADDING;
        int cy = panelY() + HEADER_H + PADDING;
        int cw = panelW() - 2 * PADDING;
        int ch = panelH() - HEADER_H - 2 * PADDING - FOOTER_H;

        cardW = (cw - GAP) / 2;
        cardH = ch;
        gradeX = cx;              gradeY = cy;
        jeuxX  = cx + cardW + GAP; jeuxY  = cy;

        renderCard(gfx, mouseX, mouseY, gradeX, gradeY, "BOUTIQUE GRADE",
                "Grades et avantages", "§8En construction", false);
        renderCard(gfx, mouseX, mouseY, jeuxX, jeuxY, "BOUTIQUE JEUX",
                "Revends tes objets au serveur", "§aOuvert", true);
    }

    private void renderCard(GuiGraphics gfx, int mouseX, int mouseY,
                            int x, int y, String titre, String sousTitre,
                            String etat, boolean actif) {
        boolean hov = mouseX >= x && mouseX < x + cardW
                && mouseY >= y && mouseY < y + cardH;

        int bg = (hov && actif) ? C_CARD_HOV : C_CARD_BG;
        GuiUtils.fillChamferedRect(gfx, x, y, cardW, cardH, 5, bg);
        if (actif && hov) {
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, cardW + 4, cardH + 4, 7, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, cardW + 2, cardH + 2, 6, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, x, y, cardW, cardH, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, x, y, cardW, cardH, 5, 1, C_BORDER);
        }
        GuiUtils.drawCornerBrackets(gfx, x, y, cardW, cardH, 5, 4, 1,
                (actif && hov) ? 0xCC3DDE6A : 0x553DDE6A);

        int centerX = x + cardW / 2;
        int ty = y + cardH / 2 - font.lineHeight * 2;

        int titleColor = !actif ? C_GREY : (hov ? 0xFF90FFB8 : C_TITLE_HOT);
        gfx.drawCenteredString(font, Component.literal(titre)
                .withStyle(ChatFormatting.BOLD), centerX, ty, titleColor);
        ty += font.lineHeight + 4;
        gfx.drawCenteredString(font, "§7" + sousTitre, centerX, ty, C_GREY);
        ty += font.lineHeight + 8;
        gfx.fill(x + 14, ty, x + cardW - 14, ty + 1, 0x221E2D3D);
        ty += 6;
        gfx.drawCenteredString(font, etat, centerX, ty, C_GREY);

        if (actif) {
            ty += font.lineHeight + 6;
            gfx.drawCenteredString(font, Component.literal(hov ? "» ENTRER «" : "ENTRER")
                    .withStyle(ChatFormatting.BOLD), centerX, ty, hov ? 0xFF90FFB8 : C_TITLE_HOT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= gradeX && mx < gradeX + cardW && my >= gradeY && my < gradeY + cardH) {
            minecraft.setScreen(new BoutiqueGradeScreen());
            return true;
        }
        if (mx >= jeuxX && mx < jeuxX + cardW && my >= jeuxY && my < jeuxY + cardH) {
            minecraft.setScreen(new BoutiqueJeuxScreen());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() { minecraft.setScreen(new MainMenuScreen()); }
}
