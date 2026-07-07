package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Paquet S2C — le serveur envoie la liste des balises + la limite du grade du joueur.
 */
public record BaliseListPayload(List<BaliseEntry> entries, int maxBalises) implements CustomPacketPayload {

    public record BaliseEntry(String nom, String monde, int x, int y, int z) {}

    public static final CustomPacketPayload.Type<BaliseListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "balise_list")
            );

    public static final StreamCodec<FriendlyByteBuf, BaliseListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.maxBalises());
                        buf.writeInt(payload.entries().size());
                        for (BaliseEntry e : payload.entries()) {
                            buf.writeUtf(e.nom());
                            buf.writeUtf(e.monde());
                            buf.writeInt(e.x());
                            buf.writeInt(e.y());
                            buf.writeInt(e.z());
                        }
                    },
                    buf -> {
                        int max   = buf.readInt();
                        int count = buf.readInt();
                        List<BaliseEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new BaliseEntry(
                                    buf.readUtf(), buf.readUtf(),
                                    buf.readInt(), buf.readInt(), buf.readInt()));
                        }
                        return new BaliseListPayload(list, max);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
