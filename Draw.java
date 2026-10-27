package com.bidboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Drawing helpers. Rounded shapes use true per-pixel anti-aliasing, and (when "ultra-smooth" is on) are drawn at the
 * screen's real pixel resolution instead of in big GUI pixels, so edges are smooth at any GUI scale.
 */
public final class Draw {
    private Draw() {}

    public static int a(int rgb, float opacity) {
        int al = Math.round(255 * Math.max(0.02f, Math.min(1f, opacity)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static float easeOut(float t) { t = Math.max(0, Math.min(1, t)); float u = 1 - t; return 1 - u * u * u; }

    // ---------- rounded rectangles ----------
    private static final class Corner {
        final float[][] cov; final int[] first;
        Corner(float[][] cov, int[] first) { this.cov = cov; this.first = first; }
    }
    private static final Map<Integer, Corner> CORNERS = new HashMap<>();

    private static Corner corner(int r) {
        return CORNERS.computeIfAbsent(r, k -> {
            float[][] t = new float[k][k]; int[] f = new int[k];
            for (int i = 0; i < k; i++) {
                for (int x = 0; x < k; x++) {
                    double dx = k - x - 0.5, dy = k - i - 0.5;
                    t[i][x] = (float) Math.max(0, Math.min(1, k - Math.sqrt(dx * dx + dy * dy) + 0.5));
                }
                int s = 0; while (s < k && t[i][s] < 0.999f) s++;
                f[i] = s;
            }
            return new Corner(t, f);
        });
    }

    /** Pixels per GUI pixel to draw shapes at (1 = plain GUI pixels). */
    private static int scale() {
        Config c = BidBoardMod.config;
        if (c == null || !c.smooth) return 1;
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        Config c = BidBoardMod.config;
        if (c != null && c.fullRound) r = Math.min(w, h) <= 64 ? Math.min(w, h) / 2 : Math.max(r, 24);
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, argb); return; }
        int s = scale();
        if (s > 1) {
            g.pose().pushMatrix(); g.pose().scale(1f / s, 1f / s);
            rr(g, x * s, y * s, w * s, h * s, r * s, argb);
            g.pose().popMatrix();
        } else rr(g, x, y, w, h, r, argb);
    }

    private static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        Corner c = corner(r);
        int base = (argb >>> 24) & 255, rgb = argb & 0xFFFFFF;
        for (int i = 0; i < r; i++) {
            int fs = c.first[i];
            g.fill(x + fs, y + i, x + w - fs, y + i + 1, argb);
            g.fill(x + fs, y + h - i - 1, x + w - fs, y + h - i, argb);
            for (int p = 0; p < fs; p++) {
                int al = (int) (base * c.cov[i][p]);
                if (al <= 2) continue;
                int col = (al << 24) | rgb;
                g.fill(x + p, y + i, x + p + 1, y + i + 1, col);
                g.fill(x + w - 1 - p, y + i, x + w - p, y + i + 1, col);
                g.fill(x + p, y + h - 1 - i, x + p + 1, y + h - i, col);
                g.fill(x + w - 1 - p, y + h - 1 - i, x + w - p, y + h - i, col);
            }
        }
        g.fill(x, y + r, x + w, y + h - r, argb);
    }

    // ---------- circular timer ring ----------
    private static final class Ring { int[] ix, iy; float[] cov, ang; }
    private static final Map<Long, Ring> RINGS = new HashMap<>();

    private static Ring ring(int ro, int ri) {
        return RINGS.computeIfAbsent((long) ro * 100000L + ri, k -> {
            int n = 0, m = ro + 1;
            int[] xs = new int[(2 * m) * (2 * m)], ys = new int[xs.length]; float[] cv = new float[xs.length], an = new float[xs.length];
            for (int iy = -m; iy < m; iy++) for (int ix = -m; ix < m; ix++) {
                double px = ix + 0.5, py = iy + 0.5, d = Math.sqrt(px * px + py * py);
                double cov = Math.max(0, Math.min(1, ro - d + 0.5)) * Math.max(0, Math.min(1, d - ri + 0.5));
                if (cov < 0.03) continue;
                double ang = Math.atan2(px, -py); if (ang < 0) ang += 2 * Math.PI;
                xs[n] = ix; ys[n] = iy; cv[n] = (float) cov; an[n] = (float) (ang / (2 * Math.PI)); n++;
            }
            Ring r = new Ring();
            r.ix = java.util.Arrays.copyOf(xs, n); r.iy = java.util.Arrays.copyOf(ys, n);
            r.cov = java.util.Arrays.copyOf(cv, n); r.ang = java.util.Arrays.copyOf(an, n);
            return r;
        });
    }

    /** Draws a ring centered at (cx, cy); the part from 12 o'clock clockwise up to frac (0..1) uses the fg color. */
    public static void ring(GuiGraphics g, int cx, int cy, int radius, int thick, float frac,
                            int fgRgb, float fgOp, int trackRgb, float trackOp) {
        int s = scale();
        Ring r = ring(radius * s, Math.max(0, (radius - thick) * s));
        if (s > 1) { g.pose().pushMatrix(); g.pose().scale(1f / s, 1f / s); }
        int ox = cx * s, oy = cy * s;
        for (int k = 0; k < r.ix.length; k++) {
            boolean on = r.ang[k] <= frac;
            float op = (on ? fgOp : trackOp) * r.cov[k];
            if (op < 0.03f) continue;
            g.fill(ox + r.ix[k], oy + r.iy[k], ox + r.ix[k] + 1, oy + r.iy[k] + 1, a(on ? fgRgb : trackRgb, op));
        }
        if (s > 1) g.pose().popMatrix();
    }

    // ---------- text with the selected font ----------
    private static Style cached; private static String cachedFor = "";

    private static Component comp(String s) {
        String name = BidBoardMod.config.font;
        if (name == null || name.equals("minecraft")) return Component.literal(s);
        if (!name.equals(cachedFor)) {
            cached = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("bidboard", name)));
            cachedFor = name;
        }
        MutableComponent c = Component.literal(s);
        return c.withStyle(cached);
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int argb, boolean shadow) {
        g.drawString(f, comp(s), x, y, argb, shadow);
    }
    public static int w(Font f, String s) { return f.width(comp(s)); }

    /** Text drawn bigger (sc = 2 means double size); x/y are the top-left in normal GUI pixels. */
    public static void textScaled(GuiGraphics g, Font f, String s, int x, int y, int argb, float sc, boolean shadow) {
        g.pose().pushMatrix(); g.pose().scale(sc, sc);
        text(g, f, s, Math.round(x / sc), Math.round(y / sc), argb, shadow);
        g.pose().popMatrix();
    }

    public static void head(GuiGraphics g, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) PlayerFaceRenderer.draw(g, mc.player.getSkin(), x, y, size);
    }

    public static int pill(GuiGraphics g, Font f, int x, int y, String s, int bgRgb, float bgOp, int textRgb, float op) {
        int w = w(f, s) + 10;
        rrect(g, x, y, w, 12, 6, a(bgRgb, bgOp * op));
        text(g, f, s, x + 5, y + 2, a(textRgb, op), false);
        return w;
    }
}
