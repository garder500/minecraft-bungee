package win.vampyuyu.survival.minions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;

/** One square island of radius 50 around the origin of {@code sb_world}. */
final class FakeIslands implements IslandApi {
    UUID id = UUID.randomUUID();
    UUID owner;
    Set<UUID> members;
    boolean exists = true;

    FakeIslands(UUID owner) {
        this.owner = owner;
        this.members = Set.of(owner);
    }

    @Override
    public Optional<IslandInfo> islandAt(Location l) {
        if (!exists || l.getWorld() == null || !l.getWorld().getName().equals("sb_world")
                || Math.abs(l.getBlockX()) > 50 || Math.abs(l.getBlockZ()) > 50) {
            return Optional.empty();
        }
        return Optional.of(new IslandInfo(id, owner, members, "sb_world", 0, 0, new Location(l.getWorld(), 0, 64, 0), 50));
    }

    @Override
    public Optional<IslandInfo> islandOf(UUID player) {
        return Optional.empty();
    }

    @Override
    public boolean isSkyblockWorld(World world) {
        return world.getName().equals("sb_world");
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return members.contains(player.getUniqueId());
    }

    @Override
    public boolean teleportHome(Player player) {
        return false;
    }

    @Override
    public boolean teleportHub(Player player) {
        return false;
    }

    @Override
    public Collection<IslandInfo> all() {
        return List.of();
    }
}
