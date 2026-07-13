package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — résultat de la création du profil RP.
 * ok=true : le client ferme l'écran de création (un ProfilRpPayload à jour suit).
 * ok=false : le client affiche {@code message} dans le formulaire.
 */
public record ProfilRpResultPayload(boolean ok, String message) implements CustomPacketPayload {

    public static final Type<ProfilRpResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "profil_rp_result"));

    public static final StreamCodec<FriendlyByteBuf, ProfilRpResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBoolean(p.ok()); buf.writeUtf(p.message(), 256); },
                    buf -> new ProfilRpResultPayload(buf.readBoolean(), buf.readUtf(256))
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
