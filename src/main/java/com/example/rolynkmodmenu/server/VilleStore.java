package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accès MySQL pour le système de villes.
 * Le pool de connexions est partagé via {@link Database}.
 *
 * Tables requises (MariaDB/MySQL) — schéma de référence, voir aussi docs/schema.sql :
 * ────────────────────────────────────────────────────────────────────────
 *
 * CREATE TABLE IF NOT EXISTS villes (
 *   id            INT AUTO_INCREMENT PRIMARY KEY,
 *   nom           VARCHAR(64) NOT NULL UNIQUE,
 *   nom_lower     VARCHAR(64) AS (LOWER(nom)) STORED,
 *   owner_uuid    VARCHAR(36) NOT NULL,
 *   banque        DECIMAL(20,2) NOT NULL DEFAULT 0,
 *   date_creation DATETIME DEFAULT NOW(),
 *   monde         VARCHAR(64) DEFAULT NULL,
 *   total_chunks  INT NOT NULL DEFAULT 0,
 *   recrutement   VARCHAR(16) NOT NULL DEFAULT 'sur_demande',
 *   KEY idx_nom_lower (nom_lower)
 * );
 *
 * CREATE TABLE IF NOT EXISTS villes_membres (
 *   id          INT AUTO_INCREMENT PRIMARY KEY,
 *   ville_id    INT NOT NULL,
 *   uuid        VARCHAR(36) NOT NULL,
 *   grade_ville VARCHAR(20) NOT NULL DEFAULT 'Membre',
 *   UNIQUE KEY uq_ville_membre (ville_id, uuid),
 *   UNIQUE KEY uq_membre_uuid (uuid),          -- un joueur = une seule ville, garanti par la DB
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * CREATE TABLE IF NOT EXISTS villes_claims (
 *   id       INT AUTO_INCREMENT PRIMARY KEY,
 *   ville_id INT NOT NULL,
 *   monde    VARCHAR(64) NOT NULL,
 *   chunk_x  INT NOT NULL,
 *   chunk_z  INT NOT NULL,
 *   UNIQUE KEY uq_claim (monde, chunk_x, chunk_z),
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * CREATE TABLE IF NOT EXISTS villes_banque_logs (
 *   id        INT AUTO_INCREMENT PRIMARY KEY,
 *   ville_id  INT NOT NULL,
 *   uuid      VARCHAR(36) NOT NULL,
 *   pseudo    VARCHAR(32) NOT NULL,
 *   action    VARCHAR(32) NOT NULL,   -- depot, retrait, grade_*, rec_*, acc_dem
 *   montant   DECIMAL(20,2) NOT NULL,
 *   timestamp DATETIME DEFAULT NOW(),
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * CREATE TABLE IF NOT EXISTS villes_demandes (
 *   id        INT AUTO_INCREMENT PRIMARY KEY,
 *   ville_id  INT NOT NULL,
 *   uuid      VARCHAR(36) NOT NULL,
 *   pseudo    VARCHAR(32) NOT NULL,
 *   timestamp DATETIME DEFAULT NOW(),
 *   UNIQUE KEY uq_demande (ville_id, uuid),
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * CREATE TABLE IF NOT EXISTS villes_invites (
 *   id         INT AUTO_INCREMENT PRIMARY KEY,
 *   ville_id   INT NOT NULL,
 *   uuid       VARCHAR(36) NOT NULL,
 *   invite_par VARCHAR(36) NOT NULL,
 *   expire_at  DATETIME NOT NULL,
 *   UNIQUE KEY uq_invite (ville_id, uuid),
 *   FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
 * );
 *
 * Toutes les méthodes sont BLOQUANTES — appeler depuis Database.EXECUTOR uniquement.
 */
public final class VilleStore {

    // ── Records de données ────────────────────────────────────────────────

    // Valeurs possibles pour le champ recrutement
    public static final String RECRUTEMENT_OUVERT     = "ouvert";
    public static final String RECRUTEMENT_SUR_DEMANDE = "sur_demande";
    public static final String RECRUTEMENT_FERME      = "ferme";

    public record VilleInfo(
            int id, String nom, String ownerUuid, String ownerPseudo,
            double banque, String dateFondation, String monde,
            int totalChunks, String recrutement,
            /** Nombre de membres — inclus directement dans la requête SQL, élimine le N+1. */
            int nbMembres
    ) {}

    public record MembreInfo(String uuid, String pseudo, String grade) {}

    public record LogInfo(String pseudo, String action, double montant, String timestamp) {}

    public record DemandeInfo(String uuid, String pseudo, String timestamp) {}

    // ── Logging ───────────────────────────────────────────────────────────
    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Caches en mémoire ─────────────────────────────────────────────────
    /** monde:chunkX:chunkZ → villeId */
    public static final ConcurrentHashMap<String, Integer> CLAIM_CACHE   = new ConcurrentHashMap<>();
    /** uuid joueur → villeId (-1 si sans ville) */
    public static final ConcurrentHashMap<String, Integer> UUID_VILLE_CACHE = new ConcurrentHashMap<>();
    /** villeId → nomVille */
    public static final ConcurrentHashMap<Integer, String> VILLE_NOM_CACHE  = new ConcurrentHashMap<>();
    /** uuid joueur → packed long chunkX<<32|chunkZ (pour actionbar) */
    public static final ConcurrentHashMap<String, Long> LAST_CHUNK = new ConcurrentHashMap<>();
    /**
     * "villeId:monde" → nombre de chunks claimés dans ce monde.
     * Permet de savoir en O(1) si une ville a déjà un claim dans un monde donné
     * (évite le scan complet de CLAIM_CACHE dans claimChunk()).
     */
    public static final ConcurrentHashMap<String, Integer> VILLE_MONDE_COUNT = new ConcurrentHashMap<>();

    // ── Directions cardinales (pour adjacence claims) ─────────────────────
    private static final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

    private VilleStore() {}

    private static Connection getConn() throws SQLException {
        return Database.getConnection();
    }

    // ── Chargement des caches au démarrage ────────────────────────────────

    public static void loadAllCaches() {
        loadClaimsCache();
        loadUuidVilleCache();
        loadVilleNomCache();
        loadVilleMondeCache();   // doit être APRÈS loadClaimsCache (lit depuis CLAIM_CACHE)
        LOGGER.info("[VilleStore] Caches chargés : {} claims, {} joueurs, {} villes, {} monde-entries.",
                CLAIM_CACHE.size(), UUID_VILLE_CACHE.size(), VILLE_NOM_CACHE.size(), VILLE_MONDE_COUNT.size());
    }

    private static void loadClaimsCache() {
        CLAIM_CACHE.clear();
        String sql = "SELECT ville_id, monde, chunk_x, chunk_z FROM villes_claims";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CLAIM_CACHE.put(claimKey(rs.getString("monde"), rs.getInt("chunk_x"), rs.getInt("chunk_z")),
                        rs.getInt("ville_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] loadClaimsCache: {}", e.getMessage());
        }
    }

    private static void loadUuidVilleCache() {
        UUID_VILLE_CACHE.clear();
        String sql = "SELECT uuid, ville_id FROM villes_membres";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID_VILLE_CACHE.put(rs.getString("uuid"), rs.getInt("ville_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] loadUuidVilleCache: {}", e.getMessage());
        }
    }

    private static void loadVilleNomCache() {
        VILLE_NOM_CACHE.clear();
        String sql = "SELECT id, nom FROM villes";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VILLE_NOM_CACHE.put(rs.getInt("id"), rs.getString("nom"));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] loadVilleNomCache: {}", e.getMessage());
        }
    }

    /**
     * Construit VILLE_MONDE_COUNT depuis CLAIM_CACHE (déjà en mémoire) — 0 requête DB.
     * Doit être appelé après loadClaimsCache().
     * Clé de CLAIM_CACHE = "monde:cx:cz" → on extrait le monde (avant le premier ':').
     */
    private static void loadVilleMondeCache() {
        VILLE_MONDE_COUNT.clear();
        CLAIM_CACHE.forEach((key, villeId) -> {
            int colon = key.indexOf(':');
            if (colon > 0) {
                VILLE_MONDE_COUNT.merge(mondeCacheKey(villeId, key.substring(0, colon)), 1, Integer::sum);
            }
        });
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    public static String claimKey(String monde, int cx, int cz) {
        return monde.toLowerCase() + ":" + cx + ":" + cz;
    }

    /** Clé pour VILLE_MONDE_COUNT : "villeId:monde". */
    private static String mondeCacheKey(int villeId, String monde) {
        return villeId + ":" + monde.toLowerCase();
    }

    /** Retourne l'ID de la ville possédant ce chunk, ou -1 si non claimé. */
    public static int getVilleOfChunk(String monde, int cx, int cz) {
        return CLAIM_CACHE.getOrDefault(claimKey(monde, cx, cz), -1);
    }

    /** Retourne l'ID de la ville du joueur, ou -1. Utilise le cache. */
    public static int getVilleIdByUuid(String uuid) {
        return UUID_VILLE_CACHE.getOrDefault(uuid, -1);
    }

    /**
     * Recharge depuis la DB l'appartenance ville du joueur dans UUID_VILLE_CACHE.
     *
     * INDISPENSABLE à la connexion : les caches sont par-backend ; un quitter/
     * rejoindre/kick effectué sur un AUTRE serveur du réseau n'est pas répercuté
     * ici. Sans ce rechargement, ce serveur peut continuer à croire le joueur
     * membre d'une ville qu'il a quittée ailleurs (affichage + autorisations).
     *
     * BLOQUANT — Database.EXECUTOR uniquement.
     * @return l'id de ville (ou -1 si sans ville).
     */
    public static int refreshMembership(String uuid) {
        String sql = "SELECT ville_id FROM villes_membres WHERE uuid=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                int villeId = rs.next() ? rs.getInt("ville_id") : -1;
                UUID_VILLE_CACHE.put(uuid, villeId);
                return villeId;
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] refreshMembership({}): {}", uuid, e.getMessage());
            return getVilleIdByUuid(uuid); // en cas d'erreur, conserve la valeur actuelle
        }
    }

    /** Retourne le nom de la ville, ou "" si inconnue. */
    public static String getVilleNom(int villeId) {
        return VILLE_NOM_CACHE.getOrDefault(villeId, "");
    }

    // ── Villes — liste ────────────────────────────────────────────────────

    public static List<VilleInfo> getAllVilles() {
        List<VilleInfo> list = new ArrayList<>();
        // Sous-requête scalaire pour nb_membres : 1 passe SQL au lieu de N appels getNbMembres()
        String sql = "SELECT v.id, v.nom, v.owner_uuid, j.pseudo AS owner_pseudo, "
                   + "v.banque, DATE_FORMAT(v.date_creation,'%d/%m/%Y') AS date_f, "
                   + "v.monde, v.total_chunks, v.recrutement, "
                   + "(SELECT COUNT(*) FROM villes_membres WHERE ville_id = v.id) AS nb_membres "
                   + "FROM villes v LEFT JOIN joueurs j ON j.uuid = v.owner_uuid "
                   + "ORDER BY v.nom ASC";
        try (Connection c = getConn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String rec = rs.getString("recrutement");
                list.add(new VilleInfo(
                        rs.getInt("id"), rs.getString("nom"), rs.getString("owner_uuid"),
                        rs.getString("owner_pseudo") == null ? "Inconnu" : rs.getString("owner_pseudo"),
                        rs.getDouble("banque"), rs.getString("date_f"),
                        rs.getString("monde"), rs.getInt("total_chunks"),
                        rec == null ? RECRUTEMENT_SUR_DEMANDE : rec,
                        rs.getInt("nb_membres")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getAllVilles: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Retourne une ville par son ID. 1 seule requête ciblée (index PK).
     * Remplace getAllVilles() + stream.filter() dans /ville info.
     * @return VilleInfo ou null si la ville n'existe pas.
     */
    public static VilleInfo getVilleById(int id) {
        String sql = "SELECT v.id, v.nom, v.owner_uuid, j.pseudo AS owner_pseudo, "
                   + "v.banque, DATE_FORMAT(v.date_creation,'%d/%m/%Y') AS date_f, "
                   + "v.monde, v.total_chunks, v.recrutement, "
                   + "(SELECT COUNT(*) FROM villes_membres WHERE ville_id = v.id) AS nb_membres "
                   + "FROM villes v LEFT JOIN joueurs j ON j.uuid = v.owner_uuid "
                   + "WHERE v.id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String rec = rs.getString("recrutement");
                    return new VilleInfo(
                            rs.getInt("id"), rs.getString("nom"), rs.getString("owner_uuid"),
                            rs.getString("owner_pseudo") == null ? "Inconnu" : rs.getString("owner_pseudo"),
                            rs.getDouble("banque"), rs.getString("date_f"),
                            rs.getString("monde"), rs.getInt("total_chunks"),
                            rec == null ? RECRUTEMENT_SUR_DEMANDE : rec,
                            rs.getInt("nb_membres"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getVilleById({}): {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * Retourne une ville par son nom (insensible à la casse, index sur LOWER(nom) recommandé).
     * Remplace getAllVilles() + stream.filter().equalsIgnoreCase() dans /ville demande et /ville rejoindre.
     * @return VilleInfo ou null si la ville n'existe pas.
     */
    public static VilleInfo getVilleByNom(String nom) {
        String sql = "SELECT v.id, v.nom, v.owner_uuid, j.pseudo AS owner_pseudo, "
                   + "v.banque, DATE_FORMAT(v.date_creation,'%d/%m/%Y') AS date_f, "
                   + "v.monde, v.total_chunks, v.recrutement, "
                   + "(SELECT COUNT(*) FROM villes_membres WHERE ville_id = v.id) AS nb_membres "
                   + "FROM villes v LEFT JOIN joueurs j ON j.uuid = v.owner_uuid "
                   + "WHERE v.nom_lower = LOWER(?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nom);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String rec = rs.getString("recrutement");
                    return new VilleInfo(
                            rs.getInt("id"), rs.getString("nom"), rs.getString("owner_uuid"),
                            rs.getString("owner_pseudo") == null ? "Inconnu" : rs.getString("owner_pseudo"),
                            rs.getDouble("banque"), rs.getString("date_f"),
                            rs.getString("monde"), rs.getInt("total_chunks"),
                            rec == null ? RECRUTEMENT_SUR_DEMANDE : rec,
                            rs.getInt("nb_membres"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getVilleByNom({}): {}", nom, e.getMessage());
        }
        return null;
    }

    // ── Création de ville ─────────────────────────────────────────────────

    /**
     * Crée une nouvelle ville en débitant atomiquement le coût.
     * Vérification du solde, débit, création de la ville et ajout en tant que Chef
     * se font dans une SEULE transaction — aucune race condition possible.
     *
     * @param cout montant à débiter depuis joueurs.money (0 = gratuit)
     * @return null si succès, message d'erreur coloré sinon.
     */
    public static String creerVille(String nom, String ownerUuid, double cout) {
        // Nettoyage orphelins (hors transaction — opération de maintenance)
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(
                "DELETE vm FROM villes_membres vm LEFT JOIN villes v ON vm.ville_id=v.id WHERE v.id IS NULL")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("[VilleStore] creerVille nettoyage: {}", e.getMessage());
        }

        if (getVilleIdByUuid(ownerUuid) != -1) return "§cTu es déjà membre d'une ville.";

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 1. Vérif nom unique (dans la transaction pour éviter les doublons concurrents)
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM villes WHERE nom=?")) {
                    ps.setString(1, nom);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { c.rollback(); return "§cCe nom de ville est déjà pris."; }
                    }
                }

                // 2. Débit atomique : échoue si solde insuffisant (pas de double vérification)
                if (cout > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE joueurs SET money = money - ? WHERE uuid = ? AND money >= ?")) {
                        ps.setDouble(1, cout);
                        ps.setString(2, ownerUuid);
                        ps.setDouble(3, cout);
                        if (ps.executeUpdate() == 0) {
                            c.rollback();
                            return "§cSolde insuffisant — il faut §e"
                                    + com.example.rolynkmodmenu.util.Money.entier(cout) + " §cpour créer une ville.";
                        }
                    }
                }

                // 3. Création de la ville
                int villeId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes (nom, owner_uuid) VALUES (?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nom);
                    ps.setString(2, ownerUuid);
                    ps.executeUpdate();
                    try (ResultSet gk = ps.getGeneratedKeys()) {
                        if (!gk.next()) throw new SQLException("Pas de clé générée.");
                        villeId = gk.getInt(1);
                    }
                }

                // 4. Ajout du fondateur comme Chef
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_membres (ville_id, uuid, grade_ville) VALUES (?,?,'Chef')")) {
                    ps.setInt(1, villeId);
                    ps.setString(2, ownerUuid);
                    ps.executeUpdate();
                }

                c.commit();
                // MAJ caches (après commit uniquement)
                VILLE_NOM_CACHE.put(villeId, nom);
                UUID_VILLE_CACHE.put(ownerUuid, villeId);
                return null; // succès
            } catch (SQLIntegrityConstraintViolationException dup) {
                // UNIQUE(nom) ou UNIQUE(uuid) violée par une opération concurrente
                c.rollback();
                return "§cCe nom est déjà pris ou tu es déjà dans une ville.";
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] creerVille({}, {}): {}", nom, ownerUuid, e.getMessage());
            return "§cErreur lors de la création de la ville.";
        }
    }

    // ── Membres ───────────────────────────────────────────────────────────

    public static List<MembreInfo> getMembres(int villeId) {
        List<MembreInfo> list = new ArrayList<>();
        String sql = "SELECT vm.uuid, j.pseudo, vm.grade_ville "
                   + "FROM villes_membres vm LEFT JOIN joueurs j ON j.uuid=vm.uuid "
                   + "WHERE vm.ville_id=? ORDER BY vm.grade_ville DESC, j.pseudo ASC";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pseudo = rs.getString("pseudo");
                    list.add(new MembreInfo(rs.getString("uuid"),
                            pseudo == null ? "Inconnu" : pseudo,
                            rs.getString("grade_ville")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getMembres({}): {}", villeId, e.getMessage());
        }
        return list;
    }

    public static String getGrade(int villeId, String uuid) {
        String sql = "SELECT grade_ville FROM villes_membres WHERE ville_id=? AND uuid=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            ps.setString(2, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("grade_ville");
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getGrade: {}", e.getMessage());
        }
        return null;
    }

    public static String retirerMembre(int villeId, String kickerUuid, String cibleUuid) {
        String kickerGrade = getGrade(villeId, kickerUuid);
        if (!"Chef".equals(kickerGrade) && !"Adjoint".equals(kickerGrade))
            return "§cSeuls les Chefs et Adjoints peuvent exclure des membres.";
        if (kickerUuid.equals(cibleUuid))
            return "§cTu ne peux pas t'exclure toi-même.";
        String cibleGrade = getGrade(villeId, cibleUuid);
        if ("Adjoint".equals(kickerGrade) && !"Membre".equals(cibleGrade))
            return "§cUn Adjoint ne peut exclure que les Membres.";
        if ("Chef".equals(cibleGrade))
            return "§cImpossible d'exclure le Chef.";

        String sql = "DELETE FROM villes_membres WHERE ville_id=? AND uuid=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            ps.setString(2, cibleUuid);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) UUID_VILLE_CACHE.put(cibleUuid, -1);
            return ok ? null : "§cMembre introuvable.";
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] retirerMembre: {}", e.getMessage());
            return "§cErreur lors de l'exclusion.";
        }
    }

    public static String changerGrade(int villeId, String chefUuid, String chefPseudo,
                                      String cibleUuid, String newGrade) {
        if (!"Chef".equals(getGrade(villeId, chefUuid)))
            return "§cSeul le Chef peut modifier les grades.";
        if (!List.of("Membre","Adjoint","Chef").contains(newGrade))
            return "§cGrade invalide.";

        // Pseudo de la cible pour le log
        String ciblePseudo = cibleUuid;
        try (Connection cx = getConn(); PreparedStatement ps = cx.prepareStatement(
                "SELECT COALESCE(j.pseudo, vm.uuid) as pseudo FROM villes_membres vm " +
                "LEFT JOIN joueurs j ON j.uuid=vm.uuid WHERE vm.ville_id=? AND vm.uuid=?")) {
            ps.setInt(1, villeId); ps.setString(2, cibleUuid);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) ciblePseudo = rs.getString("pseudo"); }
        } catch (SQLException ignored) {}

        String logAction = "grade_" + newGrade.toLowerCase();

        // Si on passe le grade de Chef à quelqu'un d'autre, on dégrade l'ancien chef à Adjoint
        if ("Chef".equals(newGrade)) {
            try (Connection c = getConn()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE villes_membres SET grade_ville='Adjoint' WHERE ville_id=? AND uuid=?")) {
                        ps.setInt(1, villeId); ps.setString(2, chefUuid); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE villes_membres SET grade_ville='Chef' WHERE ville_id=? AND uuid=?")) {
                        ps.setInt(1, villeId); ps.setString(2, cibleUuid); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE villes SET owner_uuid=? WHERE id=?")) {
                        ps.setString(1, cibleUuid); ps.setInt(2, villeId); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,?,0)")) {
                        ps.setInt(1, villeId); ps.setString(2, cibleUuid);
                        ps.setString(3, ciblePseudo); ps.setString(4, logAction);
                        ps.executeUpdate();
                    }
                    c.commit();
                    return null;
                } catch (SQLException ex) { c.rollback(); throw ex; }
                finally { c.setAutoCommit(true); }
            } catch (SQLException e) {
                LOGGER.error("[VilleStore] changerGrade(Chef): {}", e.getMessage());
                return "§cErreur lors du changement de grade.";
            }
        }

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE villes_membres SET grade_ville=? WHERE ville_id=? AND uuid=?")) {
                    ps.setString(1, newGrade); ps.setInt(2, villeId); ps.setString(3, cibleUuid);
                    if (ps.executeUpdate() == 0) { c.rollback(); return "§cMembre introuvable."; }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,?,0)")) {
                    ps.setInt(1, villeId); ps.setString(2, cibleUuid);
                    ps.setString(3, ciblePseudo); ps.setString(4, logAction);
                    ps.executeUpdate();
                }
                c.commit();
                return null;
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] changerGrade: {}", e.getMessage());
            return "§cErreur lors du changement de grade.";
        }
    }

    public static String quitterVille(String uuid) {
        int villeId = getVilleIdByUuid(uuid);
        if (villeId == -1) return "§cTu n'es dans aucune ville.";
        if ("Chef".equals(getGrade(villeId, uuid)))
            return "§cTu es Chef. Dissous la ville ou transfère le grade avant de partir.";

        String sql = "DELETE FROM villes_membres WHERE ville_id=? AND uuid=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId); ps.setString(2, uuid);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) UUID_VILLE_CACHE.put(uuid, -1);
            return ok ? null : "§cErreur lors de la sortie.";
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] quitterVille: {}", e.getMessage());
            return "§cErreur lors de la sortie de la ville.";
        }
    }

    public static String dissoudreVille(String uuid) {
        int villeId = getVilleIdByUuid(uuid);
        if (villeId == -1) return "§cTu n'es dans aucune ville.";
        if (!"Chef".equals(getGrade(villeId, uuid)))
            return "§cSeul le Chef peut dissoudre la ville.";

        // Récupère tous les membres pour nettoyer le cache
        List<String> memberUuids = new ArrayList<>();
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(
                "SELECT uuid FROM villes_membres WHERE ville_id=?")) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) memberUuids.add(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            LOGGER.warn("[VilleStore] dissoudreVille getMembres: {}", e.getMessage());
        }

        String sql = "DELETE FROM villes WHERE id=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                VILLE_NOM_CACHE.remove(villeId);
                for (String mu : memberUuids) UUID_VILLE_CACHE.put(mu, -1);
                CLAIM_CACHE.entrySet().removeIf(e -> e.getValue() == villeId);
                // Nettoie toutes les entrées "villeId:*" de VILLE_MONDE_COUNT
                VILLE_MONDE_COUNT.keySet().removeIf(k -> k.startsWith(villeId + ":"));
            }
            return ok ? null : "§cErreur lors de la dissolution.";
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] dissoudreVille: {}", e.getMessage());
            return "§cErreur lors de la dissolution de la ville.";
        }
    }

    // ── Invitations ───────────────────────────────────────────────────────

    public static String inviterJoueur(int villeId, String inviterUuid, String cibleUuid) {
        if (getVilleIdByUuid(cibleUuid) != -1)
            return "§cCe joueur est déjà dans une ville.";

        String sql = "INSERT INTO villes_invites (ville_id, uuid, invite_par, expire_at) "
                   + "VALUES (?,?,?, DATE_ADD(NOW(), INTERVAL 5 MINUTE)) "
                   + "ON DUPLICATE KEY UPDATE invite_par=VALUES(invite_par), "
                   + "expire_at=DATE_ADD(NOW(), INTERVAL 5 MINUTE)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId); ps.setString(2, cibleUuid); ps.setString(3, inviterUuid);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] inviterJoueur: {}", e.getMessage());
            return "§cErreur lors de l'invitation.";
        }
    }

    public static String accepterInvitation(String uuid) {
        String sql = "SELECT ville_id FROM villes_invites WHERE uuid=? AND expire_at > NOW()";
        try (Connection c = getConn()) {
            int villeId;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "§cAucune invitation valide trouvée (peut-être expirée ?).";
                    villeId = rs.getInt("ville_id");
                }
            }
            if (getVilleIdByUuid(uuid) != -1) return "§cTu es déjà dans une ville.";

            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM villes_invites WHERE uuid=?")) {
                    ps.setString(1, uuid); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_membres (ville_id, uuid, grade_ville) VALUES (?,?,'Membre')")) {
                    ps.setInt(1, villeId); ps.setString(2, uuid); ps.executeUpdate();
                }
                c.commit();
                UUID_VILLE_CACHE.put(uuid, villeId);
                return null;
            } catch (SQLIntegrityConstraintViolationException dup) {
                // UNIQUE(uuid) — deux adhésions concurrentes : la DB tranche
                c.rollback();
                return "§cTu es déjà dans une ville.";
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] accepterInvitation: {}", e.getMessage());
            return "§cErreur lors de l'acceptation.";
        }
    }

    public static void refuserInvitation(String uuid) {
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM villes_invites WHERE uuid=?")) {
            ps.setString(1, uuid); ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] refuserInvitation: {}", e.getMessage());
        }
    }

    // ── Demandes d'adhésion ───────────────────────────────────────────────

    public static String demanderRejoindre(int villeId, String uuid, String pseudo) {
        if (getVilleIdByUuid(uuid) != -1) return "§cTu es déjà dans une ville.";

        // Vérifier que le recrutement est "sur_demande"
        String checkSql = "SELECT recrutement FROM villes WHERE id=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(checkSql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "§cVille introuvable.";
                String mode = rs.getString("recrutement");
                if (RECRUTEMENT_FERME.equals(mode))      return "§cLe recrutement de cette ville est fermé.";
                if (RECRUTEMENT_OUVERT.equals(mode))     return "§eCette ville est en recrutement ouvert, utilise §a/ville rejoindre§e.";
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] demanderRejoindre check: {}", e.getMessage());
            return "§cErreur.";
        }

        String sql = "INSERT IGNORE INTO villes_demandes (ville_id, uuid, pseudo) VALUES (?,?,?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId); ps.setString(2, uuid); ps.setString(3, pseudo);
            int rows = ps.executeUpdate();
            return rows > 0 ? null : "§eTu as déjà une demande en attente pour cette ville.";
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] demanderRejoindre: {}", e.getMessage());
            return "§cErreur lors de la demande.";
        }
    }

    public static String accepterDemande(int villeId, String requestorUuid, String targetUuid) {
        String grade = getGrade(villeId, requestorUuid);
        if (!"Chef".equals(grade) && !"Adjoint".equals(grade))
            return "§cSeuls Chef et Adjoint peuvent accepter des demandes.";
        if (getVilleIdByUuid(targetUuid) != -1) return "§cCe joueur est déjà dans une ville.";

        // Récupère le pseudo du joueur accepté avant de supprimer la demande
        String targetPseudo = targetUuid;
        try (Connection c2 = getConn(); PreparedStatement ps = c2.prepareStatement(
                "SELECT pseudo FROM villes_demandes WHERE ville_id=? AND uuid=?")) {
            ps.setInt(1, villeId); ps.setString(2, targetUuid);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) targetPseudo = rs.getString("pseudo"); }
        } catch (SQLException ignored) {}

        final String pseudo = targetPseudo;
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM villes_demandes WHERE ville_id=? AND uuid=?")) {
                    ps.setInt(1, villeId); ps.setString(2, targetUuid); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_membres (ville_id, uuid, grade_ville) VALUES (?,?,'Membre')")) {
                    ps.setInt(1, villeId); ps.setString(2, targetUuid); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,'acc_dem',0)")) {
                    ps.setInt(1, villeId); ps.setString(2, targetUuid); ps.setString(3, pseudo);
                    ps.executeUpdate();
                }
                c.commit();
                UUID_VILLE_CACHE.put(targetUuid, villeId);
                return null;
            } catch (SQLIntegrityConstraintViolationException dup) {
                // UNIQUE(uuid) — le joueur vient d'être accepté ailleurs : la DB tranche
                c.rollback();
                return "§cCe joueur est déjà dans une ville.";
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] accepterDemande: {}", e.getMessage());
            return "§cErreur lors de l'acceptation.";
        }
    }

    public static void refuserDemande(int villeId, String targetUuid) {
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM villes_demandes WHERE ville_id=? AND uuid=?")) {
            ps.setInt(1, villeId); ps.setString(2, targetUuid); ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] refuserDemande: {}", e.getMessage());
        }
    }

    public static List<DemandeInfo> getDemandes(int villeId) {
        List<DemandeInfo> list = new ArrayList<>();
        String sql = "SELECT uuid, pseudo, DATE_FORMAT(timestamp,'%d/%m %H:%i') as ts "
                   + "FROM villes_demandes WHERE ville_id=? ORDER BY timestamp ASC";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new DemandeInfo(rs.getString("uuid"), rs.getString("pseudo"), rs.getString("ts")));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getDemandes: {}", e.getMessage());
        }
        return list;
    }

    // ── Banque ────────────────────────────────────────────────────────────

    /**
     * Dépose `montant` depuis le compte joueur vers la banque ville.
     * @return null si succès, message d'erreur sinon.
     */
    public static String deposerBanque(int villeId, String uuid, String pseudo, double montant) {
        if (montant <= 0) return "§cLe montant doit être positif.";

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // Débit joueur
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE joueurs SET money = money - ? WHERE uuid=? AND money >= ?")) {
                    ps.setDouble(1, montant); ps.setString(2, uuid); ps.setDouble(3, montant);
                    if (ps.executeUpdate() == 0) { c.rollback(); return "§cSolde insuffisant."; }
                }
                // Crédit banque
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE villes SET banque = banque + ? WHERE id=?")) {
                    ps.setDouble(1, montant); ps.setInt(2, villeId); ps.executeUpdate();
                }
                // Log
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,'depot',?)")) {
                    ps.setInt(1, villeId); ps.setString(2, uuid); ps.setString(3, pseudo); ps.setDouble(4, montant);
                    ps.executeUpdate();
                }
                c.commit();
                return null;
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] deposerBanque: {}", e.getMessage());
            return "§cErreur lors du dépôt.";
        }
    }

    /**
     * Retire `montant` de la banque ville vers le compte joueur (Chef/Adjoint uniquement).
     */
    public static String retirerBanque(int villeId, String uuid, String pseudo, double montant) {
        if (montant <= 0) return "§cLe montant doit être positif.";
        String grade = getGrade(villeId, uuid);
        if (!"Chef".equals(grade) && !"Adjoint".equals(grade))
            return "§cSeuls Chef et Adjoint peuvent retirer de la banque.";

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // Débit banque
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE villes SET banque = banque - ? WHERE id=? AND banque >= ?")) {
                    ps.setDouble(1, montant); ps.setInt(2, villeId); ps.setDouble(3, montant);
                    if (ps.executeUpdate() == 0) { c.rollback(); return "§cSolde de la banque insuffisant."; }
                }
                // Crédit joueur
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE joueurs SET money = money + ? WHERE uuid=?")) {
                    ps.setDouble(1, montant); ps.setString(2, uuid); ps.executeUpdate();
                }
                // Log
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,'retrait',?)")) {
                    ps.setInt(1, villeId); ps.setString(2, uuid); ps.setString(3, pseudo); ps.setDouble(4, montant);
                    ps.executeUpdate();
                }
                c.commit();
                return null;
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] retirerBanque: {}", e.getMessage());
            return "§cErreur lors du retrait.";
        }
    }

    public static double getBanque(int villeId) {
        String sql = "SELECT banque FROM villes WHERE id=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("banque");
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getBanque: {}", e.getMessage());
        }
        return 0;
    }

    public static List<LogInfo> getLogsBanque(int villeId) {
        List<LogInfo> list = new ArrayList<>();
        String sql = "SELECT pseudo, action, montant, DATE_FORMAT(timestamp,'%d/%m %H:%i') as ts "
                   + "FROM villes_banque_logs WHERE ville_id=? ORDER BY id DESC LIMIT 40";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(new LogInfo(rs.getString("pseudo"), rs.getString("action"),
                            rs.getDouble("montant"), rs.getString("ts")));
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getLogsBanque: {}", e.getMessage());
        }
        return list;
    }

    // ── Claims de chunks ──────────────────────────────────────────────────

    /**
     * Claim d'un chunk avec paiement — TOUT est dans une seule transaction :
     * insertion du claim, débit du joueur (conditionnel au solde) et mise à
     * jour de villes. Aucun état intermédiaire possible (avant : claim posé
     * puis remboursé par compensation si le solde manquait — fenêtre de
     * course exploitable et incohérence en cas de crash).
     *
     * @param payerUuid joueur débité du coût
     * @param cout      coût du claim (0 = gratuit)
     * @return null si succès, message d'erreur sinon.
     */
    public static String claimChunk(int villeId, String monde, int cx, int cz,
                                    String payerUuid, double cout) {
        String key = claimKey(monde, cx, cz);
        Integer existing = CLAIM_CACHE.get(key);
        if (existing != null) {
            if (existing == villeId) return "§eCe chunk appartient déjà à ta ville.";
            return "§cCe chunk est déjà revendiqué par une autre ville.";
        }

        String mondeLow = monde.toLowerCase();
        // O(1) via VILLE_MONDE_COUNT — remplace le scan O(n) de CLAIM_CACHE
        boolean premierClaim = !VILLE_MONDE_COUNT.containsKey(mondeCacheKey(villeId, mondeLow));

        if (!premierClaim) {
            boolean adjacent = false;
            for (int[] d : DIRS) {
                Integer voisin = CLAIM_CACHE.get(claimKey(monde, cx + d[0], cz + d[1]));
                if (voisin != null && voisin == villeId) { adjacent = true; break; }
            }
            if (!adjacent) return "§cCe chunk n'est pas adjacent à votre territoire.";
        }

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                // 1. Insertion du claim — la contrainte UNIQUE (monde, chunk_x, chunk_z)
                //    protège contre deux claims concurrents du même chunk.
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_claims (ville_id, monde, chunk_x, chunk_z) VALUES (?,?,?,?)")) {
                    ps.setInt(1, villeId); ps.setString(2, mondeLow);
                    ps.setInt(3, cx); ps.setInt(4, cz);
                    ps.executeUpdate();
                }

                // 2. Débit conditionnel : échoue si solde insuffisant
                if (cout > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE joueurs SET money = money - ? WHERE uuid = ? AND money >= ?")) {
                        ps.setDouble(1, cout); ps.setString(2, payerUuid); ps.setDouble(3, cout);
                        if (ps.executeUpdate() == 0) {
                            c.rollback();
                            return "§cSolde insuffisant — il faut §e"
                                    + com.example.rolynkmodmenu.util.Money.entier(cout) + " §cpour claimer.";
                        }
                    }
                }

                // 3. Compteur + monde de la ville
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE villes SET total_chunks = total_chunks + 1"
                        + (premierClaim ? ", monde=?" : "") + " WHERE id=?")) {
                    int idx = 1;
                    if (premierClaim) ps.setString(idx++, mondeLow);
                    ps.setInt(idx, villeId);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                return "§cCe chunk est déjà revendiqué.";
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] claimChunk: {}", e.getMessage());
            return "§cErreur lors du claim.";
        }

        // MAJ caches (après commit uniquement)
        CLAIM_CACHE.put(key, villeId);
        VILLE_MONDE_COUNT.merge(mondeCacheKey(villeId, mondeLow), 1, Integer::sum);
        return null;
    }

    public static String unclaimChunk(int villeId, String monde, int cx, int cz) {
        String key = claimKey(monde, cx, cz);
        Integer owner = CLAIM_CACHE.get(key);
        if (owner == null || owner != villeId)
            return "§cCe chunk n'appartient pas à ta ville.";

        // Suppression + décompte dans une seule transaction
        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM villes_claims WHERE ville_id=? AND monde=? AND chunk_x=? AND chunk_z=?")) {
                    ps.setInt(1, villeId); ps.setString(2, monde.toLowerCase());
                    ps.setInt(3, cx); ps.setInt(4, cz);
                    if (ps.executeUpdate() == 0) { c.rollback(); return "§cChunk introuvable en base."; }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE villes SET total_chunks = GREATEST(0, total_chunks - 1) WHERE id=?")) {
                    ps.setInt(1, villeId); ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] unclaimChunk: {}", e.getMessage());
            return "§cErreur lors du unclaim.";
        }

        CLAIM_CACHE.remove(key);
        // Décrémente le compteur ; retire l'entrée si elle tombe à 0 (plus de claims dans ce monde)
        VILLE_MONDE_COUNT.compute(mondeCacheKey(villeId, monde.toLowerCase()),
                (k, v) -> (v == null || v <= 1) ? null : v - 1);
        return null;
    }

    /**
     * Retourne tous les chunks claimés par une ville sur le serveur courant.
     * Lit uniquement depuis CLAIM_CACHE — pas de requête DB.
     * Utilisé avant dissolution pour fournir la liste à OPC.
     */
    public static List<int[]> getVilleClaims(int villeId) {
        List<int[]> result = new ArrayList<>();
        String prefix = RolynkConfig.serverName().toLowerCase() + ":";
        for (Map.Entry<String, Integer> entry : CLAIM_CACHE.entrySet()) {
            if (!entry.getValue().equals(villeId)) continue;
            String key = entry.getKey();
            if (!key.startsWith(prefix)) continue;
            String[] parts = key.split(":", 3);
            if (parts.length < 3) continue;
            try {
                result.add(new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ── Recrutement ───────────────────────────────────────────────────────

    /**
     * Définit le mode de recrutement.
     * @param mode "ouvert" | "sur_demande" | "ferme"
     */
    public static String setRecrutement(int villeId, String uuid, String pseudo, String mode) {
        if (!java.util.List.of(RECRUTEMENT_OUVERT, RECRUTEMENT_SUR_DEMANDE, RECRUTEMENT_FERME).contains(mode))
            return "§cMode invalide.";
        String grade = getGrade(villeId, uuid);
        if (!"Chef".equals(grade) && !"Adjoint".equals(grade))
            return "§cSeuls Chef et Adjoint peuvent modifier le recrutement.";

        String logAction = switch (mode) {
            case RECRUTEMENT_OUVERT      -> "rec_ouvert";
            case RECRUTEMENT_SUR_DEMANDE -> "rec_sur_demande";
            default                      -> "rec_ferme";
        };

        try (Connection c = getConn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE villes SET recrutement=? WHERE id=?")) {
                    ps.setString(1, mode); ps.setInt(2, villeId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO villes_banque_logs (ville_id, uuid, pseudo, action, montant) VALUES (?,?,?,?,0)")) {
                    ps.setInt(1, villeId); ps.setString(2, uuid);
                    ps.setString(3, pseudo); ps.setString(4, logAction);
                    ps.executeUpdate();
                }
                c.commit();
                return null;
            } catch (SQLException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] setRecrutement: {}", e.getMessage());
            return "§cErreur.";
        }
    }

    /**
     * Rejoindre directement une ville dont le recrutement est "ouvert".
     */
    public static String rejoindreVilleOuverte(int villeId, String uuid) {
        if (getVilleIdByUuid(uuid) != -1) return "§cTu es déjà dans une ville.";

        // Vérifier que le recrutement est bien "ouvert"
        String sql = "SELECT recrutement FROM villes WHERE id=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, villeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "§cVille introuvable.";
                String mode = rs.getString("recrutement");
                if (!RECRUTEMENT_OUVERT.equals(mode)) return "§cLe recrutement de cette ville n'est pas ouvert.";
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] rejoindreVilleOuverte check: {}", e.getMessage());
            return "§cErreur.";
        }

        String ins = "INSERT INTO villes_membres (ville_id, uuid, grade_ville) VALUES (?,?,'Membre')";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setInt(1, villeId); ps.setString(2, uuid);
            ps.executeUpdate();
            UUID_VILLE_CACHE.put(uuid, villeId);
            return null;
        } catch (SQLIntegrityConstraintViolationException e) {
            return "§cTu es déjà membre de cette ville.";
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] rejoindreVilleOuverte insert: {}", e.getMessage());
            return "§cErreur lors de la rejointe.";
        }
    }

    // ── Argent joueur ─────────────────────────────────────────────────────

    /** Retourne le solde du joueur, ou -1 si joueur introuvable. */
    public static double getMoney(String uuid) {
        String sql = "SELECT money FROM joueurs WHERE uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("money");
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] getMoney({}): {}", uuid, e.getMessage());
        }
        return -1;
    }

    // ── Profil joueur ─────────────────────────────────────────────────────

    /**
     * Retourne le nom de la ville du joueur, ou "" si sans ville.
     * Requête SQL en direct (pas le cache) : appelée à la connexion et lors des
     * rafraîchissements de profil, elle doit refléter l'état réel même après un
     * changement effectué sur un autre serveur du réseau.
     */
    public static String fetchVilleNomForPlayer(String uuid) {
        String sql = "SELECT v.nom FROM villes_membres vm "
                   + "JOIN villes v ON v.id = vm.ville_id WHERE vm.uuid = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nom");
            }
        } catch (SQLException e) {
            LOGGER.error("[VilleStore] fetchVilleNomForPlayer({}): {}", uuid, e.getMessage());
        }
        return "";
    }

}
