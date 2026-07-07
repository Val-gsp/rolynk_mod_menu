package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — notifie le client de son appartenance à une ville.
 * Envoyé à la connexion et après chaque changement d'appartenance.
 * villeNom = "" si le joueur n'a pas de ville.
 */
public record VilleProfilePayload(String villeNom) implements CustomPacketPayload {

    public static final Type<VilleProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_profile"));

    public static final StreamCodec<FriendlyByteBuf, VilleProfilePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.villeNom() == null ? "" : p.villeNom()),
                    buf -> new VilleProfilePayload(buf.readUtf())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
