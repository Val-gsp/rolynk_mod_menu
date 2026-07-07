package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — liste des membres + grade du joueur courant + sa money personnelle.
 * playerMoney = -1 si introuvable.
 */
public record VilleMembresPayload(List<MembreEntry> membres, String myGrade, double playerMoney)
        implements CustomPacketPayload {

    /** Un membre de la ville. */
    public record MembreEntry(String uuid, String pseudo, String grade, boolean online) {}

    public static final Type<VilleMembresPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_membres"));

    public static final StreamCodec<FriendlyByteBuf, VilleMembresPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.myGrade());
                        buf.writeDouble(p.playerMoney());
                        buf.writeInt(p.membres().size());
                        for (MembreEntry m : p.membres()) {
                            buf.writeUtf(m.uuid());
                            buf.writeUtf(m.pseudo());
                            buf.writeUtf(m.grade());
                            buf.writeBoolean(m.online());
                        }
                    },
                    buf -> {
                        String myGrade     = buf.readUtf();
                        double playerMoney = buf.readDouble();
                        int count          = buf.readInt();
                        List<MembreEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new MembreEntry(
                                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
                        }
                        return new VilleMembresPayload(list, myGrade, playerMoney);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
