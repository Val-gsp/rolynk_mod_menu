package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Paquet S2C : le serveur demande au client d'exécuter "/server <nom>".
 * Velocity intercepte cette commande au niveau du proxy et transfère le joueur.
 */
public record SwitchServerPayload(String serverName) implements CustomPacketPayload {

    public static final Type<SwitchServerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "switch_server"));

    public static final StreamCodec<FriendlyByteBuf, SwitchServerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.serverName()),
            buf -> new SwitchServerPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
