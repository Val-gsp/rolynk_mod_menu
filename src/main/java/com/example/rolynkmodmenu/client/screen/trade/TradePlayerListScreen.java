package com.example.rolynkmodmenu.client.screen.trade;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.trade.TradeDataManager;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.TradeListRequestPayload;
import com.example.rolynkmodmenu.network.TradeRequestPayload;
import com.example.rolynkmodmenu.network.TradeRespondPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Fenêtre Trade — MENU > PROFIL > TRADE
 *
 * Deux sections :
 *   – demandes de trade REÇUES (ACCEPTER / REFUSER) ;
 *   – joueurs connectés au même serveur (DEMANDER).
 * La liste se rafraîchit toutes les 2 s. L'écran de trade s'ouvre tout seul
 * quand une demande est acceptée (TradeOpenPayload).
 */
public class TradePlayerListScreen extends BaseMenuScreen {

    private static final int C_BORDER  = 0xFF1E2D3D;
    private static final int C_ROW_BG  = 0xFF141C26;
    private static final int C_VALUE   = 0xFFEEF2F5;
    private static final int C_GREY    = 0xFF607080;
    private static final int C_GREEN   = 0xFF3DD96A;
    private static final int C_RED     = 0xFFFF5555;
    private static final int C_GOLD    = 0xFFFFDD44;

    private static final int ROW_H   = 22;
    private static final int ROW_GAP = 3;
    private static final int BTN_W   = 66;

    private final Screen previousScreen;
    private long lastRequestMs = 0;
    private long lastActionMs  = 0;
    private static final long REFRESH_MS = 2_000L;
    private static final long ACTION_DEBOUNCE_MS = 500L;

    private double scroll = 0;
    /** Zones cliquables recalculées à chaque rendu : {x, y, w, h, type(0=demander,1=accepter,2=refuser), index}. */
    private final List<int[]> clickZones = new ArrayList<>();

    public TradePlayerListScreen(Screen previous) {
        super("PROFIL", "TRADE");
        this.previousScreen = previous;
    }

    @Override
    protected void initContent() {
        TradeDataManager.setListe(List.of(), List.of());
        requestListe();
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() - lastRequestMs > REFRESH_MS) requestListe();
    }

    private void requestListe() {
        if (minecraft.getConnection() != null) {
            lastRequestMs = System.currentTimeMillis();
            PacketDistributor.sendToServer(new TradeListRequestPayload());
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    private int contentH() { return panelH() - HEADER_H - 2 * PADDING - FOOTER_H; }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        clickZones.clear();
        int cx = contentX(), cy = contentY(), cw = contentW(), ch = contentH();

        List<String> demandes = TradeDataManager.demandes();
        List<String> joueurs  = TradeDataManager.joueurs();

        // Nombre total de lignes virtuelles (headers + rows)
        int y = cy - (int) scroll;

        gfx.enableScissor(cx, cy, cx + cw, cy + ch);

        if (!demandes.isEmpty()) {
            gfx.drawString(font, Component.literal("DEMANDES REÇUES").withStyle(ChatFormatting.BOLD),
                    cx, y + 2, C_GOLD, false);
            y += 14;
            for (int i = 0; i < demandes.size(); i++) {
                renderDemandeRow(gfx, mouseX, mouseY, cx, y, cw, i, demandes.get(i));
                y += ROW_H + ROW_GAP;
            }
            y += 6;
        }

        gfx.drawString(font, Component.literal("JOUEURS SUR CE SERVEUR").withStyle(ChatFormatting.BOLD),
                cx, y + 2, C_GREEN, false);
        y += 14;

        if (joueurs.isEmpty()) {
            gfx.drawString(font, TradeDataManager.isListeLoaded()
                    ? "§8Aucun autre joueur connecté sur ce serveur."
                    : "§8Chargement...", cx, y + 4, C_GREY, false);
            y += ROW_H;
        } else {
            for (int i = 0; i < joueurs.size(); i++) {
                renderJoueurRow(gfx, mouseX, mouseY, cx, y, cw, i, joueurs.get(i));
                y += ROW_H + ROW_GAP;
            }
        }

        gfx.disableScissor();
        totalContentH = (y + (int) scroll) - cy;
    }

    private int totalContentH = 0;

    private int maxScroll() { return Math.max(0, totalContentH - contentH()); }

    private void renderDemandeRow(GuiGraphics gfx, int mouseX, int mouseY,
                                  int x, int y, int w, int index, String pseudo) {
        GuiUtils.fillChamferedRect(gfx, x, y, w, ROW_H, 3, C_ROW_BG);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, ROW_H, 3, 1, C_BORDER);
        gfx.fill(x + 2, y + 3, x + 3, y + ROW_H - 3, C_GOLD);

        int midY = y + (ROW_H - font.lineHeight) / 2 + 1;
        gfx.drawString(font, Component.literal(pseudo).withStyle(ChatFormatting.BOLD),
                x + 8, midY, C_VALUE, false);
        gfx.drawString(font, "§7veut échanger avec toi", x + 8 + font.width(pseudo) + 6, midY, C_GREY, false);

        int refX = x + w - BTN_W - 4;
        int accX = refX - BTN_W - 4;
        renderSmallBtn(gfx, mouseX, mouseY, accX, y + 2, BTN_W, ROW_H - 4, "ACCEPTER", C_GREEN);
        renderSmallBtn(gfx, mouseX, mouseY, refX, y + 2, BTN_W, ROW_H - 4, "REFUSER", C_RED);
        clickZones.add(new int[]{accX, y + 2, BTN_W, ROW_H - 4, 1, index});
        clickZones.add(new int[]{refX, y + 2, BTN_W, ROW_H - 4, 2, index});
    }

    private void renderJoueurRow(GuiGraphics gfx, int mouseX, int mouseY,
                                 int x, int y, int w, int index, String pseudo) {
        GuiUtils.fillChamferedRect(gfx, x, y, w, ROW_H, 3, C_ROW_BG);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, ROW_H, 3, 1, C_BORDER);
        gfx.fill(x + 2, y + 3, x + 3, y + ROW_H - 3, 0xFF2EA84E);

        int midY = y + (ROW_H - font.lineHeight) / 2 + 1;
        gfx.drawString(font, pseudo, x + 8, midY, C_VALUE, false);

        int btnX = x + w - BTN_W - 4;
        renderSmallBtn(gfx, mouseX, mouseY, btnX, y + 2, BTN_W, ROW_H - 4, "DEMANDER", C_GREEN);
        clickZones.add(new int[]{btnX, y + 2, BTN_W, ROW_H - 4, 0, index});
    }

    private void renderSmallBtn(GuiGraphics gfx, int mouseX, int mouseY,
                                int x, int y, int w, int h, String label, int color) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseY >= contentY() && mouseY < contentY() + contentH();
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 3, hov ? 0xC01A2840 : 0xA0141C26);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 3, 1, hov ? color : 0xFF1C2C3C);
        gfx.drawCenteredString(font, Component.literal(label).withStyle(ChatFormatting.BOLD),
                x + w / 2, y + (h - font.lineHeight) / 2 + 1, hov ? 0xFFFFFFFF : color);
    }

    // ── Clics & scroll ────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseY < contentY() || mouseY >= contentY() + contentH())
            return super.mouseClicked(mouseX, mouseY, button);

        long now = System.currentTimeMillis();
        for (int[] z : clickZones) {
            if (mouseX >= z[0] && mouseX < z[0] + z[2] && mouseY >= z[1] && mouseY < z[1] + z[3]) {
                if (now - lastActionMs < ACTION_DEBOUNCE_MS) return true;
                lastActionMs = now;
                switch (z[4]) {
                    case 0 -> {
                        List<String> joueurs = TradeDataManager.joueurs();
                        if (z[5] < joueurs.size())
                            PacketDistributor.sendToServer(new TradeRequestPayload(joueurs.get(z[5])));
                    }
                    case 1, 2 -> {
                        List<String> demandes = TradeDataManager.demandes();
                        if (z[5] < demandes.size())
                            PacketDistributor.sendToServer(
                                    new TradeRespondPayload(demandes.get(z[5]), z[4] == 1));
                        requestListe();
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dy * (ROW_H + ROW_GAP)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(previousScreen); }
}
