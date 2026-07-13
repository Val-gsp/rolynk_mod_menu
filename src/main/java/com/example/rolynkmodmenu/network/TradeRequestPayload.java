package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — envoie une demande de trade au joueur {@code cible}.
 * Pseudo validé côté serveur (InputValidator + présence sur le backend).
 */
public record TradeRequestPayload(String cible) implements CustomPacketPayload {

    public static final Type<TradeRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_request"));

    public static final StreamCodec<FriendlyByteBuf, TradeRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.cible(), 16),
                    buf -> new TradeRequestPayload(buf.readUtf(16))
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
