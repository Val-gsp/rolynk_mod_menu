package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le client demande les demandes d'adhésion en attente d'une ville. */
public record VilleDemandesRequestPayload(int villeId) implements CustomPacketPayload {

    public static final Type<VilleDemandesRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_demandes_req"));

    public static final StreamCodec<FriendlyByteBuf, VilleDemandesRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.villeId()),
                    buf -> new VilleDemandesRequestPayload(buf.readInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
