package win.vampyuyu.survival.api;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Bosses. Implemented by the {@code boss} feature. */
public interface BossApi {
    /**
     * Spawns a boss.
     *
     * @param bossId one of {@link #bossIds()}
     * @param summoner who triggered it, may be null (natural spawn, admin command)
     * @return false when the id is unknown or the spawn was refused
     */
    boolean spawn(String bossId, Location location, @Nullable Player summoner);

    /** Ids of every registered boss. */
    Collection<String> bossIds();

    /** Registers a hook that adds loot to every boss kill. Hooks live until the plugin disables. */
    void registerLootHook(BossLootHook hook);
}
