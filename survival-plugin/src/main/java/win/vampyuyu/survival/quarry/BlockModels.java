package win.vampyuyu.survival.quarry;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** The 3D model of a placed Forge or machine: an item display over the vanilla block, which stays the hitbox. */
final class BlockModels {
    static final String TAG = "vy_block_model";

    private BlockModels() {
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    static boolean has(Block block) {
        try {
            return !block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center(block), 0.3,
            d -> d.getScoreboardTags().contains(TAG)).isEmpty();
        } catch (UnsupportedOperationException e) {
            return true; // a server without entity queries (tests): nothing to draw
        }
    }

    /** The yaw that turns the model's front towards a player looking along {@code playerYaw}, snapped to a side. */
    static float facing(float playerYaw) {
        return Math.round(playerYaw / 90f) * 90f;
    }

    static void show(Block block, ItemStack item, float yaw) {
        try {
            hide(block);
            Location at = center(block);
            at.setYaw(yaw);
            block.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.003f), new AxisAngle4f()));
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // inside a solid block it would be black
                d.addScoreboardTag(TAG);
                d.setPersistent(true);
                d.setInvulnerable(true);
            });
        } catch (RuntimeException e) {
            // a server without displays (tests): the vanilla block is all there is
        }
    }

    static void hide(Block block) {
        try {
            for (Entity e : block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center(block), 0.3,
                    d -> d.getScoreboardTags().contains(TAG))) {
                e.remove();
            }
        } catch (UnsupportedOperationException e) {
            // see show
        }
    }
}
