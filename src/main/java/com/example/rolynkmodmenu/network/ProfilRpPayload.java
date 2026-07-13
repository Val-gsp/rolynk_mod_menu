package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — profil RP du joueur connecté.
 * Envoyé : à la connexion et sur ProfilRpRequestPayload.
 *
 * Si {@code existe} est false, tous les champs sont vides : le client ouvre
 * alors automatiquement l'écran « Création Profil RP » (première connexion).
 */
public record ProfilRpPayload(
        boolean existe,
        String nom,
        String prenom,
        String sexe,
        String taille,
        String ancienneVille,
        String nouvelleVille,
        String metier,
        String description
) implements CustomPacketPayload {

    public static final Type<ProfilRpPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_rp"));

    public static final StreamCodec<FriendlyByteBuf, ProfilRpPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.existe());
                        buf.writeUtf(p.nom());
                        buf.writeUtf(p.prenom());
                        buf.writeUtf(p.sexe());
                        buf.writeUtf(p.taille());
                        buf.writeUtf(p.ancienneVille());
                        buf.writeUtf(p.nouvelleVille());
                        buf.writeUtf(p.metier());
                        buf.writeUtf(p.description());
                    },
                    buf -> new ProfilRpPayload(
                            buf.readBoolean(),
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

    /** Payload « profil absent » (première connexion). */
    public static ProfilRpPayload absent() {
        return new ProfilRpPayload(false, "", "", "", "", "", "", "", "");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
