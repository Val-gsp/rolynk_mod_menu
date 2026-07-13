package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — la demande a été acceptée : ouvre l'écran de trade avec {@code partenaire}. */
public record TradeOpenPayload(String partenaire) implements CustomPacketPayload {

    public static final Type<TradeOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_open"));

    public static final StreamCodec<FriendlyByteBuf, TradeOpenPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.partenaire(), 16),
                    buf -> new TradeOpenPayload(buf.readUtf(16))
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
