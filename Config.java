package com.bidboard;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.List;

/** Settings and chat patterns, stored in config/bidboard.json. Regex named groups: who, amt, item. */
public class Config {
    // look
    public boolean enabled = true;
    public String anchor = "TL";             // TL TC TR BL BC BR
    public int offX = 8, offY = 8;           // distance from that edge

    /** Top-left corner for a box of size w x h on a screen of sw x sh. */
    public int[] place(int sw, int sh, int w, int h) {
        int x = anchor.endsWith("R") ? sw - w - offX : anchor.endsWith("C") ? (sw - w) / 2 + offX : offX;
        int y = anchor.startsWith("B") ? sh - h - offY : offY;
        return new int[]{x, y};
    }
    public String accent = "#E0B341", bg = "#101418";
    public int bgOpacity = 80, radius = 5;
    public boolean showWorth = true, showNote = true;
    public boolean fullRound = false;
    public boolean showToasts = true, showTotals = true, smooth = true, copyWinner = true;
    public String cardStyle = "classic";    // classic | ring | number       // pill/circle shapes everywhere
    public static class Snd {
        public boolean on = true; public String id = "minecraft:ui.button.click"; public int volume = 100, pitch = 100;
        public Snd() {}
        public Snd(String id, int volume, int pitch) { this.id = id; this.volume = volume; this.pitch = pitch; }
    }
    public Snd sndOpen = new Snd("minecraft:ui.toast.in", 100, 100);
    public Snd sndStart = new Snd("minecraft:entity.experience_orb.pickup", 100, 100);
    public Snd sndWin = new Snd("minecraft:entity.player.levelup", 100, 100);
    public String subtitle = "Member";        // small line under your name in the menu
    public String font = "minecraft";      // minecraft | clean | bold | mono
    public int schema = 0;                  // config migration marker
    public int maxPayments = 5, paymentSeconds = 15;
    // behavior: placeholders {min} {qty} {timer} {note}. Leave empty to only show the card locally.
    public String startCommand = "";   // optional command to run, without the slash
    // Public chat announcement sent when you press Start. Placeholders: {item} {qty} {min} {timer} {note}. Empty = send nothing.
    public String startMessage = "{qty}x {item} auction | min {min} | {timer}s to make order";

    public List<String> paymentReceived = List.of(
        "(?<who>[\\w.]{2,24})\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\s+\\$?\\s*(?<amt>[\\d,.]+[kmbt]?)",
        "received\\s+\\$?\\s*(?<amt>[\\d,.]+[kmbt]?)\\s+from\\s+(?<who>[\\w.]{2,24})");
    public List<String> paymentSent = List.of(
        "you\\s+(?:have\\s+)?(?:paid|sent)\\s+(?<who>[\\w.]{2,24})\\s+\\$?\\s*(?<amt>[\\d,.]+[kmbt]?)");
    public List<String> auctionStart = List.of(
        "Auction started for (?<item>.+?) (?:with|at|starting at) \\$?\\s*(?<amt>[\\d,.]+[kmb]?)");
    public List<String> bid = List.of(
        "(?<who>\\w+) (?:placed|made) a bid of \\$?\\s*(?<amt>[\\d,.]+[kmb]?)");
    public List<String> auctionEnd = List.of("Auction (?:has )?ended", "(?<who>\\w+) won the auction");

    public int accentRgb() { return rgb(accent, 0xE0B341); }
    public int bgRgb() { return rgb(bg, 0x101418); }
    private static int rgb(String s, int d) {
        try { return Integer.parseInt(s.replace("#", "").trim(), 16) & 0xFFFFFF; } catch (Exception e) { return d; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("bidboard.json"); }

    public static Config load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file()), Config.class);
                if (c != null) {
                    if (c.schema < 4) { // older config: take the new payment patterns and start settings
                        Config d = new Config();
                        c.paymentReceived = d.paymentReceived; c.paymentSent = d.paymentSent;
                        c.startCommand = d.startCommand; c.startMessage = d.startMessage;
                        c.schema = 4; c.save();
                    }
                    return c;
                }
            }
        } catch (Exception e) { BidBoardMod.LOGGER.warn("Could not read config, using defaults", e); }
        Config c = new Config(); c.schema = 4; c.save(); return c;
    }

    public void save() {
        try { Files.writeString(file(), GSON.toJson(this)); }
        catch (Exception e) { BidBoardMod.LOGGER.warn("Could not save config", e); }
    }
}
