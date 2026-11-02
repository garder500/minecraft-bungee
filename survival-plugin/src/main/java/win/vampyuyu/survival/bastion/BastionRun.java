package win.vampyuyu.survival.bastion;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.core.Scheduler;

/** One live Bastion instance. Main thread only. */
final class BastionRun {
    final int number;
    final String worldName;
    final BastionConfig.Bastion bastion;
    final UUID leader;
    final UUID partyId;
    /** Players taking part: they are rewarded. A player who leaves or quits is removed. */
    final Set<UUID> members = new LinkedHashSet<>();
    final Set<UUID> eliminated = new LinkedHashSet<>();
    final Map<UUID, Integer> deaths = new HashMap<>();
    final Map<UUID, Location> returns = new HashMap<>();
    final java.util.List<Scheduler.Handle> handles = new java.util.ArrayList<>();
    World world;
    BossApi.Fight fight;
    boolean opened;
    boolean finished;
    boolean closed;
    long openedAt;
    /** Raid: radius of the platform that is still standing, -1 until the arena starts to fall. */
    int collapseRadius = -1;
    /** Raid: the next ring falls when the tick counter reaches this. */
    long nextCollapse;
    long tickCount;

    BastionRun(int number, BastionConfig.Bastion bastion, UUID leader, UUID partyId) {
        this.number = number;
        this.worldName = win.vampyuyu.survival.core.SkyblockWorlds.BASTION_PREFIX + number;
        this.bastion = bastion;
        this.leader = leader;
        this.partyId = partyId;
    }

    boolean fighting(UUID id) {
        return members.contains(id) && !eliminated.contains(id);
    }

    boolean anyFighting() {
        for (UUID id : members) {
            if (!eliminated.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
