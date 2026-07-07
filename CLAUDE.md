# Rolynk Mod Menu — NeoForge 1.21.1

## Informations du projet
- **Minecraft** : 1.21.1
- **NeoForge** : 21.1.143
- **NeoGradle plugin** : 7.1.36
- **Java** : 21 (Eclipse Temurin 21.0.10)
- **Gradle** : 8.11.1 (via wrapper)
- **Mod ID** : `rolynk_mod_menu`
- **Package** : `com.example.rolynkmodmenu`

## Commandes principales (terminal VSCode)

```bash
# Lancer le client Minecraft (avec le mod)
./gradlew runClient

# Lancer un serveur de test
./gradlew runServer

# Compiler et créer le .jar du mod (lance aussi les tests)
./gradlew build
# → Jar à DÉPLOYER : build/libs/rolynk_mod_menu-1.0.0-all.jar
#   (contient HikariCP + mysql-connector embarqués via jarJar ;
#    le jar sans suffixe ne contient pas les dépendances)

# Lancer uniquement les tests unitaires
./gradlew test

# Nettoyer le projet
./gradlew clean
```

## Structure du projet
```
src/main/java/com/example/rolynkmodmenu/
    RolynkModMenu.java      ← classe principale (@Mod) — enregistrement payloads uniquement
    util/                   ← code commun client+serveur (Money...)
    network/                ← payloads réseau (records + StreamCodec)
    server/                 ← stores SQL, handlers C2S, commandes, config
        Database.java       ← pool HikariCP UNIQUE + executor (init au démarrage serveur)
        RolynkConfig.java   ← config gameplay (config/rolynk.properties)
        BaliseDbConfig.java ← identifiants MySQL (config/rolynk_db.properties)
    client/                 ← écrans, data managers, keybinds
        network/ClientPayloadHandlers.java ← handlers S2C (sided-safe)

src/main/resources/
    META-INF/neoforge.mods.toml  ← métadonnées du mod
    pack.mcmeta                  ← pack de ressources

src/test/java/                   ← tests unitaires (./gradlew test)
docs/schema.sql                  ← schéma MySQL de référence + migrations
```

## Règles du projet (à respecter dans tout nouveau code)
- AUCUNE requête SQL sur le main thread serveur : toujours via
  `CompletableFuture.runAsync(..., Database.EXECUTOR)` (alias `BaliseStore.DB_EXECUTOR`).
- Les handlers S2C vont dans `client/network/ClientPayloadHandlers` — jamais de
  classe client importée par une classe chargée côté serveur dédié.
- Toute donnée venant d'un payload C2S passe par `InputValidator` AVANT usage.
- Symbole monétaire : `Money.SYMBOL` — jamais le caractère `§` (code couleur MC).
- Les écritures multi-tables se font dans UNE transaction (modèle : VilleStore).

## Ajouter un Item

```java
// Dans un fichier ModItems.java
public class ModItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(RolynkModMenu.MOD_ID);

    public static final DeferredHolder<Item, Item> MY_ITEM =
        ITEMS.registerSimpleItem("my_item", new Item.Properties());
}

// Dans RolynkModMenu.java, dans le constructeur :
// ModItems.ITEMS.register(modEventBus);
```

## Notes importantes
- Lancer `./gradlew runClient` la première fois va télécharger
  les assets Minecraft (~1 Go) — c'est normal.
- Les logs de la console Gradle s'affichent dans le terminal.
- Pour relancer le build après un changement : rechargez Gradle
  dans VSCode (Ctrl+Shift+P → "Java: Reload Projects").
