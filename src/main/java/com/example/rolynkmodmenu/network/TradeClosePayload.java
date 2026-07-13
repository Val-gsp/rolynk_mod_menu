package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C — la session de trade est terminée (succès, annulation, déconnexion...). Ferme l'écran. */
public record TradeClosePayload() implements CustomPacketPayload {

    public static final Type<TradeClosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_close"));

    public static final StreamCodec<FriendlyByteBuf, TradeClosePayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new TradeClosePayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
