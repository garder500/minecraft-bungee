package win.vampyuyu.survival.boss;

import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.BossLootHook;

/** The published {@link BossApi}: no summon rules, any world, works without a summoner (dungeons). */
public final class BossApiImpl implements BossApi {
    private final BossManager manager;

    public BossApiImpl(BossManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean spawn(String bossId, Location location, @Nullable Player summoner) {
        if (bossId == null || location == null) {
            return false;
        }
        return manager.spawn(bossId, location, summoner);
    }

    @Override
    public Collection<String> bossIds() {
        return List.copyOf(manager.config().bosses().keySet());
    }

    @Override
    public void registerLootHook(BossLootHook hook) {
        if (hook != null) {
            manager.addHook(hook);
        }
    }
}
