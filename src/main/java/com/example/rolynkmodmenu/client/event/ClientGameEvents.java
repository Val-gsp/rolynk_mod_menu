package com.example.rolynkmodmenu.client.event;

import com.example.rolynkmodmenu.RolynkModMenu;
import com.example.rolynkmodmenu.client.keybind.ModKeybinds;
import com.example.rolynkmodmenu.client.screen.main_menu.MainMenuScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Événements GAME bus côté CLIENT.
 * Détecte les appuis de touches pendant le jeu.
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Ouvrir le menu uniquement si le joueur est en jeu et qu'aucun screen n'est ouvert
        if (mc.player == null || mc.screen != null) return;

        while (ModKeybinds.OPEN_MAIN_MENU.consumeClick()) {
            mc.setScreen(new MainMenuScreen());
        }
    }
}
