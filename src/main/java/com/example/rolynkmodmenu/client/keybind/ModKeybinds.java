package com.example.rolynkmodmenu.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Définition des touches du mod.
 */
public class ModKeybinds {

    /** Catégorie affichée dans les options de touches. */
    public static final String CATEGORY = "key.categories.rolynk_mod_menu";

    /** Touche X → ouvre le menu principal. */
    public static final KeyMapping OPEN_MAIN_MENU = new KeyMapping(
            "key.rolynk_mod_menu.open_main_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAIN_MENU);
    }
}
