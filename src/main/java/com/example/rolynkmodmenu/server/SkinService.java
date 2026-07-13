package com.example.rolynkmodmenu.server;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Génère une texture de skin SIGNÉE à partir d'une URL d'image.
 *
 * Stratégie robuste (fonctionne avec Discord, imgur, etc.) :
 *   1. le SERVEUR télécharge l'image lui-même (User-Agent navigateur, suit les
 *      redirections) — ne dépend pas de la capacité de MineSkin à joindre l'hôte ;
 *   2. on valide que c'est bien un skin (PNG 64×64 ou 64×32) avant de consommer
 *      une génération MineSkin ;
 *   3. on envoie les octets à MineSkin en multipart (/generate/upload) qui
 *      renvoie la texture signée (indispensable en offline-mode).
 *
 * BLOQUANT (réseau) — toujours invoquer depuis DB_EXECUTOR, jamais le main thread.
 */
public final class SkinService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String UPLOAD_ENDPOINT = "https://api.mineskin.org/generate/upload";
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_IMAGE_BYTES = 512 * 1024; // 512 Kio : large pour un skin

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern VALUE_RE     = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_RE = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    /** URL http(s), longueur raisonnable — accepte les liens signés (Discord…). */
    private static final Pattern URL_RE =
            Pattern.compile("^https?://[\\w.\\-/%~:?#\\[\\]@!$&'()*+,;=]{4,480}$");

    private SkinService() {}

    public record SignedTexture(String value, String signature) {}

    /** Codes d'échec pour un message précis côté joueur. */
    public enum Erreur { URL, TELECHARGEMENT, FORMAT, MINESKIN }

    public static boolean isValidUrl(String url) {
        return url != null && URL_RE.matcher(url.trim()).matches();
    }

    /** Résultat : soit une texture signée, soit une cause d'échec. */
    public record Resultat(SignedTexture texture, Erreur erreur) {
        static Resultat ok(SignedTexture t) { return new Resultat(t, null); }
        static Resultat ko(Erreur e)        { return new Resultat(null, e); }
        public boolean isOk() { return texture != null; }
    }

    public static Resultat generateFromUrl(String imageUrl) {
        if (!isValidUrl(imageUrl)) return Resultat.ko(Erreur.URL);

        byte[] png = download(imageUrl.trim());
        if (png == null) return Resultat.ko(Erreur.TELECHARGEMENT);

        if (!estSkinValide(png)) return Resultat.ko(Erreur.FORMAT);

        SignedTexture tex = uploadToMineSkin(png);
        return tex != null ? Resultat.ok(tex) : Resultat.ko(Erreur.MINESKIN);
    }

    // ── 1. Téléchargement de l'image ──────────────────────────────────────

    private static byte[] download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (RolynkModMenu)")
                    .header("Accept", "image/png,image/*")
                    .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                LOGGER.warn("[SkinService] Téléchargement HTTP {} pour {}", resp.statusCode(), url);
                return null;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
                LOGGER.warn("[SkinService] Image absente ou trop lourde ({} o)",
                        body == null ? 0 : body.length);
                return null;
            }
            return body;
        } catch (Exception e) {
            LOGGER.warn("[SkinService] Échec téléchargement {} : {}", url, e.toString());
            return null;
        }
    }

    // ── 2. Validation format skin (PNG 64×64 ou 64×32) ────────────────────

    private static boolean estSkinValide(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return false;
            int w = img.getWidth(), h = img.getHeight();
            return w == 64 && (h == 64 || h == 32);
        } catch (Exception e) {
            return false;
        }
    }

    // ── 3. Envoi multipart à MineSkin ─────────────────────────────────────

    private static SignedTexture uploadToMineSkin(byte[] png) {
        String boundary = "----RolynkSkin" + Long.toHexString(System.nanoTime());
        try {
            byte[] body = multipart(boundary, png);
            HttpRequest req = HttpRequest.newBuilder(URI.create(UPLOAD_ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "RolynkModMenu/1.0")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            String json = resp.body();
            if (resp.statusCode() / 100 != 2) {
                LOGGER.warn("[SkinService] MineSkin HTTP {} : {}", resp.statusCode(),
                        json.substring(0, Math.min(160, json.length())));
                return null;
            }
            Matcher v = VALUE_RE.matcher(json);
            Matcher s = SIGNATURE_RE.matcher(json);
            if (v.find() && s.find()) return new SignedTexture(v.group(1), s.group(1));
            LOGGER.warn("[SkinService] Réponse MineSkin sans texture : {}",
                    json.substring(0, Math.min(160, json.length())));
        } catch (Exception e) {
            LOGGER.warn("[SkinService] Échec upload MineSkin : {}", e.toString());
        }
        return null;
    }

    /** Construit un corps multipart/form-data : champs variant/visibility + fichier PNG. */
    private static byte[] multipart(String boundary, byte[] png) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dd = "--";
        String crlf = "\r\n";
        StringBuilder head = new StringBuilder();
        head.append(dd).append(boundary).append(crlf)
            .append("Content-Disposition: form-data; name=\"variant\"").append(crlf).append(crlf)
            .append("classic").append(crlf)
            .append(dd).append(boundary).append(crlf)
            .append("Content-Disposition: form-data; name=\"visibility\"").append(crlf).append(crlf)
            .append("1").append(crlf)
            .append(dd).append(boundary).append(crlf)
            .append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"").append(crlf)
            .append("Content-Type: image/png").append(crlf).append(crlf);
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(png);
        out.write((crlf + dd + boundary + dd + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
