package com.example.rolynkmodmenu.client.screen.navigation;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.MenuButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fenêtre Navigation — MENU > NAVIGATION
 * Chaque bouton transfère le joueur vers le serveur Velocity correspondant
 * via la commande "/server <nom>" (interceptée par le proxy).
 */
public class NavigationScreen extends BaseMenuScreen {

    // Noms des serveurs tels que déclarés dans velocity.toml.
    // Doivent correspondre aux entrées [servers] du proxy.
    private static final String SERVEUR_LOBBY = "lobby";
    private static final String SERVEUR_VILLE = "ville";

    public NavigationScreen() {
        super("MENU", "NAVIGATION");
    }

    @Override
    protected void initContent() {
        int px       = panelX();
        int contentX = px + PADDING;
        int contentY = panelY() + HEADER_H + PADDING;
        int contentW = panelW() - 2 * PADDING;
        int contentH = panelH() - HEADER_H - 2 * PADDING - FOOTER_H;
        int bw      = (contentW - BTN_GAP) / 2;

        addRenderableWidget(new MenuButton(
                contentX,                contentY, bw, contentH,
                "LOBBY", "Rejoindre le Lobby",
                new ItemStack(Items.NETHER_STAR),
                () -> connectToServer(SERVEUR_LOBBY)));

        addRenderableWidget(new MenuButton(
                contentX + bw + BTN_GAP, contentY, bw, contentH,
                "VILLE", "Rejoindre le serveur Ville",
                new ItemStack(Items.CAMPFIRE),
                () -> connectToServer(SERVEUR_VILLE)));
    }

    /**
     * Demande à Velocity de transférer le joueur vers {@code serverName}
     * en lui faisant exécuter "/server <nom>", puis ferme le menu.
     * Le nom est filtré (mêmes caractères sûrs que SwitchServerPayload) par
     * sécurité, même s'il est ici constant.
     */
    private void connectToServer(String serverName) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        String safe = serverName.replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (safe.isEmpty()) return;
        minecraft.getConnection().sendCommand("server " + safe);
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new MainMenuScreen());
    }
}
