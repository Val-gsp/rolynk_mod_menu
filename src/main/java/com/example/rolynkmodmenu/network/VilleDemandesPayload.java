package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** S2C — liste des demandes d'adhésion en attente. */
public record VilleDemandesPayload(List<DemandeEntry> demandes) implements CustomPacketPayload {

    public record DemandeEntry(String uuid, String pseudo, String timestamp) {}

    public static final Type<VilleDemandesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_demandes"));

    public static final StreamCodec<FriendlyByteBuf, VilleDemandesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.demandes().size());
                        for (DemandeEntry d : p.demandes()) {
                            buf.writeUtf(d.uuid());
                            buf.writeUtf(d.pseudo());
                            buf.writeUtf(d.timestamp());
                        }
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<DemandeEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new DemandeEntry(buf.readUtf(), buf.readUtf(), buf.readUtf()));
                        }
                        return new VilleDemandesPayload(list);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
