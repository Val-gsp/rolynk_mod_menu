package com.example.rolynkmodmenu.client.screen.ville;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.profile.ProfilJoueurScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.client.ville.*;
import com.example.rolynkmodmenu.network.*;
import com.example.rolynkmodmenu.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Écran de gestion de ville pour les membres.
 *
 * États (enum Vue) :
 *   NORMAL    — vue principale avec stats + liste membres + boutons d'action
 *   MEMBRE    — overlay d'action sur un membre sélectionné
 *   BANQUE    — overlay dépôt / retrait
 *   LOGS      — overlay historique bancaire
 *   DEMANDES  — overlay demandes d'adhésion
 *   CONFIRMER — overlay de confirmation destructive
 */
public class GestionVilleScreen extends BaseMenuScreen {

    @Override public int panelW() { return Math.min(520, width  - 16); }

    // ── Machine à états ───────────────────────────────────────────────────
    private enum Vue { NORMAL, MEMBRE, BANQUE, LOGS, DEMANDES, RECRUTEMENT, CONFIRMER }
    private Vue vue = Vue.NORMAL;

    // Référence statique pour recevoir les VilleActionResultPayload
    public static GestionVilleScreen current = null;

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int C_BORDER      = 0xFF1E2D3D;
    private static final int C_INFO_BG     = 0xFF141C26;
    private static final int C_ROW_SEL     = 0x66FFFFFF;
    private static final int C_ROW_HOV     = 0x33FFFFFF;
    private static final int C_SCROLL      = 0xFF2EA84E;
    private static final int C_SCROLL_BG   = 0x44FFFFFF;
    private static final int C_OVL_BG      = 0xFF0D1117;
    private static final int C_BTN_OK      = 0xFF1A5E1A;
    private static final int C_BTN_OK_H    = 0xFF2A9E2A;
    private static final int C_BTN_RED     = 0xFF5E1A1A;
    private static final int C_BTN_RED_H   = 0xFF9E2A2A;
    private static final int C_BTN_GREY    = 0xFF333333;
    private static final int C_BTN_GREY_H  = 0xFF555555;

    private static final int SEP     = 8;

    /** Largeur du panneau infos gauche — 32 % du panel, max 165 px. */
    private int leftW() { return Math.min(165, panelW() * 32 / 100); }
    private static final int ROW_H   = 18;
    private static final int BTN_H   = 24;
    private static final int SCROLL_W= 6;

    // ── Données ───────────────────────────────────────────────────────────
    private VilleListPayload.VilleEntry ville;
    private final Screen previousScreen;
    private double currentBanque;

    // Liste membres
    private int membresScroll = 0;
    private int logsScroll    = 0;
    private int demandesScroll= 0;

    // Overlay membre
    private VilleMembresPayload.MembreEntry selectedMembre = null;

    // Overlay banque
    private StringBuilder banqueInput = new StringBuilder();

    // Overlay confirmer
    private String confirmMsg    = "";
    private Runnable confirmAction;
    private String pendingAction = "";

    public GestionVilleScreen(VilleListPayload.VilleEntry ville, Screen prev) {
        super("MENU", "VILLE", "GESTION");
        this.ville          = ville;
        this.previousScreen = prev;
        this.currentBanque  = ville.banque();
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    /** Timestamp du dernier envoi de VilleMembresRequestPayload (pour le retry). */
    private long lastMembresRequestMs = 0;
    /** Intervalle de retry tant que la liste est vide — légèrement supérieur au
     *  cooldown serveur (2 s) pour que la relance passe à coup sûr. */
    private static final long MEMBRES_RETRY_MS = 2_500L;

    @Override
    protected void initContent() {
        current = this;
        // On ne vide PAS les data managers : les données déjà reçues restent
        // affichées instantanément (et un refresh est demandé en parallèle).
        // Avant : si la requête tombait dans le cooldown serveur, elle était
        // droppée silencieusement et l'écran restait sur "Chargement..." à vie.
        requestMembres();
    }

    @Override
    public void removed() {
        super.removed();
        if (current == this) current = null;
    }

    /** Relance la requête membres tant qu'aucune donnée n'est arrivée
     *  (couvre le cas où la première requête a été absorbée par le cooldown serveur). */
    @Override
    public void tick() {
        super.tick();
        if (VilleMembresDataManager.getMembres().isEmpty()
                && System.currentTimeMillis() - lastMembresRequestMs > MEMBRES_RETRY_MS) {
            requestMembres();
        }
    }

    private void requestMembres() {
        if (minecraft.getConnection() != null) {
            lastMembresRequestMs = System.currentTimeMillis();
            PacketDistributor.sendToServer(new VilleMembresRequestPayload(ville.id()));
        }
    }

    /** Appelé par le handler de payload quand une réponse action arrive. */
    public void handleActionResult(VilleActionResultPayload payload) {
        if (!payload.newBanque().isEmpty()) {
            try { currentBanque = Double.parseDouble(payload.newBanque()); } catch (NumberFormatException ignored) {}
        }
        if (payload.success()) {
            if ("quitter".equals(pendingAction) || "dissoudre".equals(pendingAction)) {
                minecraft.setScreen(previousScreen instanceof ListeVillesScreen
                        ? previousScreen : new ListeVillesScreen());
                return;
            }
            requestMembres();
            vue = Vue.NORMAL;
            pendingAction = "";
        } else {
            // Échec : on RESTE sur l'overlay courant pour que le joueur puisse
            // corriger sa saisie (le message d'erreur arrive dans le chat).
            pendingAction = "";
        }
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px       = panelX(), py = panelY();
        int contentY = py + HEADER_H + PADDING;
        int contentH = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;

        // Zone boutons bas : 2 lignes de 18px + 2 gaps
        int btnZoneH = BTN_H * 2 + 14;
        int splitH   = contentH - btnZoneH;

        if (vue == Vue.NORMAL) {
            renderMainView(gfx, px, contentY, splitH, mouseX, mouseY);
            renderButtonsZone(gfx, px, contentY + splitH + 6, mouseX, mouseY);
        }

        // Overlays (dessinés par-dessus)
        switch (vue) {
            case MEMBRE      -> renderOverlayMembre(gfx, mouseX, mouseY);
            case BANQUE      -> renderOverlayBanque(gfx, mouseX, mouseY);
            case LOGS        -> renderOverlayLogs(gfx, mouseX, mouseY);
            case DEMANDES    -> renderOverlayDemandes(gfx, mouseX, mouseY);
            case RECRUTEMENT -> renderOverlayRecrutement(gfx, mouseX, mouseY);
            case CONFIRMER   -> renderOverlayConfirmer(gfx, mouseX, mouseY);
            default          -> {}
        }
    }

    // ── Vue principale ────────────────────────────────────────────────────

    private void renderMainView(GuiGraphics gfx, int px, int contentY, int splitH,
                                 int mouseX, int mouseY) {
        // ── Panneau gauche — infos ────────────────────────────────────────
        int lx = px + PADDING;
        GuiUtils.fillChamferedRect(gfx, lx, contentY, leftW(), splitH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, lx, contentY, leftW(), splitH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, lx, contentY, leftW(), splitH, 6, 5, 1, 0x553DDE6A);

        // Titre ville
        gfx.drawCenteredString(font, "§l" + ville.nom(), lx + leftW() / 2, contentY + 5, 0xFFFFDD88);
        gfx.fill(lx + 4, contentY + 16, lx + leftW() - 4, contentY + 17, C_BORDER);

        int tx = lx + 6, ty = contentY + 20;
        int lineH = font.lineHeight + 3;
        gfx.drawString(font, "§a⬤ §f" + ville.nbConnectes() + " §7connecté(s)", tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Membres : §f" + ville.nbMembres(), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Owner : §f" + ville.ownerPseudo(), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        String monde = ville.mondeSpawn().isEmpty() ? "Aucun" : GuiUtils.capitalize(ville.mondeSpawn());
        gfx.drawString(font, "§7Monde : §f" + monde, tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Banque : §f" + GuiUtils.formatBanque(currentBanque) + " " + Money.SYMBOL, tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Mon grade : " + gradeLabel(VilleMembresDataManager.getMyGrade()), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Recrutement : " + recrutementLabel(ville.recrutement()), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Fondée : §f" + ville.dateFondation(), tx, ty, 0xFFFFFFFF, false);

        // ── Panneau droit — membres ───────────────────────────────────────
        int rx   = px + PADDING + leftW() + SEP;
        int rw   = panelW() - PADDING - leftW() - SEP - PADDING;
        int listW = rw - SCROLL_W - 2;

        GuiUtils.fillChamferedRect(gfx, rx, contentY, listW, splitH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rx, contentY, listW, splitH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, rx, contentY, listW, splitH, 6, 5, 1, 0x553DDE6A);
        gfx.drawCenteredString(font, "§lMembres", rx + listW / 2, contentY + 4, 0xFFCCCCCC);
        gfx.fill(rx + 4, contentY + 14, rx + listW - 4, contentY + 15, 0x44FFFFFF);

        int listY = contentY + 16;
        int listH = splitH - 16;
        List<VilleMembresPayload.MembreEntry> membres = VilleMembresDataManager.getMembres();
        int visible = listH / ROW_H;
        int maxS    = Math.max(0, membres.size() - visible);
        membresScroll = Math.max(0, Math.min(membresScroll, maxS));

        if (membres.isEmpty()) {
            gfx.drawCenteredString(font, "Chargement...",
                    rx + listW / 2, listY + listH / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = membresScroll; i < membres.size() && i < membresScroll + visible; i++) {
                VilleMembresPayload.MembreEntry m = membres.get(i);
                int rowY = listY + (i - membresScroll) * ROW_H;
                boolean hov = mouseX >= rx && mouseX < rx + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
                if (hov) gfx.fill(rx + 1, rowY, rx + listW - 1, rowY + ROW_H, C_ROW_HOV);
                // Dot connecté
                gfx.fill(rx + 4, rowY + 5, rx + 8, rowY + 9, m.online() ? 0xFF55FF55 : 0xFF888888);
                // Grade à droite (nom complet + couleur)
                int gc       = GuiUtils.gradeColor(m.grade());
                String gLbl  = m.grade().isEmpty() ? "?" : m.grade();
                int gLblW    = font.width(gLbl);
                int gLblX    = rx + listW - gLblW - 6;
                gfx.drawString(font, gLbl, gLblX, rowY + (ROW_H - font.lineHeight) / 2, gc, false);
                // Pseudo (tronqué si trop long pour ne pas chevaucher le grade)
                int maxPseudoW = gLblX - (rx + 14);
                String pseudo  = m.pseudo();
                while (pseudo.length() > 1 && font.width(pseudo) > maxPseudoW)
                    pseudo = pseudo.substring(0, pseudo.length() - 1);
                if (!pseudo.equals(m.pseudo())) pseudo = pseudo.substring(0, pseudo.length() - 1) + "…";
                gfx.drawString(font, pseudo, rx + 12, rowY + (ROW_H - font.lineHeight) / 2, 0xFFFFFFFF, false);
                if (i < membres.size() - 1)
                    gfx.fill(rx + 4, rowY + ROW_H - 1, rx + listW - 4, rowY + ROW_H, 0x22FFFFFF);
            }
        }
        if (membres.size() > visible) {
            int sbX = rx + listW + 2;
            int tH  = Math.max(16, listH * visible / membres.size());
            int tY  = listY + (listH - tH) * membresScroll / Math.max(1, maxS);
            gfx.fill(sbX, listY, sbX + SCROLL_W, listY + listH, C_SCROLL_BG);
            gfx.fill(sbX, tY, sbX + SCROLL_W, tY + tH, C_SCROLL);
        }
    }

    // ── Boutons du bas ────────────────────────────────────────────────────

    // Zones calculées pour mouseClicked
    private int[] btnActionsX, btnActionsY, btnActionsW;
    private int btnLine2LeftX, btnLine2LeftY, btnLine2LeftW;
    private int btnRetourX, btnRetourY, btnRetourW;

    private void renderButtonsZone(GuiGraphics gfx, int px, int btnY, int mouseX, int mouseY) {
        int totalW = panelW() - PADDING * 2;
        int lx     = px + PADDING;
        String grade = VilleMembresDataManager.getMyGrade();

        // ── Ligne 1 : boutons d'action ────────────────────────────────────
        String[] labels;
        if (VilleMembresDataManager.isChef() || VilleMembresDataManager.isAdjoint()) {
            labels = new String[]{"BANQUE", "LOGS", "DEMANDES", "RECRUT."};
        } else {
            labels = new String[]{"DÉPOSER"};
        }

        int nb   = labels.length;
        int gap  = 6;
        int bw   = (totalW - gap * (nb - 1)) / nb;
        btnActionsX = new int[nb]; btnActionsY = new int[nb]; btnActionsW = new int[nb];

        for (int i = 0; i < nb; i++) {
            int bx = lx + i * (bw + gap);
            btnActionsX[i] = bx; btnActionsY[i] = btnY; btnActionsW[i] = bw;
            renderBtn(gfx, mouseX, mouseY, bx, btnY, bw, BTN_H, labels[i], true, 0, 0);
        }

        // ── Ligne 2 : Dissoudre/Quitter + Retour ─────────────────────────
        int btnY2 = btnY + BTN_H + 8;
        int bw2   = (totalW - 8) / 2;
        btnLine2LeftX = lx; btnLine2LeftY = btnY2; btnLine2LeftW = bw2;
        btnRetourX = lx + bw2 + 8; btnRetourY = btnY2; btnRetourW = bw2;

        String line2Label = VilleMembresDataManager.isChef() ? "SUPPRIMER VILLE" : "QUITTER";
        renderBtn(gfx, mouseX, mouseY, lx,           btnY2, bw2, BTN_H, line2Label, true, 0, 0);
        renderBtn(gfx, mouseX, mouseY, lx + bw2 + 8, btnY2, bw2, BTN_H, "RETOUR",   true, 0, 0);
    }

    // ── Overlay MEMBRE ────────────────────────────────────────────────────

    private void renderOverlayMembre(GuiGraphics gfx, int mouseX, int mouseY) {
        if (selectedMembre == null) return;
        int ow = Math.min(200, width - 40), oh = computeMembreOverlayH();
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        renderOverlayBg(gfx, ox, oy, ow, oh);

        int lineH = font.lineHeight + 4;
        int cx    = ox + ow / 2;
        int bw    = ow - 20, bx = ox + 10;
        int ty    = oy + 8;

        // 1. Pseudo
        gfx.drawCenteredString(font, "§l" + selectedMembre.pseudo(), cx, ty, 0xFFFFFFFF);
        ty += lineH;

        // 2. Grade dans la ville
        int gc = GuiUtils.gradeColor(selectedMembre.grade());
        gfx.drawCenteredString(font, selectedMembre.grade(), cx, ty, gc);
        ty += lineH + 2;

        // 3. VOIR PROFIL
        renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "VOIR PROFIL", true, 0, 0);
        ty += BTN_H + 6;

        // 4. Séparateur vert
        gfx.fill(ox + 8, ty, ox + ow - 8, ty + 1, C_BORDER);
        ty += 8;

        // 5. Boutons de grade + EXCLURE (si pas soi-même)
        String myUuid  = minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        boolean isSelf = myUuid.equals(selectedMembre.uuid());
        String cGrade  = selectedMembre.grade();
        boolean isChef = VilleMembresDataManager.isChef();
        boolean isAdj  = VilleMembresDataManager.isAdjoint();

        if (!isSelf) {
            if (isChef) {
                if ("Membre".equals(cGrade)) {
                    renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "→ ADJOINT", true, 0, 0);
                    ty += BTN_H + 4;
                } else if ("Adjoint".equals(cGrade)) {
                    renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "→ CHEF", true, 0, 0);
                    ty += BTN_H + 4;
                    renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "→ MEMBRE", true, C_BTN_GREY, C_BTN_GREY_H);
                    ty += BTN_H + 4;
                }
            }
            boolean canKick = (isChef && !"Chef".equals(cGrade)) || (isAdj && "Membre".equals(cGrade));
            if (canKick) {
                renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "EXCLURE", true, C_BTN_RED, C_BTN_RED_H);
                ty += BTN_H + 4;
            }
        }

        // 6. ANNULER
        renderBtn(gfx, mouseX, mouseY, bx, ty, bw, BTN_H, "ANNULER", true, C_BTN_GREY, C_BTN_GREY_H);
    }

    private int computeMembreOverlayH() {
        if (selectedMembre == null) return 120;
        int lineH = font.lineHeight + 4;
        // top(8) + pseudo(lineH) + grade(lineH+2) + VOIR PROFIL(BTN_H+6) + sep(8)
        int base  = 8 + lineH + (lineH + 2) + (BTN_H + 6) + 8;
        String myUuid  = minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        boolean isSelf = myUuid.equals(selectedMembre.uuid());
        String cGrade  = selectedMembre.grade();
        boolean isChef = VilleMembresDataManager.isChef();
        boolean isAdj  = VilleMembresDataManager.isAdjoint();
        int btns = 1; // ANNULER
        if (!isSelf) {
            boolean canKick = (isChef && !"Chef".equals(cGrade)) || (isAdj && "Membre".equals(cGrade));
            if (canKick) btns++;
            if (isChef) {
                if ("Membre".equals(cGrade))       btns++;
                else if ("Adjoint".equals(cGrade)) btns += 2;
            }
        }
        return base + btns * (BTN_H + 4) + 10;
    }

    // ── Overlay BANQUE ────────────────────────────────────────────────────

    private static final int BANQUE_OW      = 290;
    private int banqueOw() { return Math.min(BANQUE_OW, width - 40); }
    private static final int BANQUE_CARD_H  = 40;
    private static final int BANQUE_INPUT_H = 22;

    private int banqueOverlayH() {
        return PADDING
             + font.lineHeight + 4 + 1 + 8   // titre + sep + gap
             + BANQUE_CARD_H + 10             // cartes + gap
             + font.lineHeight + 3            // label saisie
             + BANQUE_INPUT_H + 8             // champ + gap
             + 1 + 8                          // sep + gap
             + BTN_H + 6 + BTN_H             // boutons action + gap + FERMER
             + PADDING;
    }

    /** Y absolu du début de la rangée de boutons DÉPOSER/RETIRER. */
    private int banqueBtnsY(int oy) {
        return oy + PADDING
             + font.lineHeight + 4 + 1 + 8
             + BANQUE_CARD_H + 10
             + font.lineHeight + 3
             + BANQUE_INPUT_H + 8
             + 1 + 8;
    }

    private void renderOverlayBanque(GuiGraphics gfx, int mouseX, int mouseY) {
        int ow = banqueOw(), oh = banqueOverlayH();
        int ox = (width - ow) / 2, oy = (height - oh) / 2;

        // Fond obscurcissant + fond principal
        int px = panelX(), py = panelY();
        gfx.fill(px + 1, py + HEADER_H, px + panelW() - 1, py + panelH() - FOOTER_H, 0xCC000000);
        GuiUtils.fillChamferedRect(gfx, ox, oy, ow, oh, 8, 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, ox, oy, ow, oh, 8, 1, C_BORDER);
        drawVilleCornerBrackets(gfx, ox, oy, ow, oh, false);

        // ── Titre ────────────────────────────────────────────────────────
        int ty = oy + PADDING;
        gfx.drawCenteredString(font,
                Component.literal("BANQUE DE LA VILLE").withStyle(ChatFormatting.BOLD),
                ox + ow / 2, ty, 0xFF3DD96A);
        ty += font.lineHeight + 4;
        gfx.fill(ox + 16, ty, ox + ow - 16, ty + 1, 0xFF1E2D3D);
        ty += 8;

        // ── Cartes de solde côte à côte ───────────────────────────────────
        int cw = (ow - PADDING * 2 - 8) / 2;
        int cx1 = ox + PADDING, cx2 = ox + PADDING + cw + 8;

        // Carte banque ville
        GuiUtils.fillChamferedRect(gfx, cx1, ty, cw, BANQUE_CARD_H, 4, 0xFF0A1018);
        GuiUtils.drawChamferedBorder(gfx, cx1, ty, cw, BANQUE_CARD_H, 4, 1, 0xFF1C2C3C);
        gfx.drawCenteredString(font, "Banque ville", cx1 + cw / 2, ty + 5, 0xFF607080);
        gfx.drawCenteredString(font,
                Component.literal(GuiUtils.formatBanque(currentBanque) + " " + Money.SYMBOL).withStyle(ChatFormatting.BOLD),
                cx1 + cw / 2, ty + 5 + font.lineHeight + 5, 0xFF55FF55);

        // Carte solde joueur
        double perso = VilleMembresDataManager.getPlayerMoney();
        GuiUtils.fillChamferedRect(gfx, cx2, ty, cw, BANQUE_CARD_H, 4, 0xFF0A1018);
        GuiUtils.drawChamferedBorder(gfx, cx2, ty, cw, BANQUE_CARD_H, 4, 1, 0xFF1C2C3C);
        gfx.drawCenteredString(font, "Mon solde", cx2 + cw / 2, ty + 5, 0xFF607080);
        String persoVal = perso < 0 ? "..." : GuiUtils.formatBanque(perso) + " " + Money.SYMBOL;
        int persoColor  = perso < 0 ? 0xFF888888 : (perso == 0 ? 0xFFFF5555 : 0xFFFFDD44);
        gfx.drawCenteredString(font,
                Component.literal(persoVal).withStyle(ChatFormatting.BOLD),
                cx2 + cw / 2, ty + 5 + font.lineHeight + 5, persoColor);
        ty += BANQUE_CARD_H + 10;

        // ── Label + champ de saisie ───────────────────────────────────────
        gfx.drawString(font, "Montant :", ox + PADDING, ty, 0xFF607080, false);
        ty += font.lineHeight + 3;

        boolean hasInput  = banqueInput.length() > 0;
        int fieldBg       = hasInput ? 0xFF0F1A22 : 0xFF090F18;
        int fieldBorder   = hasInput ? 0xFF2EA84E : 0xFF1C2C3C;
        int fx = ox + PADDING, fw = ow - PADDING * 2;
        GuiUtils.fillChamferedRect(gfx, fx, ty, fw, BANQUE_INPUT_H, 4, fieldBg);
        GuiUtils.drawChamferedBorder(gfx, fx, ty, fw, BANQUE_INPUT_H, 4, 1, fieldBorder);
        String display     = hasInput ? banqueInput + "▌" : "0.00";
        int    displayCol  = hasInput ? 0xFFEEF2F5 : 0xFF445566;
        gfx.drawCenteredString(font, display, fx + fw / 2,
                ty + (BANQUE_INPUT_H - font.lineHeight) / 2 + 1, displayCol);
        ty += BANQUE_INPUT_H + 8;

        // ── Séparateur ────────────────────────────────────────────────────
        gfx.fill(ox + 12, ty, ox + ow - 12, ty + 1, 0x22FFFFFF);
        ty += 8;

        // ── Boutons DÉPOSER / RETIRER ─────────────────────────────────────
        boolean canRetirer = VilleMembresDataManager.isChef() || VilleMembresDataManager.isAdjoint();
        int bw2 = canRetirer ? (ow - PADDING * 2 - 8) / 2 : ow - PADDING * 2;

        renderBtn(gfx, mouseX, mouseY, ox + PADDING, ty, bw2, BTN_H, "DÉPOSER", true, 0, 0);
        if (canRetirer)
            renderBtn(gfx, mouseX, mouseY, ox + PADDING + bw2 + 8, ty, bw2, BTN_H, "RETIRER", true, 0, 0);
        ty += BTN_H + 6;

        renderBtn(gfx, mouseX, mouseY, ox + PADDING, ty, ow - PADDING * 2, BTN_H, "FERMER", true, 0, 0);
    }

    // ── Overlay LOGS ─────────────────────────────────────────────────────

    private void renderOverlayLogs(GuiGraphics gfx, int mouseX, int mouseY) {
        int ow = Math.min(280, width - 40), oh = Math.min(200, height - 40);
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        renderOverlayBg(gfx, ox, oy, ow, oh);
        gfx.drawCenteredString(font, "§lHISTORIQUE BANCAIRE", ox + ow / 2, oy + 6, 0xFFFFFFFF);
        gfx.fill(ox + 6, oy + 18, ox + ow - 6, oy + 19, C_BORDER);

        List<VilleLogsBanquePayload.LogEntry> logs = VilleLogsBanqueDataManager.getLogs();
        int listY = oy + 22, lh = ROW_H - 4;
        int visible = (oh - 50) / lh;
        int maxS = Math.max(0, logs.size() - visible);
        logsScroll = Math.max(0, Math.min(logsScroll, maxS));

        if (logs.isEmpty()) {
            gfx.drawCenteredString(font, "Aucune transaction", ox + ow / 2,
                    listY + (oh - 60) / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = logsScroll; i < logs.size() && i < logsScroll + visible; i++) {
                VilleLogsBanquePayload.LogEntry l = logs.get(i);
                int rowY = listY + (i - logsScroll) * lh;
                String line;
                if (l.action().startsWith("rec_")) {
                    String mode = switch (l.action()) {
                        case "rec_ouvert"      -> "§aOuvert";
                        case "rec_sur_demande" -> "§eSur demande";
                        case "rec_ferme"       -> "§cFermé";
                        default                -> "§7?";
                    };
                    line = "§7" + l.timestamp() + " §f" + l.pseudo() + " §7recrut. → " + mode;
                } else if ("acc_dem".equals(l.action())) {
                    line = "§7" + l.timestamp() + " §a+ §f" + l.pseudo() + " §7rejoint la ville";
                } else if (l.action().startsWith("grade_")) {
                    String grade = switch (l.action()) {
                        case "grade_chef"    -> "§6Chef";
                        case "grade_adjoint" -> "§9Adjoint";
                        case "grade_membre"  -> "§7Membre";
                        default              -> "§8?";
                    };
                    line = "§7" + l.timestamp() + " §f" + l.pseudo() + " §7→ " + grade;
                } else {
                    boolean depot = "depot".equals(l.action());
                    String sign = depot ? "§a+" : "§c-";
                    line = "§7" + l.timestamp() + " §f" + l.pseudo() + " " + sign
                            + String.format("%.0f", l.montant()) + " §7" + Money.SYMBOL;
                }
                gfx.drawString(font, line, ox + 8, rowY, 0xFFFFFFFF, false);
            }
        }

        renderBtn(gfx, mouseX, mouseY, ox + 10, oy + oh - BTN_H - 8, ow - 20, BTN_H, "FERMER",
                true, C_BTN_GREY, C_BTN_GREY_H);
    }

    // ── Overlay DEMANDES ──────────────────────────────────────────────────

    private void renderOverlayDemandes(GuiGraphics gfx, int mouseX, int mouseY) {
        int ow = Math.min(260, width - 40), oh = Math.min(200, height - 40);
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        renderOverlayBg(gfx, ox, oy, ow, oh);
        gfx.drawCenteredString(font, "§lDEMANDES D'ADHÉSION", ox + ow / 2, oy + 6, 0xFFFFFFFF);
        gfx.fill(ox + 6, oy + 18, ox + ow - 6, oy + 19, C_BORDER);

        List<VilleDemandesPayload.DemandeEntry> demandes = VilleDemandesDataManager.getDemandes();
        int listY = oy + 22;
        int rowH2 = ROW_H + 4;
        int visible = (oh - 52) / rowH2;
        int maxS = Math.max(0, demandes.size() - visible);
        demandesScroll = Math.max(0, Math.min(demandesScroll, maxS));

        if (demandes.isEmpty()) {
            gfx.drawCenteredString(font, "Aucune demande en attente", ox + ow / 2,
                    listY + (oh - 60) / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = demandesScroll; i < demandes.size() && i < demandesScroll + visible; i++) {
                VilleDemandesPayload.DemandeEntry d = demandes.get(i);
                int rowY = listY + (i - demandesScroll) * rowH2;

                gfx.drawString(font, d.pseudo() + " §7(" + d.timestamp() + ")",
                        ox + 8, rowY + (rowH2 - font.lineHeight) / 2, 0xFFFFFFFF, false);

                int smBw = 14, smBh = 14;
                int bAccX = ox + ow - 8 - smBw * 2 - 3, bRefX = ox + ow - 8 - smBw;
                int bY = rowY + (rowH2 - smBh) / 2;
                boolean hAcc = mouseX >= bAccX && mouseX < bAccX + smBw && mouseY >= bY && mouseY < bY + smBh;
                boolean hRef = mouseX >= bRefX && mouseX < bRefX + smBw && mouseY >= bY && mouseY < bY + smBh;
                GuiUtils.fillChamferedRect(gfx, bAccX, bY, smBw, smBh, 2, hAcc ? C_BTN_OK_H : C_BTN_OK);
                gfx.drawCenteredString(font, "✓", bAccX + smBw / 2, bY + (smBh - font.lineHeight) / 2, 0xFFFFFFFF);
                GuiUtils.fillChamferedRect(gfx, bRefX, bY, smBw, smBh, 2, hRef ? C_BTN_RED_H : C_BTN_RED);
                gfx.drawCenteredString(font, "✗", bRefX + smBw / 2, bY + (smBh - font.lineHeight) / 2, 0xFFFFFFFF);

                if (i < demandes.size() - 1)
                    gfx.fill(ox + 6, rowY + rowH2 - 1, ox + ow - 6, rowY + rowH2, 0x22FFFFFF);
            }
        }

        renderBtn(gfx, mouseX, mouseY, ox + 10, oy + oh - BTN_H - 8, ow - 20, BTN_H, "FERMER",
                true, C_BTN_GREY, C_BTN_GREY_H);
    }

    // ── Overlay RECRUTEMENT ───────────────────────────────────────────────

    private static final int REC_OW       = 460;
    private static final int REC_CARD_GAP = 8;

    private int recOw()     { return Math.min(REC_OW, panelW() - PADDING * 2); }
    private int recOx()     { return panelX() + (panelW() - recOw()) / 2; }
    private int recCardH()  { return 8 + 7 + 3 + font.lineHeight + 4 + 1 + 5 + font.lineHeight * 2 + 4 + 8; }
    private int recOverlayH() {
        return PADDING + (font.lineHeight + 15) + recCardH() + 10 + 1 + 8 + BTN_H + PADDING;
    }
    private int recOy() {
        int contentTop = panelY() + HEADER_H + PADDING;
        int contentH   = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;
        return contentTop + Math.max(0, (contentH - recOverlayH()) / 2);
    }

    private void renderOverlayRecrutement(GuiGraphics gfx, int mouseX, int mouseY) {
        // Fond obscurcissant sur tout le panel
        int px = panelX(), py = panelY();
        gfx.fill(px + 1, py + HEADER_H, px + panelW() - 1, py + panelH() - FOOTER_H, 0xCC000000);

        int ow = recOw(), oh = recOverlayH();
        int ox = recOx(), oy = recOy();

        // Fond principal
        GuiUtils.fillChamferedRect(gfx, ox, oy, ow, oh, 8, 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, ox, oy, ow, oh, 8, 1, C_BORDER);
        drawVilleCornerBrackets(gfx, ox, oy, ow, oh, false);

        // ── Titre ────────────────────────────────────────────────────────
        int ty = oy + PADDING;
        gfx.drawCenteredString(font,
                Component.literal("MODE DE RECRUTEMENT").withStyle(ChatFormatting.BOLD),
                ox + ow / 2, ty, 0xFF3DD96A);
        ty += font.lineHeight + 4;
        gfx.fill(ox + 16, ty, ox + ow - 16, ty + 1, 0xFF1E2D3D);
        ty += 10;

        // ── 3 cartes côte à côte ─────────────────────────────────────────
        String cur   = ville.recrutement() == null ? "sur_demande" : ville.recrutement();
        int cardW    = (ow - PADDING * 2 - REC_CARD_GAP * 2) / 3;
        int cardH    = recCardH();
        int cardBase = ox + PADDING;

        renderRecCard(gfx, mouseX, mouseY,
                cardBase,                            ty, cardW, cardH,
                "OUVERT", "Rejoindre directement", "sans validation",
                0xFF55FF55, 0xFF224422, "ouvert", cur);

        renderRecCard(gfx, mouseX, mouseY,
                cardBase + cardW + REC_CARD_GAP,     ty, cardW, cardH,
                "SUR DEMANDE", "Le joueur postule", "Chef / Adjoint valide",
                0xFFFFDD44, 0xFF444422, "sur_demande", cur);

        renderRecCard(gfx, mouseX, mouseY,
                cardBase + 2 * (cardW + REC_CARD_GAP), ty, cardW, cardH,
                "FERMÉ", "Impossible de rejoindre", "ou de postuler",
                0xFFFF5555, 0xFF442222, "ferme", cur);

        ty += cardH + 10;
        gfx.fill(ox + 12, ty, ox + ow - 12, ty + 1, 0x22FFFFFF);
        ty += 8;

        // ── Bouton FERMER ────────────────────────────────────────────────
        renderBtn(gfx, mouseX, mouseY, ox + PADDING, ty, ow - PADDING * 2, BTN_H,
                "FERMER", true, 0, 0);
    }

    private void renderRecCard(GuiGraphics gfx, int mouseX, int mouseY,
                                int bx, int by, int bw, int bh,
                                String title, String desc1, String desc2,
                                int dotSelColor, int dotNormColor,
                                String mode, String current) {
        boolean sel = mode.equals(current);
        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

        // Fond de la carte
        int bg = sel ? 0xC0141C26 : (hov ? 0xC01A2840 : 0x800A1018);
        GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 5, bg);

        // Bordure / effet
        if (sel) {
            GuiUtils.drawChamferedBorder(gfx, bx - 3, by - 3, bw + 6, bh + 6, 8, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, 7, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, 6, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, bh, 4);
            drawVilleCornerBrackets(gfx, bx, by, bw, bh, true);
        } else if (hov) {
            GuiUtils.drawChamferedBorder(gfx, bx - 3, by - 3, bw + 6, bh + 6, 8, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, 7, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, 6, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, bh, 4);
            drawVilleCornerBrackets(gfx, bx, by, bw, bh, true);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 5, 1, 0xFF1C2C3C);
            drawVilleCornerBrackets(gfx, bx, by, bw, bh, false);
        }

        int cx = bx + bw / 2;
        int ty = by + 8;

        // ── Point indicateur coloré ───────────────────────────────────────
        int dot = (sel || hov) ? dotSelColor : dotNormColor;
        if (sel || hov) {
            int halo = (dot & 0x00FFFFFF) | 0x22000000;
            gfx.fill(cx - 5, ty - 1, cx + 5, ty + 8, halo);
        }
        gfx.fill(cx - 3, ty, cx + 3, ty + 6, dot);
        ty += 7 + 3;

        // ── Titre ─────────────────────────────────────────────────────────
        int titleColor = sel ? 0xFF3DD96A : (hov ? 0xFF3DD96A : 0xFF889988);
        gfx.drawCenteredString(font,
                Component.literal(title).withStyle(ChatFormatting.BOLD), cx, ty, titleColor);
        ty += font.lineHeight + 4;

        // ── Séparateur ────────────────────────────────────────────────────
        gfx.fill(bx + 6, ty, bx + bw - 6, ty + 1, (sel || hov) ? 0x553DDE6A : 0x221E2D3D);
        ty += 5;

        // ── Description (2 lignes) ────────────────────────────────────────
        int descColor = sel ? 0xFFAABBAA : (hov ? 0xFFAABBAA : 0xFF607060);
        gfx.drawCenteredString(font, desc1, cx, ty, descColor);
        ty += font.lineHeight + 2;
        gfx.drawCenteredString(font, desc2, cx, ty, descColor);
    }

    // ── Overlay CONFIRMER ─────────────────────────────────────────────────

    private void renderOverlayConfirmer(GuiGraphics gfx, int mouseX, int mouseY) {
        int ow = Math.min(240, width - 40), oh = 80;
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        renderOverlayBg(gfx, ox, oy, ow, oh);
        gfx.drawCenteredString(font, confirmMsg, ox + ow / 2, oy + 16, 0xFFFFFFFF);

        int bw = 80, bh = 16, by = oy + oh - bh - 10;
        int bx1 = ox + (ow / 2) - bw - 5, bx2 = ox + ow / 2 + 5;
        renderBtn(gfx, mouseX, mouseY, bx1, by, bw, bh, "CONFIRMER", true, C_BTN_RED, C_BTN_RED_H);
        renderBtn(gfx, mouseX, mouseY, bx2, by, bw, bh, "ANNULER",   true, C_BTN_GREY, C_BTN_GREY_H);
    }

    // ── Fond des overlays ─────────────────────────────────────────────────

    private void renderOverlayBg(GuiGraphics gfx, int ox, int oy, int ow, int oh) {
        int px = panelX(), py = panelY();
        gfx.fill(px + 1, py + HEADER_H, px + panelW() - 1, py + panelH() - FOOTER_H, 0xCC000000);
        GuiUtils.fillChamferedRect(gfx, ox, oy, ow, oh, 8, C_OVL_BG);
        GuiUtils.drawChamferedBorder(gfx, ox, oy, ow, oh, 8, 2, C_BORDER);
    }

    // ── Bouton helper — style neon identique à MenuButton ─────────────────

    private void renderBtn(GuiGraphics gfx, int mouseX, int mouseY,
                            int bx, int by, int bw, int bh, String label,
                            boolean enabled, int cn, int ch) {
        if (!enabled) {
            GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 4, 0x601A1A1A);
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, 0xFF1C2C3C);
            gfx.drawCenteredString(font, label, bx + bw / 2,
                    by + (bh - font.lineHeight) / 2 + 1, 0xFF555566);
            return;
        }

        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

        GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 4, hov ? 0xC01A2840 : 0xA0141C26);

        if (hov) {
            GuiUtils.drawChamferedBorder(gfx, bx - 3, by - 3, bw + 6, bh + 6, 7, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, 6, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, 5, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, bh, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, 0xFF1C2C3C);
        }

        drawVilleCornerBrackets(gfx, bx, by, bw, bh, hov);

        Component title = Component.literal(label).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, title, bx + bw / 2,
                by + (bh - font.lineHeight) / 2 + 1, hov ? 0xFF3DD96A : 0xFFEEF2F5);
    }

    private void drawVilleCornerBrackets(GuiGraphics gfx, int x, int y, int w, int h, boolean hov) {
        int arm   = 4, thick = 1, off = 5;
        int cn    = 0x553DDE6A, ch = 0xCC3DDE6A;
        int lx = x + off, rx = x + w - off;
        int ty = y + off, by = y + h - off;
        gfx.fill(lx,         ty,         lx + arm,   ty + thick, cn);
        gfx.fill(lx,         ty,         lx + thick, ty + arm,   cn);
        gfx.fill(rx - arm,   ty,         rx,         ty + thick, cn);
        gfx.fill(rx - thick, ty,         rx,         ty + arm,   cn);
        if (hov) {
            gfx.fill(lx,         ty,         lx + arm,   ty + thick, ch);
            gfx.fill(lx,         ty,         lx + thick, ty + arm,   ch);
            gfx.fill(rx - arm,   ty,         rx,         ty + thick, ch);
            gfx.fill(rx - thick, ty,         rx,         ty + arm,   ch);
            gfx.fill(lx,         by - thick, lx + arm,   by,         ch);
            gfx.fill(lx,         by - arm,   lx + thick, by,         ch);
            gfx.fill(rx - arm,   by - thick, rx,         by,         ch);
            gfx.fill(rx - thick, by - arm,   rx,         by,         ch);
        }
    }

    // ── Clics ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        switch (vue) {
            case MEMBRE      -> { handleClickMembre(mx, my);      return true; }
            case BANQUE      -> { handleClickBanque(mx, my);      return true; }
            case LOGS        -> { handleClickLogs(mx, my);        return true; }
            case DEMANDES    -> { handleClickDemandes(mx, my);    return true; }
            case RECRUTEMENT -> { handleClickRecrutement(mx, my); return true; }
            case CONFIRMER   -> { handleClickConfirmer(mx, my);   return true; }
            default          -> {}
        }

        // Clics sur les boutons ligne 1
        if (btnActionsX != null) {
            String[] lbl;
            if (VilleMembresDataManager.isChef() || VilleMembresDataManager.isAdjoint())
                lbl = new String[]{"BANQUE","LOGS","DEMANDES","RECRUT."};
            else
                lbl = new String[]{"DÉPOSER"};

            for (int i = 0; i < lbl.length && i < btnActionsX.length; i++) {
                if (mx >= btnActionsX[i] && mx < btnActionsX[i] + btnActionsW[i]
                        && my >= btnActionsY[i] && my < btnActionsY[i] + BTN_H) {
                    handleActionBtn(lbl[i]);
                    return true;
                }
            }
        }

        // Bouton ligne 2 gauche
        if (mx >= btnLine2LeftX && mx < btnLine2LeftX + btnLine2LeftW
                && my >= btnLine2LeftY && my < btnLine2LeftY + BTN_H) {
            if (VilleMembresDataManager.isChef()) {
                confirmMsg    = "§cDissoudre §e" + ville.nom() + "§c ?";
                pendingAction = "dissoudre";
                confirmAction = this::doDissoudre;
                vue = Vue.CONFIRMER;
            } else {
                confirmMsg    = "§eQuitter §f" + ville.nom() + "§e ?";
                pendingAction = "quitter";
                confirmAction = this::doQuitter;
                vue = Vue.CONFIRMER;
            }
            return true;
        }

        // Bouton Retour
        if (mx >= btnRetourX && mx < btnRetourX + btnRetourW
                && my >= btnRetourY && my < btnRetourY + BTN_H) {
            onClose();
            return true;
        }

        // Clic dans la liste membres → overlay MEMBRE
        int px       = panelX(), py = panelY();
        int rx       = px + PADDING + leftW() + SEP;
        int rw       = panelW() - PADDING - leftW() - SEP - PADDING;
        int listW    = rw - SCROLL_W - 2;
        int contentY = py + HEADER_H + PADDING;
        int btnZoneH = BTN_H * 2 + 14;
        int splitH   = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - btnZoneH;
        int listY    = contentY + 16;
        int listH    = splitH - 16;
        int visible  = listH / ROW_H;

        if (mx >= rx && mx < rx + listW && my >= listY && my < listY + listH) {
            int idx = (my - listY) / ROW_H + membresScroll;
            List<VilleMembresPayload.MembreEntry> membres = VilleMembresDataManager.getMembres();
            if (idx >= 0 && idx < membres.size()) {
                selectedMembre = membres.get(idx);
                vue = Vue.MEMBRE;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Handlers overlay ─────────────────────────────────────────────────

    private void handleClickMembre(int mx, int my) {
        if (selectedMembre == null) { vue = Vue.NORMAL; return; }
        int ow = Math.min(200, width - 40), oh = computeMembreOverlayH();
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        int bw = ow - 20, bx = ox + 10;

        String myUuid  = minecraft.player != null ? minecraft.player.getUUID().toString() : "";
        boolean isSelf = myUuid.equals(selectedMembre.uuid());
        String cGrade  = selectedMembre.grade();
        boolean isChef = VilleMembresDataManager.isChef();
        boolean isAdj  = VilleMembresDataManager.isAdjoint();

        int lineH = font.lineHeight + 4;
        // Position du bouton VOIR PROFIL (après pseudo + grade)
        int tyVoirProfil = oy + 8 + lineH + (lineH + 2);
        // Position après le séparateur
        int ty = tyVoirProfil + (BTN_H + 6) + 8;

        // 3. VOIR PROFIL
        if (isInBtn(mx, my, bx, tyVoirProfil, bw, BTN_H)) {
            String targetUuid = selectedMembre.uuid();
            vue = Vue.NORMAL;
            selectedMembre = null;
            minecraft.setScreen(new ProfilJoueurScreen(this, targetUuid));
            return;
        }

        // 5. Grade buttons + EXCLURE
        if (!isSelf) {
            if (isChef) {
                if ("Membre".equals(cGrade)) {
                    if (isInBtn(mx, my, bx, ty, bw, BTN_H)) {
                        sendAction("grade", selectedMembre.uuid(), 0, "Adjoint");
                        vue = Vue.NORMAL;
                        return;
                    }
                    ty += BTN_H + 4;
                } else if ("Adjoint".equals(cGrade)) {
                    if (isInBtn(mx, my, bx, ty, bw, BTN_H)) {
                        final String uuid   = selectedMembre.uuid();
                        final String pseudo = selectedMembre.pseudo();
                        confirmMsg    = "§ePromouvoir §f" + pseudo + "§e Chef ?";
                        pendingAction = "grade_chef";
                        confirmAction = () -> sendAction("grade", uuid, 0, "Chef");
                        vue = Vue.CONFIRMER;
                        return;
                    }
                    ty += BTN_H + 4;
                    if (isInBtn(mx, my, bx, ty, bw, BTN_H)) {
                        sendAction("grade", selectedMembre.uuid(), 0, "Membre");
                        vue = Vue.NORMAL;
                        return;
                    }
                    ty += BTN_H + 4;
                }
            }
            boolean canKick = (isChef && !"Chef".equals(cGrade)) || (isAdj && "Membre".equals(cGrade));
            if (canKick) {
                if (isInBtn(mx, my, bx, ty, bw, BTN_H)) {
                    final String uuid   = selectedMembre.uuid();
                    final String pseudo = selectedMembre.pseudo();
                    confirmMsg    = "§cExclure §e" + pseudo + "§c ?";
                    pendingAction = "kick";
                    confirmAction = () -> sendAction("kick", uuid, 0, "");
                    vue = Vue.CONFIRMER;
                    return;
                }
                ty += BTN_H + 4;
            }
        }

        // 6. ANNULER
        if (isInBtn(mx, my, bx, ty, bw, BTN_H)) { vue = Vue.NORMAL; selectedMembre = null; return; }

        // Clic hors overlay → fermer
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) { vue = Vue.NORMAL; selectedMembre = null; }
    }

    private void handleClickBanque(int mx, int my) {
        int ow = banqueOw(), oh = banqueOverlayH();
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        boolean canRetirer = VilleMembresDataManager.isChef() || VilleMembresDataManager.isAdjoint();
        int bw2 = canRetirer ? (ow - PADDING * 2 - 8) / 2 : ow - PADDING * 2;
        int ty  = banqueBtnsY(oy);

        if (isInBtn(mx, my, ox + PADDING, ty, bw2, BTN_H)) { doDeposer(); return; }
        if (canRetirer && isInBtn(mx, my, ox + PADDING + bw2 + 8, ty, bw2, BTN_H)) { doRetirer(); return; }
        if (isInBtn(mx, my, ox + PADDING, ty + BTN_H + 6, ow - PADDING * 2, BTN_H)) { vue = Vue.NORMAL; return; }
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) vue = Vue.NORMAL;
    }

    private void handleClickLogs(int mx, int my) {
        int ow = Math.min(280, width - 40), oh = Math.min(200, height - 40);
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        if (isInBtn(mx, my, ox + 10, oy + oh - BTN_H - 8, ow - 20, BTN_H)) { vue = Vue.NORMAL; return; }
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) vue = Vue.NORMAL;
    }

    private void handleClickDemandes(int mx, int my) {
        int ow = Math.min(260, width - 40), oh = Math.min(200, height - 40);
        int ox = (width - ow) / 2, oy = (height - oh) / 2;

        if (isInBtn(mx, my, ox + 10, oy + oh - BTN_H - 8, ow - 20, BTN_H)) { vue = Vue.NORMAL; return; }

        List<VilleDemandesPayload.DemandeEntry> demandes = VilleDemandesDataManager.getDemandes();
        int listY = oy + 22, rowH2 = ROW_H + 4;
        int visible = (oh - 52) / rowH2;
        int maxS = Math.max(0, demandes.size() - visible);
        demandesScroll = Math.max(0, Math.min(demandesScroll, maxS));

        for (int i = demandesScroll; i < demandes.size() && i < demandesScroll + visible; i++) {
            VilleDemandesPayload.DemandeEntry d = demandes.get(i);
            int rowY = listY + (i - demandesScroll) * rowH2;
            int smBw = 14, smBh = 14;
            int bAccX = ox + ow - 8 - smBw * 2 - 3, bRefX = ox + ow - 8 - smBw;
            int bY = rowY + (rowH2 - smBh) / 2;
            if (isInBtn(mx, my, bAccX, bY, smBw, smBh)) {
                sendAction("acc_dem", d.uuid(), 0, "");
                VilleDemandesDataManager.clear();
                if (minecraft.getConnection() != null)
                    PacketDistributor.sendToServer(new VilleDemandesRequestPayload(ville.id()));
                return;
            }
            if (isInBtn(mx, my, bRefX, bY, smBw, smBh)) {
                sendAction("ref_dem", d.uuid(), 0, "");
                VilleDemandesDataManager.clear();
                if (minecraft.getConnection() != null)
                    PacketDistributor.sendToServer(new VilleDemandesRequestPayload(ville.id()));
                return;
            }
        }
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) vue = Vue.NORMAL;
    }

    private void handleClickRecrutement(int mx, int my) {
        int ow = recOw(), oh = recOverlayH();
        int ox = recOx(), oy = recOy();

        // Recalcul positions (identique à renderOverlayRecrutement)
        int cardsY   = oy + PADDING + font.lineHeight + 4 + 1 + 10;
        int cardW    = (ow - PADDING * 2 - REC_CARD_GAP * 2) / 3;
        int cardH    = recCardH();
        int cardBase = ox + PADDING;

        if (isInBtn(mx, my, cardBase,                              cardsY, cardW, cardH)) {
            sendAction("set_rec", "", 0, "ouvert");
            ville = refreshVilleWithRecrutement("ouvert");
            vue = Vue.NORMAL; return;
        }
        if (isInBtn(mx, my, cardBase + cardW + REC_CARD_GAP,      cardsY, cardW, cardH)) {
            sendAction("set_rec", "", 0, "sur_demande");
            ville = refreshVilleWithRecrutement("sur_demande");
            vue = Vue.NORMAL; return;
        }
        if (isInBtn(mx, my, cardBase + 2 * (cardW + REC_CARD_GAP), cardsY, cardW, cardH)) {
            sendAction("set_rec", "", 0, "ferme");
            ville = refreshVilleWithRecrutement("ferme");
            vue = Vue.NORMAL; return;
        }

        // Bouton FERMER
        int closY = cardsY + cardH + 10 + 1 + 8;
        if (isInBtn(mx, my, ox + PADDING, closY, ow - PADDING * 2, BTN_H)) {
            vue = Vue.NORMAL; return;
        }

        // Clic hors overlay
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) vue = Vue.NORMAL;
    }

    /** Met à jour localement le recrutement dans l'objet ville (avant la réponse serveur). */
    private VilleListPayload.VilleEntry refreshVilleWithRecrutement(String mode) {
        return new VilleListPayload.VilleEntry(
                ville.id(), ville.nom(), ville.ownerPseudo(), ville.mondeSpawn(),
                ville.banque(), ville.nbMembres(), ville.nbConnectes(),
                mode, ville.dateFondation(), ville.totalChunks());
    }

    private void handleClickConfirmer(int mx, int my) {
        int ow = Math.min(240, width - 40), oh = 80;
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        int bw = 80, bh = 16, by = oy + oh - bh - 10;
        int bx1 = ox + (ow / 2) - bw - 5, bx2 = ox + ow / 2 + 5;
        if (isInBtn(mx, my, bx1, by, bw, bh)) {
            if (confirmAction != null) confirmAction.run();
            vue = Vue.NORMAL;
            pendingAction = "";
            return;
        }
        if (isInBtn(mx, my, bx2, by, bw, bh)) {
            vue = Vue.NORMAL;
            pendingAction = "";
            return;
        }
        if (mx < ox || mx >= ox + ow || my < oy || my >= oy + oh) {
            vue = Vue.NORMAL;
            pendingAction = "";
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX, my = (int) mouseY;
        switch (vue) {
            case LOGS -> { logsScroll = Math.max(0, logsScroll - (int) Math.signum(scrollY)); return true; }
            case DEMANDES -> { demandesScroll = Math.max(0, demandesScroll - (int) Math.signum(scrollY)); return true; }
            default -> {
                // Scroll liste membres
                int px       = panelX(), py = panelY();
                int rx       = px + PADDING + leftW() + SEP;
                int rw       = panelW() - PADDING - leftW() - SEP - PADDING;
                int contentY = py + HEADER_H + PADDING;
                int btnZoneH = BTN_H * 2 + 14;
                int splitH   = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - btnZoneH;
                if (mx >= rx && mx < rx + rw && my >= contentY && my < contentY + splitH) {
                    List<VilleMembresPayload.MembreEntry> m = VilleMembresDataManager.getMembres();
                    int listH = splitH - 16;
                    int vis   = listH / ROW_H;
                    membresScroll = Math.max(0, Math.min(membresScroll - (int) Math.signum(scrollY),
                            Math.max(0, m.size() - vis)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Touche clavier ────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (vue == Vue.BANQUE) {
            if (keyCode == 259) { // Backspace
                if (banqueInput.length() > 0)
                    banqueInput.deleteCharAt(banqueInput.length() - 1);
                return true;
            }
            if (keyCode == 256) { vue = Vue.NORMAL; return true; } // ESC
        }
        if (vue != Vue.NORMAL && vue != Vue.BANQUE && keyCode == 256) {
            vue = Vue.NORMAL; selectedMembre = null; return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (vue == Vue.BANQUE) {
            if ((Character.isDigit(c) || c == '.') && banqueInput.length() < 12) {
                if (c == '.' && banqueInput.indexOf(".") != -1) return true;
                banqueInput.append(c);
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void handleActionBtn(String label) {
        switch (label) {
            case "BANQUE", "DÉPOSER" -> {
                banqueInput = new StringBuilder();
                vue = Vue.BANQUE;
            }
            case "LOGS" -> {
                VilleLogsBanqueDataManager.clear();
                if (minecraft.getConnection() != null)
                    PacketDistributor.sendToServer(new VilleLogsRequestPayload(ville.id()));
                vue = Vue.LOGS;
            }
            case "DEMANDES" -> {
                VilleDemandesDataManager.clear();
                if (minecraft.getConnection() != null)
                    PacketDistributor.sendToServer(new VilleDemandesRequestPayload(ville.id()));
                vue = Vue.DEMANDES;
            }
            case "RECRUT." -> vue = Vue.RECRUTEMENT;
        }
    }

    private void doDeposer() {
        double montant = parseMontant();
        if (montant <= 0) return;
        sendAction("deposer", "", montant, "");
        banqueInput = new StringBuilder();
    }

    private void doRetirer() {
        double montant = parseMontant();
        if (montant <= 0) return;
        sendAction("retirer", "", montant, "");
        banqueInput = new StringBuilder();
    }

    private void doQuitter() {
        sendAction("quitter", "", 0, "");
    }

    private void doDissoudre() {
        sendAction("dissoudre", "", 0, "");
    }

    private void sendAction(String action, String targetUuid, double montant, String extra) {
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new VilleActionPayload(
                    action, ville.id(), targetUuid, montant, extra));
        }
    }

    private double parseMontant() {
        try { return Double.parseDouble(banqueInput.toString()); } catch (NumberFormatException e) { return 0; }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    private static boolean isInBtn(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private static String gradeLabel(String g) {
        return switch (g) {
            case "Chef"    -> "§6Chef";
            case "Adjoint" -> "§9Adjoint";
            case "Membre"  -> "§7Membre";
            default        -> "§8" + g;
        };
    }

    private static String recrutementLabel(String r) {
        if (r == null) r = "sur_demande";
        return switch (r) {
            case "ouvert"      -> "§aOuvert";
            case "sur_demande" -> "§eSur demande";
            case "ferme"       -> "§cFermé";
            default            -> "§8" + r;
        };
    }
}
