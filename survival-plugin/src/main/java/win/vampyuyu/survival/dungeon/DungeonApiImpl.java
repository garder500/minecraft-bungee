package win.vampyuyu.survival.dungeon;

import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.dungeon.run.RunManager;

/**
 * {@link DungeonApi} for the other features (no flight, no teleport tricks). It is world based on purpose: anybody
 * standing in a {@code sb_dungeon_*} world counts, participant or not (admins, ghosts, leftovers of a crash).
 */
public final class DungeonApiImpl implements DungeonApi {
    @Override
    public boolean inDungeon(Player player) {
        return player != null && RunManager.inDungeonWorld(player);
    }
}
