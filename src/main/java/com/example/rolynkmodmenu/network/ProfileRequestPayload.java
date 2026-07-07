package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S vide — le client demande son propre profil. */
public record ProfileRequestPayload() implements CustomPacketPayload {

    public static final Type<ProfileRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profile_request"));

    public static final StreamCodec<FriendlyByteBuf, ProfileRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new ProfileRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
