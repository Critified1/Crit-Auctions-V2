package com.bidboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.*;

/**
 * Menu with tabs: Auction, Layout, Sounds, General, and a Stats page opened by clicking your skin card.
 * Everything is drawn by us; vanilla buttons are invisible hit areas (alpha 0) so hover/click still work.
 */
public class MenuScreen extends Screen {
    private static final int PW = 406, PH = 230, SW = 104, COLS = 7, ROWS = 7, CELL = 18, FW = 150, STATS = 4;
    private static final String[] TABS = {"Auction", "Layout", "Sounds", "General"};
    private static final String[] ANCHORS = {"TL", "TC", "TR", "BL", "BC", "BR"};
    private static final String[] NAMES = {"Amber", "Mint", "Rose", "Sky", "Violet"};
    private static final String[] COLORS = {"#E0B341", "#3DDC97", "#FF6B8B", "#4DA8FF", "#A78BFA"};
    private static final String[] FONTS = {"minecraft", "clean", "bold", "mono"};
    private static final String[] FNAMES = {"Minecraft", "Clean", "Clean Bold", "Mono"};
    private static final String[] CARDS = {"classic", "ring", "number"};
    private static final String[] CNAMES = {"Classic", "Circle timer", "Number timer"};
    private static List<Item> ALL;

    private record Vis(Button b, Supplier<String> text, BooleanSupplier primary) {}
    private record Txt(int x, int y, Supplier<String> t, int rgb) {}

    private final List<Vis> vis = new ArrayList<>();
    private final List<Txt> txt = new ArrayList<>();
    private final List<int[]> fields = new ArrayList<>(), rows = new ArrayList<>();
    private final List<Button> cells = new ArrayList<>();
    private final Button[] zones = new Button[6];
    private final Map<Button, Float> hov = new IdentityHashMap<>();
    private final Tracker.Lot sample = makeSample();
    private final long openedAt = System.currentTimeMillis();
    private long tabAt = 0;
    private Button userBtn;

    private int tab, page, px, py, cx, fx;
    private float indY = -1;
    private boolean opened, resetArm;
    private Item picked;
    private List<Item> filtered = List.of();
    private String vSearch = "", vMin = "", vQty = "1", vTimer = "20", vWorth = "", vNote = "15 Seconds To Make Order";

    public MenuScreen(int tab) { super(Component.literal("Crit Auctions")); this.tab = tab; }

    // ================= layout =================
    @Override
    protected void init() {
        if (!opened) { opened = true; Sounds.play(BidBoardMod.config.sndOpen); }
        px = (width - PW) / 2; py = Math.max(4, (height - PH) / 2);
        cx = px + SW + 8; fx = cx + 136;
        vis.clear(); txt.clear(); fields.clear(); rows.clear(); cells.clear(); hov.clear();
        if (indY < 0) indY = py + 46 + Math.min(tab, TABS.length - 1) * 24;

        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            hit(px + 6, py + 46 + i * 24, SW - 12, 20, () -> goTab(t));
        }
        userBtn = hit(px + 6, py + PH - 54, SW - 12, 44, () -> goTab(STATS));

        switch (tab) {
            case 0 -> initAuction();
            case 1 -> initLayout();
            case 2 -> initSounds();
            case 3 -> initGeneral();
            default -> initStats();
        }
    }

    private void goTab(int t) {
        if (tab == t) return;
        tab = t; tabAt = System.currentTimeMillis(); page = 0; resetArm = false;
        rebuildWidgets();
    }

    private void initAuction() {
        if (ALL == null) ALL = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).toList();
        EditBox search = field(cx, py + 46, 126, "Search items", vSearch, s -> { vSearch = s; page = 0; filter(); rebuildCells(); });
        field(fx, py + 80, FW, "e.g. 10m", vMin, s -> vMin = s);
        int half = (FW - 8) / 2;
        field(fx, py + 112, half, "1", vQty, s -> vQty = s);
        field(fx + half + 8, py + 112, half, "20", vTimer, s -> vTimer = s);
        field(fx, py + 144, FW, "optional", vWorth, s -> vWorth = s);
        field(fx, py + 176, FW, "optional", vNote, s -> vNote = s);

        vbtn(cx, py + 198, 22, 16, () -> "<", false, () -> { if (page > 0) { page--; rebuildCells(); } });
        vbtn(cx + 104, py + 198, 22, 16, () -> ">", false, () -> { if ((page + 1) * COLS * ROWS < filtered.size()) { page++; rebuildCells(); } });
        vbtn(fx, py + 198, FW - 48, 18, () -> "Start auction", true, this::start);
        vbtn(fx + FW - 44, py + 198, 44, 18, () -> "Cancel", false, this::onClose);

        label(fx, py + 70, "Minimum bid"); label(fx, py + 102, "Quantity"); label(fx + half + 8, py + 102, "Timer (sec)");
        label(fx, py + 134, "Worth of one item"); label(fx, py + 166, "Note on the auction card");
        filter(); rebuildCells();
        setInitialFocus(search);
    }

    private void initLayout() {
        Config c = BidBoardMod.config;
        for (int i = 0; i < 6; i++) {
            final String a = ANCHORS[i];
            zones[i] = hit(cx + (i % 3) * 40, py + 46 + (i / 3) * 34, 40, 34, () -> c.anchor = a);
        }
        stepper(cx, py + 120, 120, "Edge X", () -> c.offX, "", d -> c.offX = clamp(c.offX + d * 2, 0, 200));
        stepper(cx, py + 140, 120, "Edge Y", () -> c.offY, "", d -> c.offY = clamp(c.offY + d * 2, 0, 200));

        int sx = cx + 130, sw = 158;
        vbtn(sx, py + 46, sw, 16, () -> "Accent: " + accentName(), false, this::cycle);
        stepper(sx, py + 64, sw, "Corners", () -> c.radius, "", d -> c.radius = clamp(c.radius + d, 0, 24));
        stepper(sx, py + 82, sw, "Opacity", () -> c.bgOpacity, "%", d -> c.bgOpacity = clamp(c.bgOpacity + d * 10, 20, 100));
        vbtn(sx, py + 100, 77, 16, () -> "Worth: " + (c.showWorth ? "on" : "off"), () -> c.showWorth, () -> c.showWorth = !c.showWorth);
        vbtn(sx + 81, py + 100, 77, 16, () -> "Note: " + (c.showNote ? "on" : "off"), () -> c.showNote, () -> c.showNote = !c.showNote);
        vbtn(sx, py + 118, 77, 16, this::fontName, () -> false, this::cycleFont);
        vbtn(sx + 81, py + 118, 77, 16, () -> "Round: " + (c.fullRound ? "on" : "off"), () -> c.fullRound, () -> c.fullRound = !c.fullRound);
        vbtn(sx, py + 136, sw, 16, () -> "Card: " + cardName(), () -> false, this::cycleCard);
        label(cx + 2, py + 34, "Position"); label(sx + 2, py + 34, "Style");
    }

    private void initSounds() {
        Config c = BidBoardMod.config;
        String[] titles = {"Open menu", "Start auction", "Auction won"};
        Config.Snd[] snds = {c.sndOpen, c.sndStart, c.sndWin};
        int w = PW - SW - 16;
        for (int i = 0; i < 3; i++) {
            final Config.Snd s = snds[i];
            final String title = titles[i];
            int sy = py + 36 + i * 60;
            txt.add(new Txt(cx + 4, sy + 4, () -> title, 0xFFFFFF));
            vbtn(cx + w - 56, sy, 56, 16, () -> s.on ? "On" : "Off", () -> s.on, () -> s.on = !s.on);
            vbtn(cx, sy + 20, w - 60, 16, () -> "Sound: " + Sounds.nameOf(s.id), false, () -> { s.id = Sounds.next(s.id); Sounds.play(s, true); });
            vbtn(cx + w - 56, sy + 20, 56, 16, () -> "Play", false, () -> Sounds.play(s, true));
            stepper(cx, sy + 40, 140, "Volume", () -> s.volume, "%", d -> { s.volume = clamp(s.volume + d * 10, 0, 200); Sounds.play(s, true); });
            stepper(cx + 146, sy + 40, 140, "Pitch", () -> s.pitch, "%", d -> { s.pitch = clamp(s.pitch + d * 10, 50, 200); Sounds.play(s, true); });
        }
    }

    private void initGeneral() {
        Config c = BidBoardMod.config;
        toggleRow(0, "Payment toasts  (+ $X from name)", () -> c.showToasts, v -> c.showToasts = v);
        toggleRow(1, "Totals line  (In / Out)", () -> c.showTotals, v -> c.showTotals = v);
        toggleRow(2, "Ultra-smooth corners", () -> c.smooth, v -> c.smooth = v);
        toggleRow(3, "Auto-copy winner's name", () -> c.copyWinner, v -> c.copyWinner = v);
        int y = py + 38 + 4 * 24;
        stepper(cx, y, 140, "Toast time", () -> c.paymentSeconds, "s", d -> c.paymentSeconds = clamp(c.paymentSeconds + d * 2, 2, 60));
        stepper(cx + 146, y, 140, "Max toasts", () -> c.maxPayments, "", d -> c.maxPayments = clamp(c.maxPayments + d, 1, 8));
        vbtn(cx, py + 38 + 5 * 24 + 6, 110, 16, () -> "Reset style", false, this::resetStyle);
        label(cx + 120, py + 38 + 5 * 24 + 10, "Lifetime stats: click your skin on the left");
    }

    private void initStats() {
        vbtn(cx + 286 - 130, py + 202, 130, 16, () -> resetArm ? "Click again to confirm" : "Reset stats", () -> resetArm, () -> {
            if (resetArm) { Stats.reset(); resetArm = false; } else resetArm = true; });
    }

    // ================= helpers =================
    private Button hit(int x, int y, int w, int h, Runnable r) {
        Button b = Button.builder(Component.empty(), btn -> r.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);               // invisible, still hoverable and clickable
        addRenderableWidget(b); return b;
    }
    private void vbtn(int x, int y, int w, int h, Supplier<String> t, boolean primary, Runnable r) { vbtn(x, y, w, h, t, () -> primary, r); }
    private void vbtn(int x, int y, int w, int h, Supplier<String> t, BooleanSupplier primary, Runnable r) { vis.add(new Vis(hit(x, y, w, h, r), t, primary)); }
    private void label(int x, int y, String s) { txt.add(new Txt(x, y, () -> s, 0xA8B0B8)); }
    private void toggleRow(int i, String label, BooleanSupplier get, Consumer<Boolean> set) {
        int y = py + 38 + i * 24, w = PW - SW - 16;
        rows.add(new int[]{cx, y, w, 18});
        txt.add(new Txt(cx + 8, y + 5, () -> label, 0xFFFFFF));
        vbtn(cx + w - 56, y + 1, 52, 16, () -> get.getAsBoolean() ? "On" : "Off", get, () -> set.accept(!get.getAsBoolean()));
    }
    private void stepper(int x, int y, int w, String name, IntSupplier v, String unit, IntConsumer change) {
        rows.add(new int[]{x, y, w, 16});
        txt.add(new Txt(x + 5, y + 4, () -> name + "  " + v.getAsInt() + unit, 0xFFFFFF));
        vbtn(x + w - 38, y, 18, 16, () -> "-", false, () -> change.accept(-1));
        vbtn(x + w - 18, y, 18, 16, () -> "+", false, () -> change.accept(1));
    }
    private EditBox field(int x, int y, int w, String hint, String init, Consumer<String> on) {
        fields.add(new int[]{x, y, w, 18});
        EditBox b = new EditBox(font, x + 6, y + 5, w - 12, 8, Component.literal(hint)); // unbordered boxes draw text at their top edge; 5px down = centered in the 18px field
        b.setBordered(false); b.setHint(Component.literal(hint)); b.setValue(init); b.setResponder(on);
        addRenderableWidget(b);
        hit(x, y, w, 18, () -> setFocused(b)); // clicking anywhere in the rounded box focuses it
        return b;
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int mix(int a, int b, float t) {
        int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (rr << 16) | (gg << 8) | bb;
    }
    private float hover(Button b) {
        float v = hov.getOrDefault(b, 0f); v += ((b.isHoveredOrFocused() ? 1f : 0f) - v) * 0.25f; hov.put(b, v); return v;
    }
    private static String pick(String[] ids, String[] names, String cur, String dflt) {
        for (int k = 0; k < ids.length; k++) if (ids[k].equals(cur)) return names[k];
        return dflt;
    }
    private static String nextOf(String[] ids, String cur) {
        int i = 0;
        for (int k = 0; k < ids.length; k++) if (ids[k].equals(cur)) i = k;
        return ids[(i + 1) % ids.length];
    }
    private String accentName() {
        for (int k = 0; k < COLORS.length; k++) if (COLORS[k].equalsIgnoreCase(BidBoardMod.config.accent)) return NAMES[k];
        return "Custom";
    }
    private void cycle() {
        Config c = BidBoardMod.config; int i = 0;
        for (int k = 0; k < COLORS.length; k++) if (COLORS[k].equalsIgnoreCase(c.accent)) i = k;
        c.accent = COLORS[(i + 1) % COLORS.length];
    }
    private String fontName() { return pick(FONTS, FNAMES, BidBoardMod.config.font, "Minecraft"); }
    private void cycleFont() { BidBoardMod.config.font = nextOf(FONTS, BidBoardMod.config.font); }
    private String cardName() { return pick(CARDS, CNAMES, BidBoardMod.config.cardStyle, "Classic"); }
    private void cycleCard() { BidBoardMod.config.cardStyle = nextOf(CARDS, BidBoardMod.config.cardStyle); }

    private void resetStyle() {
        Config c = BidBoardMod.config, d = new Config();
        c.accent = d.accent; c.bg = d.bg; c.radius = d.radius; c.bgOpacity = d.bgOpacity; c.anchor = d.anchor;
        c.offX = d.offX; c.offY = d.offY; c.showWorth = d.showWorth; c.showNote = d.showNote; c.font = d.font;
        c.fullRound = d.fullRound; c.cardStyle = d.cardStyle;
    }

    private static Tracker.Lot makeSample() {
        Tracker.Lot s = new Tracker.Lot();
        s.stack = new ItemStack(Items.DIAMOND_SWORD); s.qty = 5; s.min = 200_000_000; s.worth = 104_000_000;
        s.topBidder = "Brillz_81"; s.topBid = 289_000_000;
        s.note = "15 Seconds To Make Order"; s.createdAt = System.currentTimeMillis() - 1000;
        return s;
    }

    // ================= auction logic =================
    private void filter() {
        String q = vSearch.toLowerCase().trim();
        filtered = ALL.stream().filter(i -> q.isEmpty()
            || BuiltInRegistries.ITEM.getKey(i).getPath().contains(q.replace(' ', '_'))
            || i.getDefaultInstance().getHoverName().getString().toLowerCase().contains(q)).toList();
    }

    private void rebuildCells() {
        for (Button b : cells) removeWidget(b);
        cells.clear();
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS && start + i < filtered.size(); i++) {
            Item it = filtered.get(start + i);
            Button b = Button.builder(Component.empty(), btn -> picked = it)
                .bounds(cx + (i % COLS) * CELL, py + 68 + (i / COLS) * CELL, CELL, CELL).build();
            b.setAlpha(0f);
            cells.add(b); addRenderableWidget(b);
        }
    }

    private void start() {
        Minecraft mc = Minecraft.getInstance();
        if (picked == null) { if (mc.player != null) mc.player.displayClientMessage(Component.literal("Pick an item first"), true); return; }
        int q = (int) Math.max(1, Tracker.parse(vQty));
        int t = (int) Math.max(0, Tracker.parse(vTimer));
        Tracker.begin(new ItemStack(picked), q, Tracker.parse(vMin), Tracker.parse(vWorth), vNote.trim(), t);
        String cmd = BidBoardMod.config.startCommand.replace("{min}", vMin.trim()).replace("{qty}", String.valueOf(q))
            .replace("{timer}", String.valueOf(t)).replace("{note}", vNote.trim()).trim();
        if (!cmd.isEmpty() && mc.player != null) mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        String msg = BidBoardMod.config.startMessage.replace("{item}", picked.getDefaultInstance().getHoverName().getString())
            .replace("{qty}", String.valueOf(q)).replace("{min}", vMin.trim()).replace("{timer}", String.valueOf(t))
            .replace("{note}", vNote.trim()).trim();
        if (!msg.isEmpty() && mc.player != null) mc.player.connection.sendChat(msg);
        Sounds.play(BidBoardMod.config.sndStart);
        onClose();
    }

    // ================= drawing =================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        Config c = BidBoardMod.config; int r = c.radius, accent = c.accentRgb();
        Draw.rrect(g, px, py, PW, PH, r + 3, Draw.a(c.bgRgb(), 0.95f));
        Draw.rrect(g, px + 4, py + 4, SW, PH - 8, r + 1, Draw.a(0x000000, 0.30f));
        Draw.text(g, font, "Crit Auctions", px + 12, py + 14, Draw.a(accent, 1f), true);

        if (tab < TABS.length) { // sliding tab indicator
            float target = py + 46 + tab * 24; indY += (target - indY) * 0.3f;
            Draw.rrect(g, px + 6, (int) indY, SW - 12, 20, r, Draw.a(accent, 0.16f));
            Draw.rrect(g, px + 6, (int) indY + 4, 2, 12, 1, Draw.a(accent, 1f));
        }

        Draw.rrect(g, cx, py + 8, PW - SW - 16, 20, r + 1, Draw.a(0xFFFFFF, 0.06f));
        String who = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "";
        Draw.text(g, font, "Hello, " + who, cx + 8, py + 14, Draw.a(0xFFFFFF, 1f), false);

        for (int[] f : fields) Draw.rrect(g, f[0], f[1], f[2], f[3], Math.min(r + 1, 9), Draw.a(0xFFFFFF, 0.07f));
        if (tab == 0) {
            Draw.rrect(g, fx, py + 34, FW, 30, r + 1, Draw.a(0xFFFFFF, 0.06f));
            Draw.rrect(g, fx + 3, py + 37, 24, 24, r, Draw.a(0x000000, 0.28f));
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        Config c = BidBoardMod.config; int r = c.radius, accent = c.accentRgb();
        long now = System.currentTimeMillis();

        for (int i = 0; i < TABS.length; i++)
            Draw.text(g, font, TABS[i], px + 16, py + 46 + i * 24 + 6, Draw.a(i == tab ? 0xFFFFFF : 0xA8B0B8, 1f), false);
        drawUserCard(g, r, accent);
        for (int[] rw : rows) Draw.rrect(g, rw[0], rw[1], rw[2], rw[3], Math.min(r + 1, 8), Draw.a(0xFFFFFF, 0.04f));

        switch (tab) {
            case 0 -> drawAuction(g, accent);
            case 1 -> drawLayout(g, c, accent, now);
            case STATS -> drawStats(g, accent);
            default -> {}
        }

        for (Vis v : vis) {
            Button b = v.b(); float h = hover(b); int bh = b.getHeight(); boolean prim = v.primary().getAsBoolean();
            int rad = Math.min(bh / 2, r + 2);
            if (prim) Draw.rrect(g, b.getX(), b.getY(), b.getWidth(), bh, rad, Draw.a(mix(accent, 0xFFFFFF, h * 0.25f), 0.95f));
            else Draw.rrect(g, b.getX(), b.getY(), b.getWidth(), bh, rad, Draw.a(0xFFFFFF, 0.07f + 0.09f * h));
            String s = v.text().get();
            Draw.text(g, font, s, b.getX() + (b.getWidth() - Draw.w(font, s)) / 2, b.getY() + (bh - 8) / 2,
                Draw.a(prim ? 0x10141A : 0xFFFFFF, 1f), false);
        }
        for (Txt t : txt) Draw.text(g, font, t.t().get(), t.x(), t.y(), Draw.a(t.rgb(), 1f), false);

        float e = Draw.easeOut((now - openedAt) / 240f);
        if (e < 1f) Draw.rrect(g, px, py, PW, PH, r + 3, Draw.a(c.bgRgb(), 1f - e));
        if (tabAt > 0) {
            float e2 = Draw.easeOut((now - tabAt) / 160f);
            if (e2 < 1f) Draw.rrect(g, cx - 4, py + 32, PW - SW - 12, PH - 36, r, Draw.a(c.bgRgb(), 1f - e2));
        }
    }

    /** Skin head, rank line, online dot and your full name. Click it for lifetime stats. */
    private void drawUserCard(GuiGraphics g, int r, int accent) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        int uy = py + PH - 54;
        float h = hover(userBtn);
        Draw.rrect(g, px + 6, uy, SW - 12, 44, Math.min(r + 1, 10),
            tab == STATS ? Draw.a(accent, 0.20f) : Draw.a(0xFFFFFF, 0.06f + 0.07f * h));
        Draw.head(g, px + 10, uy + 5, 20);
        Draw.text(g, font, BidBoardMod.config.subtitle, px + 34, uy + 10, Draw.a(0xA8B0B8, 1f), false);
        Draw.rrect(g, px + SW - 17, uy + 11, 5, 5, 2, Draw.a(0x4ADE80, 1f));
        int max = SW - 12 - 8;
        String n = name;
        if (Draw.w(font, n) > max) {
            while (n.length() > 1 && Draw.w(font, n + "..") > max) n = n.substring(0, n.length() - 1);
            n += "..";
        }
        Draw.text(g, font, n, px + 10, uy + 31, Draw.a(0xFFFFFF, 1f), false);
    }

    private void drawStats(GuiGraphics g, int accent) {
        Stats s = Stats.get();
        int r = Math.min(BidBoardMod.config.radius + 1, 10), W = PW - SW - 16;
        Draw.text(g, font, "Lifetime stats", cx + 4, py + 36, Draw.a(0xFFFFFF, 1f), true);
        int half = (W - 8) / 2;
        statCard(g, cx, py + 50, half, 46, r, "Earnings (received)", Tracker.fmt(s.earned), 0x5BE37D);
        statCard(g, cx + half + 8, py + 50, half, 46, r, "Losses (paid out)", Tracker.fmt(s.lost), 0xFF6B6B);
        double net = s.earned - s.lost;
        statCard(g, cx, py + 102, W, 40, r, "Net", (net >= 0 ? "+" : "-") + Tracker.fmt(Math.abs(net)), net >= 0 ? 0x5BE37D : 0xFF6B6B);
        Draw.text(g, font, "Payments received: " + s.received + "     sent: " + s.sent + "     auctions started: " + s.auctions,
            cx + 4, py + 152, Draw.a(0xFFFFFF, 1f), false);
        Draw.text(g, font, "Biggest payment in: " + Tracker.fmt(s.biggestIn) + "     out: " + Tracker.fmt(s.biggestOut),
            cx + 4, py + 166, Draw.a(0xA8B0B8, 1f), false);
        Draw.text(g, font, "Counts every payment the mod sees, auction or not.", cx + 4, py + 184, Draw.a(0xA8B0B8, 1f), false);
    }

    private void statCard(GuiGraphics g, int x, int y, int w, int h, int r, String label, String value, int rgb) {
        Draw.rrect(g, x, y, w, h, r, Draw.a(0xFFFFFF, 0.06f));
        Draw.text(g, font, label, x + 8, y + 7, Draw.a(0xA8B0B8, 1f), false);
        Draw.textScaled(g, font, value, x + 8, y + h - 20, Draw.a(rgb, 1f), 2f, true);
    }

    private void drawAuction(GuiGraphics g, int accent) {
        Draw.rrect(g, cx + 2, py + 37, 4, 4, 2, Draw.a(accent, 1f));
        Draw.text(g, font, "New Auction", cx + 10, py + 35, Draw.a(0xFFFFFF, 1f), true);
        String n = filtered.size() + " items";
        Draw.text(g, font, n, cx + 126 - Draw.w(font, n), py + 35, Draw.a(0xA8B0B8, 1f), false);
        Draw.text(g, font, "Page " + (page + 1), cx + 48, py + 202, Draw.a(0xA8B0B8, 1f), false);

        if (picked != null) g.renderItem(new ItemStack(picked), fx + 7, py + 41);
        String pn = picked == null ? "No item yet" : picked.getDefaultInstance().getHoverName().getString();
        if (pn.length() > 20) pn = pn.substring(0, 19) + "..";
        Draw.text(g, font, pn, fx + 33, py + 39, Draw.a(picked == null ? 0xFFFFFF : accent, 1f), true);
        if (picked == null) Draw.text(g, font, "Pick one on the left", fx + 33, py + 50, Draw.a(0xA8B0B8, 1f), false);

        for (int i = 0; i < cells.size(); i++) {
            Item it = filtered.get(page * COLS * ROWS + i); Button b = cells.get(i);
            float h = hover(b);
            Draw.rrect(g, b.getX() + 1, b.getY() + 1, CELL - 2, CELL - 2, 3, Draw.a(it == picked ? accent : 0xFFFFFF, it == picked ? 0.38f : 0.05f + 0.12f * h));
            g.renderItem(it.getDefaultInstance(), b.getX() + 1, b.getY() + 1);
        }
    }

    private void drawLayout(GuiGraphics g, Config c, int accent, long now) {
        Draw.rrect(g, cx, py + 46, 120, 68, 4, Draw.a(0xFFFFFF, 0.05f));
        for (int i = 0; i < 6; i++) {
            Button z = zones[i]; float h = hover(z); boolean sel = ANCHORS[i].equals(c.anchor);
            int zx = z.getX(), zy = z.getY();
            if (sel) Draw.rrect(g, zx + 1, zy + 1, 38, 32, 3, Draw.a(accent, 0.20f));
            else if (h > 0.02f) Draw.rrect(g, zx + 1, zy + 1, 38, 32, 3, Draw.a(0xFFFFFF, 0.07f * h));
            if (sel) {
                int mx = ANCHORS[i].endsWith("L") ? zx + 3 : ANCHORS[i].endsWith("R") ? zx + 39 - 28 : zx + 6;
                int my = ANCHORS[i].startsWith("T") ? zy + 3 : zy + 34 - 12;
                Draw.rrect(g, mx, my, 28, 9, 3, Draw.a(accent, 1f));
            }
        }
        sample.startedAt = now - now % 12000; sample.endsAt = sample.startedAt + 12000; // loops so you see the timer move
        Hud.drawCard(g, sample, cx, py + 164, now);
    }

    @Override
    public void onClose() { BidBoardMod.config.save(); super.onClose(); }
}
