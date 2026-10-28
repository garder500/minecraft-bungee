package win.vampyuyu.survival.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Islands and the skyblock world grid. Implemented by the {@code island} feature. */
public interface IslandApi {
    /** The island whose area contains the location, if any. */
    Optional<IslandInfo> islandAt(Location location);

    /** The island the player owns or belongs to, if any. */
    Optional<IslandInfo> islandOf(UUID player);

    /** True for {@code sb_hub}, {@code sb_world}, {@code sb_nether}, {@code sb_end} and dungeon worlds. */
    boolean isSkyblockWorld(World world);

    /** True when the player may place and break blocks at the location (island member, or admin bypass). */
    boolean canBuild(Player player, Location location);

    /** Teleports the player to their island spawn. False when they have no island or the teleport was refused. */
    boolean teleportHome(Player player);

    /** Every known island (snapshot). */
    Collection<IslandInfo> all();
}
