package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.boutique.BoutiqueDataManager;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.BoutiqueCatalogPayload;
import com.example.rolynkmodmenu.network.BoutiqueCatalogRequestPayload;
import com.example.rolynkmodmenu.network.BoutiqueVendrePayload;
import com.example.rolynkmodmenu.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Fenêtre Boutique Jeux — MENU > BOUTIQUE > JEUX
 *
 * Le serveur RACHÈTE des lots d'objets (aucun achat possible) : une ligne
 * par offre du catalogue serveur, avec l'icône de l'item, le lot, ce que le
 * joueur possède, le prix et un bouton VENDRE (actif si le joueur a le compte).
 *
 * Les prix sont volontairement bas : la boutique est un plancher, le vrai
 * commerce doit se faire entre joueurs.
 */
public class BoutiqueJeuxScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int C_BORDER   = 0xFF1E2D3D;
    private static final int C_ROW_BG   = 0xFF141C26;
    private static final int C_ROW_HOV  = 0xC01A2840;
    private static final int C_VALUE    = 0xFFEEF2F5;
    private static final int C_GREY     = 0xFF607080;
    private static final int C_GOLD     = 0xFFFFDD44;
    private static final int C_GREEN    = 0xFF3DD96A;
    private static final int C_RED      = 0xFFFF5555;

    private static final int ROW_H    = 26;
    private static final int ROW_GAP  = 4;
    private static final int BTN_W    = 62;
    private static final int HEADER_LINE_H = 14;

    // ── État réseau ───────────────────────────────────────────────────────
    private long lastRequestMs = 0;
    private long lastVendreMs  = 0;
    private static final long RETRY_MS           = 2_500L;
    private static final long VENDRE_DEBOUNCE_MS =   600L;

    private double scroll = 0;

    public BoutiqueJeuxScreen() { super("BOUTIQUE", "JEUX"); }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        scroll = 0;
        requestCatalog();
    }

    @Override
    public void tick() {
        super.tick();
        if (!BoutiqueDataManager.isLoaded()
                && System.currentTimeMillis() - lastRequestMs > RETRY_MS) {
            requestCatalog();
        }
    }

    private void requestCatalog() {
        if (minecraft.getConnection() != null) {
            lastRequestMs = System.currentTimeMillis();
            PacketDistributor.sendToServer(new BoutiqueCatalogRequestPayload());
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    private int contentH() { return panelH() - HEADER_H - 2 * PADDING - FOOTER_H; }

    private int listY() { return contentY() + HEADER_LINE_H + 4; }
    private int listH() { return contentH() - HEADER_LINE_H - 4; }

    private int maxScroll() {
        int total = BoutiqueDataManager.offres().size() * (ROW_H + ROW_GAP) - ROW_GAP;
        return Math.max(0, total - listH());
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = contentX(), cw = contentW();

        // Bandeau d'explication
        gfx.drawCenteredString(font,
                "§7Le serveur rachète tes objets à prix fixe — vends plutôt aux joueurs !",
                cx + cw / 2, contentY() + 2, C_GREY);

        List<BoutiqueCatalogPayload.Offre> offres = BoutiqueDataManager.offres();
        if (!BoutiqueDataManager.isLoaded()) {
            gfx.drawCenteredString(font, "§8Chargement du catalogue...",
                    cx + cw / 2, listY() + listH() / 2 - font.lineHeight / 2, C_GREY);
            return;
        }
        if (offres.isEmpty()) {
            gfx.drawCenteredString(font, "§8Aucune offre disponible pour le moment.",
                    cx + cw / 2, listY() + listH() / 2 - font.lineHeight / 2, C_GREY);
            return;
        }

        int ly = listY(), lh = listH();
        gfx.enableScissor(cx, ly, cx + cw, ly + lh);
        for (int i = 0; i < offres.size(); i++) {
            int ry = ly + i * (ROW_H + ROW_GAP) - (int) scroll;
            if (ry + ROW_H < ly || ry > ly + lh) continue;
            renderRow(gfx, mouseX, mouseY, cx, ry, cw, i, offres.get(i));
        }
        gfx.disableScissor();

        // Indicateur de scroll minimal
        if (maxScroll() > 0) {
            int barH = Math.max(12, lh * lh / (lh + maxScroll()));
            int barY = ly + (int) ((lh - barH) * (scroll / maxScroll()));
            gfx.fill(cx + cw - 2, ly, cx + cw, ly + lh, 0x22FFFFFF);
            gfx.fill(cx + cw - 2, barY, cx + cw, barY + barH, 0x883DDE6A);
        }
    }

    private void renderRow(GuiGraphics gfx, int mouseX, int mouseY,
                           int x, int y, int w, int index,
                           BoutiqueCatalogPayload.Offre offre) {
        Item item = resolveItem(offre.itemId());
        int possede = item == null ? 0 : compterInventaire(item);
        boolean vendable = item != null && possede >= offre.quantite();

        int btnX = x + w - BTN_W - 6;
        int btnY = y + (ROW_H - 18) / 2;
        boolean hovBtn = vendable
                && mouseX >= btnX && mouseX < btnX + BTN_W
                && mouseY >= btnY && mouseY < btnY + 18
                && mouseY >= listY() && mouseY < listY() + listH();

        // Fond de ligne
        GuiUtils.fillChamferedRect(gfx, x, y, w, ROW_H, 3, hovBtn ? C_ROW_HOV : C_ROW_BG);
        GuiUtils.drawChamferedBorder(gfx, x, y, w, ROW_H, 3, 1, C_BORDER);
        gfx.fill(x + 2, y + 3, x + 3, y + ROW_H - 3, vendable ? 0xFF2EA84E : 0x442EA84E);

        int midY = y + (ROW_H - font.lineHeight) / 2 + 1;

        // Icône de l'item
        if (item != null) {
            gfx.renderItem(new ItemStack(item), x + 8, y + (ROW_H - 16) / 2);
        }

        // Lot : "30 × Diamant"
        String nom = item != null
                ? new ItemStack(item).getHoverName().getString() : offre.itemId();
        gfx.drawString(font, Component.literal(offre.quantite() + " × " + nom)
                .withStyle(ChatFormatting.BOLD), x + 30, midY, C_VALUE, false);

        // Possédé : "tu en as N"
        String posStr = "§7(" + possede + " en stock)";
        int posX = x + w * 45 / 100;
        gfx.drawString(font, posStr, posX, midY, possede >= offre.quantite() ? C_GREEN : C_GREY, false);

        // Prix
        String prixStr = "+" + Money.exact(offre.prix());
        int prixX = btnX - 10 - font.width(prixStr);
        gfx.drawString(font, Component.literal(prixStr).withStyle(ChatFormatting.BOLD),
                prixX, midY, C_GOLD, false);

        // Bouton VENDRE
        GuiUtils.fillChamferedRect(gfx, btnX, btnY, BTN_W, 18, 3,
                hovBtn ? 0xC01A2840 : (vendable ? 0xA0141C26 : 0x60101820));
        GuiUtils.drawChamferedBorder(gfx, btnX, btnY, BTN_W, 18, 3, 1,
                vendable ? (hovBtn ? 0xFF3DDE6A : 0xFF2EA84E) : 0xFF1C2C3C);
        gfx.drawCenteredString(font, Component.literal("VENDRE").withStyle(ChatFormatting.BOLD),
                btnX + BTN_W / 2, btnY + 5, vendable ? (hovBtn ? 0xFF90FFB8 : C_GREEN) : C_GREY);
    }

    // ── Clics & scroll ────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;
        List<BoutiqueCatalogPayload.Offre> offres = BoutiqueDataManager.offres();

        if (my >= listY() && my < listY() + listH()) {
            int cx = contentX(), cw = contentW();
            for (int i = 0; i < offres.size(); i++) {
                int ry = listY() + i * (ROW_H + ROW_GAP) - (int) scroll;
                int btnX = cx + cw - BTN_W - 6;
                int btnY = ry + (ROW_H - 18) / 2;
                if (mx >= btnX && mx < btnX + BTN_W && my >= btnY && my < btnY + 18) {
                    vendre(i, offres.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void vendre(int index, BoutiqueCatalogPayload.Offre offre) {
        long now = System.currentTimeMillis();
        if (now - lastVendreMs < VENDRE_DEBOUNCE_MS) return;

        Item item = resolveItem(offre.itemId());
        if (item == null || compterInventaire(item) < offre.quantite()) return;

        lastVendreMs = now;
        PacketDistributor.sendToServer(new BoutiqueVendrePayload(index));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dy * (ROW_H + ROW_GAP)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(new BoutiqueScreen()); }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Item resolveItem(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    /** Compte les items « propres » (sans composant custom) — même règle que le serveur. */
    private int compterInventaire(Item item) {
        if (minecraft.player == null) return 0;
        Inventory inv = minecraft.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(item) && st.getComponentsPatch().isEmpty()) {
                total += st.getCount();
            }
        }
        return total;
    }
}
