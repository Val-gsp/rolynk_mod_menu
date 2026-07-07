package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — le joueur réclame un palier de récompense temps de jeu.
 * @param palier index du palier : 0 = 30 min, 1 = 2 h, 2 = 4 h.
 *               Validé côté serveur par InputValidator.isValidPalierRecompense.
 */
public record RecompenseClaimPayload(int palier) implements CustomPacketPayload {

    public static final Type<RecompenseClaimPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "recompense_claim"));

    public static final StreamCodec<FriendlyByteBuf, RecompenseClaimPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.palier()),
                    buf -> new RecompenseClaimPayload(buf.readVarInt())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
