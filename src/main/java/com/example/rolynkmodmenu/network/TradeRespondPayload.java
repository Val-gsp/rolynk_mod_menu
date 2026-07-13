package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S — accepte ou refuse la demande de trade envoyée par {@code demandeur}. */
public record TradeRespondPayload(String demandeur, boolean accepte) implements CustomPacketPayload {

    public static final Type<TradeRespondPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_respond"));

    public static final StreamCodec<FriendlyByteBuf, TradeRespondPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeUtf(p.demandeur(), 16); buf.writeBoolean(p.accepte()); },
                    buf -> new TradeRespondPayload(buf.readUtf(16), buf.readBoolean())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
