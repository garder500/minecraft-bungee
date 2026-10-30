package win.vampyuyu.survival.items.pet;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.core.Core;

/**
 * The cosmetic pet: a scaled {@code ItemDisplay} that glides after its owner. It has no collision, is not
 * persistent, carries a PDC tag so strays can be purged, and is respawned (never teleported across worlds).
 */
public final class PetVisuals {
    /** PDC tag on every pet display. */
    public static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "sb_pet");

    private final Core core;
    private final Map<UUID, ItemDisplay> displays = new ConcurrentHashMap<>();
    private volatile boolean hideFromOwner;

    public PetVisuals(Core core, boolean hideFromOwner) {
        this.core = core;
        this.hideFromOwner = hideFromOwner;
    }

    public void hideFromOwner(boolean hide) { this.hideFromOwner = hide; }

    /** The display item for a pet type: an egg using the pet's pack model. */
    public static ItemStack displayItem(PetType type) {
        ItemStack stack = new ItemStack(Material.EGG);
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString("vampyuyu:items/pet_" + type.id());
        if (model != null) {
            meta.setItemModel(model);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Spawns (or replaces) the pet of {@code owner}. Returns false when entities cannot be created here. */
    public boolean spawn(Player owner, PetType type) {
        despawn(owner.getUniqueId());
        Location where = spot(owner, 0);
        World world = where.getWorld();
        if (world == null) {
            return false;
        }
        try {
            ItemDisplay display = world.spawn(where, ItemDisplay.class, entity -> {
                entity.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, owner.getUniqueId().toString());
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setItemStack(displayItem(type));
                entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                entity.setTeleportDuration(3);
                entity.setViewRange(0.6f);
                entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f),
                    new AxisAngle4f()));
            });
            if (hideFromOwner) {
                owner.hideEntity(core.getPlugin(), display);
            }
            displays.put(owner.getUniqueId(), display);
            return true;
        } catch (RuntimeException e) {
            core.debug("Pet display failed: " + e);
            return false;
        }
    }

    public boolean has(UUID owner) {
        ItemDisplay display = displays.get(owner);
        return display != null && display.isValid();
    }

    public void despawn(UUID owner) {
        ItemDisplay display = displays.remove(owner);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public void despawnAll() {
        for (ItemDisplay display : new ArrayList<>(displays.values())) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    /** Where the pet floats: behind and to the right of the owner, bobbing gently. */
    private static Location spot(Player owner, long tick) {
        Location base = owner.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double back = 1.3;
        double side = 0.9;
        double x = base.getX() + Math.sin(yaw) * back + Math.cos(yaw) * side;
        double z = base.getZ() - Math.cos(yaw) * back + Math.sin(yaw) * side;
        double y = base.getY() + 0.9 + Math.sin(tick / 6.0) * 0.12;
        Location out = new Location(base.getWorld(), x, y, z, base.getYaw() + 180, 0);
        return out;
    }

    /** Moves every pet display towards its owner (called every couple of ticks). */
    public void follow(long tick) {
        for (Map.Entry<UUID, ItemDisplay> entry : new ArrayList<>(displays.entrySet())) {
            Player owner = org.bukkit.Bukkit.getPlayer(entry.getKey());
            ItemDisplay display = entry.getValue();
            if (owner == null || !owner.isOnline() || !display.isValid() || owner.getWorld() != display.getWorld()) {
                if (display.isValid() && (owner == null || !owner.isOnline())) {
                    display.remove();
                    displays.remove(entry.getKey());
                }
                continue;
            }
            Location target = spot(owner, tick);
            if (display.getLocation().distanceSquared(target) > 0.0004) {
                display.teleport(target);
            }
        }
    }

    /** Removes tagged displays that this instance does not own (leftovers of a crash). */
    public int purge(World world) {
        int removed = 0;
        try {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isPet(entity) && !displays.containsValue(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        } catch (RuntimeException ignored) {
            // world unloading
        }
        return removed;
    }

    public boolean isOurs(Entity entity) {
        return displays.containsValue(entity);
    }

    public static boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(TAG, PersistentDataType.STRING);
    }
}
