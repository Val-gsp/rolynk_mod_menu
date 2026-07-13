package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — le client demande la liste des joueurs connectés (même backend) + demandes de trade reçues. */
public record TradeListRequestPayload() implements CustomPacketPayload {

    public static final Type<TradeListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_list_request"));

    public static final StreamCodec<FriendlyByteBuf, TradeListRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new TradeListRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
