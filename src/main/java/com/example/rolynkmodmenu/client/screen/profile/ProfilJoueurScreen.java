package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.grade.GradeCache;
import com.example.rolynkmodmenu.client.profile.ProfilJoueurDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.ProfilJoueurRequestPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class ProfilJoueurScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int C_INFO_BG   = 0xFF141C26;
    private static final int C_BORDER    = 0xFF1E2D3D;
    private static final int C_LABEL     = 0xFF607080;
    private static final int C_VALUE     = 0xFFEEF2F5;
    private static final int C_ONLINE    = 0xFF55FF55;
    private static final int C_OFFLINE   = 0xFFFF5555;
    private static final int C_GRADE_DEF = 0xFF55FF55;
    private static final int C_GRADE_HLP = 0xFFFF9933;
    private static final int C_GRADE_STF = 0xFFFF5555;
    private static final int C_CRISTAUX  = 0xFF55DDFF;  // cyan — couleur des cristaux

    private static final int BTN_H     = 24;
    private static final int SKIN_W    = 90;
    private static final int INFO_GAP  = 8;
    private static final int ROW_GAP   = 4;
    private static final int ROW_COUNT = 7;

    private final String targetUuid;
    private final Screen previousScreen;

    private RemotePlayer fakePlayer = null;

    // Zone du bouton RETOUR (calculée au rendu)
    private int retourBtnX, retourBtnY, retourBtnW;

    public ProfilJoueurScreen(Screen previous, String targetUuid) {
        super("MENU", "PROFIL JOUEUR");
        this.previousScreen = previous;
        this.targetUuid     = targetUuid;
    }

    @Override
    protected void initContent() {
        fakePlayer = null;
        ProfilJoueurDataManager.clear();
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new ProfilJoueurRequestPayload(targetUuid));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int px = panelX(), py = panelY();
        int cx = px + PADDING;
        int cy = py + HEADER_H + PADDING;
        int cw = panelW() - PADDING * 2;
        int ch = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;

        int topH  = ch - BTN_H - 8;
        int infoW = cw - SKIN_W - INFO_GAP;
        int infoX = cx + SKIN_W + INFO_GAP;

        // ── Panneau skin ──────────────────────────────────────────────────
        GuiUtils.fillChamferedRect(gfx, cx, cy, SKIN_W, topH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, cx, cy, SKIN_W, topH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, cx, cy, SKIN_W, topH, 6, 4, 1, 0x553DDE6A);
        renderSkin(gfx, cx, cy, topH, mouseX, mouseY);

        // ── Lignes d'infos ────────────────────────────────────────────────
        String[] labels = {"STATUS", "PSEUDO", "GRADE", "MONEY", "CRISTAUX", "HEURES DE JEU", "CONNEXION"};
        String[] values = {
                or(ProfilJoueurDataManager.getStatus()),
                or(ProfilJoueurDataManager.getPseudo()),
                gradeLabel(GradeCache.getGrade(ProfilJoueurDataManager.getPseudo())),
                or(ProfilJoueurDataManager.getMoney()),
                or(formatCristaux(ProfilJoueurDataManager.getCristaux())),
                or(ProfilJoueurDataManager.getHeuresDeJeu()),
                or(ProfilJoueurDataManager.getPremiereConnexion())
        };
        int[] valueColors = {
                statusColor(ProfilJoueurDataManager.getStatus()),
                C_VALUE,
                gradeColor(GradeCache.getGrade(ProfilJoueurDataManager.getPseudo())),
                C_VALUE, C_CRISTAUX, C_VALUE, C_VALUE
        };

        int rowH = (topH - (ROW_COUNT - 1) * ROW_GAP) / ROW_COUNT;
        for (int i = 0; i < ROW_COUNT; i++) {
            int ry = cy + i * (rowH + ROW_GAP);
            GuiUtils.fillChamferedRect(gfx, infoX, ry, infoW, rowH, 3, C_INFO_BG);
            GuiUtils.drawChamferedBorder(gfx, infoX, ry, infoW, rowH, 3, 1, C_BORDER);

            // Accent vert sur le bord gauche de chaque ligne
            gfx.fill(infoX + 2, ry + 3, infoX + 3, ry + rowH - 3, 0xFF2EA84E);

            int midY = ry + (rowH - font.lineHeight + 1) / 2 + 1;
            gfx.drawString(font, labels[i], infoX + 8, midY, C_LABEL, false);
            int vw = font.width(values[i]);
            gfx.drawString(font, values[i], infoX + infoW - vw - 6, midY, valueColors[i], false);
        }

        // ── Bouton RETOUR ─────────────────────────────────────────────────
        int btnY = cy + topH + 8;
        retourBtnX = cx; retourBtnY = btnY; retourBtnW = cw;
        renderBtn(gfx, mouseX, mouseY, cx, btnY, cw, BTN_H, "RETOUR");
    }

    // ── Rendu du skin 3D ──────────────────────────────────────────────────

    private void renderSkin(GuiGraphics gfx, int sx, int sy, int topH, int mouseX, int mouseY) {
        try {
            UUID uuid = UUID.fromString(targetUuid);
            if (minecraft.level != null) {
                var entity = minecraft.level.getPlayerByUUID(uuid);
                if (entity != null) {
                    int scale = topH / 3;
                    gfx.enableScissor(sx + 1, sy + 1, sx + SKIN_W - 1, sy + topH - 1);
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            gfx, sx, sy, sx + SKIN_W, sy + topH,
                            scale, 0.0f, mouseX, mouseY, entity);
                    gfx.disableScissor();
                    return;
                }
            }
        } catch (IllegalArgumentException ignored) {}

        String pseudo = ProfilJoueurDataManager.getPseudo();
        if (pseudo.isBlank()) {
            gfx.drawCenteredString(font, "...",
                    sx + SKIN_W / 2, sy + topH / 2 - font.lineHeight / 2, 0xFF666666);
            return;
        }

        if (fakePlayer == null && minecraft.level instanceof ClientLevel cl) {
            try {
                GameProfile gp = new GameProfile(UUID.fromString(targetUuid), pseudo);
                fakePlayer = new RemotePlayer(cl, gp);
                fakePlayer.setCustomName(Component.empty());
                fakePlayer.setCustomNameVisible(false);
                fakePlayer.setNoGravity(true);
            } catch (Exception ignored) {}
        }

        if (fakePlayer != null) {
            int scale = topH / 3;
            gfx.enableScissor(sx + 1, sy + 1, sx + SKIN_W - 1, sy + topH - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gfx, sx, sy, sx + SKIN_W, sy + topH,
                    scale, 0.0f, mouseX, mouseY, fakePlayer);
            gfx.disableScissor();
        }
    }

    // ── Bouton neon ───────────────────────────────────────────────────────

    private void renderBtn(GuiGraphics gfx, int mouseX, int mouseY,
                           int bx, int by, int bw, int bh, String label) {
        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        GuiUtils.fillChamferedRect(gfx, bx, by, bw, bh, 4, hov ? 0xC01A2840 : 0xA0141C26);
        if (hov) {
            GuiUtils.drawChamferedBorder(gfx, bx - 3, by - 3, bw + 6, bh + 6, 7, 1, 0x0D2ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, 6, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, 5, 1, 0x552ECC60);
            drawNeonEdge(gfx, bx, by, bw, bh);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, 0xFF1C2C3C);
        }
        drawCornerBrackets(gfx, bx, by, bw, bh, hov);
        Component title = Component.literal(label).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, title, bx + bw / 2,
                by + (bh - font.lineHeight) / 2 + 1, hov ? 0xFF3DD96A : 0xFFEEF2F5);
    }

    private void drawNeonEdge(GuiGraphics gfx, int x, int y, int w, int h) {
        int chamfer = 4;
        float half  = w / 2f;
        int dark = 0xFF0C2A18, mid = 0xFF2EA84E, bright = 0xFF3DDE6A, core = 0xFF90FFB8;
        float tEH = 1f - (float) Math.pow(Math.abs((chamfer + 0.5) / half      - 1.0), 2);
        float tEV = 1f - (float) Math.pow(Math.abs((chamfer + 0.5) / (h / 2.0) - 1.0), 2);
        float tEdge = Math.min(tEH, tEV);
        for (int t = 0; t < 2; t++) {
            int ox = x+t, oy = y+t, ow = w-2*t, oh = h-2*t, oc = Math.max(0, chamfer-t);
            if (oc == 0) break;
            int cc = neonCol(tEdge, dark, mid, bright, core);
            for (int d = 0; d < oc; d++) {
                gfx.fill(ox+oc-1-d,  oy+d,        ox+oc-d,      oy+d+1,       cc);
                gfx.fill(ox+ow-oc+d, oy+d,         ox+ow-oc+d+1, oy+d+1,       cc);
                gfx.fill(ox+d,       oy+oh-oc+d,   ox+d+1,       oy+oh-oc+d+1, cc);
                gfx.fill(ox+ow-1-d,  oy+oh-oc+d,   ox+ow-d,      oy+oh-oc+d+1, cc);
            }
        }
        for (int j = chamfer; j < w - chamfer; j++) {
            float t  = 1f - (float) Math.pow(Math.abs(j - half) / half, 2);
            int   lc = neonCol(t, dark, mid, bright, core);
            gfx.fill(x+j, y,       x+j+1, y+2,     lc);
            gfx.fill(x+j, y+h-2,   x+j+1, y+h,     lc);
            int ha  = (int)(t * 140); if (ha  > 6) { int hc  = (ha  << 24) | 0x003DDE6A; gfx.fill(x+j, y-1,     x+j+1, y,     hc);  gfx.fill(x+j, y+h,   x+j+1, y+h+1, hc);  }
            int ha2 = (int)(t * 60);  if (ha2 > 4) { int hc2 = (ha2 << 24) | 0x003DDE6A; gfx.fill(x+j, y-2,     x+j+1, y-1,   hc2); gfx.fill(x+j, y+h+1, x+j+1, y+h+2, hc2); }
        }
        int sc = neonCol(tEdge, dark, mid, bright, core), sh = (int)(tEdge*140), sh2 = (int)(tEdge*60);
        for (int i = chamfer; i < h - chamfer; i++) {
            gfx.fill(x,     y+i, x+2,   y+i+1, sc);
            gfx.fill(x+w-2, y+i, x+w,   y+i+1, sc);
            if (sh  > 6) { int hc  = (sh  << 24) | 0x003DDE6A; gfx.fill(x-1,   y+i, x,     y+i+1, hc);  gfx.fill(x+w,   y+i, x+w+1, y+i+1, hc);  }
            if (sh2 > 4) { int hc2 = (sh2 << 24) | 0x003DDE6A; gfx.fill(x-2,   y+i, x-1,   y+i+1, hc2); gfx.fill(x+w+1, y+i, x+w+2, y+i+1, hc2); }
        }
    }

    private int neonCol(float t, int dark, int mid, int bright, int core) {
        if (t > 0.85f) return GuiUtils.lerpColor(bright, core,   (t - 0.85f) / 0.15f);
        if (t > 0.40f) return GuiUtils.lerpColor(mid,    bright, (t - 0.40f) / 0.45f);
        return GuiUtils.lerpColor(dark, mid, t / 0.4f);
    }

    private void drawCornerBrackets(GuiGraphics gfx, int x, int y, int w, int h, boolean hov) {
        int arm = 4, thick = 1, off = 5;
        int cn = 0x553DDE6A, ch = 0xCC3DDE6A;
        int lx = x+off, rx = x+w-off, ty = y+off, by = y+h-off;
        gfx.fill(lx,       ty,       lx+arm,   ty+thick, cn); gfx.fill(lx,       ty,      lx+thick, ty+arm,   cn);
        gfx.fill(rx-arm,   ty,       rx,        ty+thick, cn); gfx.fill(rx-thick, ty,      rx,        ty+arm,   cn);
        if (hov) {
            gfx.fill(lx,       ty,       lx+arm,   ty+thick, ch); gfx.fill(lx,       ty,      lx+thick, ty+arm,   ch);
            gfx.fill(rx-arm,   ty,       rx,        ty+thick, ch); gfx.fill(rx-thick, ty,      rx,        ty+arm,   ch);
            gfx.fill(lx,       by-thick, lx+arm,   by,        ch); gfx.fill(lx,       by-arm,  lx+thick, by,        ch);
            gfx.fill(rx-arm,   by-thick, rx,        by,        ch); gfx.fill(rx-thick, by-arm,  rx,        by,        ch);
        }
    }

    // ── Clics ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;
        if (mx >= retourBtnX && mx < retourBtnX + retourBtnW
                && my >= retourBtnY && my < retourBtnY + BTN_H) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String or(String v) {
        return (v != null && !v.isEmpty()) ? v : "...";
    }

    /** Formate un nombre brut de cristaux ("1500") en "1 500 ✦". null/vide → null (or() prend le relais). */
    private static String formatCristaux(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            long val = Long.parseLong(raw.trim());
            return String.format("%,d", val).replace(',', ' ') + " ✦";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static int statusColor(String s) {
        if (s == null || s.isEmpty()) return C_VALUE;
        return "online".equalsIgnoreCase(s) ? C_ONLINE : C_OFFLINE;
    }

    private static String gradeLabel(String g) {
        if (g == null || g.isEmpty()) return "...";
        return switch (g) {
            case "staff"   -> "Staff";
            case "helper"  -> "Helper";
            case "default" -> "Citoyen";
            default        -> g;
        };
    }

    private static int gradeColor(String g) {
        if (g == null || g.isEmpty()) return C_VALUE;
        return switch (g) {
            case "staff"   -> C_GRADE_STF;
            case "helper"  -> C_GRADE_HLP;
            default        -> C_GRADE_DEF;
        };
    }
}
