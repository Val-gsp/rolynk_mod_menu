package com.example.rolynkmodmenu.client.screen.ville;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.util.GuiUtils;
import com.example.rolynkmodmenu.client.ville.VilleListDataManager;
import com.example.rolynkmodmenu.client.ville.VilleProfileDataManager;
import com.example.rolynkmodmenu.network.VilleListPayload;
import com.example.rolynkmodmenu.network.VilleListRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hub Ville — deux grandes cartes cliquables.
 *
 * Si le joueur N'A PAS de ville :
 *   • LISTE DES VILLES  → ListeVillesScreen
 *   • CRÉER UNE VILLE   → CreerVilleScreen
 *
 * Si le joueur A déjà une ville :
 *   • LISTE DES VILLES  → ListeVillesScreen
 *   • MA VILLE          → GestionVilleScreen (sa ville directement)
 */
public class VilleScreen extends BaseMenuScreen {

    // ── Couleurs ──────────────────────────────────────────────────────────
    private static final int COLOR_CARD_BG      = 0xFF141C26;
    private static final int COLOR_CARD_HOVER   = 0xFF1A2A3A;
    private static final int COLOR_FOOTER_BG    = 0xFF0D1117;
    private static final int COLOR_LISTE_ACCENT = 0xFF1E6A9E;
    private static final int COLOR_CREER_ACCENT = 0xFF2EA84E;
    private static final int COLOR_MAVILLE_ACCENT = 0xFFAA8800;

    // ── Icônes pixel-art ─────────────────────────────────────────────────
    private static final String[] ICON_LISTE = {
        " ### ",
        "#####",
        "#   #",
        "#####",
        "#   #",
        "#####",
        " ### "
    };
    private static final String[] ICON_CREER = {
        "  #  ",
        "  #  ",
        "#####",
        "  #  ",
        "  #  "
    };
    private static final String[] ICON_MAVILLE = {
        "  #  ",
        " ### ",
        "#####",
        "# # #",
        "# # #"
    };

    public VilleScreen() {
        super("MENU", "VILLE");
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private int cardY()  { return panelY() + HEADER_H + PADDING; }
    private int cardH()  { return panelH() - HEADER_H - PADDING * 2 - FOOTER_H; }
    private int cardW()  { return (panelW() - PADDING * 3) / 2; }
    private int card1X() { return panelX() + PADDING; }
    private int card2X() { return panelX() + PADDING * 2 + cardW(); }

    // ── Cycle de vie ──────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        // Pré-chargement de la liste pour que GestionVilleScreen ait les données dès le clic
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new VilleListRequestPayload());
    }

    // ── Rendu ─────────────────────────────────────────────────────────────

    @Override
    protected void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Carte 1 — toujours LISTE DES VILLES
        renderCard(gfx, mouseX, mouseY, card1X(), cardY(), cardW(), cardH(),
                "LISTE DES VILLES", ICON_LISTE, COLOR_LISTE_ACCENT,
                "Explore les villes du réseau");

        // Carte 2 — dépend de l'appartenance du joueur
        if (VilleProfileDataManager.hasVille()) {
            String nomVille = VilleProfileDataManager.getVilleNom();
            renderCard(gfx, mouseX, mouseY, card2X(), cardY(), cardW(), cardH(),
                    "MA VILLE", ICON_MAVILLE, COLOR_MAVILLE_ACCENT,
                    nomVille);
        } else {
            renderCard(gfx, mouseX, mouseY, card2X(), cardY(), cardW(), cardH(),
                    "CRÉER UNE VILLE", ICON_CREER, COLOR_CREER_ACCENT,
                    "Fonde ta propre ville (500 " + com.example.rolynkmodmenu.util.Money.SYMBOL + ")");
        }
    }

    private void renderCard(GuiGraphics gfx, int mouseX, int mouseY,
                             int x, int y, int w, int h,
                             String title, String[] icon, int accentColor,
                             String subtitle) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        GuiUtils.fillChamferedRect(gfx, x, y, w, h, 6, hovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);

        // Bande colorée en haut
        gfx.fill(x + 6, y + 2, x + w - 6, y + 4, accentColor);

        // Icône pixel-art centrée
        int iconCX = x + w / 2;
        int iconCY = y + (h - 48) / 2 + 8;
        GuiUtils.drawPixelArt(gfx, icon, iconCX, iconCY, hovered ? 5 : 4,
                hovered ? accentColor : (0x99000000 | (accentColor & 0x00FFFFFF)));

        // Footer
        int footerH = 40;
        int footerY = y + h - footerH;
        GuiUtils.fillChamferedRect(gfx, x + 1, footerY, w - 2, footerH - 1, 5, COLOR_FOOTER_BG);
        gfx.drawCenteredString(font, title,
                x + w / 2, footerY + 7, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        gfx.drawCenteredString(font, "§8" + subtitle,
                x + w / 2, footerY + 20, 0xFFAAAAAA);

        // Bordure + brackets
        GuiUtils.drawChamferedBorder(gfx, x, y, w, h, 6, hovered ? 2 : 1,
                hovered ? accentColor : 0xFF1E2D3D);
        GuiUtils.drawCornerBrackets(gfx, x, y, w, h, 6, 5, 1, 0x553DDE6A);
    }

    // ── Clics ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // Carte 1 — LISTE DES VILLES
        if (mx >= card1X() && mx < card1X() + cardW()
                && my >= cardY() && my < cardY() + cardH()) {
            minecraft.setScreen(new ListeVillesScreen());
            return true;
        }

        // Carte 2 — MA VILLE ou CRÉER UNE VILLE
        if (mx >= card2X() && mx < card2X() + cardW()
                && my >= cardY() && my < cardY() + cardH()) {
            if (VilleProfileDataManager.hasVille()) {
                ouvrirMaVille();
            } else {
                minecraft.setScreen(new CreerVilleScreen(this));
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new MainMenuScreen());
    }

    // ── Navigation vers Ma Ville ──────────────────────────────────────────

    private void ouvrirMaVille() {
        String nomVille = VilleProfileDataManager.getVilleNom();

        // Cherche l'entrée dans le cache local (déjà pré-chargé dans initContent)
        VilleListPayload.VilleEntry entry = VilleListDataManager.getByNom(nomVille);

        if (entry != null) {
            // Données disponibles → GestionVilleScreen directement
            minecraft.setScreen(new GestionVilleScreen(entry, this));
        } else {
            // Cache vide (première ouverture, pas encore reçu) → passe par la liste
            // La liste affichera le nom de la ville et le joueur pourra cliquer CONSULTER
            minecraft.setScreen(new ListeVillesScreen());
        }
    }
}
