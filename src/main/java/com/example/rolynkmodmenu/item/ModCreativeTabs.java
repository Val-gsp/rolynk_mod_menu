package com.example.rolynkmodmenu.item;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Onglet créatif « Rolynk Menu » : regroupe tous les items du mod
 * pour les retrouver d'un coup d'œil dans l'inventaire créatif.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RolynkModMenu.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rolynk_mod_menu.main"))
                    .icon(() -> new ItemStack(ModItems.GRADE.get()))
                    .displayItems((params, output) ->
                            ModItems.ITEMS.getEntries().forEach(e -> output.accept(e.get())))
                    .build());

    private ModCreativeTabs() {}
}
