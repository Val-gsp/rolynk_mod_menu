package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.SkinApplyPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fenêtre de personnalisation du skin — ouverte juste après la création du
 * profil RP. Affiche le personnage du joueur, un champ pour l'URL d'une image,
 * et un bouton VALIDER. Valider sans URL conserve le skin par défaut.
 *
 * Même habillage que le menu principal (BaseMenuScreen).
 */
public class SkinScreen extends BaseMenuScreen {

    private static final int C_INFO_BG = 0xFF141C26;
    private static final int C_BORDER  = 0xFF1E2D3D;
    private static final int C_LABEL   = 0xFF607080;
    private static final int C_VALUE   = 0xFFEEF2F5;

    private static final int BTN_H   = 20;
    private static final int FIELD_H = 16;

    private EditBox urlInput;

    /** État affiché sous le champ (info / erreur). */
    private volatile String message = null;
    private volatile boolean messageErreur = false;
    private boolean envoiEnCours = false;

    private int validerX, validerY, validerW;
    private int passerX, passerY, passerW;

    public SkinScreen() {
        super("MENU", "SKIN");
    }

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    /** Panneau de rendu du personnage (gauche). */
    private int skinW()    { return Math.min(120, Math.max(80, contentW() * 32 / 100)); }

    @Override
    protected void initContent() {
        int fieldX = contentX() + skinW() + PADDING;
        int fieldW = contentW() - skinW() - PADDING;
        // Fond du champ dessiné sous l'EditBox (widget décoratif ajouté en 1er)
        addRenderableOnly((net.minecraft.client.gui.components.Renderable)
                (gfx, mx, my, pt) -> {
            GuiUtils.fillChamferedRect(gfx, fieldX, urlFieldY(), fieldW, FIELD_H, 3, C_INFO_BG);
            GuiUtils.drawChamferedBorder(gfx, fieldX, urlFieldY(), fieldW, FIELD_H, 3, 1, C_BORDER);
            gfx.fill(fieldX + 2, urlFieldY() + 3, fieldX + 3, urlFieldY() + FIELD_H - 3, 0xFF2EA84E);
        });

        urlInput = new EditBox(font, fieldX + 5, urlFieldY() + 4, fieldW - 10, 10,
                Component.literal("URL de l'image"));
        urlInput.setMaxLength(480);
        urlInput.setHint(Component.literal("§8https://.../skin.png"));
        urlInput.setBordered(false);
        addRenderableWidget(urlInput);
        setFocused(urlInput);
    }

    private int urlFieldY() { return contentY() + 34; }

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = contentX(), cw = contentW();
        int fieldX = cx + skinW() + PADDING;
        int fieldW = cw - skinW() - PADDING;

        // ── Panneau de rendu du personnage ────────────────────────────────
        int skinH = panelY() + panelH() - FOOTER_H - PADDING - contentY();
        GuiUtils.fillChamferedRect(gfx, cx, contentY(), skinW(), skinH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, cx, contentY(), skinW(), skinH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, cx, contentY(), skinW(), skinH, 6, 4, 1, 0x553DDE6A);
        if (minecraft.player != null) {
            int scale = skinH / 3;
            gfx.enableScissor(cx + 1, contentY() + 1, cx + skinW() - 1, contentY() + skinH - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gfx, cx, contentY(), cx + skinW(), contentY() + skinH,
                    scale, 0.0f, mouseX, mouseY, minecraft.player);
            gfx.disableScissor();
        }

        // ── Zone de droite : explication + champ URL + boutons ────────────
        gfx.drawString(font, "PERSONNALISE TON SKIN", fieldX, contentY(), C_VALUE, false);
        gfx.drawString(font, "§7Lien d'une image de skin (PNG 64×64) :", fieldX, contentY() + 22, C_LABEL, false);

        // Message d'état (succès/erreur/chargement)
        if (message != null) {
            String m = (messageErreur ? "§c" : "§a") + message;
            gfx.drawString(font, m, fieldX, urlFieldY() + FIELD_H + 6,
                    messageErreur ? 0xFFFF5555 : 0xFF55FF55, false);
        }
        gfx.drawString(font, "§8Astuce : héberge ton PNG sur imgur, gyazo, etc.",
                fieldX, urlFieldY() + FIELD_H + 20, 0xFF666666, false);

        // ── Boutons VALIDER (large) + PASSER ──────────────────────────────
        int by = panelY() + panelH() - FOOTER_H - BTN_H - 6;
        passerW  = fieldW * 34 / 100;
        validerW = fieldW - passerW - 8;
        passerX  = fieldX;                 passerY  = by;
        validerX = fieldX + passerW + 8;   validerY = by;

        renderBtn(gfx, mouseX, mouseY, passerX, by, passerW, "PASSER", false);
        renderBtn(gfx, mouseX, mouseY, validerX, by, validerW,
                envoiEnCours ? "..." : "VALIDER", true);
    }

    private void renderBtn(GuiGraphics gfx, int mx, int my, int bx, int by, int bw,
                           String label, boolean neon) {
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H && !envoiEnCours;
        GuiUtils.fillChamferedRect(gfx, bx, by, bw, BTN_H, 4, hov ? 0xC01A2840 : 0xA0141C26);
        if (hov && neon) {
            GuiUtils.drawChamferedBorder(gfx, bx - 1, by - 1, bw + 2, BTN_H + 2, 5, 1, 0x552ECC60);
            GuiUtils.drawNeonEdge(gfx, bx, by, bw, BTN_H, 4);
        } else {
            GuiUtils.drawChamferedBorder(gfx, bx, by, bw, BTN_H, 4, 1, 0xFF1C2C3C);
        }
        Component t = Component.literal(label).withStyle(ChatFormatting.BOLD);
        gfx.drawCenteredString(font, t, bx + bw / 2, by + (BTN_H - font.lineHeight) / 2 + 1,
                hov ? 0xFF3DD96A : C_VALUE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !envoiEnCours) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (mx >= validerX && mx < validerX + validerW && my >= validerY && my < validerY + BTN_H) {
                envoyer(urlInput.getValue().trim());
                return true;
            }
            if (mx >= passerX && mx < passerX + passerW && my >= passerY && my < passerY + BTN_H) {
                envoyer("");  // garder le skin par défaut
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void envoyer(String url) {
        envoiEnCours = true;
        messageErreur = false;
        message = url.isEmpty() ? "Skin par défaut..." : "Application du skin...";
        PacketDistributor.sendToServer(new SkinApplyPayload(url));
    }

    /** Appelé par ClientPayloadHandlers quand le serveur répond. */
    public void onResultat(boolean ok, String msg) {
        envoiEnCours = false;
        if (ok) {
            minecraft.setScreen(null);
        } else {
            messageErreur = true;
            message = msg;
        }
    }

    // Étape finale de l'onboarding : Échap ne saute pas le choix (on peut
    // toujours cliquer PASSER pour garder le skin par défaut).
    @Override public boolean shouldCloseOnEsc() { return false; }
}
