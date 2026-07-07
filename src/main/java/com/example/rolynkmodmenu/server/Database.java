package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Point d'accès UNIQUE à MySQL pour tout le mod (balises, villes, profils).
 *
 * Un seul pool HikariCP partagé + un seul executor : sur un réseau Velocity
 * où chaque serveur charge ce mod, c'est ce qui évite de saturer le
 * max_connections de MySQL (avant : 2 pools × 45 connexions par serveur).
 *
 * Cycle de vie explicite — aucun bloc static {} :
 *   init()     → ServerAboutToStartEvent (VilleEventSubscriber)
 *   shutdown() → ServerStoppingEvent     (VilleEventSubscriber)
 *
 * Toutes les méthodes des stores sont BLOQUANTES — toujours les appeler
 * depuis {@link #EXECUTOR}, jamais depuis le main thread serveur.
 */
public final class Database {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Pool de threads dédié aux opérations DB.
     * Dimensionné sur le pool de connexions : plus de threads que de
     * connexions ne ferait qu'attendre sur Hikari.
     */
    private static final int POOL_SIZE = 10;

    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE,
            r -> {
                Thread t = new Thread(r, "RolynkDB-" + THREAD_ID.getAndIncrement());
                t.setDaemon(true);
                return t;
            });

    private static volatile HikariDataSource dataSource;

    private Database() {}

    /** Initialise le pool. Idempotent — sans effet si déjà initialisé. */
    public static synchronized void init() {
        if (dataSource != null && !dataSource.isClosed()) return;

        BaliseDbConfig dbCfg = BaliseDbConfig.load();
        if (dbCfg == null) {
            LOGGER.error("[Database] Pas de configuration MySQL valide — les systèmes balises/villes/profils sont désactivés.");
            return;
        }
        try {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(dbCfg.url());
            cfg.setUsername(dbCfg.username());
            cfg.setPassword(dbCfg.password());
            cfg.setMaximumPoolSize(POOL_SIZE);
            cfg.setMinimumIdle(2);
            cfg.setConnectionTimeout(5_000);
            cfg.setIdleTimeout(300_000);
            cfg.setMaxLifetime(600_000);
            cfg.setPoolName("RolynkDB");
            dataSource = new HikariDataSource(cfg);
            LOGGER.info("[Database] Pool HikariCP initialisé ({} connexions max).", POOL_SIZE);
        } catch (Throwable e) {
            LOGGER.error("[Database] Impossible d'initialiser le pool MySQL : {}", e.getMessage());
        }
    }

    /** @return true si le pool est opérationnel. */
    public static boolean isReady() {
        HikariDataSource ds = dataSource;
        return ds != null && !ds.isClosed();
    }

    public static Connection getConnection() throws SQLException {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) throw new SQLException("Pool RolynkDB non initialisé.");
        return ds.getConnection();
    }

    /**
     * Arrêt ordonné :
     * 1. Signale la fin à l'executor (plus de nouvelles tâches acceptées).
     * 2. Attend jusqu'à 30 s que toutes les tâches en vol se terminent.
     * 3. Ferme le pool de connexions.
     */
    public static synchronized void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(30, TimeUnit.SECONDS))
                LOGGER.warn("[Database] EXECUTOR : timeout 30 s — certaines tâches n'ont pas pu se terminer.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[Database] Attente de l'executor interrompue.");
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("[Database] Pool HikariCP fermé.");
        }
        dataSource = null;
    }
}
