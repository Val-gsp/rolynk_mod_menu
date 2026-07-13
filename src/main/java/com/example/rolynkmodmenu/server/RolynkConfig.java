package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration gameplay du mod, chargée depuis config/rolynk.properties.
 * Si le fichier est absent, un template avec les valeurs par défaut est créé.
 *
 * Le nom de serveur peut aussi être fourni via -Drolynk.server.name=... ;
 * la propriété JVM est prioritaire sur le fichier (pratique pour partager
 * le même fichier de config entre plusieurs serveurs du réseau).
 *
 * Chargée par {@link Database#init()} au démarrage serveur — les getters
 * retournent les valeurs par défaut tant que load() n'a pas été appelé,
 * ce qui rend la classe utilisable (et testable) sans serveur.
 */
public final class RolynkConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_FILE = Path.of("config", "rolynk.properties");

    // ── Valeurs par défaut ────────────────────────────────────────────────
    public static final double DEFAULT_COUT_CREATION_VILLE = 500.0;
    public static final double DEFAULT_COUT_CLAIM          = 100.0;
    /** Majoration ajoutée au coût pour chaque claim déjà possédé par la ville. */
    public static final double DEFAULT_COUT_CLAIM_INCREMENT = 50.0;
    public static final String DEFAULT_MONDES_CLAIM        = "ville";
    /** Si true, casser/poser/interagir est interdit dans les chunks NON claim (hors ville)
     *  sur les mondes de claim. Le joueur doit claim avec sa ville pour construire. */
    public static final boolean DEFAULT_PROTEGER_HORS_CLAIM = false;
    /** Si true, la faim des joueurs est gelée (ne descend jamais, remonte uniquement en mangeant). */
    public static final boolean DEFAULT_PROTECTION_FAIM_GELEE = false;
    /** Si true, les joueurs ne subissent aucun dégât sur ce serveur. */
    public static final boolean DEFAULT_PROTECTION_DEGATS_JOUEURS = false;
    public static final String DEFAULT_SERVER_NAME         = "lobby";
    /** Nom du serveur considéré comme lobby (aucune récompense récupérable dessus). */
    public static final String DEFAULT_LOBBY_NAME          = "lobby";
    /** Récompenses temps de jeu par palier : 30 min, 2 h, 4 h. */
    public static final double[] DEFAULT_RECOMPENSES_PLAYTIME = {20.0, 50.0, 130.0};
    /** Récompense créditée au votant lors d'un vote de ville. */
    public static final double DEFAULT_RECOMPENSE_VOTE_VILLE = 20.0;
    /** Distance minimale en blocs (axe XZ) pour déclencher la récompense d'exploration. */
    public static final int    DEFAULT_EXPLORATION_SEUIL_BLOCS = 20_000;
    /** Récompense créditée lorsque le joueur atteint le seuil d'exploration. */
    public static final double DEFAULT_RECOMPENSE_EXPLORATION  = 20.0;

    // ── Navigation (changements de serveur Velocity) ──────────────────────
    public static final String DEFAULT_NAV_SERVEUR_VILLE     = "ville";
    public static final String DEFAULT_NAV_SERVEUR_CAPITALE  = "capitale";
    public static final String DEFAULT_NAV_SERVEUR_RESSOURCE = "ressource";
    /** Zone de téléportation aléatoire (ressource) : carré centré, rayon en blocs. */
    public static final int    DEFAULT_RESSOURCE_CENTRE_X    = 0;
    public static final int    DEFAULT_RESSOURCE_CENTRE_Z    = 0;
    public static final int    DEFAULT_RESSOURCE_RAYON       = 5_000;

    private static volatile double coutCreationVille = DEFAULT_COUT_CREATION_VILLE;
    private static volatile double coutClaim         = DEFAULT_COUT_CLAIM;
    private static volatile double coutClaimIncrement = DEFAULT_COUT_CLAIM_INCREMENT;
    private static volatile Set<String> mondesClaim  = Set.of(DEFAULT_MONDES_CLAIM);
    private static volatile boolean protegerHorsClaim = DEFAULT_PROTEGER_HORS_CLAIM;
    private static volatile boolean protectionFaimGelee = DEFAULT_PROTECTION_FAIM_GELEE;
    private static volatile boolean protectionDegatsJoueurs = DEFAULT_PROTECTION_DEGATS_JOUEURS;
    private static volatile String serverName        = resolveServerName(null);
    private static volatile String lobbyName         = DEFAULT_LOBBY_NAME;
    private static volatile double[] recompensesPlaytime    = DEFAULT_RECOMPENSES_PLAYTIME.clone();
    private static volatile double recompenseVoteVille     = DEFAULT_RECOMPENSE_VOTE_VILLE;
    private static volatile int    explorationSeuilBlocs   = DEFAULT_EXPLORATION_SEUIL_BLOCS;
    private static volatile double recompenseExploration   = DEFAULT_RECOMPENSE_EXPLORATION;

    private RolynkConfig() {}

    // ── Accès ─────────────────────────────────────────────────────────────

    /** Nom du serveur tel que déclaré dans velocity.toml. */
    public static String serverName()        { return serverName; }
    /** Coût de création d'une ville (monnaie joueur). */
    public static double coutCreationVille() { return coutCreationVille; }
    /** Coût du premier claim de chunk (monnaie joueur). */
    public static double coutClaim()         { return coutClaim; }
    /** Majoration du coût par claim déjà possédé (1er=base, 2e=base+incr, 3e=base+2×incr…). */
    public static double coutClaimIncrement() { return coutClaimIncrement; }
    /** Serveurs (mondes) où le claim est autorisé, en lowercase. */
    public static Set<String> mondesClaim()  { return mondesClaim; }

    public static boolean isClaimAutorise(String monde) {
        return monde != null && mondesClaim.contains(monde.toLowerCase(Locale.ROOT));
    }

    /** @return true si la casse/pose hors des claims de ville est interdite (sur les mondes de claim). */
    public static boolean protegerHorsClaim() { return protegerHorsClaim; }

    /** @return true si la faim des joueurs est gelée sur ce serveur (cité protégée). */
    public static boolean protectionFaimGelee() { return protectionFaimGelee; }

    /** @return true si les joueurs ne subissent aucun dégât sur ce serveur (cité protégée). */
    public static boolean protectionDegatsJoueurs() { return protectionDegatsJoueurs; }

    /** Nom du serveur considéré comme lobby. */
    public static String lobbyName() { return lobbyName; }

    /** @return true si CE serveur est le lobby (aucune récompense ne doit y être récupérable). */
    public static boolean isLobby() {
        return serverName.equalsIgnoreCase(lobbyName);
    }

    /** Montant de la récompense temps de jeu pour un palier (0 = 30 min, 1 = 2 h, 2 = 4 h). */
    public static double recompensePlaytime(int palier) {
        return recompensesPlaytime[palier];
    }

    /** Montant crédité au joueur lorsqu'il vote pour une ville. */
    public static double recompenseVoteVille() { return recompenseVoteVille; }

    /** Nombre de blocs XZ à parcourir pour déclencher la récompense d'exploration. */
    public static int explorationSeuilBlocs() { return explorationSeuilBlocs; }

    /** Montant crédité lorsque le joueur atteint le seuil d'exploration. */
    public static double recompenseExploration() { return recompenseExploration; }

    // ── Chargement ────────────────────────────────────────────────────────

    public static synchronized void load() {
        if (!Files.exists(CONFIG_FILE)) {
            createTemplate();
            LOGGER.info("[RolynkConfig] Fichier absent — template créé avec les valeurs par défaut : {}",
                    CONFIG_FILE.toAbsolutePath());
        }

        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
            props.load(r);
        } catch (IOException e) {
            LOGGER.error("[RolynkConfig] Impossible de lire {} : {} — valeurs par défaut conservées.",
                    CONFIG_FILE, e.getMessage());
            return;
        }
        apply(props);
        LOGGER.info("[RolynkConfig] serveur='{}', coutVille={}, coutClaim={}, coutClaimIncrement={}, mondesClaim={}, protegerHorsClaim={}, faimGelee={}, degatsJoueursOff={}",
                serverName, coutCreationVille, coutClaim, coutClaimIncrement, mondesClaim, protegerHorsClaim,
                protectionFaimGelee, protectionDegatsJoueurs);
    }

    /** Applique un jeu de propriétés. Visible pour les tests. */
    static void apply(Properties props) {
        coutCreationVille = parsePositiveDouble(props.getProperty("ville.cout.creation"),
                DEFAULT_COUT_CREATION_VILLE, "ville.cout.creation");
        coutClaim = parsePositiveDouble(props.getProperty("ville.cout.claim"),
                DEFAULT_COUT_CLAIM, "ville.cout.claim");
        coutClaimIncrement = parsePositiveDouble(props.getProperty("ville.cout.claim.increment"),
                DEFAULT_COUT_CLAIM_INCREMENT, "ville.cout.claim.increment");
        mondesClaim = parseWorldList(props.getProperty("ville.mondes.claim", DEFAULT_MONDES_CLAIM));
        protegerHorsClaim = parseBoolean(props.getProperty("ville.proteger.hors.claim"),
                DEFAULT_PROTEGER_HORS_CLAIM);
        protectionFaimGelee = parseBoolean(props.getProperty("protection.faim.gelee"),
                DEFAULT_PROTECTION_FAIM_GELEE);
        protectionDegatsJoueurs = parseBoolean(props.getProperty("protection.degats.joueurs"),
                DEFAULT_PROTECTION_DEGATS_JOUEURS);
        serverName = resolveServerName(props.getProperty("server.name"));

        String lobby = props.getProperty("server.lobby.name");
        lobbyName = (lobby != null && !lobby.isBlank()) ? lobby.trim() : DEFAULT_LOBBY_NAME;

        String[] playtimeKeys = {"recompense.playtime.30m", "recompense.playtime.2h", "recompense.playtime.4h"};
        double[] playtime = new double[playtimeKeys.length];
        for (int i = 0; i < playtimeKeys.length; i++) {
            playtime[i] = parsePositiveDouble(props.getProperty(playtimeKeys[i]),
                    DEFAULT_RECOMPENSES_PLAYTIME[i], playtimeKeys[i]);
        }
        recompensesPlaytime = playtime;

        recompenseVoteVille = parsePositiveDouble(props.getProperty("recompense.vote.ville"),
                DEFAULT_RECOMPENSE_VOTE_VILLE, "recompense.vote.ville");

        explorationSeuilBlocs = (int) parsePositiveDouble(props.getProperty("exploration.seuil.blocs"),
                DEFAULT_EXPLORATION_SEUIL_BLOCS, "exploration.seuil.blocs");
        recompenseExploration = parsePositiveDouble(props.getProperty("recompense.exploration"),
                DEFAULT_RECOMPENSE_EXPLORATION, "recompense.exploration");
    }

    static double parsePositiveDouble(String raw, double fallback, String key) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double v = Double.parseDouble(raw.trim());
            if (Double.isFinite(v) && v >= 0) return v;
        } catch (NumberFormatException ignored) {}
        LOGGER.warn("[RolynkConfig] Valeur invalide pour {} : '{}' — défaut {} utilisé.", key, raw, fallback);
        return fallback;
    }

    static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("oui") || v.equals("yes")) return true;
        if (v.equals("false") || v.equals("0") || v.equals("non") || v.equals("no")) return false;
        return fallback;
    }

    static Set<String> parseWorldList(String raw) {
        Set<String> set = Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return set.isEmpty() ? Set.of(DEFAULT_MONDES_CLAIM) : set;
    }

    /** -Drolynk.server.name prioritaire, puis le fichier, puis le défaut. */
    static String resolveServerName(String fromFile) {
        String fromJvm = System.getProperty("rolynk.server.name");
        if (fromJvm != null && !fromJvm.isBlank()) return fromJvm.trim();
        if (fromFile != null && !fromFile.isBlank()) return fromFile.trim();
        return DEFAULT_SERVER_NAME;
    }

    private static void createTemplate() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                w.write("# Rolynk Mod Menu — Configuration gameplay\n");
                w.write("# Rechargée au démarrage du serveur.\n\n");
                w.write("# Nom de ce serveur tel que déclaré dans velocity.toml.\n");
                w.write("# Peut aussi être fourni via -Drolynk.server.name=... (prioritaire).\n");
                w.write("server.name=" + DEFAULT_SERVER_NAME + "\n\n");
                w.write("# Nom du serveur lobby — aucune récompense ne peut y être récupérée.\n");
                w.write("server.lobby.name=" + DEFAULT_LOBBY_NAME + "\n\n");
                w.write("# Coût de création d'une ville (monnaie joueur).\n");
                w.write("ville.cout.creation=" + (long) DEFAULT_COUT_CREATION_VILLE + "\n\n");
                w.write("# Coût d'un claim de chunk — PROGRESSIF : le 1er claim coûte la base,\n");
                w.write("# puis chaque claim suivant coûte increment de plus (100, 150, 200, ...).\n");
                w.write("ville.cout.claim=" + (long) DEFAULT_COUT_CLAIM + "\n");
                w.write("ville.cout.claim.increment=" + (long) DEFAULT_COUT_CLAIM_INCREMENT + "\n\n");
                w.write("# Serveurs où le claim est autorisé (séparés par des virgules).\n");
                w.write("ville.mondes.claim=" + DEFAULT_MONDES_CLAIM + "\n\n");
                w.write("# Si true : impossible de casser/poser/interagir hors d'un claim de ville\n");
                w.write("# (sur les mondes de claim). Le joueur doit claim avec sa ville pour construire.\n");
                w.write("ville.proteger.hors.claim=" + DEFAULT_PROTEGER_HORS_CLAIM + "\n\n");
                w.write("# Cité protégée : gel de la faim (ne descend jamais, remonte uniquement\n");
                w.write("# en mangeant) et immunité totale aux dégâts. À activer sur capitale seulement.\n");
                w.write("protection.faim.gelee=" + DEFAULT_PROTECTION_FAIM_GELEE + "\n");
                w.write("protection.degats.joueurs=" + DEFAULT_PROTECTION_DEGATS_JOUEURS + "\n\n");
                w.write("# Récompenses quotidiennes de temps de jeu (monnaie créditée par palier).\n");
                w.write("# Les paliers (30 min / 2 h / 4 h) se réinitialisent chaque jour à minuit.\n");
                w.write("recompense.playtime.30m=" + (long) DEFAULT_RECOMPENSES_PLAYTIME[0] + "\n");
                w.write("recompense.playtime.2h=" + (long) DEFAULT_RECOMPENSES_PLAYTIME[1] + "\n");
                w.write("recompense.playtime.4h=" + (long) DEFAULT_RECOMPENSES_PLAYTIME[2] + "\n\n");
                w.write("# Récompense créditée au joueur lorsqu'il vote pour une ville (quotidien).\n");
                w.write("recompense.vote.ville=" + (long) DEFAULT_RECOMPENSE_VOTE_VILLE + "\n\n");
                w.write("# Exploration quotidienne : blocs XZ à parcourir + récompense associée.\n");
                w.write("# Le compteur se remet à zéro chaque jour à minuit.\n");
                w.write("exploration.seuil.blocs=" + DEFAULT_EXPLORATION_SEUIL_BLOCS + "\n");
                w.write("recompense.exploration=" + (long) DEFAULT_RECOMPENSE_EXPLORATION + "\n");
            }
        } catch (IOException e) {
            LOGGER.error("[RolynkConfig] Impossible de créer le template : {}", e.getMessage());
        }
    }
}
