package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;

/**
 * Charge les identifiants MySQL depuis config/rolynk_db.properties.
 * Ce fichier est externe au JAR — jamais distribué aux joueurs, jamais dans le git.
 */
public final class BaliseDbConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_FILE = Path.of("config", "rolynk_db.properties");

    private final String url;
    private final String username;
    private final String password;

    private BaliseDbConfig(String url, String username, String password) {
        this.url      = url;
        this.username = username;
        this.password = password;
    }

    public String url()      { return url; }
    public String username() { return username; }
    public String password() { return password; }

    /**
     * Charge la config depuis le fichier.
     * Si le fichier est absent, crée un template et retourne null.
     * Si les identifiants sont vides ou non changés, retourne null.
     */
    public static BaliseDbConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            createTemplate();
            LOGGER.warn("[RolynkDB] Fichier de config absent — template créé : {}", CONFIG_FILE.toAbsolutePath());
            LOGGER.warn("[RolynkDB] Renseignez vos identifiants MySQL dans ce fichier puis relancez le serveur.");
            return null;
        }

        java.util.Properties props = new java.util.Properties();
        try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
            props.load(r);
        } catch (IOException e) {
            LOGGER.error("[RolynkDB] Impossible de lire {} : {}", CONFIG_FILE, e.getMessage());
            return null;
        }

        String url  = props.getProperty("db.url",      "").trim();
        String user = props.getProperty("db.username", "").trim();
        String pass = props.getProperty("db.password", "").trim();

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty() || pass.equals("CHANGE_ME")) {
            LOGGER.error("[RolynkDB] Identifiants MySQL manquants ou non configurés dans {}.", CONFIG_FILE.toAbsolutePath());
            return null;
        }

        return new BaliseDbConfig(url, user, pass);
    }

    private static void createTemplate() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                w.write("# Rolynk Mod Menu — Configuration base de données MySQL\n");
                w.write("# IMPORTANT : ce fichier contient des donnees sensibles.\n");
                w.write("# Ne jamais le distribuer, ne jamais le commiter dans git.\n");
                w.write("#\n");
                w.write("# SECURITE : useSSL=false n'est acceptable que si MySQL tourne sur la\n");
                w.write("# MEME machine (127.0.0.1). Pour une base distante, activez TLS :\n");
                w.write("#   ...?useSSL=true&requireSSL=true&verifyServerCertificate=true\n\n");
                w.write("db.url=jdbc:mysql://127.0.0.1:3306/rolynk_mc");
                w.write("?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                w.write("&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048");
                w.write("&useServerPrepStmts=true&rewriteBatchedStatements=true\n");
                w.write("db.username=minecraft\n");
                w.write("db.password=CHANGE_ME\n");
            }
        } catch (IOException e) {
            LOGGER.error("[RolynkDB] Impossible de créer le template : {}", e.getMessage());
        }
    }
}
