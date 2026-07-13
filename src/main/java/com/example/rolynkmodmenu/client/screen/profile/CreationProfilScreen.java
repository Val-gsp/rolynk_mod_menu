package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.ProfilRpCreatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fenêtre « Création Profil RP » — ouverte automatiquement à la première
 * connexion (profil RP absent en base). Même habillage que le menu principal
 * (panel BaseMenuScreen : header logo, footer horloge, style néon).
 *
 * Le joueur renseigne son personnage ; la nouvelle ville est préremplie avec
 * "Rolynk" et non modifiable. La validation finale est faite côté serveur
 * (InputValidator) ; en cas de refus, le message d'erreur s'affiche ici.
 */
public class CreationProfilScreen extends BaseMenuScreen {

    // ── Couleurs (identiques à ProfileScreen) ─────────────────────────────
    private static final int C_INFO_BG  = 0xFF141C26;
    private static final int C_BORDER   = 0xFF1E2D3D;
    private static final int C_LABEL    = 0xFF607080;
    private static final int C_VALUE    = 0xFFEEF2F5;
    private static final int C_VILLE    = 0xFFFFAA00;  // or — nouvelle ville imposée

    private static final int BTN_H   = 20;
    private static final int FIELD_H = 16;
    private static final int ROW_H   = 30;   // label (10) + champ (16) + marge (4)

    private static final String[] SEXES = {"Homme", "Femme", "Autre"};

    /** Longueur maximale de la description (avec compteur affiché). */
    private static final int DESC_MAX = 50;

    private EditBox nomInput, prenomInput, tailleInput, ancienneVilleInput, metierInput, descriptionInput;
    private int sexeIndex = 0;

    /** Message d'erreur renvoyé par le serveur (null = aucun). */
    private volatile String erreur = null;
    /** Anti double-clic pendant l'aller-retour serveur. */
    private boolean envoiEnCours = false;

    // Zones cliquables calculées au rendu
    private int sexeBtnX, sexeBtnY, sexeBtnW;
    private int validerX, validerY, validerW;

    public CreationProfilScreen() {
        super("MENU", "CRÉATION PROFIL RP");
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    private int colW()     { return (contentW() - BTN_GAP) / 2; }
    private int col2X()    { return contentX() + colW() + BTN_GAP; }
    /** Y de la rangée {@code row} (0-2 = champs, 3 = description). */
    private int rowY(int row) { return contentY() + 12 + row * ROW_H; }

    @Override
    protected void initContent() {
        int cw = colW();

        // Fonds des champs — ajoutés en PREMIER pour être rendus sous les
        // EditBox (BaseMenuScreen rend les widgets dans l'ordre d'ajout).
        addRenderableOnly((net.minecraft.client.gui.components.Renderable)
                (gfx, mx, my, pt) -> {
            drawFieldBg(gfx, contentX(), rowY(0) + 10, cw);
            drawFieldBg(gfx, col2X(),    rowY(0) + 10, cw);
            drawFieldBg(gfx, col2X(),    rowY(1) + 10, cw);
            drawFieldBg(gfx, contentX(), rowY(2) + 10, cw);
            drawFieldBg(gfx, col2X(),    rowY(2) + 10, cw);
            drawFieldBg(gfx, contentX(), rowY(3) + 10, contentW());
        });

        nomInput           = champ(contentX(), rowY(0) + 10, cw, "Nom", 32);
        prenomInput        = champ(col2X(),    rowY(0) + 10, cw, "Prénom", 32);
        // rangée 1 : Sexe = bouton cyclique (rendu manuel), Taille = champ
        tailleInput        = champ(col2X(),    rowY(1) + 10, cw, "ex : 1m80", 16);
        ancienneVilleInput = champ(contentX(), rowY(2) + 10, cw, "Ancienne ville", 32);
        metierInput        = champ(col2X(),    rowY(2) + 10, cw, "Métier", 32);
        descriptionInput   = champ(contentX(), rowY(3) + 10, contentW(), "Décris ton personnage...", 50);

        setFocused(nomInput);
        nomInput.setFocused(true);
    }

    private EditBox champ(int x, int y, int w, String hint, int maxLen) {
        EditBox box = new EditBox(font, x + 5, y + 4, w - 10, 10, Component.literal(hint));
        box.setMaxLength(maxLen);
        box.setHint(Component.literal("§8" + hint));
        box.setBordered(false);
        addRenderableWidget(box);
        return box;
    }

    // ── Rendu ─────────────────────────────────────────────────────────────
    // BaseMenuScreen dessine panel/header/widgets puis appelle renderContent :
    // les fonds des champs sont donc dessinés ici PUIS les EditBox par-dessus
    // au prochain passage — pour garder le texte visible, on redessine les
    // valeurs par-dessus n'est pas nécessaire : on peint les fonds AVANT les
    // widgets via renderContentBackground (voir render ci-dessous).

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = contentX(), cw = contentW(), colw = colW();

        // Sous-titre
        gfx.drawCenteredString(font, "§7Créez votre personnage RP.",
                panelX() + panelW() / 2, contentY(), 0xFFAAAAAA);

        // Labels des champs
        label(gfx, "NOM", cx, rowY(0));
        label(gfx, "PRÉNOM", col2X(), rowY(0));
        label(gfx, "SEXE", cx, rowY(1));
        label(gfx, "TAILLE", col2X(), rowY(1));
        label(gfx, "ANCIENNE VILLE", cx, rowY(2));
        label(gfx, "MÉTIER", col2X(), rowY(2));
        label(gfx, "DESCRIPTION DU PERSONNAGE", cx, rowY(3));

        // Compteur de caractères de la description : « 11/50 »
        int descLen = descriptionInput != null ? descriptionInput.getValue().length() : 0;
        String compteur = descLen + "/" + DESC_MAX;
        int compteurCol = descLen >= DESC_MAX ? 0xFFFF5555
                        : descLen >= DESC_MAX - 10 ? C_VILLE : C_LABEL;
        gfx.drawString(font, compteur, cx + cw - font.width(compteur), rowY(3), compteurCol, false);

        // Bouton cyclique Sexe (même gabarit que les champs)
        sexeBtnX = cx; sexeBtnY = rowY(1) + 10; sexeBtnW = colw;
        boolean hovSexe = hover(mouseX, mouseY, sexeBtnX, sexeBtnY, sexeBtnW, FIELD_H);
        GuiUtils.fillChamferedRect(gfx, sexeBtnX, sexeBtnY, sexeBtnW, FIELD_H, 3,
                hovSexe ? 0xFF1A2840 : C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, sexeBtnX, sexeBtnY, sexeBtnW, FIELD_H, 3, 1,
                hovSexe ? 0xFF3DD96A : C_BORDER);
        gfx.fill(sexeBtnX + 2, sexeBtnY + 3, sexeBtnX + 3, sexeBtnY + FIELD_H - 3, 0xFF2EA84E);
        gfx.drawCenteredString(font, SEXES[sexeIndex] + " §8⇄",
                sexeBtnX + sexeBtnW / 2, sexeBtnY + 4, C_VALUE);

        // Ligne « Nouvelle ville : Rolynk (non modifiable) » + erreur éventuelle
        int nvY = rowY(3) + ROW_H;
        gfx.drawString(font, "NOUVELLE VILLE", cx, nvY, C_LABEL, false);
        int nvX = cx + font.width("NOUVELLE VILLE") + 8;
        gfx.drawString(font, "§lRolynk", nvX, nvY, C_VILLE, false);
        gfx.drawString(font, " §8(non modifiable)", nvX + font.width("Rolynk") + 4, nvY, 0xFF666666, false);
        if (erreur != null) {
            String msg = "§c" + erreur;
            gfx.drawString(font, msg, cx + cw - font.width(msg), nvY, 0xFFFF5555, false);
        }

        // Bouton VALIDER — style néon (identique aux boutons de ProfileScreen)
        validerW = cw; validerX = cx;
        validerY = panelY() + panelH() - FOOTER_H - BTN_H - 6;
        boolean can    = champsRemplis() && !envoiEnCours;
        boolean hovVal = hover(mouseX, mouseY, validerX, validerY, validerW, BTN_H);
        GuiUtils.fillChamferedRect(gfx, validerX, validerY, validerW, BTN_H, 4,
                !can ? 0xA0141C26 : (hovVal ? 0xC01A2840 : 0xA0141C26));
        if (hovVal && can) {
            GuiUtils.drawChamferedBorder(gfx, validerX - 2, validerY - 2, validerW + 4, BTN_H + 4, 6, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, validerX - 1, validerY - 1, validerW + 2, BTN_H + 2, 5, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, validerX, validerY, validerW, BTN_H, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, validerX, validerY, validerW, BTN_H, 4, 1, 0xFF1C2C3C);
        }
        Component titre = Component.literal(envoiEnCours ? "ENVOI..." : "VALIDER")
                .withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, titre, validerX + validerW / 2,
                validerY + (BTN_H - font.lineHeight) / 2 + 1,
                !can ? 0xFF888888 : (hovVal ? 0xFF3DD96A : C_VALUE));
    }

    /** Fond d'un champ de saisie — même style que les lignes d'infos du Profil. */
    private void drawFieldBg(GuiGraphics gfx, int x, int y, int w) {
        GuiUtils.fillChamferedRect(gfx, x, y, w, FIELD_H, 3, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, FIELD_H, 3, 1, C_BORDER);
        gfx.fill(x + 2, y + 3, x + 3, y + FIELD_H - 3, 0xFF2EA84E);
    }

    private void label(GuiGraphics gfx, String txt, int x, int y) {
        gfx.drawString(font, txt, x, y, C_LABEL, false);
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
            if (hover(mx, my, sexeBtnX, sexeBtnY, sexeBtnW, FIELD_H)) {
                sexeIndex = (sexeIndex + 1) % SEXES.length;
                return true;
            }
            if (hover(mx, my, validerX, validerY, validerW, BTN_H)) {
                doValider();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Validation locale (miroir des règles serveur) pour un retour immédiat :
     * première lettre en majuscule (nom, prénom, ancienne ville, métier,
     * description), taille au format chiffre + m + nombre, description ≤ 50.
     * @return message d'erreur ciblé, ou null si tout est valide.
     */
    private String validerLocal() {
        if (!majusculeInitiale(nomInput))           return "NOM : première lettre en MAJUSCULE.";
        if (!majusculeInitiale(prenomInput))        return "PRÉNOM : première lettre en MAJUSCULE.";
        String t = tailleInput.getValue().trim();
        if (!t.matches("[0-9]m[0-9]{1,2}"))         return "TAILLE : format 1m80 (chiffre + m + nombre).";
        if (!majusculeInitiale(ancienneVilleInput)) return "ANCIENNE VILLE : première lettre en MAJUSCULE.";
        if (!majusculeInitiale(metierInput))        return "MÉTIER : première lettre en MAJUSCULE.";
        if (!majusculeInitiale(descriptionInput))   return "DESCRIPTION : première lettre en MAJUSCULE.";
        if (descriptionInput.getValue().trim().length() > DESC_MAX)
                                                    return "DESCRIPTION : " + DESC_MAX + " caractères maximum.";
        return null;
    }

    private static boolean majusculeInitiale(EditBox b) {
        String v = b.getValue().trim();
        return !v.isEmpty() && Character.isUpperCase(v.charAt(0));
    }

    private void doValider() {
        if (!champsRemplis() || envoiEnCours) return;
        erreur = validerLocal();
        if (erreur != null) return;
        envoiEnCours = true;
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
            // Enchaîne sur le choix du skin (le joueur peut valider sans changer).
            minecraft.setScreen(new SkinScreen());
        } else {
            erreur = message;
        }
    }

    // Création obligatoire : Échap ne ferme pas la fenêtre (elle se rouvrirait
    // de toute façon à la prochaine connexion tant que le profil n'existe pas).
    @Override public boolean shouldCloseOnEsc() { return false; }
}
