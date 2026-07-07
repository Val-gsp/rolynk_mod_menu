package com.example.rolynkmodmenu.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Icônes vectorielles du menu Rolynk, dessinées en code (pas de texture PNG).
 *
 * Utilisation :
 *   HenuIcons.drawPlayers(gfx, x, y, size, color);
 *   HenuIcons.drawClock(gfx,   x, y, size, color);
 *
 * x, y = coin haut-gauche de la zone carrée
 * size = côté en pixels (10-16 recommandé pour le footer)
 */
public final class HenuIcons {

    private HenuIcons() {}

    // ── Icônes publiques ──────────────────────────────────────────────────────

    /** Deux personnages qui se chevauchent (joueurs en ligne). */
    public static void drawPlayers(GuiGraphics g, int x, int y, int size, int color) {
        double s = size;
        // Personnage du fond (droite, légèrement plus petit)
        disc(g, x + 0.62 * s, y + 0.34 * s, 0.13 * s, color);
        dome(g, x + 0.62 * s, y + 0.86 * s, 0.24 * s, color);
        // Personnage de devant (gauche, par-dessus)
        disc(g, x + 0.42 * s, y + 0.40 * s, 0.16 * s, color);
        dome(g, x + 0.42 * s, y + 0.92 * s, 0.30 * s, color);
    }

    /** Cadran d'horloge + aiguille des minutes (haut) et des heures (droite). */
    public static void drawClock(GuiGraphics g, int x, int y, int size, int color) {
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;
        double r  = size / 2.0 - 1.0;
        ring(g, cx, cy, r, color);
        line(g, cx, cy, cx,            cy - r * 0.55, color);
        line(g, cx, cy, cx + r * 0.45, cy,            color);
    }

    // ── Primitives ────────────────────────────────────────────────────────────

    private static void px(GuiGraphics g, double x, double y, int c) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, c);
    }

    private static void disc(GuiGraphics g, double cx, double cy, double r, int c) {
        int ri = (int) Math.ceil(r);
        for (int yy = -ri; yy <= ri; yy++)
            for (int xx = -ri; xx <= ri; xx++)
                if (xx * xx + yy * yy <= r * r) px(g, cx + xx, cy + yy, c);
    }

    private static void dome(GuiGraphics g, double cx, double baseY, double r, int c) {
        int ri = (int) Math.ceil(r);
        for (int yy = -ri; yy <= 0; yy++)
            for (int xx = -ri; xx <= ri; xx++)
                if (xx * xx + yy * yy <= r * r) px(g, cx + xx, baseY + yy, c);
    }

    private static void ring(GuiGraphics g, double cx, double cy, double r, int c) {
        double rin = r - 1.4;
        int ri = (int) Math.ceil(r);
        for (int yy = -ri; yy <= ri; yy++) {
            for (int xx = -ri; xx <= ri; xx++) {
                double d2 = xx * xx + yy * yy;
                if (d2 <= r * r && d2 >= rin * rin) px(g, cx + xx, cy + yy, c);
            }
        }
    }

    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int c) {
        double dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0) { px(g, x0, y0, c); return; }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            px(g, x0 + dx * t, y0 + dy * t, c);
        }
    }
}
