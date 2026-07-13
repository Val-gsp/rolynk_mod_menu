package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.RolynkModMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Protections de cité (capitale) : état du joueur GELÉ, jamais regagné gratuitement.
 *
 * La faim et la vie sont synchronisées entre serveurs (PlayerSync) : un joueur
 * affamé ne doit pas pouvoir se refaire une santé en passant par la capitale.
 * Ici on empêche donc toute PERTE (ville protégée) sans jamais REMPLIR :
 * la barre de faim ne descend plus, mais ne remonte qu'en mangeant.
 *
 * Activé par serveur via config/rolynk.properties :
 *   protection.faim.gelee=true      (capitale uniquement)
 *   protection.degats.joueurs=true  (capitale uniquement)
 */
@EventBusSubscriber(modid = RolynkModMenu.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ProtectionEventSubscriber {

    private ProtectionEventSubscriber() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Purge (tous serveurs) de l'effet saturation INFINI que l'ancien datapack
        // capitale donnait en boucle : l'effet suivait le joueur sur ville/ressource
        // via la synchro des effets et y rendait la faim inépuisable.
        // Les saturations temporaires légitimes (suspicious stew) sont conservées.
        MobEffectInstance sat = sp.getEffect(MobEffects.SATURATION);
        if (sat != null && sat.isInfiniteDuration()) {
            sp.removeEffect(MobEffects.SATURATION);
        }

        // Gel de la faim : l'exhaustion est remise à zéro à chaque tick, donc le
        // niveau de nourriture ne descend jamais — et rien ne le fait remonter.
        if (RolynkConfig.protectionFaimGelee()) {
            sp.getFoodData().setExhaustion(0.0F);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (RolynkConfig.protectionDegatsJoueurs() && event.getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
        }
    }
}
