package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — le serveur envoie la liste de toutes les villes au client.
 */
public record VilleListPayload(List<VilleEntry> entries) implements CustomPacketPayload {

    /**
     * recrutement : "ouvert" | "sur_demande" | "ferme"
     *   ouvert     → le joueur rejoint directement (/ville rejoindre)
     *   sur_demande → le joueur postule, Chef/Adjoint accepte/refuse (/ville demande)
     *   ferme       → impossible de rejoindre
     */
    public record VilleEntry(
            int    id,
            String nom,
            String ownerPseudo,
            String mondeSpawn,
            double banque,
            int    nbMembres,
            int    nbConnectes,
            String recrutement,
            String dateFondation,
            int    totalChunks
    ) {}

    public static final Type<VilleListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_list"));

    public static final StreamCodec<FriendlyByteBuf, VilleListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.entries().size());
                        for (VilleEntry e : p.entries()) {
                            buf.writeInt(e.id());
                            buf.writeUtf(e.nom());
                            buf.writeUtf(e.ownerPseudo());
                            buf.writeUtf(e.mondeSpawn() == null ? "" : e.mondeSpawn());
                            buf.writeDouble(e.banque());
                            buf.writeInt(e.nbMembres());
                            buf.writeInt(e.nbConnectes());
                            buf.writeUtf(e.recrutement() == null ? "sur_demande" : e.recrutement());
                            buf.writeUtf(e.dateFondation() == null ? "" : e.dateFondation());
                            buf.writeInt(e.totalChunks());
                        }
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<VilleEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new VilleEntry(
                                    buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                    buf.readDouble(), buf.readInt(), buf.readInt(),
                                    buf.readUtf(), buf.readUtf(), buf.readInt()
                            ));
                        }
                        return new VilleListPayload(list);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
