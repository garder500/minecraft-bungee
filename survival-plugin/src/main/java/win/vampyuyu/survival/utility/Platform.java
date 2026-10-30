package win.vampyuyu.survival.utility;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The two asynchronous Paper calls the feature needs (chunk loading and teleporting), in one place. They are
 * seams: MockBukkit does not implement them, so tests replace the hooks with synchronous versions.
 */
final class Platform {
    /** A chunk to load. */
    record At(World world, int x, int z) {}

    /** Replaced by tests only. */
    static volatile Function<At, CompletableFuture<Chunk>> chunkLoader =
        at -> at.world().getChunkAtAsync(at.x(), at.z(), true);

    /** Replaced by tests only. */
    static volatile BiFunction<Player, Location, CompletableFuture<Boolean>> mover =
        (player, location) -> player.teleportAsync(location);

    private Platform() {}

    /** Loads (and generates) the chunk without blocking the main thread; completes on the main thread. */
    static CompletableFuture<Chunk> loadChunk(World world, int blockX, int blockZ) {
        return chunkLoader.apply(new At(world, blockX >> 4, blockZ >> 4));
    }

    static CompletableFuture<Boolean> teleport(Player player, Location location) {
        return mover.apply(player, location);
    }

    /** Test helper: synchronous versions. */
    static void useSynchronous() {
        chunkLoader = at -> CompletableFuture.completedFuture(at.world().getChunkAt(at.x(), at.z()));
        mover = (player, location) -> CompletableFuture.completedFuture(player.teleport(location));
    }

    static void useReal() {
        chunkLoader = at -> at.world().getChunkAtAsync(at.x(), at.z(), true);
        mover = (player, location) -> player.teleportAsync(location);
    }
}
