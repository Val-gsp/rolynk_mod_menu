package com.example.rolynkmodmenu.server;

import com.example.rolynkmodmenu.network.BoutiqueCatalogPayload;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Catalogue de rachat de la Boutique Jeux, chargé depuis
 * config/rolynk_boutique.properties. Si le fichier est absent, un template
 * avec le catalogue par défaut est créé.
 *
 * Format d'une offre : {@code vente.N=item_id;quantite;prix}
 * (ex. {@code vente.1=minecraft:diamond;30;15} → le serveur rachète
 * 30 diamants pour 15 money). Les N sont lus dans l'ordre croissant à
 * partir de 1, sans trou.
 *
 * PHILOSOPHIE DES PRIX : volontairement bas — la boutique est un plancher
 * de liquidité, pas une source de revenus. Les joueurs sont incités à se
 * vendre les objets entre eux à de meilleurs prix. Il n'y a AUCUN achat
 * possible auprès du serveur.
 *
 * Chargement paresseux et thread-safe : utilisable depuis les handlers
 * async sans dépendre de l'ordre d'init du serveur.
 */
public final class BoutiqueConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_FILE = Path.of("config", "rolynk_boutique.properties");

    /** Catalogue par défaut, écrit dans le template à la première exécution. */
    private static final String[] DEFAULT_OFFRES = {
            "minecraft:diamond;30;15",
            "minecraft:emerald;16;8",
            "minecraft:gold_ingot;64;12",
            "minecraft:iron_ingot;64;10",
            "minecraft:copper_ingot;64;4",
            "minecraft:coal;64;6",
            "minecraft:redstone;64;6",
            "minecraft:lapis_lazuli;64;6",
            "minecraft:cobblestone;64;1",
            "minecraft:oak_log;64;3",
            "minecraft:wheat;64;3",
            "minecraft:cooked_beef;64;5",
    };

    private static volatile List<BoutiqueCatalogPayload.Offre> catalogue = null;

    private BoutiqueConfig() {}

    /** Le catalogue de rachat (immuable). Charge le fichier au premier appel. */
    public static List<BoutiqueCatalogPayload.Offre> catalogue() {
        List<BoutiqueCatalogPayload.Offre> local = catalogue;
        if (local == null) {
            synchronized (BoutiqueConfig.class) {
                local = catalogue;
                if (local == null) {
                    local = load();
                    catalogue = local;
                }
            }
        }
        return local;
    }

    /** L'offre {@code id}, ou null si l'index est hors catalogue. */
    public static BoutiqueCatalogPayload.Offre offre(int id) {
        List<BoutiqueCatalogPayload.Offre> cat = catalogue();
        return (id >= 0 && id < cat.size()) ? cat.get(id) : null;
    }

    // ── Chargement ────────────────────────────────────────────────────────

    private static List<BoutiqueCatalogPayload.Offre> load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                props.load(r);
            } catch (IOException e) {
                LOGGER.error("[Boutique] Lecture de {} impossible — catalogue par défaut", CONFIG_FILE, e);
                return parse(List.of(DEFAULT_OFFRES));
            }
        } else {
            writeTemplate();
            return parse(List.of(DEFAULT_OFFRES));
        }

        List<String> lignes = new ArrayList<>();
        for (int i = 1; ; i++) {
            String v = props.getProperty("vente." + i);
            if (v == null) break;
            lignes.add(v.trim());
        }
        if (lignes.isEmpty()) {
            LOGGER.warn("[Boutique] Aucune offre dans {} — catalogue par défaut", CONFIG_FILE);
            return parse(List.of(DEFAULT_OFFRES));
        }
        return parse(lignes);
    }

    private static List<BoutiqueCatalogPayload.Offre> parse(List<String> lignes) {
        List<BoutiqueCatalogPayload.Offre> out = new ArrayList<>();
        for (String ligne : lignes) {
            String[] parts = ligne.split(";");
            if (parts.length != 3) {
                LOGGER.warn("[Boutique] Offre ignorée (format attendu item;quantite;prix) : {}", ligne);
                continue;
            }
            try {
                String itemId = parts[0].trim().toLowerCase(Locale.ROOT);
                int quantite  = Integer.parseInt(parts[1].trim());
                double prix   = Double.parseDouble(parts[2].trim());
                if (itemId.isEmpty() || quantite <= 0 || quantite > 3456 || prix <= 0 || prix > 1_000_000) {
                    LOGGER.warn("[Boutique] Offre ignorée (valeurs hors bornes) : {}", ligne);
                    continue;
                }
                out.add(new BoutiqueCatalogPayload.Offre(itemId, quantite, prix));
            } catch (NumberFormatException e) {
                LOGGER.warn("[Boutique] Offre ignorée (nombre invalide) : {}", ligne);
            }
        }
        LOGGER.info("[Boutique] Catalogue chargé : {} offre(s)", out.size());
        return List.copyOf(out);
    }

    private static void writeTemplate() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                w.write("# Boutique Jeux — catalogue de RACHAT (le serveur ne vend rien)\n");
                w.write("# Format : vente.N=item_id;quantite;prix_money  (N croissant depuis 1, sans trou)\n");
                w.write("# Prix volontairement bas : encourager la vente entre joueurs.\n");
                w.write("# Redémarrer le serveur après modification.\n\n");
                int i = 1;
                for (String o : DEFAULT_OFFRES) {
                    w.write("vente." + i++ + "=" + o + "\n");
                }
            }
            LOGGER.info("[Boutique] Template créé : {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("[Boutique] Impossible de créer {}", CONFIG_FILE, e);
        }
    }
}
