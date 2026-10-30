package win.vampyuyu.survival.island;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Locations other features may need. Published with {@code core.register(IslandAltarApi.class, ...)}. Because
 * features must not import each other's packages, a consumer outside {@code island} can instead use the documented
 * convention (see docs/skyblock-notes/island.md): the altar of an island is in {@code sb_end}, at
 * {@code x = cellX * cellSize + 0.5}, {@code z = cellZ * cellSize + 0.5}, {@code y = island-y + 1}.
 */
public interface IslandAltarApi {
    /** The dragon altar spot (centre of the 5x5 obsidian slab) in the island's own End. */
    Optional<Location> dragonAltar(UUID islandId);

    /** The altar of the End cell the location lies in (empty outside an island End). */
    Optional<Location> dragonAltarAt(Location anywhereInTheEnd);

    /** Spawn of the island's Nether platform / End platform. */
    Optional<Location> netherSpawn(UUID islandId);

    Optional<Location> endSpawn(UUID islandId);

    /** Hub spawn (a standing position on the spawn pad). */
    Optional<Location> hubSpawn();

    /** NPC pads in the hub by id (merchant, banker, reforger, enchanter, quest_master, minion_vendor, boss_arena,
     * fairy_souls, island_gate, dungeon_gate): the location is the block to stand an NPC on. Empty until built. */
    Map<String, Location> hubPads();
}
