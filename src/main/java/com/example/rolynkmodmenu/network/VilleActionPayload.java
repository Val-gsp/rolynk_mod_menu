package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S — action de gestion de ville.
 *
 * Actions supportées :
 *   "kick"       villeId + targetUuid
 *   "deposer"    villeId + montant
 *   "retirer"    villeId + montant
 *   "acc_dem"    villeId + targetUuid
 *   "ref_dem"    villeId + targetUuid
 *   "grade"      villeId + targetUuid + extra (nouveau grade)
 *   "dissoudre"  villeId
 *   "quitter"    villeId
 *   "set_rec"    villeId + extra ("ouvert" | "sur_demande" | "ferme")
 *
 * La liste exhaustive côté serveur est InputValidator.VALID_ACTIONS.
 */
public record VilleActionPayload(
        String action,
        int    villeId,
        String targetUuid,
        double montant,
        String extra
) implements CustomPacketPayload {

    public static final Type<VilleActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "ville_action"));

    public static final StreamCodec<FriendlyByteBuf, VilleActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.action());
                        buf.writeInt(p.villeId());
                        buf.writeUtf(p.targetUuid() == null ? "" : p.targetUuid());
                        buf.writeDouble(p.montant());
                        buf.writeUtf(p.extra() == null ? "" : p.extra());
                    },
                    buf -> new VilleActionPayload(
                            buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readDouble(), buf.readUtf())
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
