package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — joueurs connectés au même backend (hors soi-même) + demandes de trade reçues.
 * @param joueurs   pseudos échangeables
 * @param demandes  pseudos ayant envoyé une demande de trade au destinataire
 */
public record TradeListPayload(List<String> joueurs, List<String> demandes) implements CustomPacketPayload {

    public static final Type<TradeListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_list"));

    public static final StreamCodec<FriendlyByteBuf, TradeListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.joueurs().size());
                        for (String s : p.joueurs()) buf.writeUtf(s, 16);
                        buf.writeVarInt(p.demandes().size());
                        for (String s : p.demandes()) buf.writeUtf(s, 16);
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        List<String> joueurs = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) joueurs.add(buf.readUtf(16));
                        int m = buf.readVarInt();
                        List<String> demandes = new ArrayList<>(m);
                        for (int i = 0; i < m; i++) demandes.add(buf.readUtf(16));
                        return new TradeListPayload(joueurs, demandes);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
