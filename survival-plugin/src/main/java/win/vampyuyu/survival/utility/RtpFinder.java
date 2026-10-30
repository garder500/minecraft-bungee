package win.vampyuyu.survival.utility;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.HeightMap;

/** Finds a random safe place for /rtp. Chunks are loaded asynchronously, the block checks run on the main thread. */
public final class RtpFinder {
    private final Random random;

    public RtpFinder(Random random) {
        this.random = random;
    }

    /** A uniformly distributed point of the ring between {@code min} and {@code max} blocks around the centre. */
    public static int[] randomPoint(Random random, double centerX, double centerZ, double min, double max) {
        double radius = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        double angle = random.nextDouble() * Math.PI * 2;
        return new int[] {(int) Math.floor(centerX + Math.cos(angle) * radius),
            (int) Math.floor(centerZ + Math.sin(angle) * radius)};
    }

    /** Pure rule for a column: solid non-hazard ground that is not a liquid, free feet and head. */
    public static boolean usable(Material ground, boolean groundSolid, Material feet, boolean feetFree,
            Material head, boolean headFree) {
        return SafeSpot.standable(ground, groundSolid, feet, feetFree, head, headFree)
            && ground != Material.WATER && ground != Material.ICE && ground != Material.PACKED_ICE
            && ground != Material.BLUE_ICE && ground != Material.FROSTED_ICE
            && !ground.name().endsWith("_LEAVES") && !ground.name().contains("PORTAL");
    }

    /**
     * Tries up to {@code attempts} random points. Must be called on the main thread; the future completes on the
     * main thread too (chunk loading callbacks of Paper) with the location or empty when nothing was found.
     */
    public CompletableFuture<Optional<Location>> find(World world, double centerX, double centerZ, int min, int max,
            int attempts) {
        CompletableFuture<Optional<Location>> result = new CompletableFuture<>();
        attempt(world, centerX, centerZ, min, max, attempts, result);
        return result;
    }

    private void attempt(World world, double cx, double cz, int min, int max, int left,
            CompletableFuture<Optional<Location>> result) {
        if (left <= 0 || result.isDone()) {
            result.complete(Optional.empty());
            return;
        }
        int[] point = randomPoint(random, cx, cz, min, max);
        Location probe = new Location(world, point[0] + 0.5, world.getSeaLevel(), point[1] + 0.5);
        if (!world.getWorldBorder().isInside(probe) || nearBorder(world, probe)) {
            attempt(world, cx, cz, min, max, left - 1, result);
            return;
        }
        Platform.loadChunk(world, point[0], point[1]).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                attempt(world, cx, cz, min, max, left - 1, result);
                return;
            }
            Location spot = null;
            try {
                spot = column(world, point[0], point[1]);
            } catch (RuntimeException e) {
                // fall through to the next attempt
            }
            if (spot != null) {
                result.complete(Optional.of(spot));
            } else {
                attempt(world, cx, cz, min, max, left - 1, result);
            }
        });
    }

    private static boolean nearBorder(World world, Location location) {
        double half = world.getWorldBorder().getSize() / 2 - 8;
        double dx = Math.abs(location.getX() - world.getWorldBorder().getCenter().getX());
        double dz = Math.abs(location.getZ() - world.getWorldBorder().getCenter().getZ());
        return dx > half || dz > half;
    }

    /** The standing place at this column, or null when it is not usable. */
    static Location column(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3 || y < world.getSeaLevel() - 1) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        boolean ok = usable(ground.getType(), ground.getType().isSolid(),
            feet.getType(), !SafeSpot.obstructs(feet.getType()) && !SafeSpot.liquid(feet.getType()),
            head.getType(), !SafeSpot.obstructs(head.getType()) && !SafeSpot.liquid(head.getType()));
        return ok ? new Location(world, x + 0.5, y + 1, z + 0.5) : null;
    }
}
