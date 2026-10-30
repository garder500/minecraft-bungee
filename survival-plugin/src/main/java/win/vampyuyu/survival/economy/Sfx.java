package win.vampyuyu.survival.economy;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Vanilla sounds for the economy menus and messages (custom sounds are out of reach for features). */
final class Sfx {
    private Sfx() {}

    private static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (RuntimeException | LinkageError ignored) {
            // no sound is never a reason to fail a trade
        }
    }

    /** A trade, a payout or a claim went through. */
    static void success(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    /** An action was refused. */
    static void error(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    /** A menu button was pressed. */
    static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /** Something happened while the player was doing something else (an order filled, an outbid). */
    static void notice(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
    }

    /** Coins moved in or out of the bank. */
    static void coins(Player player) {
        play(player, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.8f, 1.1f);
    }
}
