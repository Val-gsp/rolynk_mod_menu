package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.ProfilRpCreatePayload;
import com.example.rolynkmodmenu.network.ProfilRpPayload;
import com.example.rolynkmodmenu.network.ProfilRpRequestPayload;
import com.example.rolynkmodmenu.network.ProfilRpResultPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlers C2S du profil RP (création première connexion + lecture).
 * Toutes les requêtes DB sont asynchrones (DB_EXECUTOR), l'envoi revient sur le main thread.
 *
 * SÉCURITÉ :
 *  – Rate-limiting sur les deux endpoints
 *  – Validation stricte de chaque champ (InputValidator) avant tout hit DB
 *  – La nouvelle ville est imposée côté serveur, jamais lue depuis le client
 *  – Un profil existant n'est jamais écrasé (INSERT IGNORE + refus explicite)
 */
public final class ServerProfilRpHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long REQUEST_CD_MS = 3_000L;
    private static final long CREATE_CD_MS  = 2_000L;

    private static final ConcurrentHashMap<UUID, Long> REQUEST_CD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> CREATE_CD  = new ConcurrentHashMap<>();

    private ServerProfilRpHandler() {}

    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /** Nettoyage à la déconnexion. */
    public static void onPlayerLogout(UUID uuid) {
        REQUEST_CD.remove(uuid);
        CREATE_CD.remove(uuid);
    }

    // ── Envoi du profil RP (login + requête) ──────────────────────────────

    /** Charge le profil RP en DB et l'envoie au joueur (absent() si inexistant). */
    public static void sendProfilRp(ServerPlayer sp) {
        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            ProfilRpRepository.RpData d = ProfilRpRepository.fetch(uuid);
            ProfilRpPayload payload = (d == null)
                    ? ProfilRpPayload.absent()
                    : new ProfilRpPayload(true, d.nom(), d.prenom(), d.sexe(), d.taille(),
                            d.ancienneVille(), d.nouvelleVille(), d.metier(), d.description());
            sp.getServer().execute(() -> PacketDistributor.sendToPlayer(sp, payload));
        }, BaliseStore.DB_EXECUTOR);
    }

    /** C2S — le joueur demande son profil RP. */
    public static void onRequest(ProfilRpRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(REQUEST_CD, sp.getUUID(), REQUEST_CD_MS)) return;
        sendProfilRp(sp);
    }

    // ── Création (première connexion) ─────────────────────────────────────

    /** C2S — soumission du formulaire « Création Profil RP ». */
    public static void onCreate(ProfilRpCreatePayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(CREATE_CD, sp.getUUID(), CREATE_CD_MS)) return;

        String erreur = valider(p);
        if (erreur != null) {
            LOGGER.warn("[ProfilRP] Champ invalide de {} : {}", sp.getGameProfile().getName(), erreur);
            PacketDistributor.sendToPlayer(sp, new ProfilRpResultPayload(false, erreur));
            return;
        }

        String uuid = sp.getStringUUID();
        CompletableFuture.runAsync(() -> {
            if (ProfilRpRepository.fetch(uuid) != null) {
                sp.getServer().execute(() -> PacketDistributor.sendToPlayer(sp,
                        new ProfilRpResultPayload(false, "Ton profil RP existe déjà.")));
                return;
            }
            boolean ok = ProfilRpRepository.insert(uuid,
                    p.nom().trim(), p.prenom().trim(), p.sexe().trim(), p.taille().trim(),
                    p.ancienneVille().trim(), p.metier().trim(), p.description().trim());
            sp.getServer().execute(() -> {
                PacketDistributor.sendToPlayer(sp, new ProfilRpResultPayload(ok,
                        ok ? "Profil créé !" : "Erreur serveur — réessaie."));
                if (ok) {
                    LOGGER.info("[ProfilRP] Profil créé pour {} ({})",
                            sp.getGameProfile().getName(), uuid);
                    sendProfilRp(sp); // envoie le profil à jour au client
                }
            });
        }, BaliseStore.DB_EXECUTOR);
    }

    /** @return message d'erreur, ou null si tous les champs sont valides. */
    private static String valider(ProfilRpCreatePayload p) {
        if (!InputValidator.isValidRpTexte(p.nom()))           return "Nom invalide (1-32 lettres).";
        if (!InputValidator.isValidRpTexte(p.prenom()))        return "Prénom invalide (1-32 lettres).";
        if (!InputValidator.isValidSexe(p.sexe()))             return "Sexe invalide.";
        if (!InputValidator.isValidRpTaille(p.taille()))       return "Taille invalide (ex : 1m80).";
        if (!InputValidator.isValidRpTexte(p.ancienneVille())) return "Ancienne ville invalide (1-32 lettres).";
        if (!InputValidator.isValidRpTexte(p.metier()))        return "Métier invalide (1-32 lettres).";
        if (!InputValidator.isValidRpDescription(p.description())) return "Description invalide (1-256 caractères).";
        return null;
    }
}
