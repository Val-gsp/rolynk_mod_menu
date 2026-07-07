package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le client demande le profil d'un autre joueur par UUID. */
public record ProfilJoueurRequestPayload(String uuid) implements CustomPacketPayload {

    public static final Type<ProfilJoueurRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_joueur_req"));

    public static final StreamCodec<FriendlyByteBuf, ProfilJoueurRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.uuid()),
                    buf -> new ProfilJoueurRequestPayload(buf.readUtf())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
