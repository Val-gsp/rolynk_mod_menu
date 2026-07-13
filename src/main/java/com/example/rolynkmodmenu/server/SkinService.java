package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Génère une texture de skin SIGNÉE à partir d'une URL d'image, via l'API
 * publique MineSkin (https://api.mineskin.org). La signature est indispensable :
 * le réseau tourne en offline-mode, donc un skin non signé ne s'afficherait pas.
 *
 * BLOQUANT (appel réseau) — toujours invoquer depuis DB_EXECUTOR, jamais le
 * main thread.
 */
public final class SkinService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ENDPOINT = "https://api.mineskin.org/generate/url";
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Extraction tolérante de value / signature dans la réponse JSON MineSkin
    // (data.texture.value / data.texture.signature) sans dépendance JSON.
    private static final Pattern VALUE_RE     = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_RE = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ERROR_RE     = Pattern.compile("\"error(?:Message)?\"\\s*:\\s*\"([^\"]+)\"");

    /** URL http(s) d'image, longueur raisonnable. */
    private static final Pattern URL_RE =
            Pattern.compile("^https?://[\\w.\\-/%~:?#\\[\\]@!$&'()*+,;=]{4,480}$");

    private SkinService() {}

    /** Texture signée prête à poser sur un GameProfile. */
    public record SignedTexture(String value, String signature) {}

    public static boolean isValidUrl(String url) {
        return url != null && URL_RE.matcher(url.trim()).matches();
    }

    /**
     * Demande à MineSkin de générer une texture signée depuis {@code imageUrl}.
     * @return la texture, ou null en cas d'échec (URL invalide, image refusée,
     *         MineSkin indisponible ou en rate-limit).
     */
    public static SignedTexture generateFromUrl(String imageUrl) {
        if (!isValidUrl(imageUrl)) return null;
        String body = "{\"variant\":\"classic\",\"visibility\":1,\"url\":\""
                + imageUrl.trim().replace("\\", "").replace("\"", "") + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "RolynkModMenu/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            String json = resp.body();
            if (resp.statusCode() / 100 != 2) {
                Matcher em = ERROR_RE.matcher(json);
                LOGGER.warn("[SkinService] MineSkin HTTP {} : {}", resp.statusCode(),
                        em.find() ? em.group(1) : json.substring(0, Math.min(160, json.length())));
                return null;
            }
            Matcher v = VALUE_RE.matcher(json);
            Matcher s = SIGNATURE_RE.matcher(json);
            if (v.find() && s.find()) {
                return new SignedTexture(v.group(1), s.group(1));
            }
            LOGGER.warn("[SkinService] Réponse MineSkin sans texture : {}",
                    json.substring(0, Math.min(160, json.length())));
        } catch (Exception e) {
            LOGGER.warn("[SkinService] Échec MineSkin pour {} : {}", imageUrl, e.toString());
        }
        return null;
    }
}
