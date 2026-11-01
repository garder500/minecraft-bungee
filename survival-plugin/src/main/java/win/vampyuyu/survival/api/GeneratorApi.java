package win.vampyuyu.survival.api;

import java.util.UUID;
import org.bukkit.entity.Player;

/** The cobble generator tier of a player (1 to 4). Implemented by the {@code quarry} feature. */
public interface GeneratorApi {
    int tier(UUID player);

    /** Generator blocks the player has broken so far (a player who started before the quest plan keeps this). */
    long blocksMined(UUID player);

    /** How many of an ore's item the player carries (ore ids as in the {@code ORE_FOUND} quests). */
    int oreHeld(Player player, String ore);

    /** Removes up to {@code max} of an ore's item from the player's inventory; returns how many were removed. */
    int takeOre(Player player, String ore, int max);
}
