# RolynkNetwork — Résumé complet de l'infrastructure serveur

> Réseau Minecraft **1.21.1 NeoForge** haute performance, dimensionné pour **50–100 joueurs simultanés**.
> Architecture **proxy Velocity + 4 backends NeoForge**, automatisée via systemd, Makefile, scripts Bash, et monitoring Prometheus/Grafana.
> Document généré le 2026-06-27 à partir de l'analyse du dépôt.

---

## 1. Vue d'ensemble de l'architecture

```
Internet
   │  port 25565 (SEUL port public ouvert)
   ▼
┌────────────────────────┐
│   Velocity Proxy        │  Auth Mojang + forwarding MODERN
│   (1 GB RAM, 172.20.0.10)│
└───────────┬─────────────┘
            │  réseau localhost / interne uniquement (UFW bloque l'extérieur)
   ┌────────┼──────────────┬──────────────┬──────────────┐
   ▼:25566  ▼:25567        ▼:25568        ▼:25569
┌────────┐ ┌──────────┐  ┌──────────┐  ┌────────────┐
│ Lobby  │ │ Capitale │  │  Ville   │  │ Ressource  │
│ 3 GB   │ │ 6 GB     │  │  6 GB    │  │  5 GB      │
│ Hub/   │ │ Ville    │  │  Ville   │  │  Farm /    │
│ repli  │ │ princip. │  │  2ndaire │  │  Mining    │
└────────┘ └──────────┘  └──────────┘  └────────────┘
```

**Principe de sécurité réseau** : seul Velocity expose `25565`. Les backends écoutent sur `127.0.0.1` (ports 25566–25569) et ne sont jamais joignables directement depuis Internet. Les backends sont en `online-mode=false` — c'est **Velocity** qui gère l'authentification Mojang et transmet l'identité via **forwarding MODERN** (secret partagé).

| Service    | RAM    | IP interne     | Port    | Rôle                              | Monde / type |
|------------|--------|----------------|---------|-----------------------------------|--------------|
| Velocity   | 1 GB   | 172.20.0.10    | 25565   | Proxy public                      | —            |
| Lobby      | 3 GB   | 172.20.0.11    | 25566   | Hub / repli (`try`) automatique   | `world_lobby` (flat/void, adventure) |
| Capitale   | 6 GB   | 172.20.0.12    | 25567   | Ville principale / RP             | `world_capitale` (survival) |
| Ville      | 6 GB   | 172.20.0.13    | 25568   | Ville secondaire                  | `world_ville` (survival) |
| Ressource  | 5 GB   | 172.20.0.14    | 25569   | Farm / mining (reset régulier)    | `world_ressource` (survival) |

**Total RAM** : ~21 GB + OS → **32 GB recommandé** en production.

Versions clés (`.env`) : MC `1.21.1`, NeoForge `21.1.143`, Velocity `3.3.0-SNAPSHOT`, Java **21**.

---

## 2. Fonctionnement de Velocity (le proxy)

Configuré dans [proxy/velocity.toml](proxy/velocity.toml).

- **`bind = "0.0.0.0:25565"`** — point d'entrée unique du réseau.
- **`online-mode`** : positionné à `false` dans le fichier actuel (⚠️ voir §8 — en production, le forwarding MODERN va de pair avec `online-mode=true` côté proxy pour l'auth Mojang). Les backends, eux, sont en `online-mode=false`.
- **`player-info-forwarding-mode = "MODERN"`** : transmission sécurisée de l'identité joueur (UUID, skin, IP) aux backends via un secret partagé `forwarding.secret`. Sur NeoForge cela nécessite le mod **`proxy-compatible-forge`** côté backend (présent dans chaque `mods/`).
- **`[servers]`** : déclare lobby/capitale/ville/ressource ; **`try = ["lobby"]`** → tout joueur arrive d'abord au lobby.
- **`failover-on-unexpected-server-disconnect = true`** : si un backend crash, le joueur est rebasculé automatiquement.
- **`bungee-plugin-channel-support = true`** : autorise les messages plugin cross-serveur (utilisés par le plugin RolynkTab).
- **`[query] enabled = true`** : répond aux requêtes GameSpy4 (monitoring externe).
- Compression réseau seuil 256 octets, buffers 2 MB, timeouts 5 s connect / 30 s read.

### Plugin Velocity maison : `RolynkTab` (rolynktab)

Source : [velocity-plugin/src/main/java/fr/rolynk/tabplayers/RolynkTabPlugin.java](velocity-plugin/src/main/java/fr/rolynk/tabplayers/RolynkTabPlugin.java) — déployé en `proxy/plugins/tabplayers-velocity-1.0.0.jar`.

Rôle : alimenter le mod client **`rolynk_tabplayers`** (affichage du TAB / liste joueurs) avec des infos serveur en temps réel.

- Enregistre un canal plugin `rolynk_tabplayers:server_info`.
- **Pool HikariCP** (max 5 connexions) vers la base **`luckperms`** pour lire les grades.
- **Cache des grades** rafraîchi toutes les **15 s** (évite une requête DB par connexion).
- Toutes les **5 s**, envoie à chaque joueur un paquet binaire (VarInt encodé maison) : nom du serveur courant, nombre de joueurs sur ce serveur, total réseau, ping, et la map des grades.
- Mapping des groupes LuckPerms → `staff` / `helper` / `citoyen` (défaut).
- Config DB embarquée : [velocity-plugin/src/main/resources/db-config.properties](velocity-plugin/src/main/resources/db-config.properties) (copiée au 1er démarrage dans le data dir).

---

## 3. Bases de données

Le réseau utilise **MariaDB/MySQL** avec **trois bases distinctes** :

### 3.1 `luckperms` — permissions & grades
- Utilisée par le plugin LuckPerms (gestion des rangs/permissions) et lue par le plugin Velocity `RolynkTab`.
- Utilisateur `luckperms`@`127.0.0.1`. Tables standard LuckPerms (`luckperms_players`, `luckperms_user_permissions`, …).

### 3.2 `rolynk_mc` — données de gameplay du mod maison
Schéma complet : [sql/setup_rolynk_mc.sql](sql/setup_rolynk_mc.sql). Utilisateur dédié `minecraft`@`127.0.0.1` avec droits **minimaux** (SELECT/INSERT/UPDATE/DELETE uniquement, pas de DDL). Charset `utf8mb4`.

| Table | Rôle |
|-------|------|
| `joueurs` | Profil joueur : `uuid` (PK), pseudo, grade, **money** (DECIMAL), **cristaux** (2e monnaie), status (online/offline), heures_de_jeu, première connexion |
| `grade_limits` | Nb max de balises par grade (default=2, helper=5, staff=10) |
| `balises` | Waypoints joueur (nom, monde, x/y/z) — unique (joueur, nom), FK → joueurs |
| `pending_teleports` | TP en attente (cross-serveur) |
| `villes` | Villes : nom, owner, banque, monde, total_chunks, mode de recrutement |
| `villes_membres` | Membres + `grade_ville` (Chef / Adjoint / Membre) |
| `villes_claims` | Chunks claimés (1 chunk = 1 ville max) |
| `villes_banque_logs` | Historique des 40 dernières transactions bancaires (GUI) |
| `villes_demandes` | Demandes d'adhésion (1 par joueur/ville) |
| `villes_invites` | Invitations (expirent à 5 min, nettoyées par un EVENT horaire) |
| `recompenses_quotidiennes` | Récompenses playtime du jour : PK (uuid, jour), paliers 30m/2h/4h |
| `exploration_quotidienne` | Distance parcourue/jour (20 000 blocs → 20$) |
| `votes_villes` | Votes pour le classement mensuel des villes (1 vote/joueur/jour) |

**Automatisme SQL** : `event_scheduler = ON` + EVENT `cleanup_invites` (purge horaire des invitations expirées).

**Migrations** (idempotentes) dans [sql/migrations/](sql/migrations/) :
- `001_add_indexes.sql` — index de performance (lookups par uuid, ville+date).
- `002_joueurs_profil.sql` — colonnes status / heures_de_jeu / première_connexion.
- `003_exploration.sql` — table `exploration_quotidienne`.
- `004_cristaux.sql` — colonne `cristaux` (2e monnaie).

> ⚠️ **Incohérence repérée** : le code Java `mod-src/exploration/` lit/écrit dans la table **`recompenses_quotidiennes`** avec des colonnes `blocs_parcourus` / `recompense_exploration`, alors que le schéma définit une table séparée **`exploration_quotidienne`** (colonnes `blocs_parcourus` / `recompense_recue`). À aligner (soit le code, soit la migration) pour éviter une erreur SQL au runtime.

### 3.3 `playersync` — synchronisation cross-serveur des joueurs
Gérée par le mod tiers **PlayerSync** (`playersync-2.1.5.jar`, déployé sous `/opt`, pas dans le dépôt).
- Synchronise **inventaire, armure, offhand, ender chest, XP, faim, vie, effets, advancements, score** entre **capitale, ville, ressource** — **pas la position** (mondes distincts), **pas le lobby** (volontairement exclu).
- Coordination via flags DB (`online`, `last_server`) — pas de port de messagerie (`sync_chat=false`).
- `Server_id` unique par serveur : capitale=1 (host), ville=2, ressource=3.
- Connexion JDBC commune via `config/playersync-common.toml`.

### Config DB côté mod
Chaque backend a [servers/&lt;srv&gt;/config/rolynk_db.properties](servers/lobby/config/rolynk_db.properties) : URL JDBC MariaDB vers `rolynk_mc` avec cache de prepared statements activé (`cachePrepStmts`, `prepStmtCacheSize=250`, `rewriteBatchedStatements`).

---

## 4. Mods et fonctionnalités de gameplay

### Mods déployés sur chaque backend (`servers/*/mods/`)
| Mod | Rôle |
|-----|------|
| `rolynk_mod_menu-1.0.0-all.jar` | Mod **maison** principal : profil joueur, money/cristaux, balises, villes, récompenses, exploration. Lit `rolynk_mc`. |
| `rolynk_cards-1.0.0.jar` | Mod maison (système de cartes). |
| `rolynk_tabplayers-1.0.0.jar` | Mod maison TAB (reçoit les paquets du plugin Velocity RolynkTab). |
| `open-parties-and-claims (OPC) 0.25.10` | Parties + protection de chunks (claims). Config par serveur dans [configs/opc/](configs/opc/). |
| `proxy-compatible-forge-1.2.6.jar` | **Indispensable** : rend NeoForge compatible avec le forwarding MODERN de Velocity. |
| `worldedit-mod-7.3.8.jar` | Édition de monde (build / admin). |
| PlayerSync (sous `/opt`) | Sync cross-serveur (voir §3.3). |

### Configuration gameplay : `rolynk.properties`
Par serveur ([servers/&lt;srv&gt;/config/rolynk.properties](servers/lobby/config/rolynk.properties)) :
- `server.name` : identité du serveur.
- `ville.cout.creation=500`, `ville.cout.claim=500`.
- `ville.mondes.claim` : **où le `/ville claim` est autorisé** → vide au lobby, `capitale` sur Capitale, `ville` sur Ville, vide sur Ressource.
- Récompenses playtime : 30m→20$, 2h→50$, 4h→130$ ; vote ville→20$.
- Exploration : 20 000 blocs → 20$.

### Système d'exploration (code de référence)
[mod-src/exploration/](mod-src/exploration/) — tracker serveur :
- `ExplorationTracker` : compte la distance **XZ** par tick (anti-cheat : ignore les sauts > 20 blocs/tick = TP/élytres extrêmes ; seuil min 0.05). Sync DB toutes les 5 s (100 ticks). Gère le reset au changement de jour et la déconnexion.
- `ExplorationRepository` : accès DB **async** via l'executor HikariCP du mod. `claimRecompense` fait un **UPDATE atomique** (`WHERE recompense=0`) → garantit qu'une seule instance serveur accorde la récompense, même en parallèle (anti-doublon cross-serveur).
- `RolynkConfig_snippet.java` : montre comment câbler les valeurs config.

### Particularité du Lobby
[servers/lobby/server.properties](servers/lobby/server.properties) : monde **VOID/flat**, `gamemode=adventure` + `force-gamemode=true` (les joueurs ne cassent rien), `difficulty=peaceful`, pas de mobs, PvP off. C'est un hub pur + serveur de repli.

---

## 5. Déploiement & exploitation

Deux modes de lancement coexistent :

### 5.1 systemd (mode production recommandé)
Units dans [systemd/](systemd/) : `minecraft-velocity.service` + 4 backends. Caractéristiques :
- `velocity` démarre en premier ; les backends ont `After=minecraft-velocity.service`.
- Utilisateur dédié `minecraft`, flags JVM **Aikar G1GC** adaptés à chaque taille de heap.
- Lancement backend via `run.sh` (`java @user_jvm_args.txt @libraries/.../unix_args.txt`).
- `ExecStop` propre via **RCON** (`scripts/rcon-send.sh <port> stop`) ou tmux pour Velocity.
- **Durcissement** : `NoNewPrivileges`, `PrivateTmp`, `ProtectSystem=strict`, `ReadWritePaths` limités, `LimitNOFILE=65535`.
- Anti-crash-loop : `Restart=on-failure`, max 5 redémarrages / 2 min.

### 5.2 Docker Compose (alternatif)
[docker-compose.yml](docker-compose.yml) : réseau bridge isolé `mc-net` (172.20.0.0/24), image `eclipse-temurin:21-jre-noble`, volumes bind sur `/opt/minecraft/...`, healthcheck Velocity (`nc -z 25565`), backends `depends_on: velocity healthy`. Seul Velocity publie `25565`.

### 5.3 Makefile — orchestration centrale
[Makefile](Makefile) (~50 cibles). Principales familles :
- **Cycle de vie** : `install`, `start`, `stop`, `restart`, `restart-force`, `status`, `logs[-velocity/-lobby/...]`.
- **Déploiement** : `deploy-config`, `deploy-rolynk-config`, `deploy-mods`, `deploy-systemd`, `deploy-luckperms`, `deploy-velocity-plugin`, `build-plugin`.
- **Backups** : `backup[-lobby/...]`, `restore`, `db-backup`, `db-backup-rolynk`, `clean-old-backups`.
- **Base de données** : `db-setup-rolynk`, `db-migrate`, `db-players`, `db-status`, `db-villes`, `db-sync-players`, `votes-mois`.
- **Économie/grades** : `money-get/set/add/remove`, `grade-set/remove`, `op`/`deop`, `profile`, `joueurs-online`.
- **Maintenance/infra** : `update[-velocity/-neoforge]`, `pregen`, `monitoring[-stop]`, `maintenance[-off]`, `kernel-tune`, `redis-install/status/restart`, `check-security`.

### 5.4 Scripts Bash
[scripts/](scripts/) : `install.sh`, `start/stop/restart.sh`, `status.sh`, `logs.sh`, `backup.sh`, `restore.sh`, `update.sh`, `rcon-send.sh`, `setup-kernel.sh`, `setup-redis.sh`.

---

## 6. Composants annexes

### Monitoring — [monitoring/](monitoring/)
- `prometheus.yml` : scrape (15 s) du host (`node_exporter:9100`), Velocity (`:9225`), et chaque backend NeoForge (`:19565`–`:19568`).
- `docker-compose.monitoring.yml` + Grafana (dashboards dans `monitoring/grafana/dashboards`). Ports `9090` (Prometheus) / `3000` (Grafana).

### Redis — [redis/redis.conf](redis/redis.conf)
- `bind 127.0.0.1`, `protected-mode yes`, persistance désactivée (`save ""`, `appendonly no`), `maxmemory 256mb` LRU.
- Durcissement : commandes dangereuses renommées/désactivées (`FLUSHALL`, `FLUSHDB`, `DEBUG`, `KEYS`).
- (Cache/coordination cross-serveur potentiel — installé via `make redis-install`.)

### Tuning kernel — [kernel/](kernel/)
- `sysctl.conf` + `limits.conf` : optimisations réseau et limites de fichiers/process pour soutenir 100 joueurs (`make kernel-tune`).

### Open Parties & Claims — [configs/opc/](configs/opc/)
- Un TOML par serveur. Parties activées (max 64 membres / 64 alliés). Système de permission `prometheus`, partis via `argonauts_guilds`. Claims configurables par serveur (désactivés sur certains).

### Sécurité (`.env`)
Secrets centralisés : RCON password, `DB_PASSWORD` (luckperms), `ROLYNK_DB_PASSWORD` (rolynk_mc), ports RCON 25575–25578. ⚠️ Le `.env` et les `*.secret` / `rolynk_db.properties` contiennent des **mots de passe en clair** — à ne jamais committer/distribuer.

---

## 7. Flux d'une connexion joueur (résumé end-to-end)

1. Le client se connecte sur `25565` → **Velocity** authentifie via Mojang.
2. Velocity applique `try = ["lobby"]` → le joueur arrive au **Lobby** (forwarding MODERN, identité transmise).
3. Le mod `proxy-compatible-forge` côté backend valide le forwarding ; le mod `rolynk_mod_menu` charge le profil depuis `rolynk_mc` (money, cristaux, balises, ville…).
4. Le joueur navigue vers Capitale/Ville/Ressource ; **PlayerSync** synchronise inventaire & données via la base `playersync`.
5. En continu : `RolynkTab` (plugin Velocity) pousse toutes les 5 s les infos TAB (compteurs, ping, grades depuis `luckperms`) au mod client `rolynk_tabplayers`.
6. Le mod serveur suit playtime/exploration et crédite money via la base `rolynk_mc` (UPDATE atomiques anti-doublon).

---

## 8. Points d'attention / pistes

- **Bug schéma exploration** (§3.3) : table `recompenses_quotidiennes` vs `exploration_quotidienne` à réconcilier avant prod.
- **Secrets en clair** dans `.env`, `db-config.properties`, `rolynk_db.properties` → vérifier qu'ils sont bien dans `.gitignore` ; rotation conseillée (le mot de passe `minecraft` `Xk9#mP2@vLqR7!nZ` est visible dans le SQL versionné).
- **Caractères `#` dans les mots de passe** ont déjà cassé le parsing JDBC/TOML pour PlayerSync → privilégier des mots de passe alphanumériques.
- `velocity.toml` met `online-mode = false` dans le commentaire de section mais le texte indique que Velocity gère l'auth — à vérifier (le forwarding MODERN suppose online-mode proxy à true côté production).
- Le dépôt n'est **pas un dépôt git** (`git init` absent) — pas d'historique de versions.
