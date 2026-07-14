package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.grade.GradeCache;
import com.example.rolynkmodmenu.client.profile.ProfileDataManager;
import com.example.rolynkmodmenu.client.profile.ProfilRpDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.ProfileRequestPayload;
import com.example.rolynkmodmenu.network.ProfilRpRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ProfileScreen extends BaseMenuScreen {

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

    private static final int C_VILLE    = 0xFFFFAA00;  // or — couleur du nom de ville
    private static final int C_CRISTAUX = 0xFF55DDFF;  // cyan — couleur des cristaux

    private static final int BTN_H     = 24;
    private static final int INFO_GAP  = 8;

    /** Largeur du panneau skin — 18 % du panel, entre 60 et 90 px. */
    private int skinW() { return Math.min(90, Math.max(60, panelW() * 18 / 100)); }
    private static final int ROW_GAP   = 4;
    private static final int ROW_COUNT = 8;

    private final Screen previousScreen;

    /** false = Stats serveur, true = Profil RP. */
    private boolean vueRp = false;

    // Zones des boutons RETOUR, TRADE et bascule Stats/RP (calculées au rendu)
    private int retourBtnX, retourBtnY, retourBtnW;
    private int tradeBtnX, tradeBtnY, tradeBtnW;
    private int rpBtnX, rpBtnY, rpBtnW;

    public ProfileScreen(Screen previous) {
        super("MENU", "PROFIL");
        this.previousScreen = previous;
    }

    public ProfileScreen() {
        this(null);
    }

    @Override
    protected void initContent() {
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new ProfileRequestPayload());
            PacketDistributor.sendToServer(new ProfilRpRequestPayload());
        }
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
        int infoW = cw - skinW() - INFO_GAP;
        int infoX = cx + skinW() + INFO_GAP;

        // ── Panneau skin ──────────────────────────────────────────────────
        GuiUtils.fillChamferedRect(gfx, cx, cy, skinW(), topH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, cx, cy, skinW(), topH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, cx, cy, skinW(), topH, 6, 4, 1, 0x553DDE6A);

        if (minecraft.player != null) {
            int scale = topH / 3;
            gfx.enableScissor(cx + 1, cy + 1, cx + skinW() - 1, cy + topH - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gfx, cx, cy, cx + skinW(), cy + topH,
                    scale, 0.0f, mouseX, mouseY, minecraft.player);
            gfx.disableScissor();
        }

        // ── Lignes d'infos : Stats serveur ou Profil RP selon la vue ─────
        if (vueRp) renderProfilRp(gfx, infoX, cy, infoW, topH);
        else       renderStatsServeur(gfx, infoX, cy, infoW, topH);

        // ── Boutons RETOUR + TRADE + bascule Stats/RP ─────────────────────
        int btnY   = cy + topH + 8;
        int retourW = (cw - 16) * 26 / 100;
        int tradeW  = (cw - 16) * 32 / 100;
        int rpW     = cw - 16 - retourW - tradeW;
        retourBtnX = cx;                          retourBtnY = btnY; retourBtnW = retourW;
        tradeBtnX  = cx + retourW + 8;            tradeBtnY  = btnY; tradeBtnW  = tradeW;
        rpBtnX     = cx + retourW + tradeW + 16;  rpBtnY     = btnY; rpBtnW     = rpW;
        renderBtn(gfx, mouseX, mouseY, retourBtnX, btnY, retourW, BTN_H, "RETOUR");
        renderBtn(gfx, mouseX, mouseY, tradeBtnX, btnY, tradeW, BTN_H, "⇄ TRADE");
        renderBtn(gfx, mouseX, mouseY, rpBtnX, btnY, rpW, BTN_H,
                vueRp ? "⌂ STATS SERVEUR" : "☰ PROFIL RP");
    }

    /** Vue « Stats serveur » : statut, pseudo, grade, argent, cristaux, temps de jeu, UUID... */
    private void renderStatsServeur(GuiGraphics gfx, int infoX, int cy, int infoW, int topH) {
        // PSEUDO et STATUS : disponibles directement côté client.
        // Les autres champs viennent du serveur (DB) — affiche "Chargement..." en attendant.
        String localPseudo = (minecraft.player != null)
                ? minecraft.player.getGameProfile().getName() : null;
        String localUuid   = (minecraft.player != null)
                ? minecraft.player.getStringUUID() : null;
        String localStatus = (minecraft.getConnection() != null) ? "online" : "offline";

        String displayPseudo = or(ProfileDataManager.getPseudo(), localPseudo);
        String displayStatus = or(ProfileDataManager.getStatus(), localStatus);

        String[] labels = {"STATUS", "PSEUDO", "GRADE", "MONEY", "CRISTAUX",
                           "HEURES DE JEU", "CONNEXION", "UUID"};
        String[] values = {
                displayStatus,
                displayPseudo,
                gradeLabel(GradeCache.getMonGrade()),
                orLoading(formatMoney(ProfileDataManager.getMoney())),
                orLoading(formatCristaux(ProfileDataManager.getCristaux())),
                orLoading(ProfileDataManager.getHeuresDeJeu()),
                orLoading(ProfileDataManager.getPremiereConnexion()),
                or(ProfileDataManager.getUuid(), localUuid)
        };
        int[] valueColors = {
                statusColor(displayStatus),
                C_VALUE,
                gradeColor(GradeCache.getMonGrade()),
                C_VALUE, C_CRISTAUX, C_VALUE, C_VALUE, C_LABEL
        };

        int rows = labels.length;
        int rowH = (topH - (rows - 1) * ROW_GAP) / rows;
        for (int i = 0; i < rows; i++) {
            int ry = cy + i * (rowH + ROW_GAP);
            drawInfoRow(gfx, infoX, ry, infoW, rowH, labels[i], values[i], valueColors[i]);
        }
    }

    /** Vue « Profil RP » : les informations du personnage créées à la première connexion. */
    private void renderProfilRp(GuiGraphics gfx, int infoX, int cy, int infoW, int topH) {
        boolean existe = ProfilRpDataManager.isExiste();

        String[] labels = {"NOM", "PRÉNOM", "SEXE", "TAILLE",
                           "ANCIENNE VILLE", "NOUVELLE VILLE", "MÉTIER"};
        String[] values = existe
                ? new String[]{
                        ProfilRpDataManager.getNom(),
                        ProfilRpDataManager.getPrenom(),
                        ProfilRpDataManager.getSexe(),
                        ProfilRpDataManager.getTaille(),
                        ProfilRpDataManager.getAncienneVille(),
                        ProfilRpDataManager.getNouvelleVille(),
                        ProfilRpDataManager.getMetier()}
                : new String[]{"§7—", "§7—", "§7—", "§7—", "§7—", "§7—", "§7—"};

        // 7 lignes + un bloc description qui occupe le reste de la hauteur
        int rows  = labels.length;
        int descH = Math.max(28, topH * 22 / 100);
        int gridH = topH - descH - ROW_GAP;
        int rowH  = (gridH - (rows - 1) * ROW_GAP) / rows;

        for (int i = 0; i < rows; i++) {
            int ry = cy + i * (rowH + ROW_GAP);
            int color = (i == 5) ? C_VILLE : C_VALUE; // Nouvelle ville en doré
            drawInfoRow(gfx, infoX, ry, infoW, rowH, labels[i], values[i], color);
        }

        // Bloc description (texte multi-lignes)
        int dy = cy + gridH + ROW_GAP;
        GuiUtils.fillChamferedRect(gfx, infoX, dy, infoW, descH, 3, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, infoX, dy, infoW, descH, 3, 1, C_BORDER);
        gfx.fill(infoX + 2, dy + 3, infoX + 3, dy + descH - 3, 0xFF2EA84E);
        gfx.drawString(font, "DESCRIPTION", infoX + 8, dy + 4, C_LABEL, false);

        String desc = existe ? ProfilRpDataManager.getDescription()
                             : "§7Aucun profil RP — reconnecte-toi pour le créer.";
        int lineY = dy + 4 + font.lineHeight + 2;
        for (var line : font.split(net.minecraft.network.chat.Component.literal(desc), infoW - 16)) {
            if (lineY + font.lineHeight > dy + descH - 3) break;
            gfx.drawString(font, line, infoX + 8, lineY, C_VALUE, false);
            lineY += font.lineHeight + 1;
        }
    }

    /** Ligne d'info label/valeur — style commun aux deux vues. */
    private void drawInfoRow(GuiGraphics gfx, int x, int y, int w, int h,
                             String label, String value, int valueColor) {
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 3, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 3, 1, C_BORDER);
        gfx.fill(x + 2, y + 3, x + 3, y + h - 3, 0xFF2EA84E);

        int midY = y + (h - font.lineHeight + 1) / 2 + 1;
        gfx.drawString(font, label, x + 8, midY, C_LABEL, false);
        String shown = value;
        int maxW = w - font.width(label) - 22;
        while (font.width(shown) > maxW && shown.length() > 4)
            shown = shown.substring(0, shown.length() - 2) + "…";
        int vw = font.width(shown);
        gfx.drawString(font, shown, x + w - vw - 6, midY, valueColor, false);
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
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, bh, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, bh, 4, 1, 0xFF1C2C3C);
        }
        drawCornerBrackets(gfx, bx, by, bw, bh, hov);
        Component title = Component.literal(label).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, title, bx + bw / 2,
                by + (bh - font.lineHeight) / 2 + 1, hov ? 0xFF3DD96A : 0xFFEEF2F5);
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
        if (mx >= tradeBtnX && mx < tradeBtnX + tradeBtnW
                && my >= tradeBtnY && my < tradeBtnY + BTN_H) {
            minecraft.setScreen(new com.example.rolynkmodmenu.client.screen.trade.TradePlayerListScreen(this));
            return true;
        }
        if (mx >= rpBtnX && mx < rpBtnX + rpBtnW
                && my >= rpBtnY && my < rpBtnY + BTN_H) {
            vueRp = !vueRp;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Retourne {@code server} si disponible, sinon {@code fallback}, sinon "...". */
    private static String or(String server, String fallback) {
        if (server != null && !server.isEmpty()) return server;
        if (fallback != null && !fallback.isEmpty()) return fallback;
        return "...";
    }

    /** Pour les champs exclusivement serveur : affiche "Chargement..." si absent. */
    private static String orLoading(String v) {
        return (v != null && !v.isEmpty()) ? v : "§7Chargement...";
    }

    private static String or(String v) {
        return (v != null && !v.isEmpty()) ? v : "...";
    }

    /**
     * Formate un montant brut ("1500.00") en "1 500.00 $".
     * Retourne null si la valeur est absente, laissant orLoading() prendre le relais.
     */
    private static String formatMoney(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            double val = Double.parseDouble(raw);
            // Séparateur de milliers avec espace insécable, 2 décimales
            long intPart  = (long) val;
            long decPart  = Math.round((val - intPart) * 100);
            String intFmt = String.format("%,d", intPart).replace(',', ' ');
            return intFmt + "." + String.format("%02d", decPart)
                    + " " + com.example.rolynkmodmenu.util.Money.SYMBOL;
        } catch (NumberFormatException e) {
            return raw; // Déjà formaté ou valeur inattendue — affiche tel quel
        }
    }

    /**
     * Formate un nombre brut de cristaux ("1500") en "1 500 ✦".
     * Retourne null si la valeur est absente, laissant orLoading() prendre le relais.
     */
    private static String formatCristaux(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            long val = Long.parseLong(raw.trim());
            return String.format("%,d", val).replace(',', ' ') + " ✦";
        } catch (NumberFormatException e) {
            return raw; // Valeur inattendue — affiche tel quel
        }
    }

    private static int statusColor(String s) {
        if (s == null) return C_VALUE;
        return "online".equalsIgnoreCase(s) ? C_ONLINE : C_OFFLINE;
    }

    private static String gradeLabel(String g) {
        if (g == null) return "...";
        return switch (g) {
            case "staff"   -> "Staff";
            case "helper"  -> "Helper";
            case "default" -> "Citoyen";
            default        -> g;
        };
    }

    private static int gradeColor(String g) {
        if (g == null) return C_VALUE;
        return switch (g) {
            case "staff"   -> C_GRADE_STF;
            case "helper"  -> C_GRADE_HLP;
            default        -> C_GRADE_DEF;
        };
    }
}
