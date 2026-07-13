package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — le client demande le catalogue de rachat de la Boutique Jeux.
 * Payload vide : l'identité vient de la connexion, le catalogue de BoutiqueConfig.
 */
public record BoutiqueCatalogRequestPayload() implements CustomPacketPayload {

    public static final Type<BoutiqueCatalogRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "boutique_catalog_request"));

    public static final StreamCodec<FriendlyByteBuf, BoutiqueCatalogRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new BoutiqueCatalogRequestPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
