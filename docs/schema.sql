-- ============================================================================
-- Rolynk Mod Menu — Schéma MySQL/MariaDB de référence
-- Base : rolynk_mc (utf8mb4)
--
-- Section 1 : installation neuve (CREATE TABLE IF NOT EXISTS — idempotent)
-- Section 2 : migrations pour une base existante (à exécuter UNE fois)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS rolynk_mc
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rolynk_mc;

-- ────────────────────────────────────────────────────────────────────────────
-- SECTION 1 — INSTALLATION NEUVE
-- ────────────────────────────────────────────────────────────────────────────

-- Joueurs (profil, économie, grade serveur)
CREATE TABLE IF NOT EXISTS joueurs (
  uuid               VARCHAR(36)  NOT NULL PRIMARY KEY,
  pseudo             VARCHAR(32)  NOT NULL,
  grade              VARCHAR(32)  NOT NULL DEFAULT 'default',
  money              DECIMAL(20,2) NOT NULL DEFAULT 0,
  cristaux           BIGINT       NOT NULL DEFAULT 0,
  status             VARCHAR(16)  NOT NULL DEFAULT 'offline',
  heures_de_jeu      DECIMAL(12,2) NOT NULL DEFAULT 0,
  premiere_connexion DATETIME     DEFAULT NULL
);

-- Limites de balises par grade serveur
CREATE TABLE IF NOT EXISTS grade_limits (
  grade       VARCHAR(32) NOT NULL PRIMARY KEY,
  max_balises INT         NOT NULL DEFAULT 2
);
INSERT IGNORE INTO grade_limits (grade, max_balises) VALUES ('default', 2);

-- Balises (waypoints personnels, cross-serveur)
CREATE TABLE IF NOT EXISTS balises (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  joueur_uuid VARCHAR(36) NOT NULL,
  nom         VARCHAR(32) NOT NULL,
  monde       VARCHAR(64) NOT NULL,
  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
  UNIQUE KEY uq_balise (joueur_uuid, nom),
  KEY idx_balise_joueur (joueur_uuid)
);

-- Téléportations en attente (changement de serveur Velocity)
CREATE TABLE IF NOT EXISTS pending_teleports (
  uuid VARCHAR(36) NOT NULL PRIMARY KEY,
  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL
);

-- Villes
CREATE TABLE IF NOT EXISTS villes (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  nom           VARCHAR(64) NOT NULL UNIQUE,
  nom_lower     VARCHAR(64) AS (LOWER(nom)) STORED,
  owner_uuid    VARCHAR(36) NOT NULL,
  banque        DECIMAL(20,2) NOT NULL DEFAULT 0,
  date_creation DATETIME DEFAULT NOW(),
  monde         VARCHAR(64) DEFAULT NULL,
  total_chunks  INT NOT NULL DEFAULT 0,
  recrutement   VARCHAR(16) NOT NULL DEFAULT 'sur_demande',
  KEY idx_villes_nom_lower (nom_lower)
);

-- Membres : UNIQUE(uuid) garantit qu'un joueur ne peut être que dans UNE ville,
-- même en cas d'adhésions strictement simultanées (la DB tranche).
CREATE TABLE IF NOT EXISTS villes_membres (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  ville_id    INT NOT NULL,
  uuid        VARCHAR(36) NOT NULL,
  grade_ville VARCHAR(20) NOT NULL DEFAULT 'Membre',
  UNIQUE KEY uq_ville_membre (ville_id, uuid),
  UNIQUE KEY uq_membre_uuid (uuid),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- Claims de chunks : UNIQUE(monde, chunk_x, chunk_z) empêche le double claim.
CREATE TABLE IF NOT EXISTS villes_claims (
  id       INT AUTO_INCREMENT PRIMARY KEY,
  ville_id INT NOT NULL,
  monde    VARCHAR(64) NOT NULL,
  chunk_x  INT NOT NULL,
  chunk_z  INT NOT NULL,
  UNIQUE KEY uq_claim (monde, chunk_x, chunk_z),
  KEY idx_claims_ville (ville_id),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- Journal de la banque (et des événements de ville : grades, recrutement...)
CREATE TABLE IF NOT EXISTS villes_banque_logs (
  id        INT AUTO_INCREMENT PRIMARY KEY,
  ville_id  INT NOT NULL,
  uuid      VARCHAR(36) NOT NULL,
  pseudo    VARCHAR(32) NOT NULL,
  action    VARCHAR(32) NOT NULL,   -- depot, retrait, grade_*, rec_*, acc_dem
  montant   DECIMAL(20,2) NOT NULL,
  timestamp DATETIME DEFAULT NOW(),
  KEY idx_logs_ville (ville_id, id),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- Demandes d'adhésion (recrutement "sur_demande")
CREATE TABLE IF NOT EXISTS villes_demandes (
  id        INT AUTO_INCREMENT PRIMARY KEY,
  ville_id  INT NOT NULL,
  uuid      VARCHAR(36) NOT NULL,
  pseudo    VARCHAR(32) NOT NULL,
  timestamp DATETIME DEFAULT NOW(),
  UNIQUE KEY uq_demande (ville_id, uuid),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- Invitations (expirent après 5 minutes)
CREATE TABLE IF NOT EXISTS villes_invites (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  ville_id   INT NOT NULL,
  uuid       VARCHAR(36) NOT NULL,
  invite_par VARCHAR(36) NOT NULL,
  expire_at  DATETIME NOT NULL,
  UNIQUE KEY uq_invite (ville_id, uuid),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- Exploration quotidienne (blocs parcourus sur l'axe XZ).
-- Clé (uuid, jour) : même pattern que recompenses_quotidiennes.
-- Purge automatique des lignes > 2 jours au démarrage.
CREATE TABLE IF NOT EXISTS exploration_quotidienne (
  uuid             VARCHAR(36)  NOT NULL,
  jour             DATE         NOT NULL,
  blocs_parcourus  INT          NOT NULL DEFAULT 0,
  recompense_recue TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, jour)
);

-- Récompenses quotidiennes (temps de jeu).
-- Clé (uuid, jour) : le reset à minuit est structurel — nouveau jour = nouvelle
-- ligne vierge, aucun cron nécessaire. Les serveurs du réseau cumulent tous
-- dans la même ligne. Purge automatique des lignes > 2 jours au démarrage.
CREATE TABLE IF NOT EXISTS recompenses_quotidiennes (
  uuid               VARCHAR(36) NOT NULL,
  jour               DATE NOT NULL,
  temps_jeu_secondes INT NOT NULL DEFAULT 0,
  palier_30m         TINYINT(1) NOT NULL DEFAULT 0,
  palier_2h          TINYINT(1) NOT NULL DEFAULT 0,
  palier_4h          TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, jour)
);

-- Votes de villes quotidiens.
-- Clé (uuid, jour) : un seul vote par joueur par jour.
-- Le joueur ne peut pas voter pour sa propre ville (vérifié en DB dans la transaction).
-- Purge automatique des entrées > 40 jours au démarrage (garde un mois complet + marge).
--
-- Classement mensuel (à exécuter en fin de mois pour le Discord) :
--   SELECT v.nom, COUNT(*) AS votes
--   FROM votes_villes vv
--   JOIN villes v ON v.id = vv.ville_id
--   WHERE vv.jour >= DATE_FORMAT(NOW(), '%Y-%m-01')
--   GROUP BY vv.ville_id, v.nom
--   ORDER BY votes DESC;
CREATE TABLE IF NOT EXISTS votes_villes (
  uuid     VARCHAR(36) NOT NULL,
  ville_id INT         NOT NULL,
  jour     DATE        NOT NULL,
  PRIMARY KEY (uuid, jour),
  KEY idx_votes_ville (ville_id, jour),
  FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ────────────────────────────────────────────────────────────────────────────
-- SECTION 2 — MIGRATIONS POUR UNE BASE EXISTANTE
-- À exécuter UNE seule fois sur une base créée avec l'ancien schéma.
-- Chaque bloc est indépendant ; ignorez ceux déjà appliqués.
-- ────────────────────────────────────────────────────────────────────────────

-- 2.1  Colonne recrutement en VARCHAR (remplace recrutement_ouvert TINYINT)
-- ALTER TABLE villes ADD COLUMN recrutement VARCHAR(16) NOT NULL DEFAULT 'sur_demande';
-- UPDATE villes SET recrutement = IF(recrutement_ouvert = 1, 'ouvert', 'sur_demande');
-- ALTER TABLE villes DROP COLUMN recrutement_ouvert;

-- 2.2  Colonne générée nom_lower + index (recherche de ville insensible à la casse)
-- ALTER TABLE villes ADD COLUMN nom_lower VARCHAR(64) AS (LOWER(nom)) STORED;
-- ALTER TABLE villes ADD INDEX idx_villes_nom_lower (nom_lower);

-- 2.3  Journal : actions au-delà de depot/retrait
-- ALTER TABLE villes_banque_logs MODIFY COLUMN action VARCHAR(32) NOT NULL;

-- 2.4  Un joueur = une seule ville (ESSENTIEL — ferme une race condition).
--      Vérifier d'abord les doublons éventuels :
--        SELECT uuid, COUNT(*) c FROM villes_membres GROUP BY uuid HAVING c > 1;
--      puis supprimer les doublons et :
-- ALTER TABLE villes_membres ADD UNIQUE KEY uq_membre_uuid (uuid);

-- 2.5  Index de confort (logs paginés, claims par ville)
-- ALTER TABLE villes_banque_logs ADD INDEX idx_logs_ville (ville_id, id);
-- ALTER TABLE villes_claims ADD INDEX idx_claims_ville (ville_id);

-- 2.6  Récompenses quotidiennes : nouvelle table — le CREATE TABLE IF NOT EXISTS
--      de la section 1 suffit (idempotent), aucune migration de données.

-- 2.7  Votes de villes : nouvelle table — le CREATE TABLE IF NOT EXISTS
--      de la section 1 suffit (idempotent), aucune migration de données.

-- 2.8  Exploration quotidienne : nouvelle table — le CREATE TABLE IF NOT EXISTS
--      de la section 1 suffit (idempotent), aucune migration de données.

-- 2.9  Cristaux : nouvelle monnaie/ressource affichée dans le profil.
--      À exécuter UNE fois sur une base existante :
-- ALTER TABLE joueurs ADD COLUMN cristaux BIGINT NOT NULL DEFAULT 0 AFTER money;
