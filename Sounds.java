package com.bidboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Plays the configurable UI sounds. Any vanilla sound id can also be typed into config/bidboard.json. */
public final class Sounds {
    public static final String[] IDS = {
        "minecraft:ui.toast.in", "minecraft:ui.button.click", "minecraft:entity.experience_orb.pickup",
        "minecraft:entity.player.levelup", "minecraft:ui.toast.challenge_complete", "minecraft:block.note_block.bell",
        "minecraft:block.note_block.chime", "minecraft:block.note_block.pling", "minecraft:block.amethyst_block.chime",
        "minecraft:entity.arrow.hit_player", "minecraft:block.anvil.place", "minecraft:entity.villager.yes",
        "minecraft:block.beacon.activate", "minecraft:entity.firework_rocket.blast", "minecraft:block.bell.use"};
    public static final String[] NAMES = {
        "Toast", "Click", "Orb", "Level up", "Fanfare", "Bell", "Chime", "Pling", "Crystal", "Ding", "Anvil",
        "Villager", "Beacon", "Firework", "Big bell"};

    private Sounds() {}

    public static String nameOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return "Custom";
    }

    public static String next(String id) {
        int i = -1;
        for (int k = 0; k < IDS.length; k++) if (IDS[k].equals(id)) i = k;
        return IDS[(i + 1) % IDS.length];
    }

    public static void play(Config.Snd s) { play(s, false); }

    /** force = play even if the sound is switched off (used by the Play / preview buttons). */
    public static void play(Config.Snd s, boolean force) {
        if (s == null || (!s.on && !force) || s.volume <= 0) return;
        try {
            SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.parse(s.id));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, s.pitch / 100f, s.volume / 100f));
        } catch (Exception e) { BidBoardMod.LOGGER.warn("Bad sound id: {}", s.id); }
    }
}
