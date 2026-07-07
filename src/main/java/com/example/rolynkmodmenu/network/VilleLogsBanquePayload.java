package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** S2C — historique des 40 dernières transactions bancaires d'une ville. */
public record VilleLogsBanquePayload(List<LogEntry> entries) implements CustomPacketPayload {

    /** Une ligne de transaction. action = "depot" ou "retrait". */
    public record LogEntry(String pseudo, String action, double montant, String timestamp) {}

    public static final Type<VilleLogsBanquePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_logs_banque"));

    public static final StreamCodec<FriendlyByteBuf, VilleLogsBanquePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.entries().size());
                        for (LogEntry e : p.entries()) {
                            buf.writeUtf(e.pseudo());
                            buf.writeUtf(e.action());
                            buf.writeDouble(e.montant());
                            buf.writeUtf(e.timestamp());
                        }
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<LogEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new LogEntry(
                                    buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readUtf()));
                        }
                        return new VilleLogsBanquePayload(list);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
