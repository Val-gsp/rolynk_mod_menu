package com.example.rolynkmodmenu.client.screen.recompense;

import com.example.rolynkmodmenu.client.recompense.RecompensesDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.ExplorationClaimPayload;
import com.example.rolynkmodmenu.network.RecompenseClaimPayload;
import com.example.rolynkmodmenu.network.RecompensesPayload;
import com.example.rolynkmodmenu.network.RecompensesRequestPayload;
import com.example.rolynkmodmenu.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Fenêtre Récompense — MENU > RECOMPENSE
 *
 * Grille 3×2 :
 *   [ 30 MINUTES ] [ 2 HEURES  ] [ 4 HEURES    ]  ← play time (fonctionnel)
 *   [ VOTE VILLE ] [ VOTE SRV  ] [ EXPLORATION  ]  ← vote ville + exploration (fonctionnel)
 *                                                      VOTE SERVEUR = À venir
 *
 * Cartes play time — 3 états :
 *   en cours / disponible (néon + RÉCUPÉRER) / récupéré (✔)
 *
 * Carte VOTE VILLE — 3 états :
 *   disponible (néon + VOTER ▶) / voté (✔ + nom ville) / chargement
 *
 * Carte EXPLORATION — 3 états :
 *   en cours (barre + "X / 20 000 blocs") / disponible (RÉCLAMER) / récupéré (✔)
 */
public class RecompenseScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int C_BORDER    = 0xFF1E2D3D;
    private static final int C_CARD_BG   = 0x800A1018;
    private static final int C_CARD_SEL  = 0xC0141C26;
    private static final int C_CARD_HOV  = 0xC01A2840;
    private static final int C_TITLE_DIM = 0xFF889988;
    private static final int C_TITLE_HOT = 0xFF3DD96A;
    private static final int C_GOLD      = 0xFFFFDD44;
    private static final int C_BOOSTER   = 0xFF55DDFF;  // cyan — récompense booster (cartes)
    private static final int C_GREY      = 0xFF607080;
    private static final int C_BAR_BG    = 0xFF0A1018;
    private static final int C_BAR_FILL  = 0xFF2EA84E;
    /** Couleur de la barre d'exploration (bleu cyan pour distinguer du play time). */
    private static final int C_BAR_EXPLO = 0xFF2EA8CC;

    private static final int GAP = 8;

    // ── État réseau ───────────────────────────────────────────────────────
    private long lastRequestMs     = 0;
    private long lastClaimMs       = 0;
    private long lastExploClaimMs  = 0;
    private static final long RETRY_MS          = 2_500L;
    private static final long REFRESH_MS        = 60_000L;
    private static final long CLAIM_DEBOUNCE_MS =   800L;

    public RecompenseScreen() { super("MENU", "RECOMPENSE"); }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() { requestEtat(); }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        long interval = RecompensesDataManager.isLoaded() ? REFRESH_MS : RETRY_MS;
        if (now - lastRequestMs > interval) requestEtat();
    }

    private void requestEtat() {
        if (minecraft.getConnection() != null) {
            lastRequestMs = System.currentTimeMillis();
            PacketDistributor.sendToServer(new RecompensesRequestPayload());
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    private int contentH() { return panelH() - HEADER_H - 2 * PADDING - FOOTER_H; }
    private int cardW()    { return (contentW() - 2 * GAP) / 3; }
    private int cardH()    { return (contentH() - GAP) / 2; }

    private int cardX(int col) { return contentX() + col * (cardW() + GAP); }
    private int cardY(int row) { return contentY() + row * (cardH() + GAP); }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        List<RecompensesPayload.PalierEntry> paliers = RecompensesDataManager.paliers();
        int temps = RecompensesDataManager.tempsActuelSecondes();

        // Ligne 1 — paliers play time
        for (int i = 0; i < 3; i++) {
            RecompensesPayload.PalierEntry p = i < paliers.size() ? paliers.get(i) : null;
            renderPlaytimeCard(gfx, mouseX, mouseY, cardX(i), cardY(0), i, p, temps);
        }

        // Ligne 2 — VOTE VILLE | VOTE SERVEUR (À venir) | EXPLORATION
        renderVoteVilleCard(gfx, mouseX, mouseY, cardX(0), cardY(1));
        renderAVenirCard(gfx, cardX(1), cardY(1), "VOTE SERVEUR", "Vote pour le serveur");
        renderExplorationCard(gfx, mouseX, mouseY, cardX(2), cardY(1));
    }

    // ── Carte play time ───────────────────────────────────────────────────

    private void renderPlaytimeCard(GuiGraphics gfx, int mouseX, int mouseY,
                                    int x, int y, int index,
                                    RecompensesPayload.PalierEntry p, int temps) {
        int w = cardW(), h = cardH();
        boolean loaded     = p != null;
        boolean recupere   = loaded && p.recupere();
        boolean disponible = loaded && !recupere && temps >= p.seuilSecondes();
        boolean hov        = disponible
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        int bg = recupere ? C_CARD_BG : (hov ? C_CARD_HOV : (disponible ? C_CARD_SEL : C_CARD_BG));
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 5, bg);
        if (disponible) {
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, w + 4, h + 4, 7, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 6, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, x, y, w, h, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 5, 1, C_BORDER);
        }
        GuiUtils.drawCornerBrackets(gfx, x, y, w, h, 5, 4, 1, disponible ? 0xCC3DDE6A : 0x553DDE6A);

        int cx = x + w / 2;
        int ty = y + 8;

        String[] titres = {"30 MINUTES", "2 HEURES", "4 HEURES"};
        int titleColor = recupere ? C_GREY : (disponible ? C_TITLE_HOT : C_TITLE_DIM);
        gfx.drawCenteredString(font, Component.literal(titres[index])
                .withStyle(ChatFormatting.BOLD), cx, ty, titleColor);
        ty += font.lineHeight + 2;
        gfx.drawCenteredString(font, "§7Temps de jeu", cx, ty, C_GREY);
        ty += font.lineHeight + 3;

        // Ligne 1 — récompense money
        String montantStr = loaded ? "+" + Money.entier(p.montant()) : "...";
        gfx.drawCenteredString(font, Component.literal(montantStr)
                .withStyle(ChatFormatting.BOLD), cx, ty, recupere ? C_GREY : C_GOLD);
        ty += font.lineHeight + 2;

        // Ligne 2 — récompense booster (cartes)
        gfx.drawCenteredString(font, boosterReward(index), cx, ty,
                recupere ? C_GREY : C_BOOSTER);
        ty += font.lineHeight + 4;

        gfx.fill(x + 8, ty, x + w - 8, ty + 1, disponible ? 0x553DDE6A : 0x221E2D3D);
        ty += 6;

        if (!loaded) {
            gfx.drawCenteredString(font, "§8Chargement...", cx, ty + 4, C_GREY);
        } else if (recupere) {
            gfx.drawCenteredString(font, "§a✔ Récupéré", cx, ty + 4, 0xFF55FF55);
        } else if (disponible) {
            gfx.drawCenteredString(font, Component.literal(
                    hov ? "» RÉCUPÉRER «" : "RÉCUPÉRER").withStyle(ChatFormatting.BOLD),
                    cx, ty + 4, hov ? 0xFF90FFB8 : C_TITLE_HOT);
        } else {
            int barX = x + 10, barW = w - 20, barH = 5;
            float ratio = Math.min(1f, temps / (float) p.seuilSecondes());
            gfx.fill(barX, ty, barX + barW, ty + barH, C_BAR_BG);
            gfx.fill(barX, ty, barX + (int) (barW * ratio), ty + barH, C_BAR_FILL);
            gfx.fill(barX, ty, barX + barW, ty + 1, 0x22FFFFFF);
            ty += barH + 4;
            gfx.drawCenteredString(font,
                    "§7" + formatDuree(temps) + " §8/ §7" + formatDuree(p.seuilSecondes()),
                    cx, ty, C_GREY);
        }
    }

    // ── Carte VOTE VILLE ─────────────────────────────────────────────────

    private void renderVoteVilleCard(GuiGraphics gfx, int mouseX, int mouseY, int x, int y) {
        int w = cardW(), h = cardH();
        boolean loaded = RecompensesDataManager.isLoaded();
        boolean aVote  = RecompensesDataManager.isVoteVilleEffectue();
        boolean hov    = !aVote && loaded
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        int bg = aVote ? C_CARD_BG : (hov ? C_CARD_HOV : (loaded ? C_CARD_SEL : C_CARD_BG));
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 5, bg);
        if (loaded && !aVote) {
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, w + 4, h + 4, 7, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 6, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, x, y, w, h, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 5, 1, aVote ? C_BORDER : 0xFF1E3028);
        }
        GuiUtils.drawCornerBrackets(gfx, x, y, w, h, 5, 4, 1,
                (loaded && !aVote) ? 0xCC3DDE6A : 0x553DDE6A);

        int cx = x + w / 2;
        int ty = y + 8;

        int titleColor = !loaded ? C_GREY : (aVote ? C_GREY : C_TITLE_HOT);
        gfx.drawCenteredString(font, Component.literal("VOTE VILLE")
                .withStyle(ChatFormatting.BOLD), cx, ty, titleColor);
        ty += font.lineHeight + 2;
        gfx.drawCenteredString(font, "§7Vote quotidien", cx, ty, C_GREY);
        ty += font.lineHeight + 3;

        double montant = RecompensesDataManager.getMontantVoteVille();
        String montantStr = montant > 0 ? "+" + Money.entier(montant) : "+...";
        gfx.drawCenteredString(font, Component.literal(montantStr)
                .withStyle(ChatFormatting.BOLD), cx, ty, aVote ? C_GREY : C_GOLD);
        ty += font.lineHeight + 5;

        gfx.fill(x + 8, ty, x + w - 8, ty + 1, (loaded && !aVote) ? 0x553DDE6A : 0x221E2D3D);
        ty += 6;

        if (!loaded) {
            gfx.drawCenteredString(font, "§8Chargement...", cx, ty + 4, C_GREY);
        } else if (aVote) {
            gfx.drawCenteredString(font, "§a✔ Voté", cx, ty, 0xFF55FF55);
            String villeVotee = RecompensesDataManager.getVilleVoteeNom();
            if (!villeVotee.isEmpty()) {
                ty += font.lineHeight + 3;
                gfx.drawCenteredString(font, "§7" + villeVotee, cx, ty, C_GREY);
            }
        } else {
            gfx.drawCenteredString(font, Component.literal(hov ? "» VOTER «" : "VOTER ▶")
                    .withStyle(ChatFormatting.BOLD), cx, ty + 4,
                    hov ? 0xFF90FFB8 : C_TITLE_HOT);
        }
    }

    // ── Carte EXPLORATION ─────────────────────────────────────────────────

    private void renderExplorationCard(GuiGraphics gfx, int mouseX, int mouseY, int x, int y) {
        int w = cardW(), h = cardH();
        boolean loaded     = RecompensesDataManager.isLoaded();
        boolean recue      = RecompensesDataManager.isExplorationRecue();
        int     blocs      = RecompensesDataManager.getBlocsParcourus();
        int     seuil      = RecompensesDataManager.getSeuilBlocs();
        boolean disponible = loaded && !recue && blocs >= seuil;
        boolean hov        = disponible
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        // Fond + bordure
        int bg = recue ? C_CARD_BG : (hov ? C_CARD_HOV : (disponible ? C_CARD_SEL : C_CARD_BG));
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 5, bg);
        if (disponible) {
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, w + 4, h + 4, 7, 1, 0x25CC802E);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 6, 1, 0x55CC802E);
            GuiUtils.drawNeonEdge(gfx, x, y, w, h, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 5, 1, C_BORDER);
        }
        GuiUtils.drawCornerBrackets(gfx, x, y, w, h, 5, 4, 1,
                disponible ? 0xCCDE8A3D : 0x55DE8A3D);

        int cx = x + w / 2;
        int ty = y + 8;

        // Titre (couleur or/cyan pour l'exploration)
        int titleColor = recue ? C_GREY : (disponible ? 0xFFDEB03D : C_TITLE_DIM);
        gfx.drawCenteredString(font, Component.literal("EXPLORATION")
                .withStyle(ChatFormatting.BOLD), cx, ty, titleColor);
        ty += font.lineHeight + 2;
        gfx.drawCenteredString(font, "§7Blocs parcourus", cx, ty, C_GREY);
        ty += font.lineHeight + 3;

        // Montant
        double montant = RecompensesDataManager.getMontantExploration();
        String montantStr = montant > 0 ? "+" + Money.entier(montant) : "+...";
        gfx.drawCenteredString(font, Component.literal(montantStr)
                .withStyle(ChatFormatting.BOLD), cx, ty, recue ? C_GREY : C_GOLD);
        ty += font.lineHeight + 5;

        // Séparateur
        gfx.fill(x + 8, ty, x + w - 8, ty + 1, disponible ? 0x55DE8A3D : 0x221E2D3D);
        ty += 6;

        // Zone d'état
        if (!loaded) {
            gfx.drawCenteredString(font, "§8Chargement...", cx, ty + 4, C_GREY);
        } else if (recue) {
            gfx.drawCenteredString(font, "§a✔ Récupéré", cx, ty + 4, 0xFF55FF55);
        } else if (disponible) {
            gfx.drawCenteredString(font, Component.literal(
                    hov ? "» RÉCLAMER «" : "RÉCLAMER").withStyle(ChatFormatting.BOLD),
                    cx, ty + 4, hov ? 0xFFFFCF80 : 0xFFDEB03D);
        } else {
            // Barre de progression avec couleur cyan
            int barX = x + 10, barW = w - 20, barH = 5;
            float ratio = seuil > 0 ? Math.min(1f, blocs / (float) seuil) : 0f;
            gfx.fill(barX, ty, barX + barW, ty + barH, C_BAR_BG);
            gfx.fill(barX, ty, barX + (int) (barW * ratio), ty + barH, C_BAR_EXPLO);
            gfx.fill(barX, ty, barX + barW, ty + 1, 0x22FFFFFF);
            ty += barH + 4;
            gfx.drawCenteredString(font,
                    "§7" + formatBlocs(blocs) + " §8/ §7" + formatBlocs(seuil) + " blocs",
                    cx, ty, C_GREY);
        }
    }

    // ── Carte À VENIR ─────────────────────────────────────────────────────

    private void renderAVenirCard(GuiGraphics gfx, int x, int y, String titre, String desc) {
        int w = cardW(), h = cardH();
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 5, 0x60080D14);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 5, 1, 0xFF15202C);

        int cx = x + w / 2;
        int ty = y + h / 2 - font.lineHeight - 8;
        gfx.drawCenteredString(font, Component.literal(titre)
                .withStyle(ChatFormatting.BOLD), cx, ty, 0xFF44525E);
        ty += font.lineHeight + 3;
        gfx.drawCenteredString(font, "§8" + desc, cx, ty, 0xFF44525E);
        ty += font.lineHeight + 6;
        gfx.drawCenteredString(font, "§8À VENIR", cx, ty, 0xFF333E48);
    }

    // ── Clics ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX, my = (int) mouseY;
            long now = System.currentTimeMillis();

            // ── Cartes play time ──────────────────────────────────────────
            List<RecompensesPayload.PalierEntry> paliers = RecompensesDataManager.paliers();
            int temps = RecompensesDataManager.tempsActuelSecondes();
            for (int i = 0; i < 3 && i < paliers.size(); i++) {
                RecompensesPayload.PalierEntry p = paliers.get(i);
                if (!p.recupere() && temps >= p.seuilSecondes()) {
                    int x = cardX(i), y = cardY(0);
                    if (mx >= x && mx < x + cardW() && my >= y && my < y + cardH()) {
                        if (now - lastClaimMs > CLAIM_DEBOUNCE_MS && minecraft.getConnection() != null) {
                            lastClaimMs = now;
                            PacketDistributor.sendToServer(new RecompenseClaimPayload(i));
                        }
                        return true;
                    }
                }
            }

            // ── Carte VOTE VILLE ──────────────────────────────────────────
            if (RecompensesDataManager.isLoaded() && !RecompensesDataManager.isVoteVilleEffectue()) {
                int vx = cardX(0), vy = cardY(1);
                if (mx >= vx && mx < vx + cardW() && my >= vy && my < vy + cardH()) {
                    minecraft.setScreen(new VoteVilleScreen());
                    return true;
                }
            }

            // ── Carte EXPLORATION ─────────────────────────────────────────
            if (RecompensesDataManager.isLoaded() && !RecompensesDataManager.isExplorationRecue()
                    && RecompensesDataManager.getBlocsParcourus() >= RecompensesDataManager.getSeuilBlocs()) {
                int ex = cardX(2), ey = cardY(1);
                if (mx >= ex && mx < ex + cardW() && my >= ey && my < ey + cardH()) {
                    if (now - lastExploClaimMs > CLAIM_DEBOUNCE_MS && minecraft.getConnection() != null) {
                        lastExploClaimMs = now;
                        PacketDistributor.sendToServer(new ExplorationClaimPayload());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() { minecraft.setScreen(new MainMenuScreen()); }

    // ── Utilitaires ───────────────────────────────────────────────────────

    /**
     * Libellé du booster récompensé pour chaque palier de temps de jeu.
     * Doit refléter exactement ce que distribue ServerRecompenseHandler.giveBoosters().
     *   30 min → 1 basique | 2 h → 1 amélioré + 1 basique | 4 h → 2 améliorés + 1 basique
     */
    private static String boosterReward(int index) {
        return switch (index) {
            case 0 -> "+1 booster basique";
            case 1 -> "+1 amélioré +1 basique";
            case 2 -> "+2 améliorés +1 basique";
            default -> "";
        };
    }

    /** 754 s → "12 min" ; 7530 s → "2h05". */
    private static String formatDuree(int secondes) {
        int h = secondes / 3600;
        int m = (secondes % 3600) / 60;
        return h > 0 ? h + "h" + String.format("%02d", m) : m + " min";
    }

    /** 12345 → "12 345" (séparateur de milliers par espace). */
    private static String formatBlocs(int blocs) {
        // On évite Locale pour rester constant quelle que soit la locale du client
        String s = Integer.toString(blocs);
        StringBuilder sb = new StringBuilder();
        int offset = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - offset) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
