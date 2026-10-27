package com.bidboard;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier; // if this fails to resolve, use net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

public class BidBoardMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("bidboard");
    public static Config config;
    private static KeyMapping openKey, layoutKey;
    private static Supplier<Screen> pending; // screens open on the next tick so the chat box can't close them

    @Override
    public void onInitializeClient() {
        config = Config.load();
        Stats.load();
        Tracker.compile(config);

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> Tracker.onMessage(msg.getString())); // chat + action bar (duplicates are ignored)
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, time) -> Tracker.onMessage(msg.getString()));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bidboard", "overlay"), Hud::render);

        // Shows up in Options > Controls > Key Binds under "BidBoard", so it can be rebound there.
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bidboard", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bidboard.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        layoutKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bidboard.layout",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), cat)); // unbound by default

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.screen == null) {
                while (openKey.consumeClick()) mc.setScreen(new MenuScreen(0));
                while (layoutKey.consumeClick()) mc.setScreen(new MenuScreen(1));
            }
            if (pending != null) { Screen s = pending.get(); pending = null; mc.setScreen(s); }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            { for (String root : new String[]{"crit", "bidboard"}) dispatcher.register(ClientCommandManager.literal(root)
                .executes(ctx -> { pending = () -> new MenuScreen(0); return 1; })
                .then(ClientCommandManager.literal("style").executes(ctx -> { pending = () -> new MenuScreen(1); return 1; }))
                .then(ClientCommandManager.literal("sounds").executes(ctx -> { pending = () -> new MenuScreen(2); return 1; }))
                .then(ClientCommandManager.literal("general").executes(ctx -> { pending = () -> new MenuScreen(3); return 1; }))
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    config.enabled = !config.enabled; config.save();
                    ctx.getSource().sendFeedback(Component.literal("Crit Auctions " + (config.enabled ? "on" : "off")));
                    return 1; }))
                .then(ClientCommandManager.literal("last").executes(ctx -> {
                    if (Tracker.recent.isEmpty()) ctx.getSource().sendFeedback(Component.literal("No payment/bid-looking lines seen yet"));
                    for (String s : Tracker.recent) ctx.getSource().sendFeedback(Component.literal("> " + s));
                    return 1; }))
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    Tracker.reset(); ctx.getSource().sendFeedback(Component.literal("Crit Auctions cleared")); return 1; }))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    config = Config.load(); Tracker.compile(config);
                    ctx.getSource().sendFeedback(Component.literal("Crit Auctions config reloaded")); return 1; }))
                .then(ClientCommandManager.literal("move")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0))
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0)).executes(ctx -> {
                            config.offX = IntegerArgumentType.getInteger(ctx, "x");
                            config.offY = IntegerArgumentType.getInteger(ctx, "y");
                            config.save(); return 1; })))));
            });
    }
}
