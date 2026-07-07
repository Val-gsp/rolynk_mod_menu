package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — résultat d'une action de gestion de ville.
 * newBanque      = solde banque ville mis à jour (vide si non applicable)
 * newPlayerMoney = solde personnel mis à jour   (-1 si non applicable)
 */
public record VilleActionResultPayload(
        boolean success,
        String  message,
        String  newBanque,
        double  newPlayerMoney
) implements CustomPacketPayload {

    public static final Type<VilleActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_action_result"));

    public static final StreamCodec<FriendlyByteBuf, VilleActionResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.success());
                        buf.writeUtf(p.message());
                        buf.writeUtf(p.newBanque() == null ? "" : p.newBanque());
                        buf.writeDouble(p.newPlayerMoney());
                    },
                    buf -> new VilleActionResultPayload(
                            buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readDouble())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
