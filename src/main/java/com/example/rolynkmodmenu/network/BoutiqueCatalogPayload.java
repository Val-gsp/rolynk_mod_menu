package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — catalogue de rachat de la Boutique Jeux.
 *
 * Le serveur est seul maître des prix (BoutiqueConfig) : le client ne fait
 * qu'afficher. L'index dans la liste sert d'identifiant d'offre pour
 * {@link BoutiqueVendrePayload} — validé côté serveur contre le catalogue.
 *
 * @param offres offres de rachat, dans l'ordre du catalogue serveur
 */
public record BoutiqueCatalogPayload(List<Offre> offres) implements CustomPacketPayload {

    /**
     * Une offre de rachat : le serveur rachète {@code quantite} × {@code itemId}
     * pour {@code prix} money.
     */
    public record Offre(String itemId, int quantite, double prix) {}

    public static final Type<BoutiqueCatalogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "boutique_catalog"));

    public static final StreamCodec<FriendlyByteBuf, BoutiqueCatalogPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.offres().size());
                        for (Offre o : p.offres()) {
                            buf.writeUtf(o.itemId());
                            buf.writeVarInt(o.quantite());
                            buf.writeDouble(o.prix());
                        }
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        List<Offre> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new Offre(buf.readUtf(), buf.readVarInt(), buf.readDouble()));
                        }
                        return new BoutiqueCatalogPayload(list);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
