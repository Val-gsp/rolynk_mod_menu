package com.example.rolynkmodmenu.client.screen.boutique;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.MenuButton;
import com.example.rolynkmodmenu.item.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * Fenêtre Boutique — MENU > BOUTIQUE
 *
 * Grille 3×2 de 6 catégories (même gabarit que le menu principal). Chaque
 * bouton ouvre sa fenêtre de catégorie ({@link BoutiqueCategorieScreen},
 * « à venir » tant que la rubrique n'a pas ouvert).
 *
 * L'ancien système de rachat d'objets (Boutique Jeux + catalogue serveur
 * rolynk_boutique.properties) a été retiré en v1.8.0.
 */
public class BoutiqueScreen extends BaseMenuScreen {

    // ── Données boutons ──────────────────────────────────────────────────────
    private static final String[] LABELS = {
        "GRADES", "KIT", "COSMÉTIQUES",
        "PETS", "PELUCHE", "MEUBLE"
    };
    private static final String[] SUBTITLES = {
        "Monte en grade sur le serveur",
        "Kits d'objets à débloquer",
        "Skins, titres et effets exclusifs",
        "Compagnons et montures",
        "Peluches exclusives à collectionner",
        "Meubles et décorations premium"
    };
    private static final ItemStack[] ICONS = {
        new ItemStack(ModItems.GRADE.get()),
        new ItemStack(ModItems.KIT.get()),
        new ItemStack(ModItems.COSMETIQUE.get()),
        new ItemStack(ModItems.PETS.get()),
        new ItemStack(ModItems.PELUCHE.get()),
        new ItemStack(ModItems.MEUBLE.get())
    };

    public BoutiqueScreen() { super("MENU", "BOUTIQUE"); }

    // ── Contenu ──────────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        int px    = panelX(), py = panelY();
        int bw    = gridBtnW(), bh = gridBtnH();
        int gridY = py + HEADER_H + PADDING;

        for (int i = 0; i < LABELS.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int bx  = px + PADDING + col * (bw + BTN_GAP);
            int by  = gridY + row * (bh + BTN_GAP);
            final String categorie = LABELS[i];
            // PETS ouvre le catalogue RolynkRP (section Pets) s'il est présent —
            // plus besoin du F8. Mod absent → écran « à venir » comme les autres.
            Runnable action = "PETS".equals(categorie)
                    ? () -> {
                        if (!com.example.rolynkmodmenu.client.boutique.CatalogueIntegration.openSection("Pets"))
                            minecraft.setScreen(new BoutiqueCategorieScreen(categorie));
                    }
                    : () -> minecraft.setScreen(new BoutiqueCategorieScreen(categorie));
            addRenderableWidget(new MenuButton(bx, by, bw, bh,
                    LABELS[i], SUBTITLES[i], ICONS[i], action));
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(new MainMenuScreen()); }
}
