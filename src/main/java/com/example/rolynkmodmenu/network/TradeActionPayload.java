package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — action dans la session de trade en cours.
 *
 * @param action  ADD_ITEM(0, slot inventaire), REMOVE_ITEM(1, index offre),
 *                SET_MONEY(2, montant), CONFIRM(3), UNCONFIRM(4), CANCEL(5)
 * @param intValue    slot / index (selon action, sinon 0)
 * @param intValue2   quantité offerte (ADD_ITEM uniquement, sinon 0) —
 *                    clampée côté serveur au contenu réel du slot
 * @param doubleValue montant money (SET_MONEY uniquement, sinon 0)
 */
public record TradeActionPayload(int action, int intValue, int intValue2, double doubleValue)
        implements CustomPacketPayload {

    public static final int ADD_ITEM    = 0;
    public static final int REMOVE_ITEM = 1;
    public static final int SET_MONEY   = 2;
    public static final int CONFIRM     = 3;
    public static final int UNCONFIRM   = 4;
    public static final int CANCEL      = 5;

    public static final Type<TradeActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_action"));

    public static final StreamCodec<FriendlyByteBuf, TradeActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.action());
                        buf.writeVarInt(p.intValue());
                        buf.writeVarInt(p.intValue2());
                        buf.writeDouble(p.doubleValue());
                    },
                    buf -> new TradeActionPayload(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
