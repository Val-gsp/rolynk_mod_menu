package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — profil complet du joueur connecté.
 * Envoyé : à la connexion, sur ProfileRequestPayload, après une action de ville.
 *
 * Champs :
 *   uuid, status, pseudo, grade, money, cristaux, heuresDeJeu, premiereConnexion, villeNom
 */
public record ProfilePayload(
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

    public static final Type<ProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profile"));

    public static final StreamCodec<FriendlyByteBuf, ProfilePayload> STREAM_CODEC =
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
                    buf -> new ProfilePayload(
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
