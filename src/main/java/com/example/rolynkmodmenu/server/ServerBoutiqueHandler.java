package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.BoutiqueCatalogPayload;
import com.example.rolynkmodmenu.network.BoutiqueCatalogRequestPayload;
import com.example.rolynkmodmenu.network.BoutiqueVendrePayload;
import com.example.rolynkmodmenu.util.Money;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlers C2S de la Boutique Jeux (rachat d'objets par le serveur).
 *
 * SÉCURITÉ — comme partout dans le mod :
 *   – identité = UUID de la connexion, jamais le payload ;
 *   – offreId validé contre le catalogue serveur (InputValidator) ;
 *   – prix et quantités = BoutiqueConfig, jamais le client ;
 *   – inventaire manipulé UNIQUEMENT sur le main thread (enqueueWork) ;
 *   – crédit DB async ; si le crédit échoue, les objets sont RENDUS.
 *
 * Seuls les items « propres » (aucun data component custom : ni renommage,
 * ni enchantement...) sont comptés et retirés — impossible de vendre par
 * accident un objet personnalisé.
 */
public final class ServerBoutiqueHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerBoutiqueHandler() {}

    // ── Rate-limiting ─────────────────────────────────────────────────────

    private static final long CATALOG_CD_MS = 2_000L;
    private static final long VENDRE_CD_MS  =   500L;

    private static final ConcurrentHashMap<UUID, Long> CATALOG_CD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> VENDRE_CD  = new ConcurrentHashMap<>();

    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /** Nettoyage à la déconnexion. */
    public static void onPlayerLogout(UUID uuid) {
        CATALOG_CD.remove(uuid);
        VENDRE_CD.remove(uuid);
    }

    // ── BoutiqueCatalogRequestPayload ─────────────────────────────────────

    public static void onCatalogRequest(BoutiqueCatalogRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(CATALOG_CD, sp.getUUID(), CATALOG_CD_MS)) return;

        // Le premier appel lit le fichier de config : hors main thread.
        CompletableFuture.runAsync(() -> PacketDistributor.sendToPlayer(sp,
                new BoutiqueCatalogPayload(BoutiqueConfig.catalogue())), Database.EXECUTOR);
    }

    // ── BoutiqueVendrePayload ─────────────────────────────────────────────

    public static void onVendre(BoutiqueVendrePayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(VENDRE_CD, sp.getUUID(), VENDRE_CD_MS)) return;

        if (RolynkConfig.isLobby()) {
            ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                    "§cLa boutique n'est pas disponible dans le lobby.")));
            return;
        }

        int offreId = payload.offreId();
        if (!InputValidator.isValidOffreBoutique(offreId)) {
            LOGGER.warn("[Security] BoutiqueVendre: {} a envoyé une offre invalide {}",
                    sp.getStringUUID(), offreId);
            return;
        }
        BoutiqueCatalogPayload.Offre offre = BoutiqueConfig.offre(offreId);

        ResourceLocation rl = ResourceLocation.tryParse(offre.itemId());
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null || item == Items.AIR) {
            LOGGER.error("[Boutique] Item du catalogue introuvable : {}", offre.itemId());
            ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                    "§cCette offre est mal configurée, signale-le au staff.")));
            return;
        }

        // Tout ce qui touche l'inventaire se passe sur le main thread.
        ctx.enqueueWork(() -> {
            int quantite = offre.quantite();
            Inventory inv = sp.getInventory();

            int possede = compter(inv, item);
            String nomItem = new ItemStack(item).getHoverName().getString();
            if (possede < quantite) {
                sp.sendSystemMessage(Component.literal(
                        "§cIl te faut §e" + quantite + " × " + nomItem
                        + "§c pour vendre ce lot (tu en as §e" + possede + "§c)."));
                return;
            }

            retirer(inv, item, quantite);
            inv.setChanged();

            String uuid  = sp.getStringUUID();
            double prix  = offre.prix();
            String detail = quantite + "x " + offre.itemId() + " -> " + prix;

            CompletableFuture.runAsync(() -> {
                boolean ok = BoutiqueStore.crediterVente(uuid, prix, detail);
                if (ok) {
                    ServerProfileHandler.invalidateCache(uuid);
                    ctx.enqueueWork(() -> sp.sendSystemMessage(Component.literal(
                            "§aVendu : §e" + quantite + " × " + nomItem
                            + " §a→ §e+" + Money.exact(prix))));
                } else {
                    // Crédit impossible → on REND les objets au joueur.
                    ctx.enqueueWork(() -> {
                        rendre(sp, item, quantite);
                        sp.sendSystemMessage(Component.literal(
                                "§cVente annulée (erreur interne) — tes objets t'ont été rendus."));
                    });
                }
            }, Database.EXECUTOR);
        });
    }

    // ── Inventaire (main thread uniquement) ───────────────────────────────

    /** Un stack est vendable s'il est du bon item ET sans data component custom. */
    private static boolean vendable(ItemStack st, Item item) {
        return !st.isEmpty() && st.is(item) && st.getComponentsPatch().isEmpty();
    }

    private static int compter(Inventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (vendable(st, item)) total += st.getCount();
        }
        return total;
    }

    private static void retirer(Inventory inv, Item item, int quantite) {
        int restant = quantite;
        for (int i = 0; i < inv.getContainerSize() && restant > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!vendable(st, item)) continue;
            int pris = Math.min(st.getCount(), restant);
            st.shrink(pris);
            restant -= pris;
            if (st.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Rend {@code quantite} × item (stacks respectant la taille max, surplus au sol). */
    private static void rendre(ServerPlayer sp, Item item, int quantite) {
        int restant = quantite;
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (restant > 0) {
            int n = Math.min(maxStack, restant);
            sp.getInventory().placeItemBackInInventory(new ItemStack(item, n));
            restant -= n;
        }
    }
}
