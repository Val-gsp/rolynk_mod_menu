package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — soumission du formulaire « Création Profil RP » (première connexion).
 * La nouvelle ville n'est pas transmise : le serveur l'impose ("Rolynk").
 * Tous les champs sont validés côté serveur (InputValidator).
 */
public record ProfilRpCreatePayload(
        String nom,
        String prenom,
        String sexe,
        String taille,
        String ancienneVille,
        String metier,
        String description
) implements CustomPacketPayload {

    public static final Type<ProfilRpCreatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_rp_create"));

    public static final StreamCodec<FriendlyByteBuf, ProfilRpCreatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.nom(), 64);
                        buf.writeUtf(p.prenom(), 64);
                        buf.writeUtf(p.sexe(), 32);
                        buf.writeUtf(p.taille(), 32);
                        buf.writeUtf(p.ancienneVille(), 64);
                        buf.writeUtf(p.metier(), 64);
                        buf.writeUtf(p.description(), 512);
                    },
                    buf -> new ProfilRpCreatePayload(
                            buf.readUtf(64),
                            buf.readUtf(64),
                            buf.readUtf(32),
                            buf.readUtf(32),
                            buf.readUtf(64),
                            buf.readUtf(64),
                            buf.readUtf(512)
                    )
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
