package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — le joueur valide son choix de skin depuis l'écran de personnalisation.
 * url vide = garder le skin par défaut (aucun changement).
 */
public record SkinApplyPayload(String url) implements CustomPacketPayload {

    public static final Type<SkinApplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "skin_apply"));

    public static final StreamCodec<FriendlyByteBuf, SkinApplyPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.url(), 512),
                    buf -> new SkinApplyPayload(buf.readUtf(512))
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
