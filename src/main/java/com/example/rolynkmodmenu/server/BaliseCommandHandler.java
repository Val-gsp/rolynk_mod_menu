package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.BaliseListPayload;
import com.example.rolynkmodmenu.network.SwitchServerPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Commandes /balise — toutes les opérations DB sur DB_EXECUTOR.
 * Max balises lu depuis grade_limits via JOIN joueurs (plus de dépendance LuckPerms).
 */
public final class BaliseCommandHandler {

    private BaliseCommandHandler() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balise")

            // ── /balise creer <nom> ───────────────────────────────────────
            .then(Commands.literal("creer")
                .then(Commands.argument("nom", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        String nom   = StringArgumentType.getString(ctx, "nom");
                        String uuid  = sp.getUUID().toString();
                        String monde = RolynkConfig.serverName();
                        int x = (int) sp.getX(), y = (int) sp.getY(), z = (int) sp.getZ();

                        // ── Validation du nom de balise ────────────────────────
                        if (!InputValidator.isValidBaliseName(nom)) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cNom invalide. 1–32 caractères, lettres/chiffres/tirets/underscores uniquement."));
                            return 0;
                        }

                        CompletableFuture.runAsync(() -> {
                            int     max = BaliseStore.getMaxBalises(uuid);
                            boolean ok  = BaliseStore.addBalise(uuid, nom, monde, x, y, z, max);
                            sp.getServer().execute(() -> {
                                if (ok) {
                                    sp.sendSystemMessage(Component.literal(
                                            "§aBalise §e" + nom + "§a créée en §e" + monde
                                            + "§a (" + x + ", " + y + ", " + z + ")"));
                                    sendBaliseList(sp);
                                } else {
                                    sp.sendSystemMessage(Component.literal(
                                            "§cImpossible de créer §e" + nom
                                            + "§c : nom déjà utilisé ou limite de §e" + max + "§c balises atteinte."));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);

                        return 1;
                    })
                )
            )

            // ── /balise tp <nom> ─────────────────────────────────────────
            .then(Commands.literal("tp")
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        String nom  = StringArgumentType.getString(ctx, "nom");
                        String uuid = sp.getUUID().toString();

                        CompletableFuture.runAsync(() -> {
                            BaliseStore.BaliseEntry entry = BaliseStore.getBalise(uuid, nom);
                            sp.getServer().execute(() -> {
                                if (entry == null) {
                                    sp.sendSystemMessage(Component.literal(
                                            "§cBalise §e" + nom + "§c introuvable."));
                                    return;
                                }
                                if (entry.monde().equals(RolynkConfig.serverName())) {
                                    sp.teleportTo(entry.x() + 0.5, entry.y(), entry.z() + 0.5);
                                    sp.sendSystemMessage(Component.literal(
                                            "§aTélétransporté à §e" + entry.nom()
                                            + "§a (" + entry.x() + ", " + entry.y() + ", " + entry.z() + ")"));
                                } else {
                                    CompletableFuture.runAsync(
                                            () -> BaliseStore.storePendingTeleport(uuid, entry.x(), entry.y(), entry.z()),
                                            BaliseStore.DB_EXECUTOR);
                                    sp.sendSystemMessage(Component.literal(
                                            "§aTéléportation vers §e" + entry.monde() + "§a..."));
                                    PacketDistributor.sendToPlayer(sp, new SwitchServerPayload(entry.monde()));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);

                        return 1;
                    })
                )
            )

            // ── /balise supprimer <nom> ───────────────────────────────────
            .then(Commands.literal("supprimer")
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        String nom  = StringArgumentType.getString(ctx, "nom");
                        String uuid = sp.getUUID().toString();

                        CompletableFuture.runAsync(() -> {
                            boolean ok = BaliseStore.removeBalise(uuid, nom);
                            sp.getServer().execute(() -> {
                                if (ok) {
                                    sp.sendSystemMessage(Component.literal(
                                            "§aBalise §e" + nom + "§a supprimée."));
                                    sendBaliseList(sp);
                                } else {
                                    sp.sendSystemMessage(Component.literal(
                                            "§cBalise §e" + nom + "§c introuvable."));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);

                        return 1;
                    })
                )
            )
        );
    }

    /** Envoie la liste + limite au joueur (tout sur DB_EXECUTOR). */
    public static void sendBaliseList(ServerPlayer sp) {
        String uuid = sp.getUUID().toString();
        CompletableFuture.runAsync(() -> {
            List<BaliseStore.BaliseEntry> entries = BaliseStore.getBalises(uuid);
            int max = BaliseStore.getMaxBalises(uuid);
            List<BaliseListPayload.BaliseEntry> net = entries.stream()
                    .map(e -> new BaliseListPayload.BaliseEntry(e.nom(), e.monde(), e.x(), e.y(), e.z()))
                    .toList();
            sp.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(sp, new BaliseListPayload(net, max)));
        }, BaliseStore.DB_EXECUTOR);
    }
}
