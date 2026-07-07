package com.example.rolynkmodmenu.client.screen.balise;

import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran de création d'une nouvelle balise.
 * Panneau centré 240×120 px, sans BaseMenuScreen.
 */
public class CreerBaliseScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 120;

    /** Dimensions responsive du dialogue — s'adapte aux petits écrans. */
    private int dialogW() { return Math.min(PANEL_W, width  - 40); }
    private int dialogH() { return Math.min(PANEL_H, height - 40); }

    private static final int COLOR_BG     = 0xCC000000;
    private static final int COLOR_BORDER = 0xFF3D7A2E;
    private static final int COLOR_HEADER = 0x99000000;

    private static final int COLOR_BTN_CANCEL_NORMAL = 0xFF5E1A1A;
    private static final int COLOR_BTN_CANCEL_HOVER  = 0xFF9E2A2A;
    private static final int COLOR_BTN_CONFIRM_NORMAL = 0xFF1A5E1A;
    private static final int COLOR_BTN_CONFIRM_HOVER  = 0xFF2A9E2A;

    private final Screen previousScreen;
    private EditBox nameInput;

    public CreerBaliseScreen(Screen previousScreen) {
        super(Component.literal("Créer une balise"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int px = (width  - dialogW()) / 2;
        int py = (height - dialogH()) / 2;

        // EditBox pour le nom (centré dans le panneau)
        int inputW = dialogW() - 24;
        int inputX = px + 12;
        int inputY = py + 44;

        nameInput = new EditBox(font, inputX, inputY, inputW, 16, Component.literal("Nom de la balise"));
        nameInput.setMaxLength(32);
        nameInput.setFocused(true);
        nameInput.setHint(Component.literal("Nom de la balise"));
        addRenderableWidget(nameInput);

        setFocused(nameInput);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int dw = dialogW(), dh = dialogH();
        int px = (width  - dw) / 2;
        int py = (height - dh) / 2;

        // Fond du panneau
        GuiUtils.fillRoundedRect(gfx, px, py, dw, dh, 6, COLOR_BG);

        // Header
        GuiUtils.fillRoundedRect(gfx, px + 2, py + 2, dw - 4, 28, 6, COLOR_HEADER);

        // Bordure verte
        GuiUtils.drawRoundedBorder(gfx, px, py, dw, dh, 6, 2, COLOR_BORDER);
        GuiUtils.drawRoundedBorder(gfx, px + 2, py + 2, dw - 4, 28, 5, 1, COLOR_BORDER);

        // Titre centré dans le header
        gfx.drawCenteredString(font, "CRÉER UNE BALISE", px + dw / 2, py + 10, 0xFFFFFFFF);

        // Widgets (EditBox)
        super.render(gfx, mouseX, mouseY, partialTick);

        // Label au-dessus de l'EditBox
        gfx.drawString(font, "Nom :", px + 12, py + 36, 0xFFAAAAAA, false);

        // Boutons ANNULER et CONFIRMER
        int btnY  = py + dh - 24;
        int btnW  = (dw - 36) / 2;
        int btnH  = 16;
        int btnCancelX  = px + 12;
        int btnConfirmX = px + 12 + btnW + 12;

        boolean hoverCancel  = mouseX >= btnCancelX  && mouseX < btnCancelX  + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean hoverConfirm = mouseX >= btnConfirmX && mouseX < btnConfirmX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

        GuiUtils.fillRoundedRect(gfx, btnCancelX, btnY, btnW, btnH, 3,
                hoverCancel ? COLOR_BTN_CANCEL_HOVER : COLOR_BTN_CANCEL_NORMAL);
        GuiUtils.drawRoundedBorder(gfx, btnCancelX, btnY, btnW, btnH, 3, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, "ANNULER", btnCancelX + btnW / 2, btnY + (btnH - font.lineHeight) / 2, 0xFFFFFFFF);

        boolean canConfirm = nameInput != null && !nameInput.getValue().trim().isEmpty();
        int confirmBg = !canConfirm ? 0xFF444444 : (hoverConfirm ? COLOR_BTN_CONFIRM_HOVER : COLOR_BTN_CONFIRM_NORMAL);
        GuiUtils.fillRoundedRect(gfx, btnConfirmX, btnY, btnW, btnH, 3, confirmBg);
        GuiUtils.drawRoundedBorder(gfx, btnConfirmX, btnY, btnW, btnH, 3, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, "CONFIRMER", btnConfirmX + btnW / 2, btnY + (btnH - font.lineHeight) / 2,
                canConfirm ? 0xFFFFFFFF : 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int dw = dialogW(), dh = dialogH();
        int px = (width  - dw) / 2;
        int py = (height - dh) / 2;

        int btnY  = py + dh - 24;
        int btnW  = (dw - 36) / 2;
        int btnH  = 16;
        int btnCancelX  = px + 12;
        int btnConfirmX = px + 12 + btnW + 12;

        int mx = (int) mouseX, my = (int) mouseY;

        if (mx >= btnCancelX && mx < btnCancelX + btnW && my >= btnY && my < btnY + btnH) {
            doCancel();
            return true;
        }
        if (mx >= btnConfirmX && mx < btnConfirmX + btnW && my >= btnY && my < btnY + btnH) {
            doConfirm();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter = confirmer
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER ou GLFW_KEY_KP_ENTER
            doConfirm();
            return true;
        }
        // Escape = annuler
        if (keyCode == 256) {
            doCancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mx, int my, float pt) {
        // Pas de fond Minecraft par défaut — on garde le jeu visible derrière
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void doCancel() {
        minecraft.setScreen(previousScreen);
    }

    private void doConfirm() {
        if (nameInput == null) return;
        String nom = nameInput.getValue().trim();
        if (nom.isEmpty()) return;
        if (minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("balise creer " + nom);
        }
        minecraft.setScreen(previousScreen);
    }
}
