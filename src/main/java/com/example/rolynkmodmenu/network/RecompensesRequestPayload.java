package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S vide — le client demande l'état de ses récompenses quotidiennes. */
public record RecompensesRequestPayload() implements CustomPacketPayload {

    public static final Type<RecompensesRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "recompenses_request"));

    public static final StreamCodec<FriendlyByteBuf, RecompensesRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new RecompensesRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
