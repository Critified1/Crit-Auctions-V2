package com.bidboard;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Reads chat lines; holds the current auction lot and recent payments. */
public final class Tracker {
    public record Payment(String who, double amount, boolean incoming, long at) {}

    public static final class Lot {
        public ItemStack stack = ItemStack.EMPTY;
        public String name = "Auction", topBidder = "", note = "";
        public int qty = 1;
        public double min, worth, topBid;
        public boolean ended;
        public long createdAt = System.currentTimeMillis(), startedAt = createdAt, endsAt, endedAt;
    }

    public static Lot lot;
    public static double earned, spent;
    public static final Deque<Payment> payments = new ArrayDeque<>();
    /** Last few money/bid-looking lines, for /bidboard last */
    public static final Deque<String> recent = new ArrayDeque<>();

    private static List<Pattern> in = List.of(), out = List.of(), starts = List.of(), bids = List.of(), ends = List.of();
    private static String lastKey = ""; private static long lastKeyAt;

    private static final String SMALL = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    private Tracker() {}

    public static void compile(Config c) {
        in = comp(c.paymentReceived); out = comp(c.paymentSent);
        starts = comp(c.auctionStart); bids = comp(c.bid); ends = comp(c.auctionEnd);
    }

    public static void reset() { lot = null; earned = spent = 0; payments.clear(); }

    public static void begin(ItemStack stack, int qty, double min, double worth, String note, int timerSec) {
        Lot l = new Lot();
        l.stack = stack; l.qty = qty; l.min = min; l.worth = worth; l.note = note;
        l.name = stack.getHoverName().getString();
        if (timerSec > 0) l.endsAt = l.startedAt + timerSec * 1000L;
        lot = l;
        Stats.auction();
    }

    /** Marks the auction over and copies the winner's name to the clipboard. */
    public static void finish(Lot l) {
        if (l == null || l.ended) return;
        l.ended = true; l.endedAt = System.currentTimeMillis();
        if (!l.topBidder.isEmpty()) {
            Sounds.play(BidBoardMod.config.sndWin);
            Minecraft mc = Minecraft.getInstance();
            if (BidBoardMod.config.copyWinner) {
                mc.keyboardHandler.setClipboard(l.topBidder);
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("Winner copied: " + l.topBidder), true);
            }
        }
    }

    /** Turns fancy unicode (small caps, fullwidth, bold math letters) and odd spaces into plain text. */
    public static String norm(String s) {
        if (s == null) return "";
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            int i = SMALL.indexOf(ch);
            if (i >= 0) b.append((char) ('a' + i));
            else if (Character.isSpaceChar(ch) || Character.isWhitespace(ch)) b.append(' ');
            else b.append(ch);
        }
        return b.toString().trim();
    }

    public static void onMessage(String raw) {
        String text = norm(raw);
        if (text.isBlank()) return;
        String low = text.toLowerCase();
        if (text.contains("$") || low.contains("paid") || low.contains("bid") || low.contains("auction")) {
            recent.addLast(text); while (recent.size() > 8) recent.removeFirst();
        }
        Matcher m;
        if ((m = hit(in, text)) != null)  { add(g(m, "who"), parse(g(m, "amt")), true); return; }
        if ((m = hit(out, text)) != null) { add(g(m, "who"), parse(g(m, "amt")), false); return; }
        if ((m = hit(starts, text)) != null) {
            Lot l = new Lot(); l.name = Objects.requireNonNullElse(g(m, "item"), "Item"); l.min = parse(g(m, "amt"));
            lot = l; return;
        }
        if ((m = hit(bids, text)) != null) { bid(g(m, "who"), parse(g(m, "amt"))); return; }
        if (hit(ends, text) != null) finish(lot);
    }

    private static void bid(String who, double amt) {
        if (lot == null) lot = new Lot();
        if (lot.ended || amt <= lot.topBid || amt < lot.min) return;
        lot.topBid = amt; lot.topBidder = Objects.requireNonNullElse(who, "?");
    }

    private static void add(String who, double amt, boolean incoming) {
        if (amt <= 0) return;
        who = Objects.requireNonNullElse(who, "?");
        String key = incoming + who + amt; long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastKeyAt < 1500) return; // same line from chat + action bar
        lastKey = key; lastKeyAt = now;
        payments.addLast(new Payment(who, amt, incoming, now));
        if (incoming) { earned += amt; Stats.in(amt); if (lot != null) bid(who, amt); } else { spent += amt; Stats.out(amt); } // on DonutSMP a bid is a payment
    }

    private static List<Pattern> comp(List<String> src) {
        List<Pattern> r = new ArrayList<>();
        for (String s : src) {
            try { r.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE)); }
            catch (PatternSyntaxException e) { BidBoardMod.LOGGER.warn("Bad pattern skipped: {}", s); }
        }
        return r;
    }

    private static Matcher hit(List<Pattern> ps, String t) {
        for (Pattern p : ps) { Matcher m = p.matcher(t); if (m.find()) return m; }
        return null;
    }

    private static String g(Matcher m, String name) {
        try { return m.group(name); } catch (IllegalArgumentException e) { return null; }
    }

    public static double parse(String s) {
        if (s == null) return 0;
        s = s.replace(",", "").replace("$", "").trim().toLowerCase();
        double mult = 1;
        if (s.endsWith("k")) mult = 1e3; else if (s.endsWith("m")) mult = 1e6;
        else if (s.endsWith("b")) mult = 1e9; else if (s.endsWith("t")) mult = 1e12;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try { return Double.parseDouble(s) * mult; } catch (NumberFormatException e) { return 0; }
    }

    public static String shortNum(double v) {
        if (v >= 1e12) return trim(v / 1e12) + "T";
        if (v >= 1e9) return trim(v / 1e9) + "B";
        if (v >= 1e6) return trim(v / 1e6) + "M";
        if (v >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }
    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    public static String fmt(double v) { return "$" + shortNum(v); }
}
