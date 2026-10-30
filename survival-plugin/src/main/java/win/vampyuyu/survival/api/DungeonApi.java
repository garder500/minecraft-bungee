package win.vampyuyu.survival.api;

import org.bukkit.entity.Player;

/** Dungeons. Implemented by the {@code dungeon} feature. */
public interface DungeonApi {
    /** True while the player is inside a dungeon run. */
    boolean inDungeon(Player player);
}
