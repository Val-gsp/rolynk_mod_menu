package com.example.rolynkmodmenu.client.screen.trade;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.trade.TradeDataManager;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.TradeActionPayload;
import com.example.rolynkmodmenu.network.TradeStatePayload;
import com.example.rolynkmodmenu.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Écran de trade — s'ouvre automatiquement quand une demande est acceptée.
 *
 * Design : deux panneaux d'offre (moi / lui) avec badge de confirmation,
 * panneau inventaire, colonne d'actions. Cliquer un objet de l'inventaire
 * ouvre un SÉLECTEUR DE QUANTITÉ (1 / 10 / ½ / MAX / saisie libre) ;
 * re-cliquer un objet déjà offert ajuste la quantité ; cliquer son offre
 * retire l'entrée.
 *
 * Le serveur est seul maître de l'état (TradeStatePayload après chaque
 * action). Toute modification d'offre réinitialise les deux confirmations.
 */
public class TradeScreen extends BaseMenuScreen {

    // ── Couleurs (charte Rolynk) ──────────────────────────────────────────
    private static final int C_BORDER   = 0xFF1E2D3D;
    private static final int C_PANEL    = 0xFF141C26;
    private static final int C_SLOT     = 0xFF0A1018;
    private static final int C_SLOT_BRD = 0xFF16222E;
    private static final int C_GREY     = 0xFF607080;
    private static final int C_GREEN    = 0xFF3DD96A;
    private static final int C_GREEN_D  = 0xFF2EA84E;
    private static final int C_RED      = 0xFFFF5555;
    private static final int C_GOLD     = 0xFFFFDD44;
    private static final int C_VALUE    = 0xFFEEF2F5;
    private static final int C_HOV_BG   = 0xC01A2840;

    private static final int SLOT = 22;      // slot d'offre
    private static final int INV_SLOT = 18;  // slot d'inventaire

    private final String partenaire;
    private EditBox argentBox;
    private EditBox qtyBox;
    private boolean closedByServer = false;
    private long lastActionMs = 0;
    private static final long ACTION_DEBOUNCE_MS = 200L;

    /** Slot d'inventaire en cours de sélection de quantité (-1 = sélecteur fermé). */
    private int pickerSlot = -1;

    public TradeScreen(String partenaire) {
        super("TRADE", partenaire.toUpperCase());
        this.partenaire = partenaire;
    }

    public void closeFromServer() {
        closedByServer = true;
        minecraft.setScreen(null);
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int cx() { return panelX() + PADDING; }
    private int cy() { return panelY() + HEADER_H + PADDING; }
    private int cw() { return panelW() - 2 * PADDING; }

    private int colW()      { return (cw() - 8) / 2; }
    private int offresH()   { return 76; }
    private int invPanelY() { return cy() + offresH() + 6; }
    private int invPanelW() { return 9 * INV_SLOT + 16; }
    private int invPanelH() { return 4 * INV_SLOT + 3 + 26; }
    private int invGridX()  { return cx() + 8; }
    private int invGridY()  { return invPanelY() + 18; }

    // Sélecteur de quantité
    private int pickW() { return 220; }
    private int pickH() { return 92; }
    private int pickX() { return panelX() + (panelW() - pickW()) / 2; }
    private int pickY() { return panelY() + (panelH() - pickH()) / 2; }

    @Override
    protected void initContent() {
        int colG = cx();
        argentBox = new EditBox(font, colG + 52, cy() + 45, 58, 12, Component.literal("argent"));
        argentBox.setFilter(s -> s.matches("[0-9]{0,7}(\\.[0-9]{0,2})?"));
        argentBox.setMaxLength(10);
        argentBox.setBordered(false);
        argentBox.setTextColor(0xFFEEF2F5);
        addWidget(argentBox);   // événements uniquement — rendu manuel (z-order)

        qtyBox = new EditBox(font, 0, 0, 44, 12, Component.literal("quantite"));
        qtyBox.setFilter(s -> s.matches("[0-9]{0,4}"));
        qtyBox.setMaxLength(4);
        qtyBox.setBordered(false);
        qtyBox.setTextColor(0xFFEEF2F5);
        qtyBox.setVisible(false);
        addWidget(qtyBox);
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        TradeStatePayload st = TradeDataManager.state();
        boolean maConf = st != null && st.maConfirmation();
        boolean saConf = st != null && st.saConfirmation();
        List<ItemStack> monOffre = st == null ? List.of() : st.monOffre();
        List<ItemStack> sonOffre = st == null ? List.of() : st.sonOffre();

        int colG = cx(), colD = cx() + colW() + 8;

        double monSolde = st == null ? -1 : st.monSolde();
        renderOffrePanel(gfx, mouseX, mouseY, colG, "TON OFFRE", maConf, monOffre,
                st == null ? 0 : st.monArgent(), monSolde, true);
        renderOffrePanel(gfx, mouseX, mouseY, colD, partenaire.toUpperCase(), saConf, sonOffre,
                st == null ? 0 : st.sonArgent(), -1, false);

        renderInventairePanel(gfx, mouseX, mouseY);
        renderActions(gfx, mouseX, mouseY, maConf, saConf, st != null);

        // Tooltips (au-dessus des panneaux, sous le sélecteur)
        if (pickerSlot < 0) renderTooltips(gfx, mouseX, mouseY, monOffre, sonOffre);

        // Sélecteur de quantité par-dessus tout
        if (pickerSlot >= 0) renderPicker(gfx, mouseX, mouseY);
    }

    // ── Panneau d'offre ───────────────────────────────────────────────────

    private void renderOffrePanel(GuiGraphics gfx, int mouseX, int mouseY, int x,
                                  String titre, boolean conf, List<ItemStack> offre,
                                  double argent, double solde, boolean mienne) {
        int y = cy(), w = colW(), h = offresH();

        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 4, C_PANEL);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 4, 1, conf ? C_GREEN_D : C_BORDER);
        gfx.fill(x + 2, y + 4, x + 3, y + h - 4, conf ? C_GREEN : 0x552EA84E);

        // Header : titre + badge de confirmation
        gfx.drawString(font, Component.literal(titre).withStyle(ChatFormatting.BOLD),
                x + 9, y + 6, C_VALUE, false);
        String badge = conf ? "✔ PRÊT" : "EN ATTENTE";
        int bw = font.width(badge) + 8;
        int bx = x + w - bw - 6;
        GuiUtils.fillChamferedRect(gfx, bx, y + 4, bw, 11, 2, conf ? 0x332EA84E : 0x22FFFFFF);
        gfx.drawString(font, badge, bx + 4, y + 6, conf ? C_GREEN : C_GREY, false);

        // Slots d'offre
        int sy = y + 19;
        for (int i = 0; i < 8; i++) {
            int sx = x + 8 + i * (SLOT + 2);
            boolean filled = i < offre.size();
            boolean hov = mienne && filled && pickerSlot < 0
                    && mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
            GuiUtils.fillChamferedRect(gfx, sx, sy, SLOT, SLOT, 2, hov ? 0x40FF5555 : C_SLOT);
            GuiUtils.drawChamferedBorder(gfx, sx, sy, SLOT, SLOT, 2, 1,
                    hov ? C_RED : (filled ? C_GREEN_D : C_SLOT_BRD));
            if (filled) {
                ItemStack stk = offre.get(i);
                gfx.renderItem(stk, sx + 3, sy + 3);
                gfx.renderItemDecorations(font, stk, sx + 3, sy + 3);
            }
        }

        // Ligne argent
        int ay = y + 46;
        if (mienne) {
            gfx.drawString(font, "§7ARGENT", x + 9, ay, C_GREY, false);
            // fond du champ de saisie
            GuiUtils.fillChamferedRect(gfx, x + 49, ay - 3, 64, 13, 2, C_SLOT);
            GuiUtils.drawChamferedBorder(gfx, x + 49, ay - 3, 64, 13, 2, 1,
                    argentBox.isFocused() ? C_GREEN_D : C_SLOT_BRD);
            argentBox.setX(x + 52);
            argentBox.setY(ay - 1);
            argentBox.render(gfx, mouseX, mouseY, 0);
            renderMiniBtn(gfx, mouseX, mouseY, x + 117, ay - 3, 24, 13, "OK");
            gfx.drawString(font, Component.literal("§e" + Money.exact(argent))
                    .withStyle(ChatFormatting.BOLD), x + 147, ay, C_GOLD, false);
            // Solde en banque (info) — sous la ligne argent
            String soldeStr = solde < 0 ? "§8Ton solde : §7..."
                    : "§8Ton solde : §7" + Money.exact(solde);
            gfx.drawString(font, soldeStr, x + 9, ay + 14, C_GREY, false);
        } else {
            gfx.drawString(font, "§7ARGENT", x + 9, ay, C_GREY, false);
            gfx.drawString(font, Component.literal("§e" + Money.exact(argent))
                    .withStyle(ChatFormatting.BOLD), x + 52, ay, C_GOLD, false);
        }
    }

    // ── Panneau inventaire ────────────────────────────────────────────────

    private void renderInventairePanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int x = cx(), y = invPanelY(), w = invPanelW(), h = invPanelH();
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 4, C_PANEL);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 4, 1, C_BORDER);
        gfx.drawString(font, Component.literal("TON INVENTAIRE").withStyle(ChatFormatting.BOLD),
                x + 8, y + 6, C_GREY, false);

        if (minecraft.player == null) return;
        for (int i = 0; i < 36; i++) {
            int[] pos = slotPos(i);
            ItemStack stk = minecraft.player.getInventory().getItem(i);
            boolean hov = !stk.isEmpty() && pickerSlot < 0
                    && mouseX >= pos[0] && mouseX < pos[0] + INV_SLOT
                    && mouseY >= pos[1] && mouseY < pos[1] + INV_SLOT;
            gfx.fill(pos[0], pos[1], pos[0] + INV_SLOT - 1, pos[1] + INV_SLOT - 1,
                    hov ? C_HOV_BG : C_SLOT);
            GuiUtils.drawChamferedBorder(gfx, pos[0], pos[1], INV_SLOT - 1, INV_SLOT - 1, 1, 1,
                    hov ? C_GREEN_D : C_SLOT_BRD);
            if (!stk.isEmpty()) {
                gfx.renderItem(stk, pos[0] + 1, pos[1] + 1);
                gfx.renderItemDecorations(font, stk, pos[0] + 1, pos[1] + 1);
            }
        }
    }

    /** Position écran du slot d'inventaire i (0-8 hotbar en bas, séparée). */
    private int[] slotPos(int i) {
        int col = i % 9;
        int row = (i < 9) ? 3 : (i - 9) / 9;
        int yGap = (i < 9) ? 3 : 0;
        return new int[]{ invGridX() + col * INV_SLOT, invGridY() + row * INV_SLOT + yGap };
    }

    // ── Colonne d'actions ─────────────────────────────────────────────────

    private int actionsX() { return cx() + invPanelW() + 8; }
    private int actionsW() { return cx() + cw() - actionsX(); }

    private void renderActions(GuiGraphics gfx, int mouseX, int mouseY,
                               boolean maConf, boolean saConf, boolean loaded) {
        int x = actionsX(), w = actionsW();
        int y = invPanelY();

        renderBigBtn(gfx, mouseX, mouseY, x, y, w, 28,
                maConf ? "✔ CONFIRMÉ — ANNULER" : "CONFIRMER L'ÉCHANGE",
                maConf ? C_GOLD : C_GREEN, !maConf);
        renderBigBtn(gfx, mouseX, mouseY, x, y + 34, w, 20, "ANNULER LE TRADE", C_RED, false);

        // Statut
        int sy = y + 62;
        gfx.fill(x, sy, x + w, sy + 1, 0x221E2D3D);
        String l1, l2;
        if (!loaded)              { l1 = "§8Synchronisation...";                 l2 = ""; }
        else if (maConf && saConf){ l1 = "§aFinalisation de l'échange...";       l2 = ""; }
        else if (maConf)          { l1 = "§7En attente de";                      l2 = "§e" + partenaire + "§7..."; }
        else if (saConf)          { l1 = "§e" + partenaire + " §7a confirmé.";   l2 = "§aÀ toi de valider !"; }
        else                      { l1 = "§7Ajoutez objets et argent,";          l2 = "§7puis confirmez chacun."; }
        gfx.drawString(font, l1, x, sy + 7, C_GREY, false);
        if (!l2.isEmpty()) gfx.drawString(font, l2, x, sy + 18, C_GREY, false);
    }

    // ── Sélecteur de quantité ─────────────────────────────────────────────

    private void renderPicker(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.fill(0, 0, width, height, 0x99000000);
        int x = pickX(), y = pickY(), w = pickW(), h = pickH();

        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 5, 0xFF10161F);
        GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 6, 1, 0x552ECC60);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 5, 1, C_GREEN_D);
        GuiUtils.drawCornerBrackets(gfx, x, y, w, h, 6, 4, 1, 0xCC3DDE6A);

        ItemStack stk = minecraft.player == null ? ItemStack.EMPTY
                : minecraft.player.getInventory().getItem(pickerSlot);
        if (stk.isEmpty()) { fermerPicker(); return; }

        gfx.renderItem(stk, x + 10, y + 8);
        String nom = stk.getHoverName().getString();
        if (font.width(nom) > w - 90) nom = font.plainSubstrByWidth(nom, w - 96) + "…";
        gfx.drawString(font, Component.literal(nom).withStyle(ChatFormatting.BOLD),
                x + 32, y + 9, C_VALUE, false);
        gfx.drawString(font, "§7en stock : §f" + stk.getCount(), x + 32, y + 20, C_GREY, false);

        // Champ quantité + boutons rapides
        int qy = y + 38;
        gfx.drawString(font, "§7QUANTITÉ", x + 10, qy + 1, C_GREY, false);
        GuiUtils.fillChamferedRect(gfx, x + 62, qy - 3, 50, 13, 2, C_SLOT);
        GuiUtils.drawChamferedBorder(gfx, x + 62, qy - 3, 50, 13, 2, 1,
                qtyBox.isFocused() ? C_GREEN_D : C_SLOT_BRD);
        qtyBox.setX(x + 66);
        qtyBox.setY(qy - 1);
        qtyBox.render(gfx, mouseX, mouseY, 0);

        String[] quick = {"1", "10", "½", "MAX"};
        for (int i = 0; i < 4; i++) {
            renderMiniBtn(gfx, mouseX, mouseY, x + 118 + i * 24, qy - 3, 22, 13, quick[i]);
        }

        renderBigBtn(gfx, mouseX, mouseY, x + 10, y + h - 26, (w - 26) / 2, 18, "AJOUTER", C_GREEN, true);
        renderBigBtn(gfx, mouseX, mouseY, x + 16 + (w - 26) / 2, y + h - 26, (w - 26) / 2, 18, "FERMER", C_GREY, false);
    }

    private void ouvrirPicker(int slot) {
        ItemStack stk = minecraft.player.getInventory().getItem(slot);
        if (stk.isEmpty()) return;
        pickerSlot = slot;
        qtyBox.setVisible(true);
        qtyBox.setValue(String.valueOf(stk.getCount()));
        setFocused(qtyBox);
    }

    private void fermerPicker() {
        pickerSlot = -1;
        qtyBox.setVisible(false);
        setFocused(null);
    }

    private void validerPicker() {
        if (pickerSlot < 0 || minecraft.player == null) return;
        ItemStack stk = minecraft.player.getInventory().getItem(pickerSlot);
        int max = stk.getCount();
        int q;
        try { q = Integer.parseInt(qtyBox.getValue().trim()); }
        catch (NumberFormatException e) { return; }
        q = Math.max(1, Math.min(q, max));
        PacketDistributor.sendToServer(
                new TradeActionPayload(TradeActionPayload.ADD_ITEM, pickerSlot, q, 0));
        fermerPicker();
    }

    // ── Boutons ───────────────────────────────────────────────────────────

    private void renderMiniBtn(GuiGraphics gfx, int mouseX, int mouseY,
                               int x, int y, int w, int h, String label) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 2, hov ? C_HOV_BG : 0xA0141C26);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 2, 1, hov ? C_GREEN_D : C_SLOT_BRD);
        gfx.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1,
                hov ? 0xFFFFFFFF : C_GREEN);
    }

    private void renderBigBtn(GuiGraphics gfx, int mouseX, int mouseY,
                              int x, int y, int w, int h, String label, int color, boolean neon) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 3, hov ? C_HOV_BG : 0xA0141C26);
        if (hov && neon) {
            GuiUtils.drawChamferedBorder(gfx, x - 2, y - 2, w + 4, h + 4, 5, 1, 0x252ECC60);
            GuiUtils.drawChamferedBorder(gfx, x - 1, y - 1, w + 2, h + 2, 4, 1, 0x552ECC60);
        }
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 3, 1, hov ? color : 0xFF1C2C3C);
        gfx.drawCenteredString(font, Component.literal(label).withStyle(ChatFormatting.BOLD),
                x + w / 2, y + (h - font.lineHeight) / 2 + 1, hov ? 0xFFFFFFFF : color);
    }

    // ── Tooltips ──────────────────────────────────────────────────────────

    private void renderTooltips(GuiGraphics gfx, int mouseX, int mouseY,
                                List<ItemStack> monOffre, List<ItemStack> sonOffre) {
        if (minecraft.player != null) {
            for (int i = 0; i < 36; i++) {
                int[] pos = slotPos(i);
                if (mouseX >= pos[0] && mouseX < pos[0] + INV_SLOT
                        && mouseY >= pos[1] && mouseY < pos[1] + INV_SLOT) {
                    ItemStack stk = minecraft.player.getInventory().getItem(i);
                    if (!stk.isEmpty()) gfx.renderTooltip(font, stk, mouseX, mouseY);
                    return;
                }
            }
        }
        int colG = cx(), colD = cx() + colW() + 8;
        int sy = cy() + 19;
        for (int i = 0; i < 8; i++) {
            for (int side = 0; side < 2; side++) {
                int sx = (side == 0 ? colG : colD) + 8 + i * (SLOT + 2);
                List<ItemStack> offre = side == 0 ? monOffre : sonOffre;
                if (i < offre.size()
                        && mouseX >= sx && mouseX < sx + SLOT
                        && mouseY >= sy && mouseY < sy + SLOT) {
                    gfx.renderTooltip(font, offre.get(i), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    // ── Clics ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;
        long now = System.currentTimeMillis();

        // ── Sélecteur ouvert : il capte tout ──
        if (pickerSlot >= 0) {
            int x = pickX(), y = pickY(), w = pickW(), h = pickH();
            int qy = y + 38;
            // Champ de saisie → laisser l'EditBox gérer le focus
            if (mx >= x + 62 && mx < x + 112 && my >= qy - 3 && my < qy + 10)
                return super.mouseClicked(mouseX, mouseY, button);
            // Boutons rapides
            ItemStack stk = minecraft.player == null ? ItemStack.EMPTY
                    : minecraft.player.getInventory().getItem(pickerSlot);
            int max = Math.max(1, stk.getCount());
            int[] quickVals = {1, Math.min(10, max), Math.max(1, max / 2), max};
            for (int i = 0; i < 4; i++) {
                int bx = x + 118 + i * 24;
                if (mx >= bx && mx < bx + 22 && my >= qy - 3 && my < qy + 10) {
                    qtyBox.setValue(String.valueOf(quickVals[i]));
                    return true;
                }
            }
            // AJOUTER / FERMER
            int byRow = y + h - 26, bw = (w - 26) / 2;
            if (mx >= x + 10 && mx < x + 10 + bw && my >= byRow && my < byRow + 18) {
                if (debounce(now)) validerPicker();
                return true;
            }
            if (mx >= x + 16 + bw && mx < x + 16 + 2 * bw && my >= byRow && my < byRow + 18) {
                fermerPicker();
                return true;
            }
            // Clic hors du panneau = fermer
            if (mx < x || mx >= x + w || my < y || my >= y + h) fermerPicker();
            return true;
        }

        TradeStatePayload st = TradeDataManager.state();
        boolean maConf = st != null && st.maConfirmation();

        // Mon offre : clic = retirer
        int colG = cx();
        int sy = cy() + 19;
        for (int i = 0; i < 8; i++) {
            int sx = colG + 8 + i * (SLOT + 2);
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                if (st != null && i < st.monOffre().size() && debounce(now)) {
                    PacketDistributor.sendToServer(
                            new TradeActionPayload(TradeActionPayload.REMOVE_ITEM, i, 0, 0));
                }
                return true;
            }
        }

        // Inventaire : clic = ouvrir le sélecteur de quantité
        if (minecraft.player != null) {
            for (int i = 0; i < 36; i++) {
                int[] pos = slotPos(i);
                if (mx >= pos[0] && mx < pos[0] + INV_SLOT && my >= pos[1] && my < pos[1] + INV_SLOT) {
                    if (!minecraft.player.getInventory().getItem(i).isEmpty()) ouvrirPicker(i);
                    return true;
                }
            }
        }

        // Bouton OK (argent)
        int ay = cy() + 46;
        if (mx >= colG + 117 && mx < colG + 141 && my >= ay - 3 && my < ay + 10) {
            envoyerArgent(now);
            return true;
        }

        // CONFIRMER / ANNULER
        int bx = actionsX(), bw = actionsW(), by = invPanelY();
        if (mx >= bx && mx < bx + bw && my >= by && my < by + 28) {
            if (debounce(now)) PacketDistributor.sendToServer(new TradeActionPayload(
                    maConf ? TradeActionPayload.UNCONFIRM : TradeActionPayload.CONFIRM, 0, 0, 0));
            return true;
        }
        if (mx >= bx && mx < bx + bw && my >= by + 34 && my < by + 54) {
            if (debounce(now)) PacketDistributor.sendToServer(
                    new TradeActionPayload(TradeActionPayload.CANCEL, 0, 0, 0));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean debounce(long now) {
        if (now - lastActionMs < ACTION_DEBOUNCE_MS) return false;
        lastActionMs = now;
        return true;
    }

    private void envoyerArgent(long now) {
        if (!debounce(now)) return;
        String txt = argentBox.getValue().trim();
        double v = 0;
        if (!txt.isEmpty()) {
            try { v = Double.parseDouble(txt); } catch (NumberFormatException e) { return; }
        }
        PacketDistributor.sendToServer(new TradeActionPayload(TradeActionPayload.SET_MONEY, 0, 0, v));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == 257 || keyCode == 335;
        if (enter && pickerSlot >= 0) {
            validerPicker();
            return true;
        }
        if (enter && argentBox != null && argentBox.isFocused()) {
            envoyerArgent(System.currentTimeMillis());
            return true;
        }
        // Échap avec sélecteur ouvert = fermer le sélecteur, pas l'écran
        if (keyCode == 256 && pickerSlot >= 0) {
            fermerPicker();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!closedByServer && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new TradeActionPayload(TradeActionPayload.CANCEL, 0, 0, 0));
        }
        TradeDataManager.setState(null);
        minecraft.setScreen(null);
    }
}
