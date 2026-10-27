package com.bidboard;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Auction card (3 timer styles) + payment toasts. */
public final class Hud {
    public static final int CARD_H = 64;
    private static final int TEXT = 0xFFFFFF, DIM = 0xA8B0B8, GREEN = 0x5BE37D, RED = 0xFF6B6B, DARK = 0x10141A;
    private static double shownBid;

    private Hud() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Config c = BidBoardMod.config;
        Minecraft mc = Minecraft.getInstance();
        if (!c.enabled || mc.player == null || mc.options.hideGui || mc.screen instanceof MenuScreen) return;

        long now = System.currentTimeMillis();
        Tracker.Lot lot = Tracker.lot;
        if (lot != null) {
            if (!lot.ended && lot.endsAt > 0 && now >= lot.endsAt) Tracker.finish(lot);
            if (lot.ended && now - lot.endedAt > 5700) { Tracker.lot = null; lot = null; }
        }

        int w = lot != null ? cardWidth(lot) : 250;
        int[] p = c.place(g.guiWidth(), g.guiHeight(), w, CARD_H);
        boolean bottom = c.anchor.startsWith("B");
        if (lot != null) drawCard(g, lot, p[0], p[1], now);
        if (bottom) drawPayments(g, p[0], lot != null ? p[1] - 4 : p[1] + CARD_H, now, true);
        else drawPayments(g, p[0], lot != null ? p[1] + CARD_H + 4 : p[1], now, false);
    }

    /** The card grows when its content needs more room, so nothing overlaps. */
    public static int cardWidth(Tracker.Lot lot) {
        Config c = BidBoardMod.config; Font f = Minecraft.getInstance().font;
        boolean hasBid = !lot.topBidder.isEmpty();
        int pills = 54 + (hasBid ? Draw.w(f, "Bid " + Tracker.shortNum(lot.topBid)) + 14 : 0)
                  + Draw.w(f, "Min " + Tracker.shortNum(lot.min)) + 10 + 52;
        int row3 = 62 + 8;
        if (c.showNote && !lot.note.isEmpty()) row3 += Draw.w(f, cut(lot.note, 24));
        if (c.showWorth && lot.worth > 0) row3 += 10 + Draw.w(f, "Worth " + Tracker.shortNum(lot.worth * lot.qty)) + 10;
        return Math.max(250, Math.max(pills, row3));
    }

    /** Draws the auction card, returns its height. Also used by the menu preview. */
    public static int drawCard(GuiGraphics g, Tracker.Lot lot, int x, int y, long now) {
        Config c = BidBoardMod.config;
        Font f = Minecraft.getInstance().font;
        int r = c.radius, accent = c.accentRgb(), W = cardWidth(lot);
        String style = c.cardStyle == null ? "classic" : c.cardStyle;

        float in = Draw.easeOut((now - lot.createdAt) / 260f);
        float life = lot.ended ? Math.max(0f, Math.min(1f, 1f - (now - lot.endedAt - 5000) / 600f)) : 1f;
        float e = in * life;
        x -= (int) ((1 - in) * 18);

        boolean hasBid = !lot.topBidder.isEmpty();
        if (!hasBid) shownBid = 0;
        shownBid += (lot.topBid - shownBid) * 0.2;
        if (Math.abs(lot.topBid - shownBid) < 1) shownBid = lot.topBid;

        Draw.rrect(g, x, y, W, CARD_H, r, Draw.a(c.bgRgb(), c.bgOpacity / 100f * e));

        Draw.rrect(g, x + 6, y + 6, 40, 40, Math.max(0, r - 1), Draw.a(0xFFFFFF, 0.07f * e));
        if (!lot.stack.isEmpty()) g.renderItem(lot.stack, x + 18, y + 18);
        if (lot.qty > 1) {
            String q = "x" + lot.qty;
            Draw.text(g, f, q, x + 44 - Draw.w(f, q), y + 36, Draw.a(TEXT, e), true);
        }

        String title = hasBid ? (lot.ended ? "Winner: " : "") + cut(lot.topBidder, 16) : "No bids";
        Draw.text(g, f, title, x + 54, y + 9, Draw.a(lot.ended && hasBid ? accent : TEXT, e), true);

        int px = x + 54;
        if (hasBid) px += Draw.pill(g, f, px, y + 23, "Bid " + Tracker.shortNum(shownBid), accent, 0.95f, DARK, e) + 4;
        Draw.pill(g, f, px, y + 23, "Min " + Tracker.shortNum(lot.min), 0xFFFFFF, 0.10f, DIM, e);

        // ----- timer (3 styles) -----
        boolean timed = lot.endsAt > lot.startedAt;
        double frac = 0; int secs = 0;
        if (timed) {
            long left = Math.max(0, lot.endsAt - now);
            frac = Math.min(1.0, left / (double) (lot.endsAt - lot.startedAt));
            secs = (int) Math.ceil(left / 1000.0);
        }
        if (timed || lot.ended) {
            boolean urgent = !lot.ended && secs <= 5;
            int col = urgent ? RED : accent;
            String s = lot.ended ? "End" : String.valueOf(secs);
            if (style.equals("ring")) {
                int cx = x + W - 26, cy = y + 24;
                Draw.ring(g, cx, cy, 14, 2, lot.ended ? 1f : (float) frac, col, e, 0xFFFFFF, 0.12f * e);
                Draw.text(g, f, s, cx - Draw.w(f, s) / 2, cy - 4, Draw.a(col, e), false);
            } else if (style.equals("number")) {
                int tw = Draw.w(f, s) * 2;
                Draw.textScaled(g, f, s, x + W - 10 - tw, y + 9, Draw.a(col, e), 2f, true);
            } else {
                Draw.rrect(g, x + W - 44, y + 8, 38, 18, Math.min(9, r + 4), Draw.a(col, 0.22f * e));
                Draw.text(g, f, s, x + W - 44 + (38 - Draw.w(f, s)) / 2, y + 13, Draw.a(col, e), false);
            }
        }

        // ----- note + worth (card is sized so these never overlap) -----
        if (c.showNote && !lot.note.isEmpty()) {
            Draw.rrect(g, x + 54, y + 49, 4, 4, 2, Draw.a(accent, e));
            Draw.text(g, f, cut(lot.note, 24), x + 62, y + 47, Draw.a(TEXT, e), false);
        }
        if (c.showWorth && lot.worth > 0) {
            String w = "Worth " + Tracker.shortNum(lot.worth * lot.qty);
            int pw = Draw.w(f, w) + 10;
            Draw.pill(g, f, x + W - 6 - pw, y + 44, w, accent, 0.22f, accent, e);
        }

        if (style.equals("classic") && timed && !lot.ended) { // smooth bar: updates every frame
            Draw.rrect(g, x + 6, y + CARD_H - 6, W - 12, 2, 1, Draw.a(0xFFFFFF, 0.12f * e));
            int col = secs <= 5 ? RED : accent;
            Draw.rrect(g, x + 6, y + CARD_H - 6, Math.max(2, (int) ((W - 12) * frac)), 2, 1, Draw.a(col, e));
        }
        return CARD_H;
    }

    private static void drawPayments(GuiGraphics g, int x, int y, long now, boolean above) {
        Config c = BidBoardMod.config;
        Font f = Minecraft.getInstance().font;
        long ttl = c.paymentSeconds * 1000L;
        Tracker.payments.removeIf(p -> now - p.at() > ttl);

        List<Tracker.Payment> recent = new ArrayList<>(Tracker.payments);
        int from = Math.max(0, recent.size() - c.maxPayments);
        int shown = recent.size() - from;
        boolean totals = c.showTotals && shown > 0 && (Tracker.earned > 0 || Tracker.spent > 0);
        int rows = (c.showToasts ? shown : 0) * 17 + (totals ? 12 : 0);
        if (above && rows > 0) y -= rows;

        if (c.showToasts) {
            for (Tracker.Payment p : recent.subList(from, recent.size())) {
                float fade = Math.min(1f, (ttl - (now - p.at())) / 1500f);
                float in = Draw.easeOut((now - p.at()) / 200f);
                float op = Math.min(fade, in);
                int col = p.incoming() ? GREEN : RED;
                String t = (p.incoming() ? "+ " : "- ") + Tracker.fmt(p.amount()) + (p.incoming() ? " from " : " to ") + p.who();
                int w = Draw.w(f, t) + 18, xx = x - (int) ((1 - in) * 12);
                Draw.rrect(g, xx, y, w, 14, Math.min(c.radius, 7), Draw.a(c.bgRgb(), c.bgOpacity / 100f * op));
                Draw.rrect(g, xx + 5, y + 5, 4, 4, 2, Draw.a(col, op));
                Draw.text(g, f, t, xx + 13, y + 3, Draw.a(col, op), false);
                y += 17;
            }
        }
        if (totals) {
            String t = "In " + Tracker.fmt(Tracker.earned) + "   Out " + Tracker.fmt(Tracker.spent);
            Draw.text(g, f, t, x + 4, y + 1, Draw.a(DIM, 1f), true);
        }
    }

    private static String cut(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "..."; }
}
