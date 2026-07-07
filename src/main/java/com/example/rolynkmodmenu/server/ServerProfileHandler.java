package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlers C2S pour les requêtes de profil.
 * Toutes les requêtes DB sont asynchrones (DB_EXECUTOR), l'envoi revient sur le main thread.
 *
 * SÉCURITÉ :
 *  – Rate-limiting sur ProfileRequest et ProfilJoueurRequest
 *  – Validation du format UUID sur ProfilJoueurRequest
 *  – Cache serveur TTL 30 s pour éviter des hits DB répétés
 */
public final class ServerProfileHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerProfileHandler() {}

    // ── Cache serveur (TTL 30 s) ───────────────────────────────────────────────

    private record CachedProfile(JoueurRepository.ProfileData data, long expiresAt) {}

    private static final ConcurrentHashMap<String, CachedProfile> CACHE      = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000L;

    private static JoueurRepository.ProfileData fromCache(String uuid) {
        CachedProfile c = CACHE.get(uuid);
        if (c != null && System.currentTimeMillis() < c.expiresAt()) return c.data();
        return null;
    }

    private static void putCache(String uuid, JoueurRepository.ProfileData data) {
        CACHE.put(uuid, new CachedProfile(data, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    public static void invalidateCache(String uuid) {
        CACHE.remove(uuid);
    }

    // ── Rate-limiting ──────────────────────────────────────────────────────────

    /** Cooldown entre deux ProfileRequest du même joueur. */
    private static final long PROFILE_CD_MS        = 5_000L;
    /** Cooldown entre deux ProfilJoueurRequest du même joueur. */
    private static final long PROFIL_JOUEUR_CD_MS  = 3_000L;

    private static final ConcurrentHashMap<UUID, Long> PROFILE_CD        = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> PROFIL_JOUEUR_CD  = new ConcurrentHashMap<>();

    private static boolean throttle(ConcurrentHashMap<UUID, Long> map, UUID uuid, long cdMs) {
        long now  = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && now - last < cdMs) return true;
        map.put(uuid, now);
        return false;
    }

    /** Nettoyage à la déconnexion. */
    public static void onPlayerLogout(UUID uuid) {
        PROFILE_CD.remove(uuid);
        PROFIL_JOUEUR_CD.remove(uuid);
    }

    // ── Handler ProfileRequestPayload (C2S) ───────────────────────────────────

    /** Le joueur demande son propre profil. */
    public static void onProfileRequest(ProfileRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        if (throttle(PROFILE_CD, sp.getUUID(), PROFILE_CD_MS)) return;
        sendProfile(sp);
    }

    // ── Handler ProfilJoueurRequestPayload (C2S) ──────────────────────────────

    /** Un joueur demande le profil d'un autre joueur (UUID cible). */
    public static void onProfilJoueurRequest(ProfilJoueurRequestPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer requester)) return;
        if (throttle(PROFIL_JOUEUR_CD, requester.getUUID(), PROFIL_JOUEUR_CD_MS)) return;

        // ── Validation du format UUID avant tout hit DB ─────────────────────────
        String targetUuid = payload.uuid();
        if (!InputValidator.isValidUuid(targetUuid)) {
            LOGGER.warn("[Security] ProfilJoueurRequest: {} a envoyé un UUID invalide '{}'",
                    requester.getUUID(), targetUuid);
            return;
        }

        CompletableFuture.runAsync(() -> {
            JoueurRepository.ProfileData data = JoueurRepository.fetchProfile(targetUuid);
            if (data == null) return; // Joueur inconnu en base → pas de réponse
            requester.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(requester, toProfilJoueurPayload(data)));
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── Méthodes utilitaires partagées ─────────────────────────────────────────

    /**
     * Envoie le profil d'un joueur à lui-même.
     * Sert le cache si valide, sinon requête DB.
     */
    public static void sendProfile(ServerPlayer sp) {
        String uuid = sp.getStringUUID();

        JoueurRepository.ProfileData cached = fromCache(uuid);
        if (cached != null) {
            PacketDistributor.sendToPlayer(sp, toProfilePayload(cached));
            return;
        }

        CompletableFuture.runAsync(() -> {
            JoueurRepository.ProfileData data = JoueurRepository.fetchProfile(uuid);
            if (data == null) return;
            putCache(uuid, data);
            sp.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(sp, toProfilePayload(data)));
        }, BaliseStore.DB_EXECUTOR);
    }

    /** Variante login — invalide le cache, met à jour le status en DB. */
    public static void sendProfileOnLogin(ServerPlayer sp) {
        String uuid = sp.getStringUUID();
        CACHE.remove(uuid);
        CompletableFuture.runAsync(() -> {
            JoueurRepository.onLogin(uuid);
            JoueurRepository.ProfileData data = JoueurRepository.fetchProfile(uuid);
            if (data == null) return;
            putCache(uuid, data);
            sp.getServer().execute(() ->
                    PacketDistributor.sendToPlayer(sp, toProfilePayload(data)));
        }, BaliseStore.DB_EXECUTOR);
    }

    // ── Conversions ProfileData → Payload ──────────────────────────────────────

    private static ProfilePayload toProfilePayload(JoueurRepository.ProfileData d) {
        return new ProfilePayload(
                d.uuid(), d.status(), d.pseudo(), d.grade(),
                d.money(), d.cristaux(), d.heuresDeJeu(), d.premiereConnexion(),
                d.villeNom()
        );
    }

    private static ProfilJoueurPayload toProfilJoueurPayload(JoueurRepository.ProfileData d) {
        return new ProfilJoueurPayload(
                d.uuid(), d.status(), d.pseudo(), d.grade(),
                d.money(), d.cristaux(), d.heuresDeJeu(), d.premiereConnexion(),
                d.villeNom()
        );
    }
}
