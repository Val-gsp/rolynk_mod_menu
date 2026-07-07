package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Paquet C2S vide — le client demande la liste de ses balises au serveur.
 */
public record BaliseListRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BaliseListRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "balise_list_request")
            );

    public static final StreamCodec<FriendlyByteBuf, BaliseListRequestPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new BaliseListRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
