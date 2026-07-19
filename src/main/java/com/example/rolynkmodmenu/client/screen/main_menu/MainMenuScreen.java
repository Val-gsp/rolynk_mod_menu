package com.example.rolynkmodmenu.client.screen.main_menu;

import com.example.rolynkmodmenu.client.keybind.ModKeybinds;
import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.boutique.BoutiqueScreen;
import com.example.rolynkmodmenu.client.screen.balise.BaliseScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.MenuButton;
import com.example.rolynkmodmenu.client.screen.navigation.NavigationScreen;
import com.example.rolynkmodmenu.client.screen.profile.ProfileScreen;
import com.example.rolynkmodmenu.client.screen.recompense.RecompenseScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Écran principal du mod — grille 3×2 de modules.
 * Tout le rendu (fond, header, footer, bordures) est hérité de BaseMenuScreen.
 */
public class MainMenuScreen extends BaseMenuScreen {

    // ── Données boutons ──────────────────────────────────────────────────────
    private static final String[] LABELS = {
        "BOUTIQUE", "RÉCOMPENSES", "PROFIL",
        "NAVIGATION",  "BALISE"
    };
    private static final String[] SUBTITLES = {
        "Découvre notre boutique en ligne",
        "Récupère tes récompenses quotidiennes",
        "Gère ton profil et tes statistiques",
        "Explore le monde et ses différents régions",
        "Gère et téléporte-toi vers tes balises"
    };
    private static final ItemStack[] ICONS = {
        new ItemStack(Items.CHEST),
        new ItemStack(Items.NETHER_STAR),
        new ItemStack(Items.PLAYER_HEAD),
        new ItemStack(Items.COMPASS),
        new ItemStack(Items.BEACON)
    };

    public MainMenuScreen() { super("MENU"); }

    @Override protected boolean showNavIcons() { return false; }

    // ── Contenu ──────────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        int px    = panelX(), py = panelY();
        int bw    = gridBtnW(), bh = gridBtnH();
        int gridY = py + HEADER_H + PADDING;   // = panelY() + btnOffsetY()

        Runnable[] actions = {
            () -> minecraft.setScreen(new BoutiqueScreen()),
            () -> minecraft.setScreen(new RecompenseScreen()),
            () -> minecraft.setScreen(new ProfileScreen()),
            () -> minecraft.setScreen(new NavigationScreen()),
            () -> minecraft.setScreen(new BaliseScreen()),
        };

        for (int i = 0; i < LABELS.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int bx  = px + PADDING + col * (bw + BTN_GAP);
            int by  = gridY + row * (bh + BTN_GAP);
            addRenderableWidget(new MenuButton(bx, by, bw, bh,
                    LABELS[i], SUBTITLES[i], ICONS[i], actions[i]));
        }
    }

    // ── Raccourci clavier pour fermer le menu ────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeybinds.OPEN_MAIN_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
