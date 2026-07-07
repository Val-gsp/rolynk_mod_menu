package com.example.rolynkmodmenu.client.screen.balise;

import com.example.rolynkmodmenu.client.balise.BaliseDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.BaliseListRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Écran principal de gestion des balises (waypoints).
 */
public class BaliseScreen extends BaseMenuScreen {

    // ── Couleurs ─────────────────────────────────────────────────────────
    private static final int COLOR_BORDER       = 0xFF1E2D3D;
    private static final int COLOR_INFO_BG      = 0xFF141C26;
    private static final int COLOR_ROW_SELECTED = 0x442EA84E;
    private static final int COLOR_ROW_HOVER    = 0x221E2D3D;
    private static final int COLOR_SCROLLBAR    = 0xFF2EA84E;
    private static final int COLOR_SCROLLBAR_BG = 0xFF0D1117;

    private static final int COLOR_BTN_BG       = 0xA0141C26;
    private static final int COLOR_BTN_BG_HOV   = 0xC01A2840;
    private static final int COLOR_BTN_BORDER    = 0xFF1C2C3C;
    private static final int COLOR_BTN_TITLE     = 0xFFEEF2F5;
    private static final int COLOR_BTN_TITLE_H   = 0xFF3DD96A;
    private static final int COLOR_BTN_DISABLED  = 0x601A1A1A;

    // ── Layout ───────────────────────────────────────────────────────────
    private static final int SEP      = 8;
    private static final int ROW_H    = 20;
    private static final int SEARCH_H = 16;
    private static final int BTN_H    = 24;
    private static final int SCROLL_W = 7;

    /** Largeur du panneau gauche — 27 % du panel, max 140 px. */
    private int leftW() { return Math.min(140, panelW() * 27 / 100); }
    /** Hauteur de la boîte d'infos — remplit l'espace au-dessus des 3 boutons. */
    private int infoH(int contentH) { return Math.min(100, Math.max(60, contentH - (3 * BTN_H + 2 * 5 + 12))); }

    // ── État ─────────────────────────────────────────────────────────────
    private EditBox searchBox;
    private int selectedIndex = -1;
    private int scrollOffset  = 0;

    // ── Boutons (zones cliquables calculées à chaque frame) ───────────────
    private int btnTpX, btnTpY, btnTpW;
    private int btnDelX, btnDelY, btnDelW;
    private int btnAddX, btnAddY, btnAddW;

    public BaliseScreen() {
        super("MENU", "BALISE");
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        int rightX = panelX() + PADDING + leftW() + SEP;
        int rightW = panelW() - PADDING - leftW() - SEP - PADDING - SCROLL_W - 2;
        int contentY = panelY() + HEADER_H + PADDING;

        int searchBoxY = contentY + (SEARCH_H - font.lineHeight) / 2;
        searchBox = new EditBox(font, rightX + 4, searchBoxY, rightW - 8, font.lineHeight,
                Component.literal("Rechercher..."));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setResponder(t -> {
            scrollOffset  = 0;
            selectedIndex = -1;
        });
        searchBox.setBordered(false);
        addWidget(searchBox); // rendu manuel dans renderRightPanel pour contrôler l'ordre

        // Demande la liste au serveur si connecté
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new BaliseListRequestPayload());
        }
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px = panelX(), py = panelY();
        int contentY = py + HEADER_H + PADDING;
        int contentH = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;

        renderLeftPanel(gfx, px, contentY, contentH, mouseX, mouseY);
        renderRightPanel(gfx, px, contentY, contentH, mouseX, mouseY, partialTick);
    }

    private void renderLeftPanel(GuiGraphics gfx, int px, int contentY, int contentH, int mouseX, int mouseY) {
        int lx    = px + PADDING;
        int lw    = leftW();
        int infoH = infoH(contentH);

        // ── Info box ─────────────────────────────────────────────────────
        GuiUtils.fillChamferedRect(gfx, lx, contentY, lw, infoH, 4, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, lx, contentY, lw, infoH, 4, 1, COLOR_BORDER);

        List<BaliseDataManager.Balise> filtered = getFiltered();
        BaliseDataManager.Balise sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;

        if (sel == null) {
            // Aucune sélection
            int cx = lx + lw / 2;
            int cy = contentY + infoH / 2 - font.lineHeight;
            gfx.drawCenteredString(font, "Sélectionnez", cx, cy, 0xFF888888);
            gfx.drawCenteredString(font, "une balise", cx, cy + font.lineHeight + 2, 0xFF888888);
        } else {
            // Affichage des infos de la balise sélectionnée
            int tx = lx + 6;
            int ty = contentY + 6;
            int lineH = font.lineHeight + 3;

            gfx.drawString(font, "§l" + sel.nom(), tx, ty, 0xFFDDCC88, false);
            ty += lineH;

            // Séparateur
            gfx.fill(lx + 4, ty, lx + lw - 4, ty + 1, COLOR_BORDER);
            ty += 5;

            String mondeRaw = sel.monde().contains(":") ? sel.monde().split(":")[1] : sel.monde();
            String mondeCap = mondeRaw.isEmpty() ? mondeRaw : Character.toUpperCase(mondeRaw.charAt(0)) + mondeRaw.substring(1).toLowerCase();
            gfx.drawString(font, "§7Serveur : §f" + mondeCap, tx, ty, 0xFFFFFFFF, false);
            ty += lineH;

            gfx.drawString(font, "§7X: §f" + sel.x(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Y: §f" + sel.y(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Z: §f" + sel.z(), tx, ty, 0xFFFFFFFF, false);
        }

        // ── 3 boutons ────────────────────────────────────────────────────
        int btnAreaY = contentY + infoH + 6;
        int btnGap   = 5;
        int totalBtnH = 3 * BTN_H + 2 * btnGap;
        boolean hasSel = sel != null;
        int totalBalises = BaliseDataManager.getBalises().size();
        int  maxBalises = BaliseDataManager.getMaxBalises();
        boolean canAdd  = totalBalises < maxBalises;

        btnTpW = lw;
        btnTpX = lx;
        btnTpY = btnAreaY;

        btnDelW = lw;
        btnDelX = lx;
        btnDelY = btnAreaY + BTN_H + btnGap;

        btnAddW = lw;
        btnAddX = lx;
        btnAddY = btnAreaY + 2 * (BTN_H + btnGap);

        renderButton(gfx, btnTpX,  btnTpY,  btnTpW,  BTN_H, "TÉLÉPORTER",                    hasSel, mouseX, mouseY);
        renderButton(gfx, btnDelX, btnDelY, btnDelW, BTN_H, "SUPPRIMER",                     hasSel, mouseX, mouseY);
        renderButton(gfx, btnAddX, btnAddY, btnAddW, BTN_H, "+ CRÉER  " + totalBalises + "/" + maxBalises, canAdd, mouseX, mouseY);
    }

    private void renderButton(GuiGraphics gfx, int bx, int by, int bw, int bh,
                               String label, boolean enabled, int mouseX, int mouseY) {
        if (!enabled) {
            GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 4, COLOR_BTN_DISABLED);
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, COLOR_BTN_BORDER);
            gfx.drawCenteredString(font, label, bx + bw / 2,
                    by + (bh - font.lineHeight) / 2 + 1, 0xFF555566);
            return;
        }

        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

        GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 4, hov ? COLOR_BTN_BG_HOV : COLOR_BTN_BG);

        if (hov) {
            GuiUtils.drawChamferedBorder(gfx, bx - 3, by - 3, bw + 6, bh + 6, 7, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, 6, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, 5, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, bh, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, COLOR_BTN_BORDER);
        }

        drawBtnCornerBrackets(gfx, bx, by, bw, bh, hov);

        Component title = Component.literal(label).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, title, bx + bw / 2,
                by + (bh - font.lineHeight) / 2 + 1, hov ? COLOR_BTN_TITLE_H : COLOR_BTN_TITLE);
    }

    private void drawBtnCornerBrackets(GuiGraphics gfx, int x, int y, int w, int h, boolean hov) {
        int arm   = 4;
        int thick = 1;
        int off   = 5;
        int cn = 0x553DDE6A;
        int ch = 0xCC3DDE6A;
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

    private void renderRightPanel(GuiGraphics gfx, int px, int contentY, int contentH, int mouseX, int mouseY, float partialTick) {
        int rightX = px + PADDING + leftW() + SEP;
        int rightW = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;

        // Barre de recherche avec fond arrondi
        GuiUtils.fillChamferedRect(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, 1, COLOR_BORDER);
        if (searchBox != null) searchBox.render(gfx, mouseX, mouseY, partialTick);

        int listY = contentY + SEARCH_H + 4;
        int listH = contentH - SEARCH_H - 4;

        List<BaliseDataManager.Balise> filtered = getFiltered();
        int visibleRows = listH / ROW_H;
        int maxScroll   = Math.max(0, filtered.size() - visibleRows);
        scrollOffset    = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Fond de la liste
        GuiUtils.fillChamferedRect(gfx, rightX, listY, listAreaW, listH, 3, COLOR_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, listY, listAreaW, listH, 3, 1, COLOR_BORDER);

        if (filtered.isEmpty()) {
            String msg = searchBox != null && !searchBox.getValue().isEmpty()
                    ? "Aucun résultat" : "Aucune balise";
            gfx.drawCenteredString(font, msg,
                    rightX + listAreaW / 2, listY + listH / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            // Rows
            int clipY1 = listY + 2;
            int clipY2 = listY + listH - 2;

            for (int i = scrollOffset; i < filtered.size() && i < scrollOffset + visibleRows; i++) {
                BaliseDataManager.Balise b = filtered.get(i);
                int rowY = listY + (i - scrollOffset) * ROW_H;

                boolean hovered  = mouseX >= rightX && mouseX < rightX + listAreaW
                        && mouseY >= rowY && mouseY < rowY + ROW_H;
                boolean selected = (i == selectedIndex);

                if (selected) {
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, COLOR_ROW_SELECTED);
                } else if (hovered) {
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, COLOR_ROW_HOVER);
                }

                // Nom de la balise
                int nameColor = selected ? 0xFFFFFF55 : 0xFFFFFFFF;
                gfx.drawString(font, "§l" + b.nom(), rightX + 5, rowY + (ROW_H - font.lineHeight) / 2, nameColor, false);

                // Serveur à droite (grisé)
                String mondeRaw2 = b.monde().contains(":") ? b.monde().split(":")[1] : b.monde();
                String mondeCap2 = mondeRaw2.isEmpty() ? mondeRaw2 : Character.toUpperCase(mondeRaw2.charAt(0)) + mondeRaw2.substring(1).toLowerCase();
                String mondeLabel = "Serveur : " + mondeCap2;
                int mondeW = font.width(mondeLabel);
                gfx.drawString(font, mondeLabel,
                        rightX + listAreaW - mondeW - 4,
                        rowY + (ROW_H - font.lineHeight) / 2,
                        0xFF888888, false);

                // Séparateur entre rows
                if (i < filtered.size() - 1) {
                    gfx.fill(rightX + 4, rowY + ROW_H - 1, rightX + listAreaW - 4, rowY + ROW_H, 0x22FFFFFF);
                }
            }
        }

        // ── Scrollbar ─────────────────────────────────────────────────────
        if (filtered.size() > visibleRows) {
            int sbX    = rightX + listAreaW + 2;
            int sbY    = listY;
            int sbH    = listH;
            int thumbH = Math.max(20, sbH * visibleRows / filtered.size());
            int thumbY = sbY + (sbH - thumbH) * scrollOffset / Math.max(1, maxScroll);

            gfx.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, COLOR_SCROLLBAR_BG);
            gfx.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, COLOR_SCROLLBAR);
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX, my = (int) mouseY;

        // Clics sur les boutons
        List<BaliseDataManager.Balise> filtered = getFiltered();
        BaliseDataManager.Balise sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;
        int totalBalises = BaliseDataManager.getBalises().size();

        if (sel != null && isInside(mx, my, btnTpX, btnTpY, btnTpW, BTN_H)) {
            doTeleport(sel);
            return true;
        }
        if (sel != null && isInside(mx, my, btnDelX, btnDelY, btnDelW, BTN_H)) {
            doSupprimer(sel);
            return true;
        }
        if (totalBalises < BaliseDataManager.getMaxBalises() && isInside(mx, my, btnAddX, btnAddY, btnAddW, BTN_H)) {
            doCreer();
            return true;
        }

        // Clics sur la liste
        int px = panelX(), py = panelY();
        int rightX    = px + PADDING + leftW() + SEP;
        int rightW    = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;
        int contentY  = py + HEADER_H + PADDING;
        int listY     = contentY + SEARCH_H + 4;
        int listH     = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - SEARCH_H - 4;
        int visibleRows = listH / ROW_H;

        if (mx >= rightX && mx < rightX + listAreaW && my >= listY && my < listY + listH) {
            int rowIndex = (my - listY) / ROW_H + scrollOffset;
            if (rowIndex >= 0 && rowIndex < filtered.size()) {
                selectedIndex = rowIndex;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<BaliseDataManager.Balise> filtered = getFiltered();
        int px = panelX(), py = panelY();
        int rightX   = px + PADDING + leftW() + SEP;
        int rightW   = panelW() - PADDING - leftW() - SEP - PADDING;
        int contentY = py + HEADER_H + PADDING;
        int listY    = contentY + SEARCH_H + 4;
        int listH    = panelH() - HEADER_H - PADDING * 2 - SEARCH_H - 4;

        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= rightX && mx < rightX + rightW && my >= listY && my < listY + listH) {
            int visibleRows = listH / ROW_H;
            int maxScroll   = Math.max(0, filtered.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new MainMenuScreen());
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private void doTeleport(BaliseDataManager.Balise sel) {
        if (minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("balise tp " + sel.nom());
        }
    }

    private void doSupprimer(BaliseDataManager.Balise sel) {
        if (minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("balise supprimer " + sel.nom());
        }
        selectedIndex = -1;
        // Le serveur renverra la liste mise à jour via paquet S2C
        PacketDistributor.sendToServer(new BaliseListRequestPayload());
    }

    private void doCreer() {
        minecraft.setScreen(new CreerBaliseScreen(this));
    }

    // ── Utilitaires ──────────────────────────────────────────────────────

    private List<BaliseDataManager.Balise> getFiltered() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        if (query.isEmpty()) return BaliseDataManager.getBalises();
        return BaliseDataManager.getBalises().stream()
                .filter(b -> b.nom().toLowerCase().contains(query)
                          || b.monde().toLowerCase().contains(query))
                .toList();
    }

    private static boolean isInside(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }
}
