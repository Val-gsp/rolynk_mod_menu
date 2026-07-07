package com.example.rolynkmodmenu.client.screen.ville;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.profile.ProfilJoueurScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.client.ville.VilleListDataManager;
import com.example.rolynkmodmenu.client.ville.VilleMembresDataManager;
import com.example.rolynkmodmenu.client.ville.VilleProfileDataManager;
import com.example.rolynkmodmenu.network.VilleListPayload;
import com.example.rolynkmodmenu.network.VilleMembresPayload;
import com.example.rolynkmodmenu.network.VilleMembresRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Fiche de détail d'une ville (vue non-membre).
 *
 * ┌───────────────────────────────────────────────────┐
 * │  MENU > VILLE > LISTE > <Nom ville>               │
 * ├─────────────────────┬─────────────────────────────┤
 * │  Infos générales    │  Liste des membres          │
 * │  Owner / Banque /   │  (avatar skin + pseudo +    │
 * │  Monde / Fondée...  │   grade + dot connecté)     │
 * ├─────────────────────┴─────────────────────────────┤
 * │  [REJOINDRE / QUITTER]           [RETOUR]         │
 * └───────────────────────────────────────────────────┘
 */
public class DetailsVilleScreen extends BaseMenuScreen {

    @Override public int panelW() { return Math.min(480, width  - 16); }
    @Override public int panelH() { return Math.min(310, height - 16); }

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int COLOR_BORDER       = 0xFF1E2D3D;
    private static final int COLOR_INFO_BG      = 0xFF141C26;
    private static final int COLOR_ROW_HOVER    = 0x33FFFFFF;
    private static final int COLOR_SCROLL       = 0xFF2EA84E;
    private static final int COLOR_SCROLL_BG    = 0x44FFFFFF;
    private static final int COLOR_BTN_JOIN_N   = 0xFF1A3E5E;
    private static final int COLOR_BTN_JOIN_H   = 0xFF2A6A9E;
    private static final int COLOR_BTN_QUIT_N   = 0xFF5E1A1A;
    private static final int COLOR_BTN_QUIT_H   = 0xFF9E2A2A;
    private static final int COLOR_BTN_RETOUR_N = 0xFF333333;
    private static final int COLOR_BTN_RETOUR_H = 0xFF555555;

    private static final int SEP      = 8;

    /** Largeur du panneau infos gauche — 35 % du panel, max 170 px. */
    private int leftW() { return Math.min(170, panelW() * 35 / 100); }
    private static final int ROW_H    = 18;
    private static final int BTN_H    = 18;
    private static final int SCROLL_W = 6;

    // ── État ─────────────────────────────────────────────────────────────
    private VilleListPayload.VilleEntry ville;
    private final Screen previousScreen;
    private int scrollOffset = 0;

    // Zones boutons (calculées chaque frame)
    private int btnLeftX, btnLeftY, btnLeftW;
    private int btnRetourX, btnRetourY, btnRetourW;

    // Overlay de confirmation quitter
    private boolean showConfirmQuitter = false;

    public DetailsVilleScreen(VilleListPayload.VilleEntry ville, Screen prev) {
        super("MENU", "VILLE", "LISTE", ville.nom().toUpperCase());
        this.ville          = ville;
        this.previousScreen = prev;
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        VilleMembresDataManager.clear();
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new VilleMembresRequestPayload(ville.id()));
    }

    @Override
    public void tick() {
        super.tick();
        // Rafraîchir les données depuis le cache
        VilleListPayload.VilleEntry fresh = VilleListDataManager.getById(ville.id());
        if (fresh != null) ville = fresh;
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px       = panelX(), py = panelY();
        int contentY = py + HEADER_H + PADDING;
        int contentH = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;
        int btnAreaH = BTN_H + 8;
        int splitH   = contentH - btnAreaH;

        renderLeftInfo(gfx, px, contentY, splitH);
        renderRightMembers(gfx, px, contentY, splitH, mouseX, mouseY);
        renderButtons(gfx, px, contentY, splitH, mouseX, mouseY);

        if (showConfirmQuitter)
            renderConfirmOverlay(gfx, mouseX, mouseY);
    }

    private void renderLeftInfo(GuiGraphics gfx, int px, int contentY, int splitH) {
        int lx = px + PADDING;
        GuiUtils.fillChamferedRect(gfx, lx, contentY, leftW(), splitH, 4, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, lx, contentY, leftW(), splitH, 4, 1, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, lx, contentY, leftW(), splitH, 6, 5, 1, 0x553DDE6A);

        int tx = lx + 6, ty = contentY + 6;
        int lineH = font.lineHeight + 3;

        gfx.drawString(font, "§l" + ville.nom(), tx, ty, 0xFFFFDD88, false);
        ty += lineH + 2;
        gfx.fill(lx + 4, ty, lx + leftW() - 4, ty + 1, COLOR_BORDER);
        ty += 5;

        gfx.drawString(font, "§a⬤ §f" + ville.nbConnectes() + " §7| §f" + ville.nbMembres() + " membres",
                tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Owner : §f" + ville.ownerPseudo(), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        String monde = ville.mondeSpawn().isEmpty() ? "Aucun" : GuiUtils.capitalize(ville.mondeSpawn());
        gfx.drawString(font, "§7Monde : §f" + monde, tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Banque : §f" + GuiUtils.formatBanque(ville.banque())
                + " " + com.example.rolynkmodmenu.util.Money.SYMBOL, tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Chunks : §f" + ville.totalChunks(), tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        String recLabel = switch (ville.recrutement() == null ? "" : ville.recrutement()) {
            case "ouvert"      -> "§aOuvert";
            case "sur_demande" -> "§eSur demande";
            default            -> "§cFermé";
        };
        gfx.drawString(font, "§7Recrutement : " + recLabel, tx, ty, 0xFFFFFFFF, false);
        ty += lineH;
        gfx.drawString(font, "§7Fondée : §f" + ville.dateFondation(), tx, ty, 0xFFFFFFFF, false);
    }

    private void renderRightMembers(GuiGraphics gfx, int px, int contentY, int splitH,
                                     int mouseX, int mouseY) {
        int rx     = px + PADDING + leftW() + SEP;
        int rw     = panelW() - PADDING - leftW() - SEP - PADDING;
        int listW  = rw - SCROLL_W - 2;

        GuiUtils.fillChamferedRect(gfx, rx, contentY, listW, splitH, 4, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rx, contentY, listW, splitH, 4, 1, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, rx, contentY, listW, splitH, 6, 5, 1, 0x553DDE6A);

        // Titre colonne
        gfx.drawCenteredString(font, "§lMembres", rx + listW / 2, contentY + 4, 0xFFCCCCCC);

        int listY = contentY + 16;
        int listH = splitH - 16;

        List<VilleMembresPayload.MembreEntry> membres = VilleMembresDataManager.getMembres();
        int visibleRows = listH / ROW_H;
        int maxScroll   = Math.max(0, membres.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (membres.isEmpty()) {
            gfx.drawCenteredString(font, "Chargement...", rx + listW / 2,
                    listY + listH / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = scrollOffset; i < membres.size() && i < scrollOffset + visibleRows; i++) {
                VilleMembresPayload.MembreEntry m = membres.get(i);
                int rowY = listY + (i - scrollOffset) * ROW_H;

                boolean hovered = mouseX >= rx && mouseX < rx + listW
                        && mouseY >= rowY && mouseY < rowY + ROW_H;
                if (hovered) {
                    GuiUtils.fillChamferedRect(gfx, rx + 1, rowY, listW - 2, ROW_H, 3, 0xC01A2840);
                    GuiUtils.drawChamferedBorder(gfx, rx - 2, rowY - 1, listW + 3, ROW_H + 2, 5, 1, 0x252ECC60);
                    GuiUtils.drawChamferedBorder(gfx, rx - 1, rowY,     listW + 1, ROW_H,     4, 1, 0x552ECC60);
                    GuiUtils.drawChamferedBorder(gfx, rx + 1, rowY,     listW - 2, ROW_H,     3, 1, 0xFF2EA84E);
                }

                // Dot connexion
                gfx.fill(rx + 4, rowY + 5, rx + 8, rowY + 9, m.online() ? 0xFF55FF55 : 0xFF888888);

                // Grade à droite (nom complet + couleur)
                int gradeColor = GuiUtils.gradeColor(m.grade());
                String gradeLbl = m.grade().isEmpty() ? "?" : m.grade();
                int gradeLblW   = font.width(gradeLbl);
                int gradeLblX   = rx + listW - gradeLblW - 6;
                gfx.drawString(font, gradeLbl, gradeLblX,
                        rowY + (ROW_H - font.lineHeight) / 2, gradeColor, false);

                // Pseudo (tronqué si trop long pour ne pas chevaucher le grade)
                int maxPseudoW = gradeLblX - (rx + 14);
                String pseudo  = m.pseudo();
                while (pseudo.length() > 1 && font.width(pseudo) > maxPseudoW)
                    pseudo = pseudo.substring(0, pseudo.length() - 1);
                if (!pseudo.equals(m.pseudo())) pseudo = pseudo.substring(0, pseudo.length() - 1) + "…";
                gfx.drawString(font, pseudo, rx + 12,
                        rowY + (ROW_H - font.lineHeight) / 2, hovered ? 0xFF3DD96A : 0xFFFFFFFF, false);

            }
        }

        // Scrollbar
        if (membres.size() > visibleRows) {
            int sbX    = rx + listW + 2;
            int thumbH = Math.max(16, listH * visibleRows / membres.size());
            int thumbY = listY + (listH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            gfx.fill(sbX, listY, sbX + SCROLL_W, listY + listH, COLOR_SCROLL_BG);
            gfx.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, COLOR_SCROLL);
        }
    }

    private void renderButtons(GuiGraphics gfx, int px, int contentY, int splitH,
                                int mouseX, int mouseY) {
        int btnY   = contentY + splitH + 6;
        int totalW = panelW() - PADDING * 2;
        boolean isInThisVille = ville.nom().equalsIgnoreCase(VilleProfileDataManager.getVilleNom());
        boolean hasVille      = VilleProfileDataManager.hasVille();

        if (isInThisVille) {
            // QUITTER + RETOUR
            int bw = (totalW - 8) / 2;
            btnLeftX = px + PADDING; btnLeftY = btnY; btnLeftW = bw;
            btnRetourX = px + PADDING + bw + 8; btnRetourY = btnY; btnRetourW = bw;
            renderBtn(gfx, mouseX, mouseY, btnLeftX, btnY, bw, BTN_H,
                    "QUITTER", true, COLOR_BTN_QUIT_N, COLOR_BTN_QUIT_H);
            renderBtn(gfx, mouseX, mouseY, btnRetourX, btnY, bw, BTN_H,
                    "RETOUR", true, COLOR_BTN_RETOUR_N, COLOR_BTN_RETOUR_H);
        } else if (!hasVille) {
            // Bouton gauche selon le mode de recrutement
            int bw = (totalW - 8) / 2;
            btnLeftX = px + PADDING; btnLeftY = btnY; btnLeftW = bw;
            btnRetourX = px + PADDING + bw + 8; btnRetourY = btnY; btnRetourW = bw;

            String rec = ville.recrutement() == null ? "sur_demande" : ville.recrutement();
            boolean canJoin = !"ferme".equals(rec);
            String joinLabel = switch (rec) {
                case "ouvert"     -> "REJOINDRE";
                case "sur_demande" -> "POSTULER";
                default           -> "FERMÉ";
            };
            renderBtn(gfx, mouseX, mouseY, btnLeftX, btnY, bw, BTN_H,
                    joinLabel, canJoin, COLOR_BTN_JOIN_N, COLOR_BTN_JOIN_H);
            renderBtn(gfx, mouseX, mouseY, btnRetourX, btnY, bw, BTN_H,
                    "RETOUR", true, COLOR_BTN_RETOUR_N, COLOR_BTN_RETOUR_H);
        } else {
            // Dans une autre ville → RETOUR pleine largeur
            btnLeftX = -1; btnLeftY = -1; btnLeftW = 0;
            btnRetourX = px + PADDING; btnRetourY = btnY; btnRetourW = totalW;
            renderBtn(gfx, mouseX, mouseY, btnRetourX, btnY, totalW, BTN_H,
                    "RETOUR", true, COLOR_BTN_RETOUR_N, COLOR_BTN_RETOUR_H);
        }
    }

    private void renderBtn(GuiGraphics gfx, int mouseX, int mouseY,
                            int bx, int by, int bw, int bh, String label,
                            boolean enabled, int colorN, int colorH) {
        boolean hov = enabled && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int bg = !enabled ? 0xFF444444 : (hov ? colorH : colorN);
        GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 3, bg);
        GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 3, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, label, bx + bw / 2,
                by + (bh - font.lineHeight + 1) / 2 + 1, enabled ? 0xFFFFFFFF : 0xFF888888);
    }

    private void renderConfirmOverlay(GuiGraphics gfx, int mouseX, int mouseY) {
        int ow = Math.min(200, width - 40), oh = 80;
        int ox = (width - ow) / 2, oy = (height - oh) / 2;
        GuiUtils.fillChamferedRect(gfx, ox, oy, ow, oh, 6, 0xFF0D1117);
        GuiUtils.drawChamferedBorder(gfx, ox, oy, ow, oh, 6, 2, 0xFF9E2A2A);
        gfx.drawCenteredString(font, "§cQuitter §e" + ville.nom() + "§c ?", ox + ow / 2, oy + 14, 0xFFFFFFFF);

        int bw = 70, bh = 16;
        int bx1 = ox + (ow / 2) - bw - 6, bx2 = ox + ow / 2 + 6;
        int by = oy + oh - bh - 10;
        renderBtn(gfx, mouseX, mouseY, bx1, by, bw, bh, "CONFIRMER", true, 0xFF5E1A1A, 0xFF9E2A2A);
        renderBtn(gfx, mouseX, mouseY, bx2, by, bw, bh, "ANNULER",   true, 0xFF333333, 0xFF555555);
    }

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        if (showConfirmQuitter) {
            int ow = Math.min(200, width - 40), oh = 80;
            int ox = (width - ow) / 2, oy = (height - oh) / 2;
            int bw = 70, bh = 16;
            int bx1 = ox + (ow / 2) - bw - 6, bx2 = ox + ow / 2 + 6;
            int by = oy + oh - bh - 10;
            if (mx >= bx1 && mx < bx1 + bw && my >= by && my < by + bh) {
                doQuitter();
                showConfirmQuitter = false;
            } else if (mx >= bx2 && mx < bx2 + bw && my >= by && my < by + bh) {
                showConfirmQuitter = false;
            }
            return true;
        }

        boolean isInThisVille = ville.nom().equalsIgnoreCase(VilleProfileDataManager.getVilleNom());
        boolean hasVille      = VilleProfileDataManager.hasVille();

        // Bouton gauche
        if (btnLeftX != -1 && mx >= btnLeftX && mx < btnLeftX + btnLeftW
                && my >= btnLeftY && my < btnLeftY + BTN_H) {
            if (isInThisVille) {
                showConfirmQuitter = true;
            } else if (!hasVille) {
                String rec = ville.recrutement() == null ? "sur_demande" : ville.recrutement();
                if ("ouvert".equals(rec))       doRejoindre();
                else if ("sur_demande".equals(rec)) doPostuler();
                // "ferme" → bouton désactivé, pas d'action
            }
            return true;
        }
        // Bouton retour
        if (mx >= btnRetourX && mx < btnRetourX + btnRetourW
                && my >= btnRetourY && my < btnRetourY + BTN_H) {
            onClose();
            return true;
        }

        // Clic sur la liste des membres
        int px       = panelX(), py = panelY();
        int rx       = px + PADDING + leftW() + SEP;
        int rw       = panelW() - PADDING - leftW() - SEP - PADDING;
        int listW    = rw - SCROLL_W - 2;
        int contentY = py + HEADER_H + PADDING;
        int splitH   = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - BTN_H - 8;
        int listY    = contentY + 16;
        int listH    = splitH - 16;

        if (mx >= rx && mx < rx + listW && my >= listY && my < listY + listH) {
            List<VilleMembresPayload.MembreEntry> membres = VilleMembresDataManager.getMembres();
            int visibleRows = listH / ROW_H;
            for (int i = scrollOffset; i < membres.size() && i < scrollOffset + visibleRows; i++) {
                int rowY = listY + (i - scrollOffset) * ROW_H;
                if (my >= rowY && my < rowY + ROW_H) {
                    VilleMembresPayload.MembreEntry m = membres.get(i);
                    minecraft.setScreen(new ProfilJoueurScreen(this, m.uuid()));
                    return true;
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px       = panelX(), py = panelY();
        int rx       = px + PADDING + leftW() + SEP;
        int rw       = panelW() - PADDING - leftW() - SEP - PADDING;
        int contentY = py + HEADER_H + PADDING;
        int splitH   = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - BTN_H - 8;
        int mx = (int) mouseX, my = (int) mouseY;

        if (mx >= rx && mx < rx + rw && my >= contentY && my < contentY + splitH) {
            List<VilleMembresPayload.MembreEntry> membres = VilleMembresDataManager.getMembres();
            int listH   = splitH - 16;
            int visible = listH / ROW_H;
            int maxS    = Math.max(0, membres.size() - visible);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), maxS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    // ── Actions ───────────────────────────────────────────────────────────

    /** Rejoindre directement (recrutement "ouvert"). */
    private void doRejoindre() {
        if (minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand("ville rejoindre " + ville.nom());
        onClose();
    }

    /** Postuler (recrutement "sur_demande"). */
    private void doPostuler() {
        if (minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand("ville demande " + ville.nom());
        onClose();
    }

    private void doQuitter() {
        if (minecraft.player != null && minecraft.player.connection != null)
            minecraft.player.connection.sendCommand("ville quitter");
        onClose();
    }

}
