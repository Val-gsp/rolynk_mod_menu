package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S vide — le joueur réclame sa récompense d'exploration quotidienne.
 *
 * Le seuil et le montant sont lus côté serveur (RolynkConfig) — le client
 * n'envoie aucune valeur falsifiable.
 */
public record ExplorationClaimPayload() implements CustomPacketPayload {

    public static final Type<ExplorationClaimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "exploration_claim"));

    public static final StreamCodec<FriendlyByteBuf, ExplorationClaimPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new ExplorationClaimPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
