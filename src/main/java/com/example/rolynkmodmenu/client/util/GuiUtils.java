package com.example.rolynkmodmenu.client.util;

import com.example.rolynkmodmenu.client.screen.BaseMenuScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Utilitaires de rendu GUI partagés entre tous les screens du mod.
 */
public class GuiUtils {

    // ── Panneau "module en construction" ─────────────────────────────────────

    /** Pictogramme engrenage stylisé pour les modules à venir. */
    private static final String[] ICON_WIP = {
        "  # #  ",
        " ##### ",
        "## # ##",
        " # # # ",
        "## # ##",
        " ##### ",
        "  # #  "
    };

    /**
     * Affiche un état "en construction" centré dans la zone de contenu d'un
     * BaseMenuScreen — utilisé par les modules pas encore ouverts (boutique,
     * récompenses...) à la place de boutons factices.
     */
    public static void renderComingSoon(GuiGraphics gfx, Font font, BaseMenuScreen screen,
                                        String title, String message) {
        int contentY = screen.panelY() + BaseMenuScreen.HEADER_H + 12;
        int contentH = screen.panelH() - BaseMenuScreen.HEADER_H - 12 * 2 - BaseMenuScreen.FOOTER_H;
        int cx = screen.panelX() + screen.panelW() / 2;
        int cy = contentY + contentH / 2;

        drawPixelArt(gfx, ICON_WIP, cx, cy - 22, 4, 0xFF2EA84E);
        gfx.drawCenteredString(font, "§l" + title, cx, cy + 8, 0xFF3DD96A);
        gfx.drawCenteredString(font, "§7" + message, cx, cy + 22, 0xFFAAAAAA);
        gfx.drawCenteredString(font, "§8EN CONSTRUCTION", cx, cy + 36, 0xFF607080);
    }

    /** Interpolation linéaire canal par canal entre deux couleurs ARGB. */
    public static int lerpColor(int colorA, int colorB, float t) {
        int a1 = (colorA >> 24) & 0xFF, a2 = (colorB >> 24) & 0xFF;
        int r1 = (colorA >> 16) & 0xFF, r2 = (colorB >> 16) & 0xFF;
        int g1 = (colorA >>  8) & 0xFF, g2 = (colorB >>  8) & 0xFF;
        int b1 =  colorA        & 0xFF, b2 =  colorB        & 0xFF;
        return ((int)(a1 + (a2 - a1) * t) << 24)
             | ((int)(r1 + (r2 - r1) * t) << 16)
             | ((int)(g1 + (g2 - g1) * t) <<  8)
             |  (int)(b1 + (b2 - b1) * t);
    }

    /**
     * Dessine un pixel art depuis un tableau de chaînes.
     * '#' = pixel coloré, tout autre caractère = transparent.
     * Le dessin est centré sur (cx, cy).
     *
     * @param gfx   contexte de rendu
     * @param grid  tableau de lignes (ex: {"  ##  ", " #### "})
     * @param cx    centre X sur l'écran
     * @param cy    centre Y sur l'écran
     * @param scale taille d'un "pixel" en pixels écran
     * @param color couleur ARGB (0xAARRGGBB)
     */
    public static void drawPixelArt(GuiGraphics gfx, String[] grid,
                                     int cx, int cy, int scale, int color) {
        int cols = 0;
        for (String row : grid) cols = Math.max(cols, row.length());
        int rows = grid.length;

        int startX = cx - (cols * scale) / 2;
        int startY = cy - (rows * scale) / 2;

        for (int r = 0; r < grid.length; r++) {
            String line = grid[r];
            for (int c = 0; c < line.length(); c++) {
                if (line.charAt(c) == '#') {
                    int px = startX + c * scale;
                    int py = startY + r * scale;
                    gfx.fill(px, py, px + scale, py + scale, color);
                }
            }
        }
    }

    /**
     * Remplit un rectangle aux coins arrondis.
     */
    public static void fillRoundedRect(GuiGraphics gfx, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            gfx.fill(x, y, x + w, y + h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        if (r <= 0) { gfx.fill(x, y, x + w, y + h, color); return; }
        // Middle rows (r ≤ i < h-r) batched into one fill
        if (h - 2 * r > 0) gfx.fill(x, y + r, x + w, y + h - r, color);
        // Top rounded corners
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.round(Math.sqrt((double)(r * r) - (double)(dy * dy)));
            int x1 = x + r - dx, x2 = x + w - r + dx;
            if (x2 > x1) gfx.fill(x1, y + i, x2, y + i + 1, color);
        }
        // Bottom rounded corners
        for (int i = h - r; i < h; i++) {
            int dy = i - (h - r - 1);
            int dx = (int) Math.round(Math.sqrt(Math.max(0.0, (double)(r * r) - (double)(dy * dy))));
            int x1 = x + r - dx, x2 = x + w - r + dx;
            if (x2 > x1) gfx.fill(x1, y + i, x2, y + i + 1, color);
        }
    }

    /**
     * Dessine UNIQUEMENT l'anneau de bordure d'un rectangle arrondi —
     * l'intérieur n'est pas touché (reste transparent).
     *
     * C'est la bonne approche quand fillColor = transparent : un fill
     * alpha=0 ne gomme pas ce qui a déjà été dessiné, il faut donc ne
     * jamais peindre l'intérieur.
     */
    public static void drawRoundedBorder(GuiGraphics gfx, int x, int y, int w, int h,
                                         int radius, int thickness, int color) {
        int innerX = x + thickness;
        int innerW = w - 2 * thickness;
        int innerH = h - 2 * thickness;
        int innerR = Math.max(0, radius - thickness);

        for (int i = 0; i < h; i++) {
            // Extents extérieurs de cette ligne
            int x1Out, x2Out;
            if (i < radius) {
                int dy = radius - i;
                int dx = (int) Math.round(Math.sqrt((double)(radius * radius) - (double)(dy * dy)));
                x1Out = x + radius - dx;
                x2Out = x + w - radius + dx;
            } else if (i >= h - radius) {
                int dy = i - (h - radius - 1);
                int dx = (int) Math.round(Math.sqrt(Math.max(0.0, (double)(radius * radius) - (double)(dy * dy))));
                x1Out = x + radius - dx;
                x2Out = x + w - radius + dx;
            } else {
                x1Out = x;
                x2Out = x + w;
            }

            // Ligne dans la zone de bordure haut/bas → pleine largeur
            int iInner = i - thickness;
            if (iInner < 0 || iInner >= innerH) {
                if (x2Out > x1Out) gfx.fill(x1Out, y + i, x2Out, y + i + 1, color);
                continue;
            }

            // Extents intérieurs (trou) de cette ligne
            int x1In, x2In;
            if (innerR <= 0) {
                x1In = innerX;
                x2In = innerX + innerW;
            } else if (iInner < innerR) {
                int dy = innerR - iInner;
                int dx = (int) Math.round(Math.sqrt((double)(innerR * innerR) - (double)(dy * dy)));
                x1In = innerX + innerR - dx;
                x2In = innerX + innerW - innerR + dx;
            } else if (iInner >= innerH - innerR) {
                int dy = iInner - (innerH - innerR - 1);
                int dx = (int) Math.round(Math.sqrt(Math.max(0.0, (double)(innerR * innerR) - (double)(dy * dy))));
                x1In = innerX + innerR - dx;
                x2In = innerX + innerW - innerR + dx;
            } else {
                x1In = innerX;
                x2In = innerX + innerW;
            }

            // Bande gauche de la bordure
            if (x1In > x1Out) gfx.fill(x1Out, y + i, x1In, y + i + 1, color);
            // Bande droite de la bordure
            if (x2Out > x2In) gfx.fill(x2In, y + i, x2Out, y + i + 1, color);
        }
    }

    /**
     * Bordure arrondie animée : vague gauche→droite, sinus décalé par colonne.
     * Même principe que la vague texte (phase = now*0.002 - j*0.35) mais appliqué
     * colonne par colonne sur la bordure, interpolant entre colorDark et colorLight.
     */
    public static void drawWaveBorder(GuiGraphics gfx, int x, int y, int w, int h,
                                      int radius, int thickness,
                                      int colorDark, int colorLight) {
        long now = System.currentTimeMillis();

        int innerY = y + thickness;
        int innerW = w - 2 * thickness;
        int innerH = h - 2 * thickness;
        int innerR = Math.max(0, radius - thickness);

        for (int j = 0; j < w; j++) {
            // Même formule que la vague texte, décalage par colonne
            double phase = now * 0.002 - j * 0.06;
            float t = (float)(Math.sin(phase) * 0.5 + 0.5);
            int color = lerpColor(colorDark, colorLight, t);

            // Extents Y extérieurs à la colonne j
            int y1Out, y2Out;
            if (j < radius) {
                int dj = radius - j;
                int dy = (int) Math.round(Math.sqrt((double)(radius * radius) - (double)(dj * dj)));
                y1Out = y + radius - dy;
                y2Out = y + h - radius + dy;
            } else if (j >= w - radius) {
                int dj = j - (w - radius - 1);
                int dy = (int) Math.round(Math.sqrt(Math.max(0.0, (double)(radius * radius) - (double)(dj * dj))));
                y1Out = y + radius - dy;
                y2Out = y + h - radius + dy;
            } else {
                y1Out = y;
                y2Out = y + h;
            }

            // Colonne dans la bande gauche/droite → pleine hauteur
            int jInner = j - thickness;
            if (jInner < 0 || jInner >= innerW) {
                if (y2Out > y1Out) gfx.fill(x + j, y1Out, x + j + 1, y2Out, color);
                continue;
            }

            // Extents Y intérieurs (trou) à la colonne jInner
            int y1In, y2In;
            if (innerR <= 0) {
                y1In = innerY;
                y2In = innerY + innerH;
            } else if (jInner < innerR) {
                int dj = innerR - jInner;
                int dy = (int) Math.round(Math.sqrt((double)(innerR * innerR) - (double)(dj * dj)));
                y1In = innerY + innerR - dy;
                y2In = innerY + innerH - innerR + dy;
            } else if (jInner >= innerW - innerR) {
                int dj = jInner - (innerW - innerR - 1);
                int dy = (int) Math.round(Math.sqrt(Math.max(0.0, (double)(innerR * innerR) - (double)(dj * dj))));
                y1In = innerY + innerR - dy;
                y2In = innerY + innerH - innerR + dy;
            } else {
                y1In = innerY;
                y2In = innerY + innerH;
            }

            // Bande haute de la bordure
            if (y1In > y1Out) gfx.fill(x + j, y1Out, x + j + 1, y1In, color);
            // Bande basse de la bordure
            if (y2Out > y2In) gfx.fill(x + j, y2In, x + j + 1, y2Out, color);
        }
    }

    /**
     * Retourne la couleur ARGB associée à un grade de ville.
     * Chef → vert vif, Adjoint → bleu, Membre → gris clair
     */
    public static int gradeColor(String grade) {
        return switch (grade == null ? "" : grade) {
            case "Chef"    -> 0xFF55FF55;  // vert vif
            case "Adjoint" -> 0xFF5599FF;  // bleu
            default        -> 0xFFCCCCCC;  // gris clair
        };
    }


    /**
     * Remplit un rectangle avec des coins biseautés (coupés en diagonale à 45°).
     * @param chamfer taille du biseau en pixels
     */
    public static void fillChamferedRect(GuiGraphics gfx, int x, int y, int w, int h, int chamfer, int color) {
        int c = Math.min(chamfer, Math.min(w / 2, h / 2));
        if (c <= 0) { gfx.fill(x, y, x + w, y + h, color); return; }
        // Full-width rows (c to h-c inclusive) batched into one fill
        gfx.fill(x, y + c, x + w, y + h - c + 1, color);
        // Top chamfered corners
        for (int i = 0; i < c; i++) {
            int cut = c - i;
            gfx.fill(x + cut, y + i, x + w - cut, y + i + 1, color);
        }
        // Bottom chamfered corners (d=0 already in middle fill)
        for (int d = 1; d < c; d++) {
            gfx.fill(x + d, y + (h - c) + d, x + w - d, y + (h - c) + d + 1, color);
        }
    }

    /**
     * Dessine la bordure d'un rectangle biseauté SANS le segment haut (horizontal + coins haut).
     * Utile quand un conteneur plus large est visuellement recouvert par un conteneur plus haut.
     */
    public static void drawChamferedBorderNoTop(GuiGraphics gfx, int x, int y, int w, int h,
                                                int chamfer, int thickness, int color) {
        int c = Math.min(chamfer, Math.min(w / 2, h / 2));
        for (int t = 0; t < thickness; t++) {
            int ox = x + t, oy = y + t;
            int ow = w - 2 * t, oh = h - 2 * t;
            int oc = Math.max(0, c - t);
            if (ow <= 0 || oh <= 0) break;
            // Segment bas
            if (ow - 2 * oc > 0)
                gfx.fill(ox + oc, oy + oh - 1, ox + ow - oc, oy + oh, color);
            // Côtés gauche et droit (depuis oy+oc jusqu'en bas — on saute les coins haut)
            if (oh - 2 * oc > 0) {
                gfx.fill(ox,          oy + oc, ox + 1,      oy + oh - oc, color);
                gfx.fill(ox + ow - 1, oy + oc, ox + ow,     oy + oh - oc, color);
            }
            // Coins biseautés BAS uniquement
            for (int d = 0; d < oc; d++) {
                gfx.fill(ox + d,          oy + oh - 1 - (oc-1-d), ox + d + 1,    oy + oh - (oc-1-d),       color);
                gfx.fill(ox + ow - 1 - d, oy + oh - oc + d,       ox + ow - d,   oy + oh - oc + d + 1,     color);
            }
        }
    }

    /**
     * Dessine les crochets de coin (┌┐└┘) à l'intérieur d'un rectangle.
     * @param off   décalage depuis les bords du rectangle
     * @param arm   longueur de chaque bras du crochet
     * @param thick épaisseur du trait
     */
    public static void drawCornerBrackets(GuiGraphics gfx, int x, int y, int w, int h,
                                           int off, int arm, int thick, int color) {
        int lx = x + off, rx = x + w - off;
        int top = y + off, bot = y + h - off;
        gfx.fill(lx,         top,         lx + arm,   top + thick, color);
        gfx.fill(lx,         top,         lx + thick, top + arm,   color);
        gfx.fill(rx - arm,   top,         rx,         top + thick, color);
        gfx.fill(rx - thick, top,         rx,         top + arm,   color);
        gfx.fill(lx,         bot - thick, lx + arm,   bot,         color);
        gfx.fill(lx,         bot - arm,   lx + thick, bot,         color);
        gfx.fill(rx - arm,   bot - thick, rx,         bot,         color);
        gfx.fill(rx - thick, bot - arm,   rx,         bot,         color);
    }

    /**
     * Dessine uniquement la bordure d'un rectangle biseauté (coins coupés à 45°).
     */
    public static void drawChamferedBorder(GuiGraphics gfx, int x, int y, int w, int h,
                                           int chamfer, int thickness, int color) {
        int c = Math.min(chamfer, Math.min(w / 2, h / 2));
        for (int t = 0; t < thickness; t++) {
            int ox = x + t, oy = y + t;
            int ow = w - 2 * t, oh = h - 2 * t;
            int oc = Math.max(0, c - t);
            if (ow <= 0 || oh <= 0) break;
            // Straight edges as single fill calls (from O(w+h) to O(1))
            if (ow - 2 * oc > 0) {
                gfx.fill(ox + oc, oy,          ox + ow - oc, oy + 1,          color); // haut
                gfx.fill(ox + oc, oy + oh - 1, ox + ow - oc, oy + oh,         color); // bas
            }
            if (oh - 2 * oc > 0) {
                gfx.fill(ox,          oy + oc, ox + 1,          oy + oh - oc, color); // gauche
                gfx.fill(ox + ow - 1, oy + oc, ox + ow,         oy + oh - oc, color); // droite
            }
            // Diagonales des 4 coins (inévitablement pixel par pixel, mais court : oc pixels)
            for (int d = 0; d < oc; d++) {
                gfx.fill(ox + (oc - 1 - d), oy + d,                   ox + (oc - d),     oy + d + 1,                   color); // haut-gauche
                gfx.fill(ox + ow - oc + d,  oy + d,                   ox + ow - oc + d + 1, oy + d + 1,               color); // haut-droit
                gfx.fill(ox + d,             oy + oh - 1 - (oc-1-d),  ox + d + 1,        oy + oh - (oc-1-d),           color); // bas-gauche
                gfx.fill(ox + ow - 1 - d,   oy + oh - oc + d,         ox + ow - d,       oy + oh - oc + d + 1,         color); // bas-droit
            }
        }
    }

    // ── Effet néon partagé ────────────────────────────────────────────────────

    /**
     * Effet néon sur les 4 bords d'un rectangle biseauté.
     * Trait opaque, gradient horizontal (sombre aux extrémités → blanc-vert au centre).
     * Utilisé sur les boutons survolés/sélectionnés dans tous les screens du mod.
     *
     * @param chamfer taille du biseau du rectangle (pixels) — ex : 6 pour MenuButton, 4 pour les overlays ville
     */
    public static void drawNeonEdge(GuiGraphics gfx, int x, int y, int w, int h, int chamfer) {
        float half      = w / 2f;
        int darkEdge    = 0xFF0C2A18;
        int midGreen    = 0xFF2EA84E;
        int brightGreen = 0xFF3DDE6A;
        int coreWhite   = 0xFF90FFB8;

        float tEdgeH = 1f - (float) Math.pow(Math.abs((chamfer + 0.5) / half       - 1.0), 2);
        float tEdgeV = 1f - (float) Math.pow(Math.abs((chamfer + 0.5) / (h / 2.0) - 1.0), 2);
        float tEdge  = Math.min(tEdgeH, tEdgeV);

        // Coins biseautés
        for (int t = 0; t < 2; t++) {
            int ox = x + t, oy = y + t, ow = w - 2 * t, oh = h - 2 * t;
            int oc = Math.max(0, chamfer - t);
            if (oc == 0) break;
            int cc = neonColor(tEdge, darkEdge, midGreen, brightGreen, coreWhite);
            for (int d = 0; d < oc; d++) {
                gfx.fill(ox+oc-1-d,  oy+d,         ox+oc-d,      oy+d+1,       cc);
                gfx.fill(ox+ow-oc+d, oy+d,         ox+ow-oc+d+1, oy+d+1,       cc);
                gfx.fill(ox+d,       oy+oh-oc+d,   ox+d+1,       oy+oh-oc+d+1, cc);
                gfx.fill(ox+ow-1-d,  oy+oh-oc+d,   ox+ow-d,      oy+oh-oc+d+1, cc);
            }
        }

        // Traits haut et bas : gradient horizontal centré
        for (int j = chamfer; j < w - chamfer; j++) {
            float dist = Math.abs(j - half) / half;
            float t    = 1f - dist * dist;
            int lc     = neonColor(t, darkEdge, midGreen, brightGreen, coreWhite);
            gfx.fill(x + j, y,         x + j + 1, y + 2,         lc);
            gfx.fill(x + j, y + h - 2, x + j + 1, y + h,         lc);
            int ha = (int)(t * 140);
            if (ha > 6) {
                int hcol = (ha << 24) | 0x003DDE6A;
                gfx.fill(x + j, y - 1,     x + j + 1, y,         hcol);
                gfx.fill(x + j, y + h,     x + j + 1, y + h + 1, hcol);
            }
            int ha2 = (int)(t * 60);
            if (ha2 > 4) {
                int hcol2 = (ha2 << 24) | 0x003DDE6A;
                gfx.fill(x + j, y - 2,     x + j + 1, y - 1,     hcol2);
                gfx.fill(x + j, y + h + 1, x + j + 1, y + h + 2, hcol2);
            }
        }

        // Traits gauche et droit : couleur uniforme = valeur de tEdge
        int sc  = neonColor(tEdge, darkEdge, midGreen, brightGreen, coreWhite);
        int sh  = (int)(tEdge * 140);
        int sh2 = (int)(tEdge * 60);
        for (int i = chamfer; i < h - chamfer; i++) {
            gfx.fill(x,         y + i, x + 2,     y + i + 1, sc);
            gfx.fill(x + w - 2, y + i, x + w,     y + i + 1, sc);
            if (sh > 6) {
                int hcol = (sh << 24) | 0x003DDE6A;
                gfx.fill(x - 1,     y + i, x,         y + i + 1, hcol);
                gfx.fill(x + w,     y + i, x + w + 1, y + i + 1, hcol);
            }
            if (sh2 > 4) {
                int hcol2 = (sh2 << 24) | 0x003DDE6A;
                gfx.fill(x - 2,     y + i, x - 1,     y + i + 1, hcol2);
                gfx.fill(x + w + 1, y + i, x + w + 2, y + i + 1, hcol2);
            }
        }
    }

    /** Gradient à 3 étapes utilisé par drawNeonEdge. */
    private static int neonColor(float t, int dark, int mid, int bright, int core) {
        if (t > 0.85f) return lerpColor(bright, core,  (t - 0.85f) / 0.15f);
        if (t > 0.40f) return lerpColor(mid,    bright, (t - 0.40f) / 0.45f);
        return lerpColor(dark, mid, t / 0.4f);
    }

    // ── Utilitaires texte partagés ────────────────────────────────────────────

    /**
     * Formate une valeur monétaire lisible.
     * Exemples : 1 200 000 → "1.2M" ; 5 300 → "5.3k" ; 42.50 → "42.50"
     */
    public static String formatBanque(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("%.1fk", v / 1_000);
        return String.format("%.2f", v);
    }

    /**
     * Capitalise un identifiant de dimension Minecraft.
     * Exemples : "minecraft:overworld" → "Overworld" ; "nether" → "Nether"
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String part = s.contains(":") ? s.split(":")[1] : s;
        return part.isEmpty() ? part
                : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase();
    }
}
