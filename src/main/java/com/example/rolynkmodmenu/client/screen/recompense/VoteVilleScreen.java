package com.example.rolynkmodmenu.client.screen.recompense;

import com.example.rolynkmodmenu.client.recompense.RecompensesDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.client.ville.VilleListDataManager;
import com.example.rolynkmodmenu.network.VilleListPayload;
import com.example.rolynkmodmenu.network.VilleListRequestPayload;
import com.example.rolynkmodmenu.network.VoteVilleActionPayload;
import com.example.rolynkmodmenu.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Fenêtre de vote pour une ville — MENU > RECOMPENSE > VOTE VILLE
 *
 * Layout (même structure que ListeVillesScreen) :
 * ┌──────────────┬───────────────────────────────────────────┐
 * │ Info ville   │  [ Rechercher... ]                        │
 * │ sélectionnée │  ─────────────────────────────────────────│
 * │              │  Ville A           ●2/18                  │
 * │ Nom: ...     │  Ville B  ← propre ville (grisée)         │
 * │ Membres: ... │  Ville C           ●1/8                   │
 * │              │                                           │
 * │  [ VOTER ]   │                             [scrollbar]   │
 * └──────────────┴───────────────────────────────────────────┘
 *
 * États du bouton VOTER :
 *   – Aucune sélection       → grisé
 *   – Propre ville           → rouge "Votre propre ville"
 *   – Déjà voté aujourd'hui  → vert "✔ Voté aujourd'hui"  (bouton non cliquable)
 *   – Disponible             → vert "VOTER" (cliquable)
 *
 * La propre ville du joueur est affichée en gris dans la liste mais toujours
 * visible (pour qu'il sache quelle ville est bloquée).
 */
public class VoteVilleScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int C_BORDER       = 0xFF1E2D3D;
    private static final int C_INFO_BG      = 0xFF141C26;
    private static final int C_ROW_SEL      = 0x442EA84E;
    private static final int C_ROW_HOV      = 0x221E2D3D;
    private static final int C_ROW_OWN      = 0x18FF4444;   // propre ville (rouge très léger)
    private static final int C_SCROLL       = 0xFF2EA84E;
    private static final int C_SCROLL_BG    = 0xFF0D1117;
    private static final int C_GOLD         = 0xFFFFDD44;
    private static final int C_GREY         = 0xFF607080;
    private static final int C_GREEN        = 0xFF3DD96A;
    private static final int C_GREEN_HOV    = 0xFF90FFB8;
    private static final int C_RED          = 0xFFFF5555;
    private static final int C_BTN_VOTE     = 0xFF0D1E35;
    private static final int C_BTN_VOTE_H   = 0xFF1A3A5C;
    private static final int C_BTN_DONE     = 0xFF0A2014;
    private static final int C_BTN_DISABLED = 0xFF1A1A1A;

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int SEP      = 8;
    private static final int ROW_H    = 22;
    private static final int SEARCH_H = 16;
    private static final int SCROLL_W = 7;
    private static final int BTN_H    = 18;

    // ── État ─────────────────────────────────────────────────────────────
    private EditBox searchBox;
    private int selectedIndex = -1;
    private int scrollOffset  = 0;

    /** Zone du bouton VOTER (calculée chaque frame pour le hit-test). */
    private int btnVoterX, btnVoterY, btnVoterW;

    /** Anti double-clic le temps que la réponse du serveur revienne. */
    private long lastVoteMs = 0;
    private static final long VOTE_DEBOUNCE_MS = 1_200L;

    // ── Rafraîchissement ──────────────────────────────────────────────────
    private static final int REFRESH_INTERVAL = 100; // ticks (5 s)
    private int refreshTick = 0;

    public VoteVilleScreen() {
        super("MENU", "RECOMPENSE", "VOTE VILLE");
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
        int px       = panelX();
        int contentY = panelY() + HEADER_H + PADDING;
        int contentH = panelH() - HEADER_H - PADDING * 2 - FOOTER_H;

        renderLeftPanel(gfx, px, contentY, contentH, mouseX, mouseY);
        renderRightPanel(gfx, px, contentY, contentH, mouseX, mouseY, partialTick);
    }

    private void renderLeftPanel(GuiGraphics gfx, int px, int contentY, int contentH,
                                  int mouseX, int mouseY) {
        int lx    = px + PADDING;
        int infoH = contentH - BTN_H - 10;

        // ── Boîte d'infos ─────────────────────────────────────────────────
        GuiUtils.fillChamferedRect(gfx, lx, contentY, leftW(), infoH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, lx, contentY, leftW(), infoH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, lx, contentY, leftW(), infoH, 6, 5, 1, 0x553DDE6A);

        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        VilleListPayload.VilleEntry sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;

        boolean aVote     = RecompensesDataManager.isVoteVilleEffectue();
        String maVille    = RecompensesDataManager.getMaVilleNom();
        boolean estOwnVille = sel != null && !maVille.isEmpty()
                && sel.nom().equalsIgnoreCase(maVille);

        if (sel == null) {
            int cx = lx + leftW() / 2;
            int cy = contentY + infoH / 2 - font.lineHeight;
            gfx.drawCenteredString(font, "Sélectionnez", cx, cy, 0xFF888888);
            gfx.drawCenteredString(font, "une ville", cx, cy + font.lineHeight + 2, 0xFF888888);
        } else {
            int tx = lx + 6, ty = contentY + 6;
            int lineH = font.lineHeight + 3;

            // Nom (rouge si propre ville)
            int nomColor = estOwnVille ? 0xFFFF9090 : 0xFFFFDD88;
            gfx.drawString(font, "§l" + sel.nom(), tx, ty, nomColor, false);
            ty += lineH + 2;
            gfx.fill(lx + 4, ty, lx + leftW() - 4, ty + 1, C_BORDER);
            ty += 5;

            gfx.drawString(font, "§a⬤ §f" + sel.nbConnectes() + " §7| §f" + sel.nbMembres() + " membres",
                    tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Owner : §f" + sel.ownerPseudo(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Banque : §f" + GuiUtils.formatBanque(sel.banque())
                    + " " + Money.SYMBOL, tx, ty, 0xFFFFFFFF, false);
            ty += lineH;
            gfx.drawString(font, "§7Chunks : §f" + sel.totalChunks(), tx, ty, 0xFFFFFFFF, false);
            ty += lineH;

            if (estOwnVille) {
                gfx.drawString(font, "§c⚑ Votre ville", tx, ty, 0xFFFFFFFF, false);
            }
        }

        // ── Bouton VOTER ──────────────────────────────────────────────────
        int btnY = contentY + infoH + 8;
        btnVoterX = lx;
        btnVoterY = btnY;
        btnVoterW = leftW();

        boolean canVote = !aVote && sel != null && !estOwnVille;
        boolean hov     = canVote
                && mouseX >= btnVoterX && mouseX < btnVoterX + btnVoterW
                && mouseY >= btnVoterY && mouseY < btnVoterY + BTN_H;

        int btnBg;
        int btnTextColor;
        String btnText;

        if (aVote) {
            btnBg       = C_BTN_DONE;
            btnTextColor = C_GREEN;
            btnText     = "§l✔ Voté aujourd'hui";
        } else if (estOwnVille) {
            btnBg       = C_BTN_DISABLED;
            btnTextColor = C_RED;
            btnText     = "Votre propre ville";
        } else if (sel != null) {
            btnBg       = hov ? C_BTN_VOTE_H : C_BTN_VOTE;
            btnTextColor = hov ? C_GREEN_HOV : C_GREEN;
            btnText     = hov ? "» VOTER «" : "VOTER";
        } else {
            btnBg       = C_BTN_DISABLED;
            btnTextColor = C_GREY;
            btnText     = "VOTER";
        }

        GuiUtils.fillChamferedRect(gfx, btnVoterX, btnVoterY, btnVoterW, BTN_H, 3, btnBg);
        GuiUtils.drawChamferedBorder(gfx, btnVoterX, btnVoterY, btnVoterW, BTN_H, 3, 1,
                canVote ? (hov ? 0xFF3DDE6A : 0x553DDE6A) : C_BORDER);
        gfx.drawCenteredString(font, btnText,
                btnVoterX + btnVoterW / 2,
                btnVoterY + (BTN_H - font.lineHeight + 1) / 2 + 1,
                btnTextColor);

        // Petite étiquette "récompense" sous le bouton (si pas encore voté)
        if (!aVote && sel != null && !estOwnVille) {
            double montant = RecompensesDataManager.getMontantVoteVille();
            String rewardStr = montant > 0
                    ? "§7Récompense : §e+" + Money.entier(montant)
                    : "§7Récompense : §e+20$";
            gfx.drawCenteredString(font, rewardStr,
                    btnVoterX + btnVoterW / 2, btnVoterY + BTN_H + 3, C_GREY);
        }
    }

    private void renderRightPanel(GuiGraphics gfx, int px, int contentY, int contentH,
                                   int mouseX, int mouseY, float partialTick) {
        int rightX    = px + PADDING + leftW() + SEP;
        int rightW    = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;
        String maVille = RecompensesDataManager.getMaVilleNom();

        // Barre de recherche
        GuiUtils.fillChamferedRect(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, contentY, listAreaW, SEARCH_H, 3, 1, C_BORDER);
        if (searchBox != null) searchBox.render(gfx, mouseX, mouseY, partialTick);

        int listY = contentY + SEARCH_H + 4;
        int listH = contentH - SEARCH_H - 4;

        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        int visibleRows = listH / ROW_H;
        int maxScroll   = Math.max(0, filtered.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Fond liste
        GuiUtils.fillChamferedRect(gfx, rightX, listY, listAreaW, listH, 3, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, rightX, listY, listAreaW, listH, 3, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, rightX, listY, listAreaW, listH, 6, 5, 1, 0x553DDE6A);

        if (filtered.isEmpty()) {
            String msg = VilleListDataManager.getVilles().isEmpty() ? "Chargement..." : "Aucun résultat";
            gfx.drawCenteredString(font, msg,
                    rightX + listAreaW / 2, listY + listH / 2 - font.lineHeight / 2, 0xFF888888);
        } else {
            for (int i = scrollOffset; i < filtered.size() && i < scrollOffset + visibleRows; i++) {
                VilleListPayload.VilleEntry v = filtered.get(i);
                int rowY = listY + (i - scrollOffset) * ROW_H;
                boolean isOwnVille = !maVille.isEmpty() && v.nom().equalsIgnoreCase(maVille);
                boolean hovered  = !isOwnVille && mouseX >= rightX && mouseX < rightX + listAreaW
                        && mouseY >= rowY && mouseY < rowY + ROW_H;
                boolean selected = (i == selectedIndex);

                if (isOwnVille) {
                    // Fond légèrement teinté rouge pour la propre ville
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, C_ROW_OWN);
                } else if (selected) {
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, C_ROW_SEL);
                } else if (hovered) {
                    gfx.fill(rightX + 1, rowY, rightX + listAreaW - 1, rowY + ROW_H, C_ROW_HOV);
                }

                // Nom
                int nameColor = isOwnVille ? 0xFF886666 : (selected ? 0xFFFFFF55 : 0xFFFFFFFF);
                String prefix = isOwnVille ? "§7" : (selected ? "§l" : "");
                gfx.drawString(font, prefix + v.nom(), rightX + 5,
                        rowY + (ROW_H - font.lineHeight) / 2, nameColor, false);

                // Tag "votre ville" ou compteur connectés/membres
                if (isOwnVille) {
                    String tag = "§c⚑ votre ville";
                    int tagW = font.width("⚑ votre ville") + 4;
                    gfx.drawString(font, tag, rightX + listAreaW - tagW - 4,
                            rowY + (ROW_H - font.lineHeight) / 2, 0xFFFF9090, false);
                } else {
                    String stat = "§a" + v.nbConnectes() + "§7/§f" + v.nbMembres();
                    int statW = font.width(stat.replaceAll("§.", "")) + 4;
                    gfx.drawString(font, stat, rightX + listAreaW - statW - 4,
                            rowY + (ROW_H - font.lineHeight) / 2, 0xFFFFFFFF, false);
                }

                if (i < filtered.size() - 1)
                    gfx.fill(rightX + 4, rowY + ROW_H - 1, rightX + listAreaW - 4, rowY + ROW_H, 0x22FFFFFF);
            }
        }

        // Scrollbar
        if (filtered.size() > visibleRows) {
            int sbX    = rightX + listAreaW + 2;
            int thumbH = Math.max(20, listH * visibleRows / filtered.size());
            int thumbY = listY + (listH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            gfx.fill(sbX, listY, sbX + SCROLL_W, listY + listH, C_SCROLL_BG);
            gfx.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, C_SCROLL);
        }
    }

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // Clic sur le bouton VOTER
        List<VilleListPayload.VilleEntry> filtered = getFiltered();
        VilleListPayload.VilleEntry sel = (selectedIndex >= 0 && selectedIndex < filtered.size())
                ? filtered.get(selectedIndex) : null;
        boolean aVote    = RecompensesDataManager.isVoteVilleEffectue();
        String maVille   = RecompensesDataManager.getMaVilleNom();
        boolean estOwn   = sel != null && !maVille.isEmpty() && sel.nom().equalsIgnoreCase(maVille);
        boolean canVote  = !aVote && sel != null && !estOwn;

        if (canVote && mx >= btnVoterX && mx < btnVoterX + btnVoterW
                && my >= btnVoterY && my < btnVoterY + BTN_H) {
            long now = System.currentTimeMillis();
            if (now - lastVoteMs > VOTE_DEBOUNCE_MS && minecraft.getConnection() != null) {
                lastVoteMs = now;
                PacketDistributor.sendToServer(new VoteVilleActionPayload(sel.id()));
            }
            return true;
        }

        // Clic dans la liste
        int px        = panelX(), py = panelY();
        int contentY  = py + HEADER_H + PADDING;
        int rightX    = px + PADDING + leftW() + SEP;
        int rightW    = panelW() - PADDING - leftW() - SEP - PADDING;
        int listAreaW = rightW - SCROLL_W - 2;
        int listY     = contentY + SEARCH_H + 4;
        int listH     = panelH() - HEADER_H - PADDING * 2 - FOOTER_H - SEARCH_H - 4;
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
        minecraft.setScreen(new RecompenseScreen());
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

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
