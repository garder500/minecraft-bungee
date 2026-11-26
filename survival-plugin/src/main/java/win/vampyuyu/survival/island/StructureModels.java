package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The display models of one built structure ({@link BlockPlan#models()}): item displays over the blocks, for the shapes
 * blocks cannot make (rings, halos). They are not persistent: they are spawned while a player is near the structure and
 * removed when nobody is, so a crash, a rebuild or a plan change never leaves stale ones in the world (a leftover is
 * swept by its mark). Spinning models turn a step per {@link #tick} with client-side interpolation. A model with a
 * {@code key} can be shown or hidden per player ({@link #visible}); a {@code hidden} one is seen by nobody until shown.
 */
public final class StructureModels {
    static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "structure_model");
    /** Players closer than this (blocks, horizontally) to the origin keep the models up. */
    static final double ACTIVE_RADIUS = 160;

    private final Plugin plugin;
    private final String name;
    private final World world;
    private final double ox;
    private final double oy;
    private final double oz;
    private final List<BlockPlan.Model> models;
    private final List<ItemDisplay> live = new ArrayList<>();
    private final float[] angles;
    /** Where the models are (their mean position): players near it keep them up. */
    private final double cx;
    private final double cz;

    public StructureModels(Plugin plugin, String name, World world, double ox, double oy, double oz, List<BlockPlan.Model> models) {
        this.plugin = plugin;
        this.name = name;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.models = List.copyOf(models);
        this.angles = new float[models.size()];
        this.cx = ox + models.stream().mapToDouble(BlockPlan.Model::x).average().orElse(0);
        this.cz = oz + models.stream().mapToDouble(BlockPlan.Model::z).average().orElse(0);
        purge();
    }

    /** The orientation of a model: its fixed tilt (Y, then X, then Z). */
    static Quaternionf tilt(BlockPlan.Model m) {
        return new Quaternionf().rotateY((float) Math.toRadians(m.tilt()[1])).rotateX((float) Math.toRadians(m.tilt()[0]))
            .rotateZ((float) Math.toRadians(m.tilt()[2]));
    }

    /** The spin of a model at an angle (degrees), around its own axis. */
    static Quaternionf spin(BlockPlan.Model m, float degrees) {
        float r = (float) Math.toRadians(degrees);
        return switch (m.spinAxis()) {
            case "x" -> new Quaternionf().rotateX(r);
            case "z" -> new Quaternionf().rotateZ(r);
            default -> new Quaternionf().rotateY(r);
        };
    }

    static Transformation transformation(BlockPlan.Model m, float angle) {
        return new Transformation(new Vector3f(), tilt(m), new Vector3f(m.scale()), spin(m, angle));
    }

    /** Called every {@code periodTicks}: spawns or removes the models by who is near, and turns the spinning ones. */
    public void tick(int periodTicks) {
        if (world == null || models.isEmpty()) {
            return;
        }
        if (!anyoneNear()) {
            remove();
            return;
        }
        if (live.size() != models.size() || live.stream().anyMatch(d -> !d.isValid())) {
            remove();
            spawn();
            return;
        }
        for (int i = 0; i < models.size(); i++) {
            BlockPlan.Model m = models.get(i);
            if (m.spin() == 0) {
                continue;
            }
            angles[i] = (angles[i] + m.spin() * periodTicks / 20f) % 360f;
            ItemDisplay d = live.get(i);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(periodTicks);
            d.setTransformation(transformation(m, angles[i]));
        }
    }

    /** Shows or hides, for one player, every model with this key. */
    public void visible(Player player, String key, boolean show) {
        for (int i = 0; i < live.size(); i++) {
            if (!key.equals(models.get(i).key())) {
                continue;
            }
            ItemDisplay d = live.get(i);
            if (show) {
                player.showEntity(plugin, d);
            } else {
                player.hideEntity(plugin, d);
            }
        }
    }

    /** Removes the displays (feature stop, rebuild). */
    public void remove() {
        for (ItemDisplay d : live) {
            d.remove();
        }
        live.clear();
    }

    private boolean anyoneNear() {
        if (!world.isChunkLoaded((int) Math.floor(cx) >> 4, (int) Math.floor(cz) >> 4)) {
            return false; // beyond the view distance: no respawn loop on an unloaded structure
        }
        for (Player p : world.getPlayers()) {
            Location at = p.getLocation();
            if (Math.hypot(at.getX() - cx, at.getZ() - cz) < ACTIVE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void purge() {
        if (world == null) {
            return;
        }
        try {
            for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
                if (name.equals(d.getPersistentDataContainer().get(MARK, PersistentDataType.STRING))) {
                    d.remove();
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // a server without entities (tests)
        }
    }

    private static ItemStack stack(BlockPlan.Model m) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("vampyuyu", m.item()));
        if (m.color() >= 0) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(Color.fromRGB(m.color())));
            meta.setCustomModelDataComponent(cmd);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private void spawn() {
        for (int i = 0; i < models.size(); i++) {
            BlockPlan.Model m = models.get(i);
            float angle = angles[i];
            Location at = new Location(world, ox + m.x(), oy + m.y(), oz + m.z());
            live.add(world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(stack(m));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(transformation(m, angle));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                d.setShadowRadius(0);
                d.setPersistent(false);
                d.setInvulnerable(true);
                if (m.hidden()) {
                    d.setVisibleByDefault(false);
                }
                d.getPersistentDataContainer().set(MARK, PersistentDataType.STRING, name);
            }));
        }
    }
}
