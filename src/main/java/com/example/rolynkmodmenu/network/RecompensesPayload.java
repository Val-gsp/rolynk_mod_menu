package com.example.rolynkmodmenu.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C — état complet des récompenses quotidiennes du joueur.
 *
 * Tous les seuils et montants viennent du serveur (RolynkConfig) pour que
 * l'UI affiche toujours les valeurs réelles, même si le joueur bidouille son client.
 *
 * @param tempsJeuSecondes  temps de jeu cumulé aujourd'hui (tous serveurs du réseau)
 * @param paliers           les 3 paliers temps de jeu, dans l'ordre (30 min, 2 h, 4 h)
 * @param voteVilleEffectue le joueur a-t-il déjà voté pour une ville aujourd'hui ?
 * @param villeVoteeNom     nom de la ville votée (vide si pas encore voté)
 * @param maVilleNom        nom de la ville du joueur (vide si sans ville)
 * @param montantVoteVille  récompense en money pour un vote de ville (config serveur)
 * @param blocsParcourus    blocs XZ parcourus aujourd'hui (après flush in-memory)
 * @param explorationRecue  la récompense d'exploration a-t-elle déjà été réclamée ?
 * @param seuilBlocs        seuil de blocs à atteindre (config serveur)
 * @param montantExploration récompense en money pour l'exploration (config serveur)
 */
public record RecompensesPayload(
        int tempsJeuSecondes,
        List<PalierEntry> paliers,
        boolean voteVilleEffectue,
        String villeVoteeNom,
        String maVilleNom,
        double montantVoteVille,
        int blocsParcourus,
        boolean explorationRecue,
        int seuilBlocs,
        double montantExploration
) implements CustomPacketPayload {

    /** Un palier de récompense temps de jeu. */
    public record PalierEntry(int seuilSecondes, double montant, boolean recupere) {}

    public static final Type<RecompensesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rolynk_mod_menu", "recompenses"));

    public static final StreamCodec<FriendlyByteBuf, RecompensesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        // ── Play time ─────────────────────────────────────
                        buf.writeVarInt(p.tempsJeuSecondes());
                        buf.writeVarInt(p.paliers().size());
                        for (PalierEntry e : p.paliers()) {
                            buf.writeVarInt(e.seuilSecondes());
                            buf.writeDouble(e.montant());
                            buf.writeBoolean(e.recupere());
                        }
                        // ── Vote ville ────────────────────────────────────
                        buf.writeBoolean(p.voteVilleEffectue());
                        buf.writeUtf(p.villeVoteeNom());
                        buf.writeUtf(p.maVilleNom());
                        buf.writeDouble(p.montantVoteVille());
                        // ── Exploration ───────────────────────────────────
                        buf.writeVarInt(p.blocsParcourus());
                        buf.writeBoolean(p.explorationRecue());
                        buf.writeVarInt(p.seuilBlocs());
                        buf.writeDouble(p.montantExploration());
                    },
                    buf -> {
                        // ── Play time ─────────────────────────────────────
                        int temps = buf.readVarInt();
                        int count = buf.readVarInt();
                        List<PalierEntry> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            list.add(new PalierEntry(
                                    buf.readVarInt(), buf.readDouble(), buf.readBoolean()));
                        }
                        // ── Vote ville ────────────────────────────────────
                        boolean voteEffectue = buf.readBoolean();
                        String villeVoteeNom = buf.readUtf();
                        String maVilleNom    = buf.readUtf();
                        double montantVote   = buf.readDouble();
                        // ── Exploration ───────────────────────────────────
                        int blocsParcourus    = buf.readVarInt();
                        boolean exploRecue    = buf.readBoolean();
                        int seuilBlocs        = buf.readVarInt();
                        double montantExplo   = buf.readDouble();
                        return new RecompensesPayload(temps, list,
                                voteEffectue, villeVoteeNom, maVilleNom, montantVote,
                                blocsParcourus, exploRecue, seuilBlocs, montantExplo);
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
