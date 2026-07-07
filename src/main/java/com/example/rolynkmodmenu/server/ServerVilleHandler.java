package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handlers des payloads C2S du système de villes.
 *
 * SÉCURITÉ — chaque handler applique dans l'ordre :
 *   1. Rate-limiting  : rejette les requêtes trop fréquentes
 *   2. Validation     : whitelist actions, format UUID, bornes numériques
 *   3. Autorisation   : le villeId doit correspondre à la ville du joueur
 *   4. Logique métier : délégué à VilleStore (déjà validé côté DB)
 */
public final class ServerVilleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerVilleHandler() {}

    // ── Rate-limiting ──────────────────────────────────────────────────────────

    private static final long LIST_CD_MS      = 3_000L;   // liste villes
    private static final long REQUEST_CD_MS   = 2_000L;   // membres / logs / demandes
    private static final long ACTION_CD_MS    = 750L;     // actions (dépôt, kick, grade...)

    private static final ConcurrentHashMap<UUID, Long> LIST_CD     = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> MEMBRES_CD  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LOGS_CD     = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> DEMANDES_CD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> ACTION_CD   = new ConcurrentHashMap<>();

    /**
     * @return {@code true} si la requête doit être ignorée (trop rapide).
     */
    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /** Appelé à la déconnexion pour nettoyer les entrées de cooldown du joueur. */
    public static void onPlayerLogout(UUID uuid) {
        LIST_CD.remove(uuid);
        MEMBRES_CD.remove(uuid);
        LOGS_CD.remove(uuid);
        DEMANDES_CD.remove(uuid);
        ACTION_CD.remove(uuid);
    }

    // ── VilleListRequestPayload ────────────────────────────────────────────────

    public static void onListRequest(VilleListRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(LIST_CD, sp.getUUID(), LIST_CD_MS)) return;

        CompletableFuture.runAsync(() -> {
            // 1 requête SQL (avec nb_membres intégré) au lieu de 1 + N appels getNbMembres()
            var villes = VilleStore.getAllVilles();

            // Retour sur le main thread pour lire la player list (thread-safe)
            // et calculer le comptage en ligne en O(joueurs) au lieu de O(joueurs × villes)
            ctx.enqueueWork(() -> {
                Map<Integer, Long> onlineByVille = sp.getServer().getPlayerList().getPlayers().stream()
                        .collect(Collectors.groupingBy(
                                p -> VilleStore.getVilleIdByUuid(p.getUUID().toString()),
                                Collectors.counting()));

                List<VilleListPayload.VilleEntry> entries = villes.stream().map(vi ->
                        new VilleListPayload.VilleEntry(
                                vi.id(), vi.nom(), vi.ownerPseudo(),
                                vi.monde() == null ? "" : vi.monde(),
                                vi.banque(),
                                vi.nbMembres(),
                                onlineByVille.getOrDefault(vi.id(), 0L).intValue(),
                                vi.recrutement() == null ? VilleStore.RECRUTEMENT_SUR_DEMANDE : vi.recrutement(),
                                vi.dateFondation() == null ? "" : vi.dateFondation(),
                                vi.totalChunks())
                ).toList();
                PacketDistributor.sendToPlayer(sp, new VilleListPayload(entries));
            });
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── VilleMembresRequestPayload ─────────────────────────────────────────────

    public static void onMembresRequest(VilleMembresRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(MEMBRES_CD, sp.getUUID(), REQUEST_CD_MS)) return;

        int    villeId = payload.villeId();
        String myUuid  = sp.getUUID().toString();

        // ── Validation : le joueur doit être membre de cette ville ──────────────
        if (!InputValidator.isValidVilleId(villeId)) return;

        int playerVilleId = VilleStore.getVilleIdByUuid(myUuid);
        if (playerVilleId != villeId) {
            LOGGER.warn("[Security] MembresRequest: {} demande ville {} mais est en ville {}",
                    myUuid, villeId, playerVilleId);
            return;
        }

        CompletableFuture.runAsync(() -> {
            String myGrade    = VilleStore.getGrade(villeId, myUuid);
            if (myGrade == null) myGrade = "";
            double playerMoney = VilleStore.getMoney(myUuid);

            var membres = VilleStore.getMembres(villeId);
            List<VilleMembresPayload.MembreEntry> entries = membres.stream().map(m -> {
                boolean online = sp.getServer().getPlayerList()
                        .getPlayer(UUID.fromString(m.uuid())) != null;
                return new VilleMembresPayload.MembreEntry(m.uuid(), m.pseudo(), m.grade(), online);
            }).toList();

            String finalGrade = myGrade;
            double finalMoney = playerMoney;
            ctx.enqueueWork(() -> PacketDistributor.sendToPlayer(sp,
                    new VilleMembresPayload(entries, finalGrade, finalMoney)));
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── VilleActionPayload ─────────────────────────────────────────────────────

    public static void onAction(VilleActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        // Anti-spam : chaque action déclenche une transaction DB — un client
        // modifié ne doit pas pouvoir en envoyer à la cadence du réseau.
        if (throttle(ACTION_CD, sp.getUUID(), ACTION_CD_MS)) return;

        String uuid   = sp.getUUID().toString();
        String pseudo = sp.getGameProfile().getName();
        int    villeId = payload.villeId();

        // ── 1. Whitelist de l'action ────────────────────────────────────────────
        if (!InputValidator.isValidAction(payload.action())) {
            LOGGER.warn("[Security] VilleAction: {} a envoyé une action inconnue '{}'",
                    uuid, payload.action());
            return;
        }

        // ── 2. villeId structurellement valide ──────────────────────────────────
        if (!InputValidator.isValidVilleId(villeId)) {
            LOGGER.warn("[Security] VilleAction: {} a envoyé un villeId invalide {}",
                    uuid, villeId);
            return;
        }

        // ── 3. Autorisation : le joueur doit être dans CETTE ville ─────────────
        //    (lu depuis UUID_VILLE_CACHE, O(1), pas de requête DB)
        int playerVilleId = VilleStore.getVilleIdByUuid(uuid);
        if (playerVilleId != villeId) {
            LOGGER.warn("[Security] VilleAction '{}': {} ciblant ville {} mais est en ville {}",
                    payload.action(), uuid, villeId, playerVilleId);
            return;
        }

        // ── 4. Validation des champs selon l'action ─────────────────────────────
        switch (payload.action()) {

            case "deposer", "retirer" -> {
                if (!InputValidator.isValidMontant(payload.montant())) {
                    LOGGER.warn("[Security] VilleAction '{}': {} montant invalide {}",
                            payload.action(), uuid, payload.montant());
                    ctx.enqueueWork(() -> sp.sendSystemMessage(
                            Component.literal("§cMontant invalide ou hors limites.")));
                    return;
                }
            }

            case "kick", "acc_dem", "ref_dem" -> {
                if (!InputValidator.isValidUuid(payload.targetUuid())) {
                    LOGGER.warn("[Security] VilleAction '{}': {} UUID cible invalide '{}'",
                            payload.action(), uuid, payload.targetUuid());
                    return;
                }
                // Empêcher l'auto-ciblage depuis le réseau
                if (uuid.equals(payload.targetUuid())) {
                    LOGGER.warn("[Security] VilleAction '{}': {} essaie de se cibler lui-même",
                            payload.action(), uuid);
                    return;
                }
            }

            case "grade" -> {
                if (!InputValidator.isValidUuid(payload.targetUuid())) {
                    LOGGER.warn("[Security] VilleAction 'grade': {} UUID cible invalide '{}'",
                            uuid, payload.targetUuid());
                    return;
                }
                if (!InputValidator.isValidGrade(payload.extra())) {
                    LOGGER.warn("[Security] VilleAction 'grade': {} grade invalide '{}'",
                            uuid, payload.extra());
                    return;
                }
            }

            case "set_rec" -> {
                if (!InputValidator.isValidRecrutement(payload.extra())) {
                    LOGGER.warn("[Security] VilleAction 'set_rec': {} mode invalide '{}'",
                            uuid, payload.extra());
                    return;
                }
            }

            // "dissoudre", "quitter" : aucun champ supplémentaire — déjà couvert par l'auth.
        }

        // ── 5. Arrondi des montants — élimine les imprécisions double du client ─
        final double montant = InputValidator.roundMontant(payload.montant());

        CompletableFuture.runAsync(() -> {
            String result         = null;
            String newBanque      = "";
            double newPlayerMoney = -1;
            boolean success       = false;

            switch (payload.action()) {

                case "kick" -> {
                    result  = VilleStore.retirerMembre(villeId, uuid, payload.targetUuid());
                    success = (result == null);
                    if (success) {
                        ServerPlayer cibleSp = sp.getServer().getPlayerList()
                                .getPlayer(UUID.fromString(payload.targetUuid()));
                        if (cibleSp != null) {
                            sp.getServer().execute(() -> {
                                cibleSp.sendSystemMessage(
                                        Component.literal("§cVous avez été exclu de la ville."));
                                VilleCommandHandler.sendVilleProfile(cibleSp);
                            });
                        }
                    }
                }

                case "deposer" -> {
                    result  = VilleStore.deposerBanque(villeId, uuid, pseudo, montant);
                    success = (result == null);
                    if (success) {
                        newBanque      = String.format("%.2f", VilleStore.getBanque(villeId));
                        newPlayerMoney = VilleStore.getMoney(uuid);
                        result = "§a−" + com.example.rolynkmodmenu.util.Money.exact(montant) + " déposé dans la banque.";
                    }
                }

                case "retirer" -> {
                    result  = VilleStore.retirerBanque(villeId, uuid, pseudo, montant);
                    success = (result == null);
                    if (success) {
                        newBanque      = String.format("%.2f", VilleStore.getBanque(villeId));
                        newPlayerMoney = VilleStore.getMoney(uuid);
                        result = "§a+" + com.example.rolynkmodmenu.util.Money.exact(montant) + " retiré de la banque.";
                    }
                }

                case "acc_dem" -> {
                    result  = VilleStore.accepterDemande(villeId, uuid, payload.targetUuid());
                    success = (result == null);
                    if (success) {
                        ServerPlayer cibleSp = sp.getServer().getPlayerList()
                                .getPlayer(UUID.fromString(payload.targetUuid()));
                        String nomVille = VilleStore.getVilleNom(villeId);
                        if (cibleSp != null) {
                            sp.getServer().execute(() -> {
                                cibleSp.sendSystemMessage(Component.literal(
                                        "§aVous avez été admis dans la ville §e" + nomVille + "§a !"));
                                VilleCommandHandler.sendVilleProfile(cibleSp);
                            });
                        }
                        result = "§aDemande acceptée.";
                    }
                }

                case "ref_dem" -> {
                    VilleStore.refuserDemande(villeId, payload.targetUuid());
                    success = true;
                    result  = "§eDemande refusée.";
                }

                case "grade" -> {
                    result  = VilleStore.changerGrade(
                            villeId, uuid, pseudo, payload.targetUuid(), payload.extra());
                    success = (result == null);
                    if (success) result = "§aGrade modifié → §e" + payload.extra();
                }

                case "dissoudre" -> {
                    result  = VilleStore.dissoudreVille(uuid);
                    success = (result == null);
                    if (success) {
                        VilleCommandHandler.sendVilleProfile(sp);
                        result = "§aVille dissoute.";
                    }
                }

                case "quitter" -> {
                    result  = VilleStore.quitterVille(uuid);
                    success = (result == null);
                    if (success) {
                        VilleCommandHandler.sendVilleProfile(sp);
                        result = "§aVous avez quitté la ville.";
                    }
                }

                case "set_rec" -> {
                    result  = VilleStore.setRecrutement(villeId, uuid, pseudo, payload.extra());
                    success = (result == null);
                    if (success) result = switch (payload.extra()) {
                        case VilleStore.RECRUTEMENT_OUVERT      -> "§aRecrutement : §eOuvert";
                        case VilleStore.RECRUTEMENT_SUR_DEMANDE -> "§aRecrutement : §eSur demande";
                        case VilleStore.RECRUTEMENT_FERME       -> "§aRecrutement : §cFermé";
                        default -> "§aRecrutement mis à jour.";
                    };
                }
            }

            // Argent et/ou appartenance ont pu changer → invalider le profil en cache
            // pour que la prochaine ouverture de l'écran Profil soit à jour.
            if (success) ServerProfileHandler.invalidateCache(uuid);

            final String  finalResult      = result;
            final String  finalBanque      = newBanque;
            final double  finalPlayerMoney = newPlayerMoney;
            final boolean finalSuccess     = success;

            ctx.enqueueWork(() -> {
                sp.sendSystemMessage(Component.literal(finalResult == null ? "" : finalResult));
                PacketDistributor.sendToPlayer(sp, new VilleActionResultPayload(
                        finalSuccess, finalResult == null ? "" : finalResult,
                        finalBanque, finalPlayerMoney));
            });
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── VilleLogsRequestPayload ────────────────────────────────────────────────

    public static void onLogsRequest(VilleLogsRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(LOGS_CD, sp.getUUID(), REQUEST_CD_MS)) return;

        int    villeId = payload.villeId();
        String myUuid  = sp.getUUID().toString();

        if (!InputValidator.isValidVilleId(villeId)) return;

        // Seuls les membres de la ville peuvent voir les logs
        int playerVilleId = VilleStore.getVilleIdByUuid(myUuid);
        if (playerVilleId != villeId) {
            LOGGER.warn("[Security] LogsRequest: {} demande logs de ville {} mais est en ville {}",
                    myUuid, villeId, playerVilleId);
            return;
        }

        CompletableFuture.runAsync(() -> {
            // Vérification grade en DB : seuls Chef/Adjoint voient les logs
            String grade = VilleStore.getGrade(villeId, myUuid);
            if (!"Chef".equals(grade) && !"Adjoint".equals(grade)) return;

            var logs = VilleStore.getLogsBanque(villeId);
            List<VilleLogsBanquePayload.LogEntry> entries = logs.stream()
                    .map(l -> new VilleLogsBanquePayload.LogEntry(
                            l.pseudo(), l.action(), l.montant(), l.timestamp()))
                    .toList();
            ctx.enqueueWork(() ->
                    PacketDistributor.sendToPlayer(sp, new VilleLogsBanquePayload(entries)));
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── VilleDemandesRequestPayload ────────────────────────────────────────────

    public static void onDemandesRequest(VilleDemandesRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(DEMANDES_CD, sp.getUUID(), REQUEST_CD_MS)) return;

        int    villeId = payload.villeId();
        String myUuid  = sp.getUUID().toString();

        if (!InputValidator.isValidVilleId(villeId)) return;

        // Seuls les membres de la ville peuvent voir les demandes d'adhésion
        int playerVilleId = VilleStore.getVilleIdByUuid(myUuid);
        if (playerVilleId != villeId) {
            LOGGER.warn("[Security] DemandesRequest: {} demande demandes de ville {} mais est en ville {}",
                    myUuid, villeId, playerVilleId);
            return;
        }

        CompletableFuture.runAsync(() -> {
            // Vérification grade en DB : seuls Chef/Adjoint peuvent gérer les demandes
            String grade = VilleStore.getGrade(villeId, myUuid);
            if (!"Chef".equals(grade) && !"Adjoint".equals(grade)) return;

            var demandes = VilleStore.getDemandes(villeId);
            List<VilleDemandesPayload.DemandeEntry> entries = demandes.stream()
                    .map(d -> new VilleDemandesPayload.DemandeEntry(
                            d.uuid(), d.pseudo(), d.timestamp()))
                    .toList();
            ctx.enqueueWork(() ->
                    PacketDistributor.sendToPlayer(sp, new VilleDemandesPayload(entries)));
        }, BaliseStore.DB_EXECUTOR);
    }
}
