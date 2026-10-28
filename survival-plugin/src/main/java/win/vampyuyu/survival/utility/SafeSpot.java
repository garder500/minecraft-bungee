package win.vampyuyu.survival.utility;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

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

    /**
     * Checks a real location: a free body, and ground within 4 blocks below without a hazard in between.
     * Water under or around the player is fine (a home on a boat dock stays valid).
     */
    public static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int y = location.getBlockY();
        if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (blocked(feet) || blocked(head)) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            Block below = feet.getRelative(0, -i, 0);
            if (below.getY() < world.getMinHeight()) {
                return false;
            }
            Material type = below.getType();
            if (hazard(type)) {
                return false;
            }
            if (type.isSolid() || liquid(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocked(Block block) {
        return obstructs(block.getType());
    }
}
