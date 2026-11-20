package win.vampyuyu.survival.breath;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.HubBreach;
import win.vampyuyu.survival.island.IslandService;

/**
 * An invasion of the hub happens in the Breach only ({@link HubBreach}): the rest of the hub is a safe zone where no
 * player can be hurt, so immune cells there were no challenge. Cells spawn on the islet, for the players standing on it,
 * and a cell that leaves it (onto the bridge or the hub) is removed.
 */
final class BreachSpawns {
    private BreachSpawns() {}

    static boolean isHub(World world) {
        return world != null && SkyblockWorlds.HUB.equals(world.getName());
    }

    /** The y of the hub surface (the island feature's {@code hub.y}). */
    static int hubY(Core core) {
        return core.service(IslandApi.class).filter(IslandService.class::isInstance)
            .map(api -> ((IslandService) api).config().hubY).orElse(100);
    }

    /** A spawn spot on the islet, standing on its surface. */
    static Location spot(World hub, int hubY, Random rng) {
        int[] column = HubBreach.randomColumn(rng);
        return new Location(hub, column[0] + 0.5, hubY + 1, column[1] + 0.5);
    }

    /** Removes the hub's immune cells that left the Breach. */
    static void cull(Set<UUID> invaders, World hub, int hubY) {
        for (Iterator<UUID> it = invaders.iterator(); it.hasNext();) {
            Entity e = Bukkit.getEntity(it.next());
            if (e != null && e.getWorld() == hub && !HubBreach.contains(e.getLocation(), hubY)) {
                e.remove();
                it.remove();
            }
        }
    }
}
