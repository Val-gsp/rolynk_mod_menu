package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le client demande la liste des membres d'une ville. */
public record VilleMembresRequestPayload(int villeId) implements CustomPacketPayload {

    public static final Type<VilleMembresRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_membres_req"));

    public static final StreamCodec<FriendlyByteBuf, VilleMembresRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.villeId()),
                    buf -> new VilleMembresRequestPayload(buf.readInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
