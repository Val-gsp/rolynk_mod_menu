-- ============================================================
--  Rolynk Mod Menu — Schéma MySQL / MariaDB
--  À importer UNE FOIS sur ton serveur MySQL :
--    mysql -u root -p < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS rolynk_mc
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rolynk_mc;

-- ── Joueurs ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS joueurs (
    uuid               VARCHAR(36)    NOT NULL PRIMARY KEY,
    pseudo             VARCHAR(32)    NOT NULL,
    grade              VARCHAR(32)    NOT NULL DEFAULT 'default',
    money              DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    heures_de_jeu      DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    status             VARCHAR(10)    NOT NULL DEFAULT 'offline',
    premiere_connexion DATETIME       DEFAULT NULL
);

-- ── Grades & limites ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS grade_limits (
    grade        VARCHAR(32) NOT NULL PRIMARY KEY,
    max_balises  INT         NOT NULL DEFAULT 2,
    max_chunks   INT         NOT NULL DEFAULT 0
);

-- Valeurs par défaut des grades
INSERT IGNORE INTO grade_limits (grade, max_balises, max_chunks) VALUES
    ('default', 2,  0),
    ('vip',     5,  10),
    ('vip+',    8,  20),
    ('helper',  10, 30),
    ('staff',   15, 50),
    ('admin',   99, 999);

-- ── Balises de téléportation ───────────────────────────────
CREATE TABLE IF NOT EXISTS balises (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    joueur_uuid VARCHAR(36)  NOT NULL,
    nom         VARCHAR(32)  NOT NULL,
    monde       VARCHAR(64)  NOT NULL,
    x           INT          NOT NULL,
    y           INT          NOT NULL,
    z           INT          NOT NULL,
    UNIQUE KEY uq_balise (joueur_uuid, nom),
    INDEX idx_joueur (joueur_uuid)
);

-- ── Téléportations inter-serveurs en attente ───────────────
CREATE TABLE IF NOT EXISTS pending_teleports (
    uuid  VARCHAR(36) NOT NULL PRIMARY KEY,
    x     INT         NOT NULL,
    y     INT         NOT NULL,
    z     INT         NOT NULL
);

-- ── Villes ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS villes (
    id                  INT           AUTO_INCREMENT PRIMARY KEY,
    nom                 VARCHAR(64)   NOT NULL UNIQUE,
    owner_uuid          VARCHAR(36)   NOT NULL,
    banque              DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    date_creation       DATETIME      DEFAULT NOW(),
    monde               VARCHAR(64)   DEFAULT NULL,
    total_chunks        INT           NOT NULL DEFAULT 0,
    recrutement_ouvert  TINYINT(1)    NOT NULL DEFAULT 1,
    recrutement         VARCHAR(20)   NOT NULL DEFAULT 'ouvert',
    INDEX idx_owner (owner_uuid)
);

-- ── Membres des villes ────────────────────────────────────
CREATE TABLE IF NOT EXISTS villes_membres (
    id          INT         AUTO_INCREMENT PRIMARY KEY,
    ville_id    INT         NOT NULL,
    uuid        VARCHAR(36) NOT NULL,
    grade_ville VARCHAR(20) NOT NULL DEFAULT 'Membre',
    UNIQUE KEY uq_ville_membre (ville_id, uuid),
    INDEX idx_uuid (uuid),
    FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ── Chunks claimés par les villes ────────────────────────
CREATE TABLE IF NOT EXISTS villes_claims (
    id       INT         AUTO_INCREMENT PRIMARY KEY,
    ville_id INT         NOT NULL,
    monde    VARCHAR(64) NOT NULL,
    chunk_x  INT         NOT NULL,
    chunk_z  INT         NOT NULL,
    UNIQUE KEY uq_claim (monde, chunk_x, chunk_z),
    FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ── Logs de banque des villes ─────────────────────────────
CREATE TABLE IF NOT EXISTS villes_banque_logs (
    id        INT           AUTO_INCREMENT PRIMARY KEY,
    ville_id  INT           NOT NULL,
    uuid      VARCHAR(36)   NOT NULL,
    pseudo    VARCHAR(32)   NOT NULL,
    action    ENUM('depot','retrait') NOT NULL,
    montant   DECIMAL(20,2) NOT NULL,
    timestamp DATETIME      DEFAULT NOW(),
    INDEX idx_ville (ville_id),
    FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ── Demandes d'adhésion aux villes ────────────────────────
CREATE TABLE IF NOT EXISTS villes_demandes (
    id        INT         AUTO_INCREMENT PRIMARY KEY,
    ville_id  INT         NOT NULL,
    uuid      VARCHAR(36) NOT NULL,
    pseudo    VARCHAR(32) NOT NULL,
    timestamp DATETIME    DEFAULT NOW(),
    UNIQUE KEY uq_demande (ville_id, uuid),
    FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ── Invitations en attente ────────────────────────────────
CREATE TABLE IF NOT EXISTS villes_invites (
    id         INT         AUTO_INCREMENT PRIMARY KEY,
    ville_id   INT         NOT NULL,
    uuid       VARCHAR(36) NOT NULL,
    invite_par VARCHAR(36) NOT NULL,
    expire_at  DATETIME    NOT NULL,
    UNIQUE KEY uq_invite (ville_id, uuid),
    FOREIGN KEY (ville_id) REFERENCES villes(id) ON DELETE CASCADE
);

-- ============================================================
--  Exemple : créer un compte MySQL dédié (optionnel)
--  À exécuter avec un compte admin MySQL :
--
--  CREATE USER IF NOT EXISTS 'minecraft'@'localhost' IDENTIFIED BY 'TON_MOT_DE_PASSE';
--  GRANT ALL PRIVILEGES ON rolynk_mc.* TO 'minecraft'@'localhost';
--  FLUSH PRIVILEGES;
-- ============================================================
