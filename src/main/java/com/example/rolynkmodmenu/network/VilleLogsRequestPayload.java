package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le client demande les logs bancaires d'une ville. */
public record VilleLogsRequestPayload(int villeId) implements CustomPacketPayload {

    public static final Type<VilleLogsRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_logs_req"));

    public static final StreamCodec<FriendlyByteBuf, VilleLogsRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.villeId()),
                    buf -> new VilleLogsRequestPayload(buf.readInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
