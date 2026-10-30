package win.vampyuyu.survival.dungeon.run;

import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.dungeon.Leaderboard;
import win.vampyuyu.survival.dungeon.party.PartyService;

/** Everything a run needs from the feature: services, configuration and lazy lookups of the optional features. */
public final class RunContext {
    public final Core core;
    public final Keys keys;
    public final MobFactory mobFactory;
    public final Leaderboard leaderboard;
    public final PartyService parties;
    public volatile DungeonConfig cfg;
    /** Players with a hub teleport in flight (uuid -> start millis): see {@link RunManager#sendOut}. */
    final java.util.Map<java.util.UUID, Long> exiting = new java.util.concurrent.ConcurrentHashMap<>();
    /** Set while the feature itself teleports a player, so the teleport guard lets it through. */
    private boolean teleporting;

    public RunContext(Core core, DungeonConfig cfg, Keys keys, Leaderboard leaderboard, PartyService parties) {
        this.core = core;
        this.cfg = cfg;
        this.keys = keys;
        this.mobFactory = new MobFactory(keys);
        this.leaderboard = leaderboard;
        this.parties = parties;
    }

    public Scheduler scheduler() { return core.scheduler(); }

    public Logger log() { return core.getPlugin().getLogger(); }

    private final java.util.Map<Class<?>, Optional<?>> overrides = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * TESTS ONLY: makes {@code type} resolve to {@code impl} (or to "absent" when null) for this feature, whatever the
     * other features registered. {@code Core.register} refuses a second implementation, so tests of the dungeon cannot
     * use it once the real island, economy or boss features are merged.
     */
    public <T> void override(Class<T> type, T impl) {
        overrides.put(type, Optional.ofNullable(impl));
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> lookup(Class<T> type) {
        Optional<?> forced = overrides.get(type);
        return forced != null ? (Optional<T>) forced : core.service(type);
    }

    // lazy lookups: a later feature may be absent or may have failed
    public Optional<IslandApi> islands() { return lookup(IslandApi.class); }

    public Optional<EconomyApi> economy() { return lookup(EconomyApi.class); }

    public Optional<SkillsApi> skills() { return lookup(SkillsApi.class); }

    public Optional<BossApi> bosses() { return lookup(BossApi.class); }

    /** True while {@link #teleport} is running. */
    public boolean teleporting() { return teleporting; }

    /** Runs an action that teleports players (another feature's code) without the teleport guard interfering. */
    public boolean teleportWith(java.util.function.BooleanSupplier action) {
        boolean before = teleporting;
        teleporting = true;
        try {
            return action.getAsBoolean();
        } finally {
            teleporting = before;
        }
    }

    /** Teleports through the feature's own door: the teleport guard does not cancel it. */
    public boolean teleport(Player player, Location to) {
        if (player == null || !player.isOnline() || to == null || to.getWorld() == null) {
            return false;
        }
        teleporting = true;
        try {
            return player.teleport(to);
        } finally {
            teleporting = false;
        }
    }
}
