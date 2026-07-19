package com.example.rolynkmodmenu.client.screen;

import com.example.rolynkmodmenu.client.HenuIcons;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.MenuButton;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.NavIconButton;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe de base pour tous les sous-écrans du mod.
 * Applique le même style visuel que MainMenuScreen :
 * panel biseauté sombre, header imbriqué, footer avec horloge.
 */
public abstract class BaseMenuScreen extends Screen {

    // ── Taille du panel (identique à MainMenuScreen) ─────────────────────────
    private static final int PREFERRED_W = 520;
    private static final int PREFERRED_H = 295;
    private static final int MARGIN      = 16;

    // ── Header ────────────────────────────────────────────────────────────────
    private static final int HDR_INNER_W = 214;
    private static final int HDR_MID_W   = 242;
    private static final int HDR_H       = 80;
    private static final int HDR_STEP    = 12;

    // ── Footer ────────────────────────────────────────────────────────────────
    public static final int FOOTER_H = 26;

    /** Hauteur effective du header vue depuis les sous-classes (= HDR_H - 2*HDR_STEP).
     *  contentY = panelY() + HEADER_H + PADDING → démarre juste sous le header. */
    public static final int HEADER_H = 56;

    // ── Layout boutons ────────────────────────────────────────────────────────
    protected static final int PADDING   = 12;
    protected static final int GRID_COLS = 3;
    protected static final int GRID_ROWS = 2;
    protected static final int BTN_GAP   = 8;

    // ── Couleurs (identiques à MainMenuScreen) ────────────────────────────────
    private static final int COLOR_PANEL    = 0xF20D1117;
    private static final int COLOR_BORDER   = 0xFF1E2D3D;
    private static final int COLOR_DIVIDER  = 0xFF182230;
    private static final int COLOR_ACCENT   = 0xFF2EA84E;
    private static final int COLOR_TEXT_DIM = 0xFF4A6070;

    private static final ResourceLocation LOGO_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "textures/gui/logo.png");

    protected BaseMenuScreen(String... crumbs) {
        super(Component.literal(String.join(" > ", crumbs)));
    }

    // ── Dimensions responsives ────────────────────────────────────────────────

    public  int panelW() { return Math.min(PREFERRED_W, width  - MARGIN); }
    public  int panelH() { return Math.min(PREFERRED_H, height - 2 * HDR_STEP - MARGIN); }
    public  int panelX() { return (width  - panelW()) / 2; }
    public  int panelY() { return hdrTopY() + 2 * HDR_STEP; }

    private int hdrTopY() { return (height - 2 * HDR_STEP - panelH()) / 2; }
    private int btnOffsetY() { return HDR_H - 2 * HDR_STEP + PADDING; }   // 68 px sous panelY

    protected int gridBtnW() {
        return (panelW() - 2 * PADDING - (GRID_COLS - 1) * BTN_GAP) / GRID_COLS;
    }
    protected int gridBtnH() {
        return (panelH() - btnOffsetY() - PADDING - FOOTER_H - (GRID_ROWS - 1) * BTN_GAP) / GRID_ROWS;
    }

    /**
     * Ajoute une grille 3×2 de boutons dans la zone de contenu.
     * @param labels  textes des boutons (max 6)
     * @param actions actions correspondantes (même index)
     */
    protected void addButtonGrid(String[] labels, Runnable[] actions) {
        int gridX = panelX() + PADDING;
        int gridY = panelY() + btnOffsetY();
        int bw    = gridBtnW();
        int bh    = gridBtnH();

        int count = Math.min(labels.length, GRID_COLS * GRID_ROWS);
        for (int i = 0; i < count; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int bx  = gridX + col * (bw + BTN_GAP);
            int by  = gridY + row * (bh + BTN_GAP);
            addRenderableWidget(new MenuButton(bx, by, bw, bh, labels[i], actions[i]));
        }
    }

    // ── Navigation (icônes retour/maison sous le header, à droite) ────────────

    /** Écran ouvert par l'icône retour. Par défaut : le menu principal. */
    protected net.minecraft.client.gui.screens.Screen backTarget() { return new MainMenuScreen(); }

    /** Certaines fenêtres masquent les icônes (menu principal, flux forcés). */
    protected boolean showNavIcons() { return true; }

    private void addNavIcons() {
        int homeX = panelX() + panelW() - PADDING - 13;
        int backX = homeX - 19;
        int y = panelY() + HEADER_H - 4;
        addRenderableWidget(new NavIconButton(backX, y, NavIconButton.ICON_BACK, "Retour",
                () -> minecraft.setScreen(backTarget())));
        addRenderableWidget(new NavIconButton(homeX, y, NavIconButton.ICON_HOME, "Menu principal",
                () -> minecraft.setScreen(new MainMenuScreen())));
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @Override
    public final void init() {
        if (showNavIcons()) addNavIcons();
        initContent();
    }

    /** Ajouter les widgets ici via addButtonGrid() ou addRenderableWidget(). */
    protected abstract void initContent();

    // ── Rendu ─────────────────────────────────────────────────────────────────

    @Override
    public final void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px = panelX(), py = panelY();
        int pw = panelW(), ph = panelH();

        gfx.fill(0, 0, width, height, 0xBB000000);
        GuiUtils.fillChamferedRect(gfx, px, py, pw, ph, 12, COLOR_PANEL);
        GuiUtils.drawChamferedBorder(gfx, px, py, pw, ph, 12, 1, COLOR_BORDER);

        renderHeader(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);

        renderContent(gfx, mouseX, mouseY, partialTick);

        renderFooter(gfx, px, py, pw, ph);
    }

    private void renderHeader(GuiGraphics gfx) {
        int px     = panelX(), pw = panelW();
        int innerY = hdrTopY();
        int midY   = innerY + HDR_STEP;
        int innerX = px + (pw - HDR_INNER_W) / 2;
        int midX   = px + (pw - HDR_MID_W)   / 2;
        int innerH = HDR_H;
        int midH   = HDR_H - HDR_STEP;

        // 1. Fonds
        GuiUtils.fillChamferedRect(gfx, midX,   midY,   HDR_MID_W,   midH,   8, 0xFF141C26);
        GuiUtils.fillChamferedRect(gfx, innerX, innerY, HDR_INNER_W, innerH, 6, 0xFF141C26);

        // 2. Logo personnalisé centré (même logique que MainMenuScreen)
        int texW = 1024, texH = 262;
        float logoScale = Math.min((float)(HDR_INNER_W - 24) / texW, (float)(innerH - 24) / texH);
        int dispW = Math.round(texW * logoScale);
        int dispH = Math.round(texH * logoScale);
        int logoX = innerX + (HDR_INNER_W - dispW) / 2;
        int logoY = innerY + (innerH - dispH) / 2;
        gfx.pose().pushPose();
        gfx.pose().translate(logoX, logoY, 0);
        gfx.pose().scale(logoScale, logoScale, 1f);
        gfx.blit(LOGO_TEXTURE, 0, 0, 0, 0, texW, texH, texW, texH);
        gfx.pose().popPose();

        // 3. Bordures
        GuiUtils.drawChamferedBorderNoTop(gfx, midX,   midY,   HDR_MID_W,   midH,   8, 1, COLOR_DIVIDER);
        GuiUtils.drawChamferedBorder(gfx, innerX, innerY, HDR_INNER_W, innerH, 6, 1, COLOR_BORDER);

        // 4. Coins décoratifs (même style que les boutons)
        int arm = 5, thick = 1, off = 9, col = 0x553DDE6A;
        int lx = innerX + off,         rx = innerX + HDR_INNER_W - off;
        int top = innerY + off,        bot = innerY + innerH - off;
        gfx.fill(lx,         top,         lx + arm,   top + thick, col);
        gfx.fill(lx,         top,         lx + thick, top + arm,   col);
        gfx.fill(rx - arm,   top,         rx,         top + thick, col);
        gfx.fill(rx - thick, top,         rx,         top + arm,   col);
        gfx.fill(lx,         bot - thick, lx + arm,   bot,         col);
        gfx.fill(lx,         bot - arm,   lx + thick, bot,         col);
        gfx.fill(rx - arm,   bot - thick, rx,         bot,         col);
        gfx.fill(rx - thick, bot - arm,   rx,         bot,         col);
    }

    private void renderFooter(GuiGraphics gfx, int px, int py, int pw, int ph) {
        int fy      = py + ph - FOOTER_H;
        int textY   = fy + (FOOTER_H - font.lineHeight) / 2;
        int iconSize = 12;
        int iconY   = fy + (FOOTER_H - iconSize) / 2;

        // Fond footer
        gfx.fill(px, fy, px + pw, fy + FOOTER_H - 12, 0xFF141C26);
        for (int d = 0; d < 12; d++)
            gfx.fill(px + d, fy + FOOTER_H - 12 + d, px + pw - d, fy + FOOTER_H - 11 + d, 0xFF141C26);

        // Ligne de séparation
        gfx.fill(px, fy, px + pw, fy + 1, COLOR_BORDER);

        // Gauche : joueurs en ligne (logo légèrement remonté pour s'aligner sur le texte)
        int count = minecraft != null && minecraft.getConnection() != null
                  ? minecraft.getConnection().getOnlinePlayers().size() : 0;
        HenuIcons.drawPlayers(gfx, px + PADDING, iconY - 2, iconSize, COLOR_ACCENT);
        gfx.drawString(font, count + " JOUEURS EN LIGNE", px + PADDING + iconSize + 4, textY, COLOR_ACCENT, false);

        // Centre : → ROLYNK.FR ←
        String srv  = "→  ROLYNK.FR  ←";
        int    srvW = font.width(srv);
        gfx.drawString(font, srv, px + pw / 2 - srvW / 2, textY, COLOR_TEXT_DIM, false);

        // Droite : horloge + heure (vert, logo légèrement remonté pour s'aligner sur l'heure)
        String time  = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int    timeW = font.width(time);
        int    timeX = px + pw - PADDING - timeW;
        HenuIcons.drawClock(gfx, timeX - iconSize - 4, iconY - 2, iconSize, COLOR_ACCENT);
        gfx.drawString(font, time, timeX, textY, COLOR_ACCENT, false);

        // Redessine les bordures du panel dans la zone footer (côtés + coins bas + bas)
        gfx.fill(px,          fy, px + 1,      py + ph - 12, COLOR_BORDER);
        gfx.fill(px + pw - 1, fy, px + pw,     py + ph - 12, COLOR_BORDER);
        gfx.fill(px + 12, py + ph - 1, px + pw - 12, py + ph, COLOR_BORDER);
        for (int d = 0; d < 12; d++) {
            gfx.fill(px + d,          py + ph - 12 + d, px + d + 1,  py + ph - 11 + d, COLOR_BORDER);
            gfx.fill(px + pw - 1 - d, py + ph - 12 + d, px + pw - d, py + ph - 11 + d, COLOR_BORDER);
        }
    }

    /** Rendu optionnel propre à la fenêtre (appelé après les widgets). */
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) { }

    @Override public final void renderBackground(GuiGraphics gfx, int mx, int my, float pt) { }
    @Override public boolean isPauseScreen() { return false; }
}
