package com.example.rolynkmodmenu.client.screen.ville;

import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Formulaire de création d'une nouvelle ville.
 * Panneau overlay 280×140 px.
 */
public class CreerVilleScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 140;

    /** Dimensions responsive du dialogue — s'adapte aux petits écrans. */
    private int dialogW() { return Math.min(PANEL_W, width  - 40); }
    private int dialogH() { return Math.min(PANEL_H, height - 40); }

    private static final int COLOR_BG      = 0xF20D1117;
    private static final int COLOR_HEADER  = 0xFF141C26;
    private static final int COLOR_BORDER  = 0xFF1E2D3D;
    private static final int COLOR_CANCEL_N  = 0xFF5E1A1A;
    private static final int COLOR_CANCEL_H  = 0xFF9E2A2A;
    private static final int COLOR_CONFIRM_N = 0xFF1A5E1A;
    private static final int COLOR_CONFIRM_H = 0xFF2A9E2A;

    private final Screen previousScreen;
    private EditBox nameInput;

    public CreerVilleScreen(Screen previousScreen) {
        super(Component.literal("Créer une ville"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int px = (width  - dialogW()) / 2;
        int py = (height - dialogH()) / 2;

        nameInput = new EditBox(font, px + 12, py + 60, dialogW() - 24, 16,
                Component.literal("Nom de la ville"));
        nameInput.setMaxLength(32);
        nameInput.setFocused(true);
        nameInput.setHint(Component.literal("Nom de la ville (max 32 caractères)"));
        addRenderableWidget(nameInput);
        setFocused(nameInput);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int dw = dialogW(), dh = dialogH();
        int px = (width  - dw) / 2;
        int py = (height - dh) / 2;

        // Fond + header
        GuiUtils.fillChamferedRect(gfx, px, py, dw, dh, 7, COLOR_BG);
        GuiUtils.fillChamferedRect(gfx, px + 2, py + 2, dw - 4, 30, 6, COLOR_HEADER);
        GuiUtils.drawChamferedBorder(gfx, px, py, dw, dh, 7, 2, COLOR_BORDER);
        GuiUtils.drawChamferedBorder(gfx, px + 2, py + 2, dw - 4, 30, 5, 1, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, px, py, dw, dh, 6, 5, 1, 0x553DDE6A);

        // Titre
        gfx.drawCenteredString(font, "§lCRÉER UNE VILLE", px + dw / 2, py + 11, 0xFFFFFFFF);

        // Coût de création (le serveur reste l'autorité — voir RolynkConfig)
        gfx.drawCenteredString(font, "§7Coût de création : §e500 "
                + com.example.rolynkmodmenu.util.Money.SYMBOL + " §7seront débités",
                px + dw / 2, py + 36, 0xFFAAAAAA);

        // Label + EditBox
        gfx.drawString(font, "§7Nom de la ville :", px + 12, py + 50, 0xFFAAAAAA, false);

        // Fond EditBox
        GuiUtils.fillChamferedRect(gfx, px + 10, py + 58, dw - 20, 20, 3, 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, px + 10, py + 58, dw - 20, 20, 3, 1, COLOR_BORDER);

        super.render(gfx, mouseX, mouseY, partialTick);

        // Boutons
        int btnY  = py + dh - 26;
        int btnW  = (dw - 36) / 2;
        int btnH  = 18;
        int btnCX = px + 12;
        int btnFX = px + 12 + btnW + 12;

        boolean hCan = mouseX >= btnCX && mouseX < btnCX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean hCon = mouseX >= btnFX && mouseX < btnFX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean can  = nameInput != null && !nameInput.getValue().trim().isEmpty();

        GuiUtils.fillChamferedRect(gfx, btnCX, btnY, btnW, btnH, 4, hCan ? COLOR_CANCEL_H  : COLOR_CANCEL_N);
        GuiUtils.drawChamferedBorder(gfx, btnCX, btnY, btnW, btnH, 4, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, "ANNULER", btnCX + btnW / 2, btnY + (btnH - font.lineHeight) / 2, 0xFFFFFFFF);

        int bgCon = !can ? 0xFF444444 : (hCon ? COLOR_CONFIRM_H : COLOR_CONFIRM_N);
        GuiUtils.fillChamferedRect(gfx, btnFX, btnY, btnW, btnH, 4, bgCon);
        GuiUtils.drawChamferedBorder(gfx, btnFX, btnY, btnW, btnH, 4, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, "CONFIRMER", btnFX + btnW / 2, btnY + (btnH - font.lineHeight) / 2,
                can ? 0xFFFFFFFF : 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int dw = dialogW(), dh = dialogH();
        int px = (width  - dw) / 2;
        int py = (height - dh) / 2;
        int btnY  = py + dh - 26;
        int btnW  = (dw - 36) / 2;
        int btnH  = 18;
        int btnCX = px + 12;
        int btnFX = px + 12 + btnW + 12;
        int mx = (int) mouseX, my = (int) mouseY;

        if (mx >= btnCX && mx < btnCX + btnW && my >= btnY && my < btnY + btnH) { doCancel(); return true; }
        if (mx >= btnFX && mx < btnFX + btnW && my >= btnY && my < btnY + btnH) { doConfirm(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { doConfirm(); return true; }
        if (keyCode == 256) { doCancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics gfx, int mx, int my, float pt) {}

    private void doCancel() { minecraft.setScreen(previousScreen); }

    private void doConfirm() {
        if (nameInput == null) return;
        String nom = nameInput.getValue().trim();
        if (nom.isEmpty()) return;
        if (minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand("ville creer " + nom);
        minecraft.setScreen(new ListeVillesScreen());
    }
}
