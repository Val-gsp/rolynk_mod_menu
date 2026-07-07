package com.example.rolynkmodmenu.client.screen.ville;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.client.ville.VilleListDataManager;
import com.example.rolynkmodmenu.client.ville.VilleProfileDataManager;
import com.example.rolynkmodmenu.network.VilleListPayload;
import com.example.rolynkmodmenu.network.VilleListRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Liste paginée de toutes les villes avec barre de recherche.
 *
 * Layout :
 *  ┌─────────────────────────────────────────────────────┐
 *  │  MENU > VILLE > LISTE                               │  ← header 48px
 *  ├──────────────┬──────────────────────────────────────┤
 *  │  Info ville  │  Barre recherche                     │
 *  │  sélectionnée│  ─────────────────────────────────── │
 *  │              │  Grille des villes (scrollable)       │
 *  │  [CONSULTER] │                              [scroll] │
 *  └──────────────┴──────────────────────────────────────┘
 */
public class ListeVillesScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int COLOR_BORDER        = 0xFF1E2D3D;
    private static final int COLOR_INFO_BG       = 0xFF141C26;
    private static final int COLOR_ROW_SELECTED  = 0x442EA84E;
    private static final int COLOR_ROW_HOVER     = 0x221E2D3D;
    private static final int COLOR_SCROLL        = 0xFF2EA84E;
    private static final int COLOR_SCROLL_BG     = 0xFF0D1117;
    private static final int COLOR_BTN_CONSULT   = 0xFF0D1E35;
    private static final int COLOR_BTN_CONSULT_H = 0xFF1A3A5C;

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int SEP       = 8;
    private static final int ROW_H     = 22;
    private static final int SEARCH_H  = 16;
    private static final int SCROLL_W  = 7;
    private static final int BTN_H     = 16;

    // Rafraîchissement auto toutes les 5s (100 ticks)
    private static final int REFRESH_INTERVAL = 100;
    private int refreshTick = 0;

    // ── État ─────────────────────────────────────────────────────────────
    private EditBox searchBox;
    private int selectedIndex = -1;
    private int scrollOffset  = 0;

    // Zone du bouton Consulter (calculée chaque frame)
    private int btnConsultX, btnConsultY, btnConsultW;

    public ListeVillesScreen() {
        super("MENU", "VILLE", "LISTE");
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        int rightX   = panelX() + PADDING + leftW() + SEP;
        int rightW   = panelW() - PADDING - leftW() - SEP - PADDING - SCROLL_W - 2;
        int contentY = panelY() + HEADER_H + PADDING;

        int sbY = contentY + (SEARCH_H - font.lineHeight) / 2;
        searchBox = new EditBox(font, rightX + 4, sbY, rightW - 8, font.lineHeight,
                Component.literal("Rechercher..."));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setBordered(false);
        searchBox.setResponder(t -> { scrollOffset = 0; selectedIndex = -1; });
        addWidget(searchBox);

        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new VilleListRequestPayload());
    }

    @Override
    public void tick() {
        super.tick();
        refreshTick++;
        if (refreshTick >= REFRESH_INTERVAL) {
            refreshTick = 0;
            if (minecraft.getConnection() != null)
                PacketDistributor.sendToServer(new VilleListRequestPayload());
        }
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px       = panelX(), py = panelY();
        int contentY = py + HEADER_H + PADDING;
        int contentH = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;

        renderLeftPanel(gfx, px, contentY, contentH, mouseX, mouseY);
        renderRightPanel(gfx, px, contentY, contentH, mouseX, mouseY, partialTick);
    }

    private void renderLeftPanel(GuiGraphics gfx, int px, int contentY, int contentH,
                                  int mouseX, int mouseY) {
        int lx    = px + PADDING;
        int infoH = contentH - BTN_H - 8;

        // Boîte d'infos
        GuiUtils.fillChamferedRect(gfx, lx, contentY, leftW(), infoH, 4, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, lx, contentY, leftW(), infoH, 4, 1, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, lx, contentY, leftW(), infoH, 6, 5, 1, 0x553DDE6A);

        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        VilleListPayload.VilleEntry sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;

        if (sel == null) {
            int cx = lx + leftW() / 2;
            int cy = contentY + infoH / 2 - font.lineHeight;
            gfx.drawCenteredString(font, "Sélectionnez", cx, cy, 0xFF888888);
            gfx.drawCenteredString(font, "une ville", cx, cy + font.lineHeight + 2, 0xFF888888);
        } else {
            int tx = lx + 6, ty = contentY + 6;
            int lineH = font.lineHeight + 3;

            // Nom
            gfx.drawString(font, "§l" + sel.nom(), tx, ty, 0xFFFFDD88, false);
            ty += lineH + 2;
            gfx.fill(lx + 4, ty, lx + leftW() - 4, ty + 1, COLOR_BORDER);
            ty += 5;

            // Stats connectés / membres
            gfx.drawString(font, "§a⬤ §f" + sel.nbConnectes() + " §7| §f" + sel.nbMembres() + " membres",
                    tx, ty, 0xFFFFFFFF, false);
            ty += lineH;

            // Infos
            gfx.drawString(font, "§7Owner : §f" + sel.ownerPseudo(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            String monde = sel.mondeSpawn().isEmpty() ? "Aucun" : GuiUtils.capitalize(sel.mondeSpawn());
            gfx.drawString(font, "§7Monde : §f" + monde, tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Banque : §f" + GuiUtils.formatBanque(sel.banque())
                    + " " + com.example.rolynkmodmenu.util.Money.SYMBOL, tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Chunks : §f" + sel.totalChunks(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            String recLabel = switch (sel.recrutement() == null ? "" : sel.recrutement()) {
                case "ouvert"      -> "§aOuvert";
                case "sur_demande" -> "§eSur demande";
                default            -> "§cFermé";
            };
            gfx.drawString(font, "§7Recrutement : " + recLabel, tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Fondée : §f" + sel.dateFondation(), tx, ty, 0xFFFFFFFF, false);
        }

        // Bouton CONSULTER
        int btnY = contentY + infoH + 6;
        btnConsultX = lx;
        btnConsultY = btnY;
        btnConsultW = leftW();
        boolean canConsult = sel != null;
        boolean hC = canConsult && mouseX >= btnConsultX && mouseX < btnConsultX + btnConsultW
                && mouseY >= btnConsultY && mouseY < btnConsultY + BTN_H;
        int bgBtn = !canConsult ? 0xFF444444 : (hC ? COLOR_BTN_CONSULT_H : COLOR_BTN_CONSULT);
        GuiUtils.fillChamferedRect(gfx, btnConsultX, btnConsultY, btnConsultW, BTN_H, 3, bgBtn);
        GuiUtils.drawChamferedBorder(gfx, btnConsultX, btnConsultY, btnConsultW, BTN_H, 3, 1, COLOR_BORDER);
        gfx.drawCenteredString(font, "CONSULTER", btnConsultX + btnConsultW / 2,
                btnConsultY + (BTN_H - font.lineHeight + 1) / 2 + 1, canConsult ? 0xFFFFFFFF : 0xFF888888);
    }

    private void renderRightPanel(GuiGraphics gfx, int px, int contentY, int contentH,
                                   int mouseX, int mouseY, float partialTick) {
        int rightX    = px + PADDING + leftW() + SEP;
        int rightW    = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;

        // Barre de recherche
        GuiUtils.fillChamferedRect(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, 1, COLOR_BORDER);
        if (searchBox != null) searchBox.render(gfx, mouseX, mouseY, partialTick);

        int listY = contentY + SEARCH_H + 4;
        int listH = contentH - SEARCH_H - 4;

        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        int visibleRows = listH / ROW_H;
        int maxScroll   = Math.max(0, filtered.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Fond liste
        GuiUtils.fillChamferedRect(gfx, rightX, listY, listAreaW, listH, 3, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, listY, listAreaW, listH, 3, 1, COLOR_BORDER);
        GuiUtils.drawCornerBrackets(gfx, rightX, listY, listAreaW, listH, 6, 5, 1, 0x553DDE6A);

        if (filtered.isEmpty()) {
            String msg = searchBox != null && !searchBox.getValue().isEmpty()
                    ? "Aucun résultat" : "Aucune ville";
            gfx.drawCenteredString(font, msg,
                    rightX + listAreaW / 2, listY + listH / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = scrollOffset; i < filtered.size() && i < scrollOffset + visibleRows; i++) {
                VilleListPayload.VilleEntry v = filtered.get(i);
                int rowY = listY + (i - scrollOffset) * ROW_H;

                boolean hovered  = mouseX >= rightX && mouseX < rightX + listAreaW
                        && mouseY >= rowY && mouseY < rowY + ROW_H;
                boolean selected = (i == selectedIndex);

                if (selected)
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, COLOR_ROW_SELECTED);
                else if (hovered)
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, COLOR_ROW_HOVER);

                // Nom ville
                int nameColor = selected ? 0xFFFFFF55 : 0xFFFFFFFF;
                gfx.drawString(font, "§l" + v.nom(), rightX + 5,
                        rowY + (ROW_H - font.lineHeight) / 2, nameColor, false);

                // Connectés + membres à droite
                String statStr = "§a" + v.nbConnectes() + "§7/§f" + v.nbMembres();
                int statW = font.width(statStr.replaceAll("§.", "")) + 4;
                gfx.drawString(font, statStr, rightX + listAreaW - statW - 4,
                        rowY + (ROW_H - font.lineHeight) / 2, 0xFFFFFFFF, false);

                // Séparateur
                if (i < filtered.size() - 1)
                    gfx.fill(rightX + 4, rowY + ROW_H - 1, rightX + listAreaW - 4, rowY + ROW_H, 0x22FFFFFF);
            }
        }

        // Scrollbar
        if (filtered.size() > visibleRows) {
            int sbX    = rightX + listAreaW + 2;
            int thumbH = Math.max(20, listH * visibleRows / filtered.size());
            int thumbY = listY + (listH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            gfx.fill(sbX, listY, sbX + SCROLL_W, listY + listH, COLOR_SCROLL_BG);
            gfx.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, COLOR_SCROLL);
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // Bouton Consulter
        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        VilleListPayload.VilleEntry sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;

        if (sel != null && mx >= btnConsultX && mx < btnConsultX + btnConsultW
                && my >= btnConsultY && my < btnConsultY + BTN_H) {
            openDetails(sel);
            return true;
        }

        // Clic dans la liste
        int px       = panelX(), py = panelY();
        int contentY = py + HEADER_H + PADDING;
        int rightX   = px + PADDING + leftW() + SEP;
        int rightW   = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;
        int listY    = contentY + SEARCH_H + 4;
        int listH    = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - SEARCH_H - 4;
        int visibleRows = listH / ROW_H;

        if (mx >= rightX && mx < rightX + listAreaW && my >= listY && my < listY + listH) {
            int idx = (my - listY) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < filtered.size()) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int px       = panelX(), py = panelY();
        int rightX   = px + PADDING + leftW() + SEP;
        int contentY = py + HEADER_H + PADDING;
        int listY    = contentY + SEARCH_H + 4;
        int listH    = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - SEARCH_H - 4;
        int rightW   = panelW() - PADDING - leftW() - SEP - PADDING;
        int mx = (int) mouseX, my = (int) mouseY;

        if (mx >= rightX && mx < rightX + rightW && my >= listY && my < listY + listH) {
            List<VilleListPayload.VilleEntry> filtered = getFiltered();
            int visibleRows = listH / ROW_H;
            int maxScroll   = Math.max(0, filtered.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new VilleScreen());
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void openDetails(VilleListPayload.VilleEntry ville) {
        // Si le joueur est membre de cette ville → ouvrir directement GestionVilleScreen
        if (ville.nom().equalsIgnoreCase(VilleProfileDataManager.getVilleNom())) {
            minecraft.setScreen(new GestionVilleScreen(ville, this));
        } else {
            minecraft.setScreen(new DetailsVilleScreen(ville, this));
        }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    /** Largeur du panneau gauche — 29 % du panel, max 150 px. */
    private int leftW() { return Math.min(150, panelW() * 29 / 100); }

    private List<VilleListPayload.VilleEntry> getFiltered() {
        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        if (q.isEmpty()) return VilleListDataManager.getVilles();
        return VilleListDataManager.getVilles().stream()
                .filter(v -> v.nom().toLowerCase().contains(q)
                          || v.ownerPseudo().toLowerCase().contains(q))
                .toList();
    }

}
