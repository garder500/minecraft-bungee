package win.vampyuyu.survival.dungeon;

import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.dungeon.run.DungeonRun;
import win.vampyuyu.survival.dungeon.run.RunListener;
import win.vampyuyu.survival.dungeon.run.RunManager;

/**
 * {@link DungeonApi} for the other features (no flight, no teleport tricks). A player is "in the dungeon" when they
 * stand in a {@code sb_dungeon_*} world AND take part in a run (admins standing there count too). A stray (a login
 * into a dungeon world without a run, after a crash) is not in the dungeon: the join router may send them away, and
 * {@code /dungeon leave} also gets them out.
 */
public final class DungeonApiImpl implements DungeonApi {
    private final RunManager runs;

    public DungeonApiImpl(RunManager runs) {
        this.runs = runs;
    }

    @Override
    public boolean inDungeon(Player player) {
        if (player == null || !RunManager.inDungeonWorld(player)) {
            return false;
        }
        DungeonRun run = runs.runOf(player.getUniqueId());
        return (run != null && run.isParticipant(player.getUniqueId())) || player.hasPermission(RunListener.ADMIN);
    }
}
