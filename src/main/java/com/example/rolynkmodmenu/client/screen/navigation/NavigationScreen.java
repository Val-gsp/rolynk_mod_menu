package com.example.rolynkmodmenu.client.screen.navigation;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import com.example.rolynkmodmenu.client.screen.balise.BaliseScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import com.example.rolynkmodmenu.client.screen.main_menu.widget.MenuButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fenêtre Navigation — MENU > NAVIGATION
 * Boutons Lobby et Ville : transfert du joueur vers le serveur Velocity
 * correspondant via la commande "/server <nom>" (interceptée par le proxy).
 * Bouton central Balises : ouvre le système de balises (téléportations).
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
        int bw      = (contentW - 2 * BTN_GAP) / 3;
        int sideH   = contentH * 70 / 100;
        int sideOffY = (contentH - sideH) / 2;

        addRenderableWidget(new MenuButton(
                contentX,                       contentY + sideOffY, bw, sideH,
                "LOBBY",   "Rejoindre le Lobby",
                new ItemStack(Items.NETHER_STAR),
                () -> connectToServer(SERVEUR_LOBBY)));

        addRenderableWidget(new MenuButton(
                contentX + bw + BTN_GAP,        contentY, bw, contentH,
                "BALISES", "Gérer et utiliser tes balises",
                new ItemStack(Items.BEACON),
                () -> minecraft.setScreen(new BaliseScreen())));

        addRenderableWidget(new MenuButton(
                contentX + 2 * (bw + BTN_GAP), contentY + sideOffY, bw, sideH,
                "VILLE",   "Rejoindre le serveur Ville",
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
