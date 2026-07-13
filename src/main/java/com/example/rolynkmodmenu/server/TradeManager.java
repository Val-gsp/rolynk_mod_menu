package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.TradeClosePayload;
import com.example.rolynkmodmenu.network.TradeOpenPayload;
import com.example.rolynkmodmenu.network.TradeStatePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sessions et demandes de trade — TOUT l'état est muté sur le MAIN THREAD
 * uniquement (les handlers passent par ctx.enqueueWork, la fin d'échange
 * async revient par server.execute). Aucune synchronisation nécessaire.
 *
 * ANTI-ARNAQUE / ANTI-DUPE :
 *   – les items restent dans les inventaires jusqu'à l'exécution finale ;
 *     une offre = référence (slot + snapshot exact du stack) ;
 *   – toute modification d'offre réinitialise les DEUX confirmations ;
 *   – à l'exécution : validation des items (ItemStack.matches sur snapshot),
 *     échange d'argent en transaction SQL avec garde de solde, RE-validation
 *     des items au retour, retrait puis don croisé — le tout dans le même
 *     tick serveur pour les items ;
 *   – si les items ont bougé entre la validation et le retour SQL, l'argent
 *     est compensé (transaction inverse) et le trade annulé ;
 *   – session verrouillée (executing) pendant l'exécution : aucune action.
 */
public final class TradeManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Taille max d'une offre (stacks). */
    public static final int MAX_OFFRE = 8;
    /** Durée de vie d'une demande de trade. */
    private static final long REQUEST_TTL_MS = 60_000L;

    private TradeManager() {}

    // ── Modèle ────────────────────────────────────────────────────────────

    /** Une entrée d'offre : le slot d'inventaire + le snapshot exact du stack. */
    record OffreEntry(int slot, ItemStack snapshot) {}

    static final class Session {
        final ServerPlayer a, b;
        final List<OffreEntry> offreA = new ArrayList<>(), offreB = new ArrayList<>();
        double argentA = 0, argentB = 0;
        /** Soldes en banque (affichage) — -1 tant que la lecture DB n'est pas revenue. */
        double soldeA = -1, soldeB = -1;
        boolean confA = false, confB = false;
        boolean executing = false;

        Session(ServerPlayer a, ServerPlayer b) { this.a = a; this.b = b; }

        boolean estA(ServerPlayer p)      { return p.getUUID().equals(a.getUUID()); }
        ServerPlayer autre(ServerPlayer p) { return estA(p) ? b : a; }
        List<OffreEntry> offreDe(ServerPlayer p) { return estA(p) ? offreA : offreB; }
        double argentDe(ServerPlayer p)   { return estA(p) ? argentA : argentB; }
        void setArgent(ServerPlayer p, double v) { if (estA(p)) argentA = v; else argentB = v; }
        boolean confDe(ServerPlayer p)    { return estA(p) ? confA : confB; }
        void setConf(ServerPlayer p, boolean v) { if (estA(p)) confA = v; else confB = v; }
        void resetConfs() { confA = false; confB = false; }
    }

    /** Sessions actives — les deux joueurs pointent vers la même session. */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Demandes en attente : cible → (demandeur → expiration). */
    private static final Map<UUID, LinkedHashMap<UUID, Long>> REQUESTS = new HashMap<>();

    public static boolean enSession(UUID uuid) { return SESSIONS.containsKey(uuid); }

    public static Session session(UUID uuid) { return SESSIONS.get(uuid); }

    // ── Demandes ──────────────────────────────────────────────────────────

    /** Pseudos ayant une demande en cours vers {@code cible} (purge les expirées). */
    public static List<String> demandesPour(ServerPlayer cible) {
        LinkedHashMap<UUID, Long> map = REQUESTS.get(cible.getUUID());
        List<String> out = new ArrayList<>();
        if (map == null) return out;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() < now) { it.remove(); continue; }
            ServerPlayer demandeur = cible.getServer().getPlayerList().getPlayer(e.getKey());
            if (demandeur == null) { it.remove(); continue; }
            out.add(demandeur.getGameProfile().getName());
        }
        return out;
    }

    /** Enregistre une demande demandeur→cible. @return message d'erreur ou null. */
    public static String demander(ServerPlayer demandeur, ServerPlayer cible) {
        if (demandeur.getUUID().equals(cible.getUUID())) return "§cTu ne peux pas trader avec toi-même.";
        if (enSession(demandeur.getUUID())) return "§cTu as déjà un trade en cours.";
        if (enSession(cible.getUUID()))    return "§c" + cible.getGameProfile().getName() + " est déjà en trade.";
        LinkedHashMap<UUID, Long> map = REQUESTS.computeIfAbsent(cible.getUUID(), k -> new LinkedHashMap<>());
        map.put(demandeur.getUUID(), System.currentTimeMillis() + REQUEST_TTL_MS);
        cible.sendSystemMessage(Component.literal(
                "§e" + demandeur.getGameProfile().getName()
                + " §aveut échanger avec toi ! §7(MENU > PROFIL > TRADE pour répondre, expire dans 60s)"));
        return null;
    }

    /** Réponse de la cible à la demande de {@code demandeur}. */
    public static void repondre(ServerPlayer cible, ServerPlayer demandeur, boolean accepte) {
        LinkedHashMap<UUID, Long> map = REQUESTS.get(cible.getUUID());
        Long expiry = map == null ? null : map.remove(demandeur.getUUID());
        if (expiry == null || expiry < System.currentTimeMillis()) {
            cible.sendSystemMessage(Component.literal("§cCette demande n'existe plus."));
            return;
        }
        if (!accepte) {
            demandeur.sendSystemMessage(Component.literal(
                    "§c" + cible.getGameProfile().getName() + " a refusé ton trade."));
            cible.sendSystemMessage(Component.literal("§7Demande refusée."));
            return;
        }
        if (enSession(cible.getUUID()) || enSession(demandeur.getUUID())) {
            cible.sendSystemMessage(Component.literal("§cL'un de vous est déjà en trade."));
            return;
        }
        Session s = new Session(demandeur, cible);
        SESSIONS.put(demandeur.getUUID(), s);
        SESSIONS.put(cible.getUUID(), s);
        // La demande acceptée annule les autres demandes reçues par les deux joueurs
        REQUESTS.remove(cible.getUUID());
        REQUESTS.remove(demandeur.getUUID());
        PacketDistributor.sendToPlayer(demandeur, new TradeOpenPayload(cible.getGameProfile().getName()));
        PacketDistributor.sendToPlayer(cible, new TradeOpenPayload(demandeur.getGameProfile().getName()));
        sync(s);
        LOGGER.info("[Trade] Session ouverte : {} <-> {}",
                demandeur.getGameProfile().getName(), cible.getGameProfile().getName());

        // Lecture des soldes en arrière-plan (affichage) puis re-sync sur le main thread.
        String uuidA = demandeur.getStringUUID(), uuidB = cible.getStringUUID();
        CompletableFuture.runAsync(() -> {
            double[] soldes = TradeStore.lireSoldes(uuidA, uuidB);
            demandeur.getServer().execute(() -> {
                Session cur = SESSIONS.get(s.a.getUUID());
                if (cur == s && !s.executing) {
                    s.soldeA = soldes[0];
                    s.soldeB = soldes[1];
                    sync(s);
                }
            });
        }, Database.EXECUTOR);
    }

    // ── Actions dans la session ───────────────────────────────────────────

    /**
     * Ajoute {@code quantite} items du slot à l'offre (clampée au contenu réel).
     * Si le slot est déjà offert, la quantité est simplement REMPLACÉE.
     */
    public static void ajouterItem(ServerPlayer p, int slot, int quantite) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.executing) return;
        ItemStack st = p.getInventory().getItem(slot);
        if (st.isEmpty()) return;
        int q = Math.max(1, Math.min(quantite, st.getCount()));

        List<OffreEntry> offre = s.offreDe(p);
        offre.removeIf(e -> e.slot() == slot); // remplace si déjà offert
        if (offre.size() >= MAX_OFFRE) {
            p.sendSystemMessage(Component.literal("§cOffre pleine (" + MAX_OFFRE + " emplacements max)."));
            return;
        }
        offre.add(new OffreEntry(slot, st.copyWithCount(q)));
        s.resetConfs();
        sync(s);
    }

    public static void retirerItem(ServerPlayer p, int index) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.executing) return;
        List<OffreEntry> offre = s.offreDe(p);
        if (index < 0 || index >= offre.size()) return;
        offre.remove(index);
        s.resetConfs();
        sync(s);
    }

    public static void setArgent(ServerPlayer p, double montant) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.executing) return;
        s.setArgent(p, montant);
        s.resetConfs();
        sync(s);
    }

    public static void confirmer(ServerPlayer p, boolean conf) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.executing) return;
        s.setConf(p, conf);
        sync(s);
        if (s.confA && s.confB) executer(s);
    }

    public static void annuler(ServerPlayer p, String raison) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.executing) return;
        fermer(s, raison);
    }

    /** À la déconnexion : annule la session et les demandes du joueur. */
    public static void onPlayerLogout(UUID uuid) {
        REQUESTS.remove(uuid);
        for (LinkedHashMap<UUID, Long> m : REQUESTS.values()) m.remove(uuid);
        Session s = SESSIONS.get(uuid);
        if (s != null && !s.executing) {
            fermer(s, "§cTrade annulé : joueur déconnecté.");
        }
        // Si executing : l'exécution en cours détecte la déconnexion et compense.
    }

    // ── Exécution finale ──────────────────────────────────────────────────

    private static void executer(Session s) {
        s.executing = true;

        // 1. Validation des items des deux côtés (main thread).
        String err = validerItems(s.a, s.offreA);
        if (err == null) err = validerItems(s.b, s.offreB);
        if (err != null) {
            s.executing = false;
            s.resetConfs();
            message(s, err + " §7Les confirmations ont été réinitialisées.");
            sync(s);
            return;
        }

        double deA = InputValidator.roundMontant(s.argentA);
        double deB = InputValidator.roundMontant(s.argentB);
        String uuidA = s.a.getStringUUID(), uuidB = s.b.getStringUUID();
        String nomA = s.a.getGameProfile().getName(), nomB = s.b.getGameProfile().getName();

        // 2. Échange d'argent en transaction (async), puis retour main thread.
        CompletableFuture.runAsync(() -> {
            String errArgent = TradeStore.echangerArgent(uuidA, nomA, deA, uuidB, nomB, deB);
            s.a.getServer().execute(() -> finirExecution(s, errArgent, deA, deB));
        }, Database.EXECUTOR);
    }

    /** Étape finale, de retour sur le MAIN THREAD après la transaction d'argent. */
    private static void finirExecution(Session s, String errArgent, double deA, double deB) {
        String uuidA = s.a.getStringUUID(), uuidB = s.b.getStringUUID();
        String nomA = s.a.getGameProfile().getName(), nomB = s.b.getGameProfile().getName();

        if (errArgent != null) {
            s.executing = false;
            s.resetConfs();
            message(s, errArgent + " §7Les confirmations ont été réinitialisées.");
            sync(s);
            return;
        }

        // 3. RE-validation : déconnexion ou items déplacés pendant la fenêtre SQL
        //    → on compense l'argent et on annule, rien d'autre n'a bougé.
        String err = null;
        if (s.a.hasDisconnected() || s.b.hasDisconnected()) err = "§cTrade annulé : joueur déconnecté.";
        if (err == null) err = validerItems(s.a, s.offreA);
        if (err == null) err = validerItems(s.b, s.offreB);
        if (err != null) {
            if (deA > 0 || deB > 0) {
                final String fErr = err;
                CompletableFuture.runAsync(() ->
                        TradeStore.compenser(uuidA, nomA, deA, uuidB, nomB, deB), Database.EXECUTOR);
            }
            fermer(s, err);
            return;
        }

        // 4. Transfert des items — retrait des deux côtés PUIS don croisé,
        //    dans le même tick : aucune fenêtre de dupe possible.
        List<ItemStack> versB = retirer(s.a, s.offreA);
        List<ItemStack> versA = retirer(s.b, s.offreB);
        for (ItemStack st : versA) s.a.getInventory().placeItemBackInInventory(st);
        for (ItemStack st : versB) s.b.getInventory().placeItemBackInInventory(st);
        s.a.getInventory().setChanged();
        s.b.getInventory().setChanged();

        if (deA > 0 || deB > 0) {
            ServerProfileHandler.invalidateCache(uuidA);
            ServerProfileHandler.invalidateCache(uuidB);
        }

        s.a.sendSystemMessage(Component.literal("§a✔ Trade conclu avec §e" + nomB
                + "§a : " + resume(versA.size(), deB) + " reçu(s)."));
        s.b.sendSystemMessage(Component.literal("§a✔ Trade conclu avec §e" + nomA
                + "§a : " + resume(versB.size(), deA) + " reçu(s)."));
        LOGGER.info("[Trade] Conclu : {} ({} items, {}$) <-> {} ({} items, {}$)",
                nomA, versB.size(), deA, nomB, versA.size(), deB);
        fermerSansMessage(s);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Vérifie que chaque slot offert contient toujours le MÊME item (type +
     * composants identiques au snapshot) en quantité suffisante.
     */
    private static String validerItems(ServerPlayer p, List<OffreEntry> offre) {
        for (OffreEntry e : offre) {
            ItemStack actuel = p.getInventory().getItem(e.slot());
            if (!ItemStack.isSameItemSameComponents(actuel, e.snapshot())
                    || actuel.getCount() < e.snapshot().getCount()) {
                return "§cLes objets offerts par " + p.getGameProfile().getName()
                        + " ont changé dans son inventaire.";
            }
        }
        return null;
    }

    /** Retire les quantités offertes de l'inventaire et retourne les stacks à donner. */
    private static List<ItemStack> retirer(ServerPlayer p, List<OffreEntry> offre) {
        List<ItemStack> out = new ArrayList<>(offre.size());
        for (OffreEntry e : offre) {
            ItemStack actuel = p.getInventory().getItem(e.slot());
            int q = e.snapshot().getCount();
            actuel.shrink(q);
            if (actuel.isEmpty()) p.getInventory().setItem(e.slot(), ItemStack.EMPTY);
            out.add(e.snapshot().copy());
        }
        return out;
    }

    private static String resume(int nbItems, double argent) {
        StringBuilder sb = new StringBuilder();
        if (nbItems > 0) sb.append(nbItems).append(" stack(s)");
        if (argent > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("§e+").append(com.example.rolynkmodmenu.util.Money.exact(argent)).append("§a");
        }
        return sb.length() == 0 ? "rien" : sb.toString();
    }

    private static void message(Session s, String msg) {
        s.a.sendSystemMessage(Component.literal(msg));
        s.b.sendSystemMessage(Component.literal(msg));
    }

    private static void fermer(Session s, String raison) {
        message(s, raison);
        fermerSansMessage(s);
    }

    private static void fermerSansMessage(Session s) {
        SESSIONS.remove(s.a.getUUID());
        SESSIONS.remove(s.b.getUUID());
        if (!s.a.hasDisconnected()) PacketDistributor.sendToPlayer(s.a, new TradeClosePayload());
        if (!s.b.hasDisconnected()) PacketDistributor.sendToPlayer(s.b, new TradeClosePayload());
    }

    /** Envoie l'état miroir de la session aux deux joueurs. */
    static void sync(Session s) {
        List<ItemStack> stacksA = s.offreA.stream().map(e -> e.snapshot().copy()).toList();
        List<ItemStack> stacksB = s.offreB.stream().map(e -> e.snapshot().copy()).toList();
        PacketDistributor.sendToPlayer(s.a, new TradeStatePayload(
                s.b.getGameProfile().getName(), stacksA, stacksB,
                s.argentA, s.argentB, s.soldeA, s.confA, s.confB));
        PacketDistributor.sendToPlayer(s.b, new TradeStatePayload(
                s.a.getGameProfile().getName(), stacksB, stacksA,
                s.argentB, s.argentA, s.soldeB, s.confB, s.confA));
    }
}
