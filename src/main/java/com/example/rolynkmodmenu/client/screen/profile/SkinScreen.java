package com.example.rolynkmodmenu.client.screen.profile;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.network.SkinApplyPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fenêtre de personnalisation du skin — dernière étape de la création de
 * personnage, ouverte après le profil RP.
 *
 * Flux en deux temps demandé :
 *   1. le joueur saisit une URL d'image puis clique « VALIDER SKIN » → l'image
 *      est téléchargée CÔTÉ CLIENT et affichée immédiatement en aperçu (avatar
 *      2D reconstitué depuis la texture de skin) ;
 *   2. « VALIDER PERSONNAGE RP » finalise : l'URL est envoyée au serveur qui
 *      signe (MineSkin) et applique le skin, puis l'écran se ferme.
 *
 * Valider le personnage sans skin conserve le skin par défaut.
 */
public class SkinScreen extends BaseMenuScreen {

    private static final int C_INFO_BG = 0xFF141C26;
    private static final int C_BORDER  = 0xFF1E2D3D;
    private static final int C_LABEL   = 0xFF607080;
    private static final int C_VALUE   = 0xFFEEF2F5;

    private static final int BTN_H   = 20;
    private static final int FIELD_H = 16;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicInteger TEX_SEQ = new AtomicInteger();

    private EditBox urlInput;

    // Aperçu client
    private ResourceLocation previewTex;   // texture du skin prévisualisé (null = aucune)
    private int previewTexH = 64;          // 64 ou 32 (legacy)
    private volatile boolean chargementApercu = false;
    private volatile String message = null;
    private volatile boolean messageErreur = false;

    // Envoi serveur (finalisation)
    private boolean envoiEnCours = false;

    private int skinBtnX, skinBtnY, skinBtnW;
    private int finalBtnX, finalBtnY, finalBtnW;

    public SkinScreen() {
        super("MENU", "SKIN");
    }

    private int contentX() { return panelX() + PADDING; }
    private int contentY() { return panelY() + HEADER_H + PADDING; }
    private int contentW() { return panelW() - 2 * PADDING; }
    private int skinW()    { return Math.min(120, Math.max(80, contentW() * 32 / 100)); }
    private int fieldX()   { return contentX() + skinW() + PADDING; }
    private int fieldW()   { return contentW() - skinW() - PADDING; }
    private int urlFieldY(){ return contentY() + 34; }

    @Override
    protected void initContent() {
        int fx = fieldX(), fw = fieldW();
        addRenderableOnly((net.minecraft.client.gui.components.Renderable)
                (gfx, mx, my, pt) -> {
            GuiUtils.fillChamferedRect(gfx, fx, urlFieldY(), fw, FIELD_H, 3, C_INFO_BG);
            GuiUtils.drawChamferedBorder(gfx, fx, urlFieldY(), fw, FIELD_H, 3, 1, C_BORDER);
            gfx.fill(fx + 2, urlFieldY() + 3, fx + 3, urlFieldY() + FIELD_H - 3, 0xFF2EA84E);
        });

        urlInput = new EditBox(font, fx + 5, urlFieldY() + 4, fw - 10, 10,
                Component.literal("URL de l'image"));
        urlInput.setMaxLength(480);
        urlInput.setHint(Component.literal("§8https://.../skin.png"));
        urlInput.setBordered(false);
        addRenderableWidget(urlInput);
        setFocused(urlInput);
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = contentX(), fx = fieldX(), fw = fieldW();
        int skinH = panelY() + panelH() - FOOTER_H - PADDING - contentY();

        // Panneau d'aperçu
        GuiUtils.fillChamferedRect(gfx, cx, contentY(), skinW(), skinH, 4, C_INFO_BG);
        GuiUtils.drawChamferedBorder(gfx, cx, contentY(), skinW(), skinH, 4, 1, C_BORDER);
        GuiUtils.drawCornerBrackets(gfx, cx, contentY(), skinW(), skinH, 6, 4, 1, 0x553DDE6A);
        gfx.enableScissor(cx + 1, contentY() + 1, cx + skinW() - 1, contentY() + skinH - 1);
        if (previewTex != null) {
            drawAvatar(gfx, previewTex, cx, contentY(), skinW(), skinH, previewTexH);
        } else if (minecraft.player != null) {
            int scale = skinH / 3;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    gfx, cx, contentY(), cx + skinW(), contentY() + skinH,
                    scale, 0.0f, mouseX, mouseY, minecraft.player);
        }
        gfx.disableScissor();

        // Zone de droite
        gfx.drawString(font, "PERSONNALISE TON SKIN", fx, contentY(), C_VALUE, false);
        gfx.drawString(font, "§7Lien d'une image de skin (PNG 64×64) :", fx, contentY() + 22, C_LABEL, false);

        if (message != null) {
            gfx.drawString(font, (messageErreur ? "§c" : "§a") + message,
                    fx, urlFieldY() + FIELD_H + 6, messageErreur ? 0xFFFF5555 : 0xFF55FF55, false);
        }
        gfx.drawString(font, "§8Astuce : héberge ton PNG sur imgur, gyazo, Discord…",
                fx, urlFieldY() + FIELD_H + 20, 0xFF666666, false);

        // Boutons
        int by = panelY() + panelH() - FOOTER_H - BTN_H - 6;
        skinBtnW  = fw;
        finalBtnW = fw;
        skinBtnX  = fx; skinBtnY  = by - BTN_H - 6;
        finalBtnX = fx; finalBtnY = by;
        renderBtn(gfx, mouseX, mouseY, skinBtnX, skinBtnY, skinBtnW,
                chargementApercu ? "..." : "VALIDER SKIN", false);
        renderBtn(gfx, mouseX, mouseY, finalBtnX, finalBtnY, finalBtnW,
                envoiEnCours ? "..." : "VALIDER PERSONNAGE RP", true);
    }

    /** Reconstitue un avatar 2D de face à partir d'une texture de skin. */
    private void drawAvatar(GuiGraphics gfx, ResourceLocation tex,
                            int px, int py, int panelW, int panelH, int texH) {
        // Grille avatar : 16 large × 32 haut (unités skin).
        int scale = Math.max(1, Math.min((panelW - 8) / 16, (panelH - 8) / 32));
        int aw = 16 * scale, ah = 32 * scale;
        int ox = px + (panelW - aw) / 2;
        int oy = py + (panelH - ah) / 2;
        boolean legacy = texH == 32;

        // Base : tête, corps, bras, jambes
        part(gfx, tex, ox + 4 * scale, oy,            8, 8,  8,  8, scale, texH);   // tête
        part(gfx, tex, ox + 4 * scale, oy + 8 * scale, 8, 12, 20, 20, scale, texH); // corps
        part(gfx, tex, ox,             oy + 8 * scale, 4, 12, 44, 20, scale, texH); // bras droit
        part(gfx, tex, ox + 4 * scale, oy + 20 * scale, 4, 12, 4, 20, scale, texH); // jambe droite
        // Bras/jambe gauches : coords dédiées en 64×64, miroir du droit en legacy
        part(gfx, tex, ox + 12 * scale, oy + 8 * scale, 4, 12, legacy ? 44 : 36, legacy ? 20 : 52, scale, texH); // bras gauche
        part(gfx, tex, ox + 8 * scale,  oy + 20 * scale, 4, 12, legacy ? 4 : 20, legacy ? 20 : 52, scale, texH); // jambe gauche

        // Overlays (chapeau + vêtements) en 64×64 uniquement
        if (!legacy) {
            part(gfx, tex, ox + 4 * scale, oy,            8, 8,  40, 8,  scale, texH); // chapeau
            part(gfx, tex, ox + 4 * scale, oy + 8 * scale, 8, 12, 20, 36, scale, texH); // veste
            part(gfx, tex, ox,             oy + 8 * scale, 4, 12, 44, 36, scale, texH); // manche D
            part(gfx, tex, ox + 12 * scale, oy + 8 * scale, 4, 12, 52, 52, scale, texH); // manche G
            part(gfx, tex, ox + 4 * scale, oy + 20 * scale, 4, 12, 4, 52, scale, texH); // pantalon D
            part(gfx, tex, ox + 8 * scale,  oy + 20 * scale, 4, 12, 4, 52, scale, texH); // pantalon G
        }
    }

    private void part(GuiGraphics gfx, ResourceLocation tex, int x, int y,
                      int w, int h, int u, int v, int scale, int texH) {
        gfx.blit(tex, x, y, w * scale, h * scale, u, v, w, h, 64, texH);
    }

    private void renderBtn(GuiGraphics gfx, int mx, int my, int bx, int by, int bw,
                           String label, boolean neon) {
        boolean busy = chargementApercu || envoiEnCours;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + BTN_H && !busy;
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

    // ── Interactions ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !chargementApercu && !envoiEnCours) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (in(mx, my, skinBtnX, skinBtnY, skinBtnW)) { previsualiser(); return true; }
            if (in(mx, my, finalBtnX, finalBtnY, finalBtnW)) { finaliser(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean in(int mx, int my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + BTN_H;
    }

    /** Étape 1 : télécharge l'image côté client et l'affiche en aperçu. */
    private void previsualiser() {
        String url = urlInput.getValue().trim();
        if (url.isEmpty()) {
            messageErreur = true; message = "Saisis d'abord une URL.";
            return;
        }
        if (!url.matches("^https?://.{4,480}$")) {
            messageErreur = true; message = "URL invalide (doit commencer par http).";
            return;
        }
        chargementApercu = true;
        messageErreur = false;
        message = "Chargement de l'aperçu...";
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "Mozilla/5.0 (RolynkModMenu)")
                        .GET().build();
                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() / 100 != 2 || resp.body() == null)
                    throw new IllegalStateException("http " + resp.statusCode());
                NativeImage img = NativeImage.read(new ByteArrayInputStream(resp.body()));
                int w = img.getWidth(), h = img.getHeight();
                if (w != 64 || (h != 64 && h != 32)) {
                    img.close();
                    fail("L'image doit être un skin PNG 64×64.");
                    return;
                }
                Minecraft.getInstance().execute(() -> appliquerApercu(img, h));
            } catch (Exception e) {
                fail("Image inaccessible — vérifie le lien (Discord expiré ?).");
            }
        });
    }

    private void appliquerApercu(NativeImage img, int texH) {
        try {
            if (previewTex != null) minecraft.getTextureManager().release(previewTex);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                    "rolynk_mod_menu", "skin_preview/" + TEX_SEQ.incrementAndGet());
            minecraft.getTextureManager().register(rl, new DynamicTexture(img));
            previewTex = rl;
            previewTexH = texH;
            message = "Aperçu chargé — valide ton personnage pour appliquer.";
            messageErreur = false;
        } catch (Exception e) {
            img.close();
            fail("Aperçu impossible.");
        } finally {
            chargementApercu = false;
        }
    }

    private void fail(String msg) {
        Minecraft.getInstance().execute(() -> {
            chargementApercu = false;
            messageErreur = true;
            message = msg;
        });
    }

    /** Étape 2 : envoie l'URL au serveur (signature + application) puis ferme. */
    private void finaliser() {
        String url = urlInput.getValue().trim();
        envoiEnCours = true;
        messageErreur = false;
        message = url.isEmpty() ? "Personnage sans skin personnalisé..." : "Application du skin...";
        PacketDistributor.sendToServer(new SkinApplyPayload(url));
    }

    /** Réponse du serveur à la finalisation. */
    public void onResultat(boolean ok, String msg) {
        envoiEnCours = false;
        if (ok) {
            onClose();
        } else {
            messageErreur = true;
            message = msg;
        }
    }

    @Override
    public void onClose() {
        if (previewTex != null) {
            minecraft.getTextureManager().release(previewTex);
            previewTex = null;
        }
        minecraft.setScreen(null);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
