package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.VilleProfilePayload;
import com.example.rolynkmodmenu.util.Money;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commandes /ville — toutes les opérations DB sur Database.EXECUTOR,
 * jamais sur le main thread (y compris les vérifications de grade).
 * Coûts et mondes de claim configurables via {@link RolynkConfig}.
 */
public final class VilleCommandHandler {

    // Cooldowns par UUID
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_COURT   = 2_000L;   // 2s
    private static final long COOLDOWN_LONG    = 30_000L;  // 30s

    // Rate-limit pour les requêtes de liste
    private static final Map<UUID, Long> LIST_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long LIST_COOLDOWN = 3_000L;      // 3s

    private VilleCommandHandler() {}

    // ── Cooldown ──────────────────────────────────────────────────────────

    private static boolean checkCooldown(ServerPlayer sp, long ms) {
        UUID uid = sp.getUUID();
        long now = System.currentTimeMillis();
        long last = COOLDOWNS.getOrDefault(uid, 0L);
        if (now - last < ms) {
            long reste = (ms - (now - last)) / 1000 + 1;
            sp.sendSystemMessage(Component.literal(
                    "§cAttends encore §e" + reste + "s §cavant de refaire cette commande."));
            return false;
        }
        COOLDOWNS.put(uid, now);
        return true;
    }

    public static void onPlayerLogout(ServerPlayer sp) {
        COOLDOWNS.remove(sp.getUUID());
        LIST_COOLDOWNS.remove(sp.getUUID());
    }

    // ── Envoi du profil ville ─────────────────────────────────────────────

    public static void sendVilleProfile(ServerPlayer sp) {
        String uuid = sp.getUUID().toString();
        // sendVilleProfile est appelé après chaque changement d'appartenance
        // (créer, quitter, dissoudre, rejoindre, accepter, kick) : le profil
        // mis en cache est donc périmé, on le force à se recharger.
        ServerProfileHandler.invalidateCache(uuid);
        // Pousse aussi un ProfilePayload frais : l'écran Profil détail lit en
        // direct ProfileDataManager → la ligne VILLE se met à jour en temps réel,
        // sans attendre une ré-ouverture de l'écran.
        ServerProfileHandler.sendProfile(sp);
        CompletableFuture.runAsync(() -> {
            String villeNom = VilleStore.fetchVilleNomForPlayer(uuid);
            sp.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(sp, new VilleProfilePayload(villeNom)));
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── Enregistrement des commandes ──────────────────────────────────────

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        d.register(Commands.literal("ville")

            // /ville creer <nom>
            // greedyString() capture tout (y compris les espaces) pour donner
            // un message d'erreur explicite plutôt que l'erreur Minecraft générique.
            .then(Commands.literal("creer")
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!checkCooldown(sp, COOLDOWN_LONG)) return 0;
                        String nom  = StringArgumentType.getString(ctx, "nom").trim();
                        String uuid = sp.getUUID().toString();

                        // ── Validation du nom de ville — messages explicites ────────────
                        if (nom.contains(" ")) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cLes espaces ne sont pas autorisés dans un nom de ville."
                                    + "\n§7Utilise §e_§7 à la place. Ex : §a/ville creer Mon_Village"));
                            return 0;
                        }
                        if (nom.length() < 3) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cNom trop court. §7Le nom doit faire au moins §e3 caractères§7."));
                            return 0;
                        }
                        if (nom.length() > 32) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cNom trop long. §7Maximum §e32 caractères§7 (tu en as " + nom.length() + ")."));
                            return 0;
                        }
                        // Détection des caractères spéciaux les plus courants
                        if (nom.contains("§")) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cLes codes de couleur (§) sont interdits dans un nom de ville."));
                            return 0;
                        }
                        if (nom.contains("'") || nom.contains("\"")) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cLes guillemets (' et \") sont interdits dans un nom de ville."));
                            return 0;
                        }
                        if (!InputValidator.isValidVilleName(nom)) {
                            sp.sendSystemMessage(Component.literal(
                                    "§cNom invalide : §e" + nom
                                    + "\n§7Caractères autorisés : §elettres §7(a-z, A-Z, accents), §echiffres§7, "
                                    + "§etirets (-)§7, §eunderscores (_)§7."));
                            return 0;
                        }

                        double cout = RolynkConfig.coutCreationVille();
                        CompletableFuture.runAsync(() -> {
                            // Vérification du solde, débit et création sont atomiques dans VilleStore
                            String err = VilleStore.creerVille(nom, uuid, cout);
                            sp.getServer().execute(() -> {
                                if (err == null) {
                                    sp.sendSystemMessage(Component.literal(
                                            "§aVille §e" + nom + "§a créée ! §7(−" + Money.entier(cout) + ")"));
                                    sendVilleProfile(sp);
                                } else {
                                    sp.sendSystemMessage(Component.literal(err));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);
                        return 1;
                    })
                )
            )

            // /ville quitter
            .then(Commands.literal("quitter")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                    String uuid = sp.getUUID().toString();

                    CompletableFuture.runAsync(() -> {
                        String err = VilleStore.quitterVille(uuid);
                        sp.getServer().execute(() -> {
                            if (err == null) {
                                sp.sendSystemMessage(Component.literal("§aVous avez quitté la ville."));
                                sendVilleProfile(sp);
                            } else {
                                sp.sendSystemMessage(Component.literal(err));
                            }
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )

            // /ville dissoudre
            .then(Commands.literal("dissoudre")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_LONG)) return 0;
                    String uuid = sp.getUUID().toString();
                    // Collecte des claims AVANT suppression (pour OPC) — lecture cache uniquement
                    int villeId = VilleStore.getVilleIdByUuid(uuid);
                    List<int[]> oldClaims = villeId != -1
                            ? VilleStore.getVilleClaims(villeId) : List.of();

                    CompletableFuture.runAsync(() -> {
                        String err = VilleStore.dissoudreVille(uuid);
                        sp.getServer().execute(() -> {
                            if (err == null) {
                                sp.sendSystemMessage(Component.literal("§aVille dissoute."));
                                sendVilleProfile(sp);
                                OPCServerIntegration.unregisterAll(sp.getServer(), oldClaims);
                            } else {
                                sp.sendSystemMessage(Component.literal(err));
                            }
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )

            // /ville inviter <pseudo>
            .then(Commands.literal("inviter")
                .then(Commands.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                        String pseudo = StringArgumentType.getString(ctx, "pseudo");
                        String uuid   = sp.getUUID().toString();
                        int villeId   = VilleStore.getVilleIdByUuid(uuid);
                        if (villeId == -1) {
                            sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville."));
                            return 0;
                        }

                        ServerPlayer cible = ctx.getSource().getServer().getPlayerList().getPlayerByName(pseudo);
                        if (cible == null) {
                            sp.sendSystemMessage(Component.literal("§cJoueur introuvable ou non connecté."));
                            return 0;
                        }
                        String cibleUuid = cible.getUUID().toString();

                        CompletableFuture.runAsync(() -> {
                            // Grade vérifié ici (requête DB) — jamais sur le main thread
                            String grade = VilleStore.getGrade(villeId, uuid);
                            if (!"Chef".equals(grade) && !"Adjoint".equals(grade)) {
                                sp.getServer().execute(() -> sp.sendSystemMessage(
                                        Component.literal("§cSeuls Chef et Adjoint peuvent inviter.")));
                                return;
                            }
                            String err = VilleStore.inviterJoueur(villeId, uuid, cibleUuid);
                            sp.getServer().execute(() -> {
                                if (err == null) {
                                    sp.sendSystemMessage(Component.literal(
                                            "§aInvitation envoyée à §e" + pseudo + "§a (expire dans 5 min)."));
                                    cible.sendSystemMessage(Component.literal(
                                            "§eVous avez reçu une invitation de §b" + sp.getName().getString()
                                            + "§e pour rejoindre §a" + VilleStore.getVilleNom(villeId)
                                            + "§e. Tapez §a/ville accepter §epour l'accepter."));
                                } else {
                                    sp.sendSystemMessage(Component.literal(err));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);
                        return 1;
                    })
                )
            )

            // /ville accepter
            .then(Commands.literal("accepter")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                    String uuid = sp.getUUID().toString();

                    CompletableFuture.runAsync(() -> {
                        String err = VilleStore.accepterInvitation(uuid);
                        sp.getServer().execute(() -> {
                            if (err == null) {
                                int vId = VilleStore.getVilleIdByUuid(uuid);
                                String nom = vId != -1 ? VilleStore.getVilleNom(vId) : "?";
                                sp.sendSystemMessage(Component.literal("§aTu as rejoint la ville §e" + nom + "§a !"));
                                sendVilleProfile(sp);
                            } else {
                                sp.sendSystemMessage(Component.literal(err));
                            }
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )

            // /ville refuser
            .then(Commands.literal("refuser")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                    String uuid = sp.getUUID().toString();
                    CompletableFuture.runAsync(
                            () -> VilleStore.refuserInvitation(uuid), BaliseStore.DB_EXECUTOR);
                    sp.sendSystemMessage(Component.literal("§eInvitation refusée."));
                    return 1;
                })
            )

            // /ville demande <nom>  — postule (recrutement "sur_demande")
            .then(Commands.literal("demande")
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                        String nom    = StringArgumentType.getString(ctx, "nom");
                        String uuid   = sp.getUUID().toString();
                        String pseudo = sp.getGameProfile().getName();

                        if (VilleStore.getVilleIdByUuid(uuid) != -1) {
                            sp.sendSystemMessage(Component.literal("§cTu es déjà dans une ville."));
                            return 0;
                        }

                        CompletableFuture.runAsync(() -> {
                            // 1 requête ciblée au lieu de getAllVilles() + stream filter
                            VilleStore.VilleInfo cible = VilleStore.getVilleByNom(nom);
                            if (cible == null) {
                                sp.getServer().execute(() ->
                                        sp.sendSystemMessage(Component.literal("§cVille §e" + nom + "§c introuvable.")));
                                return;
                            }
                            String err = VilleStore.demanderRejoindre(cible.id(), uuid, pseudo);
                            if (err == null) {
                                // Notifier Chef et Adjoints en ligne
                                String nomVille = cible.nom();
                                var membres = VilleStore.getMembres(cible.id());
                                sp.getServer().execute(() -> {
                                    sp.sendSystemMessage(Component.literal("§aDemande envoyée à §e" + nomVille + "§a !"));
                                    membres.stream()
                                        .filter(m -> "Chef".equals(m.grade()) || "Adjoint".equals(m.grade()))
                                        .forEach(m -> {
                                            ServerPlayer resp = sp.getServer().getPlayerList()
                                                    .getPlayer(UUID.fromString(m.uuid()));
                                            if (resp != null) {
                                                resp.sendSystemMessage(Component.literal(
                                                        "§6⚑ §e" + pseudo + "§6 demande à rejoindre §a" + nomVille
                                                        + "§6 ! §7(/ville info pour gérer)"));
                                            }
                                        });
                                });
                            } else {
                                sp.getServer().execute(() ->
                                        sp.sendSystemMessage(Component.literal(err)));
                            }
                        }, BaliseStore.DB_EXECUTOR);
                        return 1;
                    })
                )
            )

            // /ville rejoindre <nom>  — rejoindre directement (recrutement "ouvert")
            .then(Commands.literal("rejoindre")
                .then(Commands.argument("nom", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                        String nom  = StringArgumentType.getString(ctx, "nom");
                        String uuid = sp.getUUID().toString();

                        if (VilleStore.getVilleIdByUuid(uuid) != -1) {
                            sp.sendSystemMessage(Component.literal("§cTu es déjà dans une ville."));
                            return 0;
                        }

                        CompletableFuture.runAsync(() -> {
                            // 1 requête ciblée au lieu de getAllVilles() + stream filter
                            VilleStore.VilleInfo cible = VilleStore.getVilleByNom(nom);
                            if (cible == null) {
                                sp.getServer().execute(() ->
                                        sp.sendSystemMessage(Component.literal("§cVille §e" + nom + "§c introuvable.")));
                                return;
                            }
                            String err = VilleStore.rejoindreVilleOuverte(cible.id(), uuid);
                            sp.getServer().execute(() -> {
                                if (err == null) {
                                    sp.sendSystemMessage(Component.literal("§aTu as rejoint §e" + cible.nom() + "§a !"));
                                    sendVilleProfile(sp);
                                } else {
                                    sp.sendSystemMessage(Component.literal(err));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);
                        return 1;
                    })
                )
            )

            // /ville kick <pseudo>
            .then(Commands.literal("kick")
                .then(Commands.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                        String pseudo = StringArgumentType.getString(ctx, "pseudo");
                        String uuid   = sp.getUUID().toString();
                        int villeId   = VilleStore.getVilleIdByUuid(uuid);
                        if (villeId == -1) { sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville.")); return 0; }

                        CompletableFuture.runAsync(() -> {
                            // Trouver la cible par pseudo dans les membres
                            var membres = VilleStore.getMembres(villeId);
                            var mc = membres.stream().filter(m -> m.pseudo().equalsIgnoreCase(pseudo)).findFirst();
                            if (mc.isEmpty()) {
                                sp.getServer().execute(() -> sp.sendSystemMessage(Component.literal("§cMembre introuvable.")));
                                return;
                            }
                            String cibleUuid = mc.get().uuid();
                            String err = VilleStore.retirerMembre(villeId, uuid, cibleUuid);
                            sp.getServer().execute(() -> {
                                if (err == null) {
                                    sp.sendSystemMessage(Component.literal("§a" + pseudo + " a été exclu."));
                                    // Notifier la cible si en ligne
                                    ServerPlayer cibleSp = ctx.getSource().getServer()
                                            .getPlayerList().getPlayer(UUID.fromString(cibleUuid));
                                    if (cibleSp != null) {
                                        cibleSp.sendSystemMessage(Component.literal("§cTu as été expulsé de la ville."));
                                        sendVilleProfile(cibleSp);
                                    }
                                } else {
                                    sp.sendSystemMessage(Component.literal(err));
                                }
                            });
                        }, BaliseStore.DB_EXECUTOR);
                        return 1;
                    })
                )
            )

            // /ville grade <pseudo> <grade>
            .then(Commands.literal("grade")
                .then(Commands.argument("pseudo", StringArgumentType.word())
                    .then(Commands.argument("grade", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                            String pseudo   = StringArgumentType.getString(ctx, "pseudo");
                            String newGrade = StringArgumentType.getString(ctx, "grade");
                            String uuid     = sp.getUUID().toString();
                            int villeId     = VilleStore.getVilleIdByUuid(uuid);
                            if (villeId == -1) { sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville.")); return 0; }

                            CompletableFuture.runAsync(() -> {
                                var membres = VilleStore.getMembres(villeId);
                                var mc = membres.stream().filter(m -> m.pseudo().equalsIgnoreCase(pseudo)).findFirst();
                                if (mc.isEmpty()) {
                                    sp.getServer().execute(() -> sp.sendSystemMessage(Component.literal("§cMembre introuvable.")));
                                    return;
                                }
                                String err = VilleStore.changerGrade(villeId, uuid, sp.getName().getString(), mc.get().uuid(), newGrade);
                                sp.getServer().execute(() -> sp.sendSystemMessage(Component.literal(
                                        err == null ? "§aGrade de §e" + pseudo + "§a mis à jour → §e" + newGrade : err)));
                            }, BaliseStore.DB_EXECUTOR);
                            return 1;
                        })
                    )
                )
            )

            // /ville claim
            .then(Commands.literal("claim")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                    String uuid   = sp.getUUID().toString();
                    int villeId   = VilleStore.getVilleIdByUuid(uuid);
                    if (villeId == -1) { sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville.")); return 0; }
                    String monde = RolynkConfig.serverName();
                    if (!RolynkConfig.isClaimAutorise(monde)) {
                        sp.sendSystemMessage(Component.literal("§cLes claims ne sont pas autorisés dans ce monde."));
                        return 0;
                    }
                    int cx = sp.getBlockX() >> 4;
                    int cz = sp.getBlockZ() >> 4;
                    double cout = RolynkConfig.coutClaim();

                    CompletableFuture.runAsync(() -> {
                        // Grade vérifié ici (requête DB) — jamais sur le main thread
                        String grade = VilleStore.getGrade(villeId, uuid);
                        if (!"Chef".equals(grade) && !"Adjoint".equals(grade)) {
                            sp.getServer().execute(() -> sp.sendSystemMessage(
                                    Component.literal("§cSeuls Chef et Adjoint peuvent claimer.")));
                            return;
                        }
                        // Claim + débit dans une SEULE transaction (VilleStore)
                        String err = VilleStore.claimChunk(villeId, monde, cx, cz, uuid, cout);
                        if (err != null) {
                            sp.getServer().execute(() -> sp.sendSystemMessage(Component.literal(err)));
                            return;
                        }
                        // Argent débité → profil en cache périmé.
                        ServerProfileHandler.invalidateCache(uuid);
                        sp.getServer().execute(() -> {
                            sp.sendSystemMessage(Component.literal(
                                    "§aChunk (" + cx + "," + cz + ") claimé ! §7(−" + Money.entier(cout) + ")"));
                            OPCServerIntegration.registerClaim(
                                    sp.getServer(), villeId, VilleStore.getVilleNom(villeId), cx, cz);
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )

            // /ville unclaim
            .then(Commands.literal("unclaim")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    if (!checkCooldown(sp, COOLDOWN_COURT)) return 0;
                    String uuid   = sp.getUUID().toString();
                    int villeId   = VilleStore.getVilleIdByUuid(uuid);
                    if (villeId == -1) { sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville.")); return 0; }
                    String monde = RolynkConfig.serverName();
                    int cx = sp.getBlockX() >> 4;
                    int cz = sp.getBlockZ() >> 4;

                    CompletableFuture.runAsync(() -> {
                        // Grade vérifié ici (requête DB) — jamais sur le main thread
                        String grade = VilleStore.getGrade(villeId, uuid);
                        if (!"Chef".equals(grade) && !"Adjoint".equals(grade)) {
                            sp.getServer().execute(() -> sp.sendSystemMessage(
                                    Component.literal("§cSeuls Chef et Adjoint peuvent unclaimer.")));
                            return;
                        }
                        String err = VilleStore.unclaimChunk(villeId, monde, cx, cz);
                        sp.getServer().execute(() -> {
                            if (err == null) {
                                sp.sendSystemMessage(Component.literal("§aChunk (" + cx + "," + cz + ") libéré."));
                                OPCServerIntegration.unregisterClaim(sp.getServer(), cx, cz);
                            } else {
                                sp.sendSystemMessage(Component.literal(err));
                            }
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )

            // /ville info
            .then(Commands.literal("info")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    String uuid   = sp.getUUID().toString();
                    int villeId   = VilleStore.getVilleIdByUuid(uuid);
                    if (villeId == -1) { sp.sendSystemMessage(Component.literal("§cTu n'es dans aucune ville.")); return 0; }

                    CompletableFuture.runAsync(() -> {
                        // 1 requête ciblée (index PK) au lieu de getAllVilles() + stream filter
                        // + getGrade() déplacé ici (async) pour ne pas bloquer le main thread
                        VilleStore.VilleInfo vi = VilleStore.getVilleById(villeId);
                        String grade = VilleStore.getGrade(villeId, uuid);
                        sp.getServer().execute(() -> {
                            if (vi == null) return;
                            sp.sendSystemMessage(Component.literal(
                                    "§6=== §e" + vi.nom() + " §6===\n"
                                    + "§7Monde : §f" + (vi.monde() == null || vi.monde().isEmpty() ? "Aucun claim" : vi.monde()) + "\n"
                                    + "§7Chunks : §f" + vi.totalChunks() + "\n"
                                    + "§7Banque : §f" + Money.exact(vi.banque()) + "\n"
                                    + "§7Fondée : §f" + vi.dateFondation() + "\n"
                                    + "§7Mon grade : §f" + grade));
                        });
                    }, BaliseStore.DB_EXECUTOR);
                    return 1;
                })
            )
        );
    }
}
