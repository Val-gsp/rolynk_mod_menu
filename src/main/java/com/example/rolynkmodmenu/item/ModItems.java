package com.example.rolynkmodmenu.item;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items décoratifs du menu Rolynk (icônes RP : grades, monnaies, services…).
 * Items simples sans logique — regroupés dans l'onglet créatif du mod
 * ({@link ModCreativeTabs}) pour les retrouver facilement.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(RolynkModMenu.MOD_ID);

    public static final DeferredHolder<Item, Item> GRADE       = simple("grade");
    public static final DeferredHolder<Item, Item> CRISTAUX    = simple("cristaux");
    public static final DeferredHolder<Item, Item> PELUCHE     = simple("peluche");
    public static final DeferredHolder<Item, Item> MEUBLE      = simple("meuble");
    public static final DeferredHolder<Item, Item> KIT         = simple("kit");
    public static final DeferredHolder<Item, Item> BALISE      = simple("balise");
    public static final DeferredHolder<Item, Item> BILLET      = simple("billet");
    public static final DeferredHolder<Item, Item> CARTE_BLEUE = simple("carte_bleue");
    public static final DeferredHolder<Item, Item> VILLE       = simple("ville");
    public static final DeferredHolder<Item, Item> BANQUE      = simple("banque");
    public static final DeferredHolder<Item, Item> PETS        = simple("pets");
    public static final DeferredHolder<Item, Item> LOBBY       = simple("lobby");

    private static DeferredHolder<Item, Item> simple(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties());
    }

    private ModItems() {}
}
