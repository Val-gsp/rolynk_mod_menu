package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — profil d'un autre joueur (consultation depuis GestionVilleScreen / DetailsVilleScreen).
 * Structure identique à ProfilePayload.
 */
public record ProfilJoueurPayload(
        String uuid,
        String status,
        String pseudo,
        String grade,
        String money,
        String cristaux,
        String heuresDeJeu,
        String premiereConnexion,
        String villeNom
) implements CustomPacketPayload {

    public static final Type<ProfilJoueurPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_joueur"));

    public static final StreamCodec<FriendlyByteBuf, ProfilJoueurPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.uuid());
                        buf.writeUtf(p.status());
                        buf.writeUtf(p.pseudo());
                        buf.writeUtf(p.grade());
                        buf.writeUtf(p.money());
                        buf.writeUtf(p.cristaux());
                        buf.writeUtf(p.heuresDeJeu());
                        buf.writeUtf(p.premiereConnexion());
                        buf.writeUtf(p.villeNom());
                    },
                    buf -> new ProfilJoueurPayload(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf()
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
