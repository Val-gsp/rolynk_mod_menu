package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — le joueur vend un lot de la Boutique Jeux.
 * @param offreId index de l'offre dans le catalogue serveur.
 *                Validé côté serveur par InputValidator.isValidOffreBoutique.
 */
public record BoutiqueVendrePayload(int offreId) implements CustomPacketPayload {

    public static final Type<BoutiqueVendrePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "boutique_vendre"));

    public static final StreamCodec<FriendlyByteBuf, BoutiqueVendrePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.offreId()),
                    buf -> new BoutiqueVendrePayload(buf.readVarInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
