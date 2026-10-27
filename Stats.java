package com.bidboard;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

/** Lifetime payment totals, saved to config/bidboard_stats.json. */
public final class Stats {
    public double earned, lost, biggestIn, biggestOut;
    public int received, sent, auctions;

    private static Stats I = new Stats();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("bidboard_stats.json"); }

    private Stats() {}
    public static Stats get() { return I; }

    public static void load() {
        try {
            if (Files.exists(file())) { Stats s = GSON.fromJson(Files.readString(file()), Stats.class); if (s != null) I = s; }
        } catch (Exception e) { I = new Stats(); }
    }
    public static void save() { try { Files.writeString(file(), GSON.toJson(I)); } catch (Exception ignored) {} }
    public static void reset() { I = new Stats(); save(); }

    public static void in(double a)  { I.earned += a; I.received++; I.biggestIn = Math.max(I.biggestIn, a); save(); }
    public static void out(double a) { I.lost += a;   I.sent++;     I.biggestOut = Math.max(I.biggestOut, a); save(); }
    public static void auction()     { I.auctions++; save(); }
}
