package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S vide — le client demande la liste de toutes les villes. */
public record VilleListRequestPayload() implements CustomPacketPayload {

    public static final Type<VilleListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_list_req"));

    public static final StreamCodec<FriendlyByteBuf, VilleListRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new VilleListRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
