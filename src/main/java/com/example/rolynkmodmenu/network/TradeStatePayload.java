package com.example.rolynkmodmenu.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — état complet de la session de trade, renvoyé après chaque action.
 *
 * Le serveur est seul maître de l'état : le client ne fait qu'afficher.
 * Les stacks sont des COPIES d'affichage (les vrais items restent dans les
 * inventaires jusqu'à l'exécution finale du trade).
 *
 * Nécessite un RegistryFriendlyByteBuf (codec ItemStack) — fourni par le
 * canal play de NeoForge.
 *
 * @param partenaire     pseudo de l'autre joueur
 * @param monOffre       stacks que J'offre
 * @param sonOffre       stacks qu'IL offre
 * @param monArgent      money que j'offre
 * @param sonArgent      money qu'il offre
 * @param monSolde       mon solde actuel en banque (-1 = pas encore chargé) —
 *                       lu en DB à l'ouverture de la session, affichage uniquement
 * @param maConfirmation j'ai confirmé
 * @param saConfirmation il a confirmé
 */
public record TradeStatePayload(
        String partenaire,
        List<ItemStack> monOffre,
        List<ItemStack> sonOffre,
        double monArgent,
        double sonArgent,
        double monSolde,
        boolean maConfirmation,
        boolean saConfirmation
) implements CustomPacketPayload {

    public static final Type<TradeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "trade_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.partenaire(), 16);
                        writeStacks(buf, p.monOffre());
                        writeStacks(buf, p.sonOffre());
                        buf.writeDouble(p.monArgent());
                        buf.writeDouble(p.sonArgent());
                        buf.writeDouble(p.monSolde());
                        buf.writeBoolean(p.maConfirmation());
                        buf.writeBoolean(p.saConfirmation());
                    },
                    buf -> new TradeStatePayload(
                            buf.readUtf(16),
                            readStacks(buf),
                            readStacks(buf),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readBoolean(),
                            buf.readBoolean()
                    )
            );

    private static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks) {
        buf.writeVarInt(stacks.size());
        for (ItemStack st : stacks) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, st);
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        return list;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
