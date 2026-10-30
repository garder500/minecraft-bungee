package win.vampyuyu.survival.utility;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Decides whether a place is safe to arrive at. The material rules are pure and unit tested. */
public final class SafeSpot {
    /** Blocks that hurt when standing in or on them. */
    private static final Set<Material> HAZARDS = EnumSet.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
        Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW, Material.LAVA_CAULDRON,
        Material.COBWEB, Material.POINTED_DRIPSTONE);

    private SafeSpot() {}

    public static boolean hazard(Material material) {
        return HAZARDS.contains(material);
    }

    /** Fluids a player can stand in or on top of. */
    public static boolean liquid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    /** Something the body cannot occupy: a solid block (slabs, fences...) or a hazard. */
    public static boolean obstructs(Material material) {
        return material.isSolid() || hazard(material);
    }

    /**
     * Pure rule: feet and head blocks must be free (not solid, not a hazard), and the block under the feet must be
     * solid ground that is not a hazard.
     */
    public static boolean standable(Material ground, boolean groundSolid, Material feet, boolean feetFree,
            Material head, boolean headFree) {
        return groundSolid && !hazard(ground) && feetFree && !hazard(feet) && headFree && !hazard(head);
    }

    /** Player body height and half width, with a little slack so rounding in saved coordinates never matters. */
    static final double BODY_HEIGHT = 1.8;
    static final double HALF_WIDTH = 0.29;
    private static final double EPS = 0.01;

    /**
     * Pure rule: does a collision box (block local coordinates, 0..1) of the block at height {@code cellY} stop a
     * body whose feet are at {@code feetY} and that stands at the block local position ({@code localX},
     * {@code localZ})? A box that ends at or below the feet (carpet, slab or stair the player stands on) does not.
     */
    static boolean boxStopsBody(BoundingBox box, int cellY, double localX, double localZ, double feetY) {
        if (box.getMaxX() <= localX - HALF_WIDTH || box.getMinX() >= localX + HALF_WIDTH
                || box.getMaxZ() <= localZ - HALF_WIDTH || box.getMinZ() >= localZ + HALF_WIDTH) {
            return false;
        }
        double bottom = cellY + box.getMinY();
        double top = cellY + box.getMaxY();
        return top > feetY + EPS && bottom < feetY + BODY_HEIGHT - EPS;
    }

    /** Pure rule: does a collision box hold the feet up (its top is at or just under the feet)? */
    static boolean boxHoldsFeet(BoundingBox box, int cellY, double localX, double localZ, double feetY) {
        if (box.getMaxX() <= localX - HALF_WIDTH || box.getMinX() >= localX + HALF_WIDTH
                || box.getMaxZ() <= localZ - HALF_WIDTH || box.getMinZ() >= localZ + HALF_WIDTH) {
            return false;
        }
        return cellY + box.getMaxY() <= feetY + EPS;
    }

    /**
     * Checks a real location: the whole body (1.8 blocks from the feet, so a slab, stair, carpet or path floor
     * counts) must be free of collision boxes and hazards, and there must be ground at the feet or within 4
     * blocks below without a hazard in between. Water under or around the player is fine (a home on a boat dock
     * stays valid).
     */
    public static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        double feetY = location.getY();
        int y = location.getBlockY();
        if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        int bx = location.getBlockX();
        int bz = location.getBlockZ();
        double localX = location.getX() - bx;
        double localZ = location.getZ() - bz;
        int topCell = (int) Math.floor(feetY + BODY_HEIGHT - EPS);
        for (int cy = y; cy <= topCell; cy++) {
            Block cell = world.getBlockAt(bx, cy, bz);
            if (hazard(cell.getType())) {
                return false;
            }
            for (BoundingBox box : boxes(cell)) {
                if (boxStopsBody(box, cy, localX, localZ, feetY)) {
                    return false;
                }
            }
        }
        for (int i = 0; i <= 4; i++) {
            Block below = world.getBlockAt(bx, y - i, bz);
            if (below.getY() < world.getMinHeight()) {
                return false;
            }
            Material type = below.getType();
            if (hazard(type)) {
                return false;
            }
            for (BoundingBox box : boxes(below)) {
                if (boxHoldsFeet(box, below.getY(), localX, localZ, feetY)) {
                    return true;
                }
            }
            if (i > 0 && liquid(type)) {
                return true;
            }
        }
        return false;
    }

    /** Reads the collision boxes of a block; replaced by tests only (MockBukkit cannot describe shapes). */
    static volatile Function<Block, Collection<BoundingBox>> shapes = SafeSpot::realShape;

    private static Collection<BoundingBox> boxes(Block block) {
        if (block.getType().isAir()) {
            return List.of();
        }
        return shapes.apply(block);
    }

    /** Collision boxes in block local coordinates; a server that cannot describe the shape gets a full cube. */
    private static Collection<BoundingBox> realShape(Block block) {
        try {
            return block.getCollisionShape().getBoundingBoxes();
        } catch (RuntimeException | LinkageError e) {
            return block.getType().isSolid() ? List.of(new BoundingBox(0, 0, 0, 1, 1, 1)) : List.of();
        }
    }
}
