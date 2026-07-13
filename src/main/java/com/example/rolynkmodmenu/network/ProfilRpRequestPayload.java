package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le joueur demande son propre profil RP. */
public record ProfilRpRequestPayload() implements CustomPacketPayload {

    public static final Type<ProfilRpRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_rp_request"));

    public static final StreamCodec<FriendlyByteBuf, ProfilRpRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new ProfilRpRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
