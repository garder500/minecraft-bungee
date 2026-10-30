package win.vampyuyu.survival.api;

import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Read-only snapshot of an island.
 *
 * @param id unique island id
 * @param owner owner's player UUID
 * @param members every co-op member, owner included
 * @param world world name (see {@code SkyblockWorlds})
 * @param cellX grid cell index on X (island centre = cell * spacing)
 * @param cellZ grid cell index on Z
 * @param center spawn centre of the island
 * @param radius build radius in blocks around the centre
 */
public record IslandInfo(UUID id, UUID owner, Set<UUID> members, String world, int cellX, int cellZ,
        Location center, int radius) {
    public IslandInfo {
        members = Set.copyOf(members);
    }
}
