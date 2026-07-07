package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — le joueur vote pour une ville.
 *
 * @param villeId id de la ville choisie.
 *                Validé côté serveur : existence en DB, joueur non-membre,
 *                pas encore voté aujourd'hui. Le montant vient de RolynkConfig,
 *                jamais du client.
 */
public record VoteVilleActionPayload(int villeId) implements CustomPacketPayload {

    public static final Type<VoteVilleActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "vote_ville_action"));

    public static final StreamCodec<FriendlyByteBuf, VoteVilleActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.villeId()),
                    buf -> new VoteVilleActionPayload(buf.readVarInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
