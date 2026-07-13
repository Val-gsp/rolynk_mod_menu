package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.ProfilRpCreatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fenêtre « Création Profil RP » — ouverte automatiquement à la première
 * connexion (profil RP absent en base). Le joueur renseigne son personnage ;
 * la nouvelle ville est préremplie avec "Rolynk" et non modifiable.
 *
 * La validation finale est faite côté serveur (InputValidator) ; en cas de
 * refus, le message d'erreur est affiché dans le formulaire.
 */
public class CreationProfilScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 232;

    private int dialogW() { return Math.min(PANEL_W, width  - 20); }
    private int dialogH() { return Math.min(PANEL_H, height - 20); }

    private static final int COLOR_BG        = 0xF20D1117;
    private static final int COLOR_HEADER    = 0xFF141C26;
    private static final int COLOR_BORDER    = 0xFF1E2D3D;
    private static final int COLOR_LABEL     = 0xFFAAAAAA;
    private static final int COLOR_CONFIRM_N = 0xFF1A5E1A;
    private static final int COLOR_CONFIRM_H = 0xFF2A9E2A;
    private static final int COLOR_READONLY  = 0xFFFFAA00;

    private static final String[] SEXES = {"Homme", "Femme", "Autre"};

    private EditBox nomInput, prenomInput, tailleInput, ancienneVilleInput, metierInput, descriptionInput;
    private int sexeIndex = 0;

    /** Message d'erreur renvoyé par le serveur (null = aucun). */
    private volatile String erreur = null;
    /** Anti double-clic pendant l'aller-retour serveur. */
    private boolean envoiEnCours = false;

    // Zones cliquables calculées au rendu
    private int sexeBtnX, sexeBtnY, sexeBtnW, sexeBtnH;
    private int validerX, validerY, validerW, validerH;

    public CreationProfilScreen() {
        super(Component.literal("Création Profil RP"));
    }

    // ── Grille du formulaire ──────────────────────────────────────────────
    // 3 rangées de 2 colonnes + description pleine largeur + bouton VALIDER.

    private int colW()        { return (dialogW() - 36) / 2; }
    private int col1X(int px) { return px + 12; }
    private int col2X(int px) { return px + 24 + colW(); }
    private int rowY(int py, int row) { return py + 48 + row * 30; }

    @Override
    protected void init() {
        int px = (width  - dialogW()) / 2;
        int py = (height - dialogH()) / 2;
        int cw = colW();

        nomInput           = champ(col1X(px), rowY(py, 0) + 10, cw, "Nom", 32);
        prenomInput        = champ(col2X(px), rowY(py, 0) + 10, cw, "Prénom", 32);
        // rangée 1 : Sexe = bouton cyclique (rendu manuel), Taille = champ
        tailleInput        = champ(col2X(px), rowY(py, 1) + 10, cw, "ex : 1m80", 16);
        ancienneVilleInput = champ(col1X(px), rowY(py, 2) + 10, cw, "Ancienne ville", 32);
        metierInput        = champ(col2X(px), rowY(py, 2) + 10, cw, "Métier", 32);
        descriptionInput   = champ(col1X(px), rowY(py, 3) + 10, dialogW() - 24, "Décris ton personnage...", 256);

        setFocused(nomInput);
        nomInput.setFocused(true);
    }

    private EditBox champ(int x, int y, int w, String hint, int maxLen) {
        EditBox box = new EditBox(font, x + 2, y + 2, w - 4, 12, Component.literal(hint));
        box.setMaxLength(maxLen);
        box.setHint(Component.literal("§8" + hint));
        box.setBordered(false);
        addRenderableWidget(box);
        return box;
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int dw = dialogW(), dh = dialogH();
        int px = (width  - dw) / 2;
        int py = (height - dh) / 2;
        int cw = colW();

        // Fond + header
        GuiUtils.fillChamferedRect(gfx, px, py, dw, dh, 7, COLOR_BG);
        GuiUtils.fillChamferedRect(gfx, px + 2, py + 2, dw - 4, 24, 6, COLOR_HEADER);
        GuiUtils.drawChamferedBorder(gfx, px, py, dw, dh, 7, 2, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, px, py, dw, dh, 6, 5, 1, 0x553DDE6A);

        gfx.drawCenteredString(font, "§lCRÉATION PROFIL RP", px + dw / 2, py + 10, 0xFFFFFFFF);
        gfx.drawCenteredString(font, "§7Créez votre personnage RP.", px + dw / 2, py + 32, 0xFFAAAAAA);

        // Rangée 0 : Nom | Prénom
        label(gfx, "Nom", col1X(px), rowY(py, 0));
        boxBg(gfx, col1X(px), rowY(py, 0) + 10, cw);
        label(gfx, "Prénom", col2X(px), rowY(py, 0));
        boxBg(gfx, col2X(px), rowY(py, 0) + 10, cw);

        // Rangée 1 : Sexe (bouton cyclique) | Taille
        label(gfx, "Sexe", col1X(px), rowY(py, 1));
        sexeBtnX = col1X(px); sexeBtnY = rowY(py, 1) + 10; sexeBtnW = cw; sexeBtnH = 16;
        boolean hovSexe = hover(mouseX, mouseY, sexeBtnX, sexeBtnY, sexeBtnW, sexeBtnH);
        GuiUtils.fillChamferedRect(gfx, sexeBtnX, sexeBtnY, sexeBtnW, sexeBtnH, 3,
                hovSexe ? 0xFF1A2840 : 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, sexeBtnX, sexeBtnY, sexeBtnW, sexeBtnH, 3, 1,
                hovSexe ? 0xFF3DD96A : COLOR_BORDER);
        gfx.drawCenteredString(font, SEXES[sexeIndex] + " §8⇄", sexeBtnX + sexeBtnW / 2,
                sexeBtnY + 4, 0xFFEEF2F5);

        label(gfx, "Taille", col2X(px), rowY(py, 1));
        boxBg(gfx, col2X(px), rowY(py, 1) + 10, cw);

        // Rangée 2 : Ancienne ville | Métier
        label(gfx, "Ancienne ville", col1X(px), rowY(py, 2));
        boxBg(gfx, col1X(px), rowY(py, 2) + 10, cw);
        label(gfx, "Métier", col2X(px), rowY(py, 2));
        boxBg(gfx, col2X(px), rowY(py, 2) + 10, cw);

        // Rangée 3 : Description (pleine largeur)
        label(gfx, "Description du personnage", col1X(px), rowY(py, 3));
        boxBg(gfx, col1X(px), rowY(py, 3) + 10, dw - 24);

        // Nouvelle ville — préremplie, non modifiable
        int nvY = rowY(py, 3) + 30;
        gfx.drawString(font, "§7Nouvelle ville :", col1X(px), nvY + 3, COLOR_LABEL, false);
        gfx.drawString(font, "§l" + "Rolynk", col1X(px) + font.width("Nouvelle ville :  "),
                nvY + 3, COLOR_READONLY, false);
        gfx.drawString(font, "§8(non modifiable)", col1X(px) + font.width("Nouvelle ville :  Rolynk  "),
                nvY + 3, 0xFF666666, false);

        // Erreur serveur éventuelle
        if (erreur != null)
            gfx.drawCenteredString(font, "§c" + erreur, px + dw / 2, nvY + 14, 0xFFFF5555);

        // Bouton VALIDER
        validerW = dw - 24; validerH = 18;
        validerX = px + 12;  validerY = py + dh - 24;
        boolean can    = champsRemplis() && !envoiEnCours;
        boolean hovVal = hover(mouseX, mouseY, validerX, validerY, validerW, validerH);
        int bg = !can ? 0xFF444444 : (hovVal ? COLOR_CONFIRM_H : COLOR_CONFIRM_N);
        GuiUtils.fillChamferedRect(gfx, validerX, validerY, validerW, validerH, 4, bg);
        GuiUtils.drawChamferedBorder(gfx, validerX, validerY, validerW, validerH, 4, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, envoiEnCours ? "ENVOI..." : "VALIDER",
                validerX + validerW / 2, validerY + 5, can ? 0xFFFFFFFF : 0xFF888888);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics gfx, String txt, int x, int y) {
        gfx.drawString(font, "§7" + txt, x, y, COLOR_LABEL, false);
    }

    private void boxBg(GuiGraphics gfx, int x, int y, int w) {
        GuiUtils.fillChamferedRect(gfx, x, y, w, 16, 3, 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, 16, 3, 1, COLOR_BORDER);
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean champsRemplis() {
        return filled(nomInput) && filled(prenomInput) && filled(tailleInput)
                && filled(ancienneVilleInput) && filled(metierInput) && filled(descriptionInput);
    }

    private static boolean filled(EditBox b) {
        return b != null && !b.getValue().trim().isEmpty();
    }

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (hover(mx, my, sexeBtnX, sexeBtnY, sexeBtnW, sexeBtnH)) {
                sexeIndex = (sexeIndex + 1) % SEXES.length;
                return true;
            }
            if (hover(mx, my, validerX, validerY, validerW, validerH)) {
                doValider();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void doValider() {
        if (!champsRemplis() || envoiEnCours) return;
        envoiEnCours = true;
        erreur = null;
        PacketDistributor.sendToServer(new ProfilRpCreatePayload(
                nomInput.getValue().trim(),
                prenomInput.getValue().trim(),
                SEXES[sexeIndex],
                tailleInput.getValue().trim(),
                ancienneVilleInput.getValue().trim(),
                metierInput.getValue().trim(),
                descriptionInput.getValue().trim()
        ));
    }

    /** Appelé par ClientPayloadHandlers quand le serveur répond. */
    public void onResultat(boolean ok, String message) {
        envoiEnCours = false;
        if (ok) {
            minecraft.setScreen(null);
        } else {
            erreur = message;
        }
    }

    // Création obligatoire : Échap ne ferme pas la fenêtre (elle se rouvrirait
    // de toute façon à la prochaine connexion tant que le profil n'existe pas).
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen()    { return false; }
    @Override public void renderBackground(GuiGraphics gfx, int mx, int my, float pt) {
        gfx.fill(0, 0, width, height, 0xB0000000);
    }
}
