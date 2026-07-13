package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C — résultat de l'application du skin.
 * ok=true : l'écran de skin se ferme (skin appliqué ou défaut conservé).
 * ok=false : {@code message} s'affiche dans l'écran (URL/MineSkin en échec).
 */
public record SkinResultPayload(boolean ok, String message) implements CustomPacketPayload {

    public static final Type<SkinResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "skin_result"));

    public static final StreamCodec<FriendlyByteBuf, SkinResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBoolean(p.ok()); buf.writeUtf(p.message(), 256); },
                    buf -> new SkinResultPayload(buf.readBoolean(), buf.readUtf(256))
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
