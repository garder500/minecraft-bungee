package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The look of every boss ability: models of the resource pack (resource-pack/art/fx_models.py, sources in
 * art/blockbench/fx/) on item displays at full brightness, tinted per element, never a vanilla particle. An effect is a list
 * of keyframes (scale, rotation, offset); at each key the display gets the next pose and the client interpolates in between,
 * so the server only speaks at the keys. An effect ends by shrinking to nothing (an item display has no opacity) and is then
 * removed. Displays are not persistent and carry {@link #MARK}: {@link #purge(World)} sweeps any left behind by a crash.
 *
 * <p>Abilities reach it through the static helpers; before {@link #install} (unit tests) they do nothing.
 */
public final class BossVfx {
    static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "boss_vfx");

    /** The element colours (the tint of the white primitives). */
    public static final Color VOID = Color.fromRGB(0xD24BFF);
    public static final Color RIFT = Color.fromRGB(0xFF4FD8);
    public static final Color FROST = Color.fromRGB(0x9FE6FF);
    public static final Color EMBER = Color.fromRGB(0xFF7A2A);
    public static final Color TOXIC = Color.fromRGB(0x8CE04A);
    public static final Color WITHER = Color.fromRGB(0x9A86B8);
    public static final Color STORM = Color.fromRGB(0xA77BFF);
    public static final Color TIDE = Color.fromRGB(0x4FB6FF);
    public static final Color SOUL = Color.fromRGB(0x8FFFD6);
    public static final Color BONE = Color.fromRGB(0xF0E6D2);
    public static final Color GOLD = Color.fromRGB(0xFFD25A);
    /** A marked danger (a dash line, a dive mark). */
    public static final Color DANGER = Color.fromRGB(0xFF3B5C);
    /** A safe way out (the gap of a fire ring or a wave). */
    public static final Color SAFE = Color.fromRGB(0x6DFFB0);

    /** One pose: at tick {@code at} of the effect, this scale, these rotations (degrees) and this offset (blocks). */
    public record Key(int at, float sx, float sy, float sz, float yaw, float pitch, float roll, float tx, float ty, float tz) {
        static Key of(int at, float scale) {
            return new Key(at, scale, scale, scale, 0, 0, 0, 0, 0, 0);
        }

        Key at(int tick) {
            return new Key(tick, sx, sy, sz, yaw, pitch, roll, tx, ty, tz);
        }

        Key scaled(float x, float y, float z) {
            return new Key(at, x, y, z, yaw, pitch, roll, tx, ty, tz);
        }

        Key turned(float yaw, float pitch, float roll) {
            return new Key(at, sx, sy, sz, yaw, pitch, roll, tx, ty, tz);
        }

        Key moved(float x, float y, float z) {
            return new Key(at, sx, sy, sz, yaw, pitch, roll, x, y, z);
        }

        Transformation transformation() {
            Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
            return new Transformation(new Vector3f(tx, ty, tz), q, new Vector3f(sx, sy, sz), new Quaternionf());
        }
    }

    private static final class Live {
        final ItemDisplay display;
        final List<Key> keys;
        final Entity follow;
        final Vector followOffset;
        int age;
        int next = 1;

        Live(ItemDisplay display, List<Key> keys, Entity follow, Vector followOffset) {
            this.display = display;
            this.keys = keys;
            this.follow = follow;
            this.followOffset = followOffset;
        }

        int end() {
            return keys.get(keys.size() - 1).at();
        }
    }

    private static BossVfx instance;

    private final Core core;
    private final List<Live> live = new ArrayList<>();
    private Scheduler.Handle task;

    private BossVfx(Core core) {
        this.core = core;
    }

    static void install(Core core) {
        uninstall();
        instance = new BossVfx(core);
        instance.task = core.scheduler().repeating(1, 1, instance::tick);
    }

    static void uninstall() {
        if (instance == null) {
            return;
        }
        if (instance.task != null) {
            instance.task.cancel();
        }
        instance.live.forEach(l -> l.display.remove());
        instance.live.clear();
        instance = null;
    }

    /** Removes every effect display of a world that no running effect owns (left behind by a crash or a stop). */
    static void purge(World world) {
        for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
            if (d.getPersistentDataContainer().has(MARK, PersistentDataType.BYTE)
                    && (instance == null || instance.live.stream().noneMatch(l -> l.display.equals(d)))) {
                d.remove();
            }
        }
    }

    static int running() {
        return instance == null ? 0 : instance.live.size();
    }

    // ------------------------------------------------------------------------------------------------ the generic effect

    /**
     * Spawns primitive {@code model} (vampyuyu:fx/&lt;model&gt;) at {@code at}, tinted {@code color}, playing {@code keys}
     * (sorted by tick, the first at 0); {@code follow} (nullable) keeps it on an entity with {@code offset}.
     */
    public static Handle play(Location at, String model, Color color, List<Key> keys, Entity follow, Vector offset,
            Display.Billboard billboard) {
        Handle handle = new Handle();
        if (instance == null || at == null || at.getWorld() == null || keys.size() < 2) {
            return handle;
        }
        Live main = spawn(at, model, color, keys, follow, offset, billboard);
        if (main == null) {
            return handle;
        }
        handle.lives.add(main);
        if (HALOS.contains(model)) {
            // the halo shell (fx_models.py HALOS): a translucent twin 30 % bigger that the item shader makes breathe;
            // a beam keeps its length, only its section grows
            boolean beam = model.equals("beam");
            List<Key> shell = new ArrayList<>();
            for (Key k : keys) {
                shell.add(k.scaled(k.sx() * HALO_GROWTH, k.sy() * HALO_GROWTH, beam ? k.sz() : k.sz() * HALO_GROWTH));
            }
            Live halo = spawn(at, model + "_halo", color, shell, follow, offset, billboard);
            if (halo != null) {
                handle.lives.add(halo);
            }
        }
        return handle;
    }

    /** The displays of one effect: a missile moves them every tick, and ends them when it lands. */
    public static final class Handle {
        private final List<Live> lives = new ArrayList<>();

        public void move(Location to) {
            Location at = to.clone();
            at.setYaw(0);
            at.setPitch(0);
            for (Live l : lives) {
                if (l.display.isValid()) {
                    l.display.teleport(at);
                }
            }
        }

        public void end() {
            for (Live l : lives) {
                l.display.remove();
            }
        }
    }

    /** Primitives with a halo shell, the same list as resource-pack/art/fx_models.py HALOS. */
    static final java.util.Set<String> HALOS = java.util.Set.of("beam", "bolt", "pillar", "orb", "burst", "spike", "shard",
        "vortex", "wave", "slash");
    static final float HALO_GROWTH = 1.3f;

    private static Live spawn(Location at, String model, Color color, List<Key> keys, Entity follow, Vector offset,
            Display.Billboard billboard) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("vampyuyu", "fx/" + model));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(color));
        meta.setCustomModelDataComponent(cmd);
        stack.setItemMeta(meta);
        Location spawn = at.clone();
        spawn.setYaw(0);
        spawn.setPitch(0);
        ItemDisplay display;
        try {
            display = at.getWorld().spawn(spawn, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(keys.get(0).transformation());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(3f);
            d.setShadowRadius(0);
            if (billboard != null && billboard != Display.Billboard.FIXED) {
                d.setBillboard(billboard);
            }
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
        });
        } catch (RuntimeException e) {
            return null;   // a look must never break a fight (an unloaded chunk, a test server without displays)
        }
        Live live = new Live(display, List.copyOf(keys), follow, offset == null ? new Vector() : offset);
        instance.live.add(live);
        return live;
    }

    public static Handle play(Location at, String model, Color color, List<Key> keys) {
        return play(at, model, color, keys, null, null, null);
    }

    void tick() {
        Iterator<Live> it = live.iterator();
        while (it.hasNext()) {
            Live l = it.next();
            if (!l.display.isValid() || l.age > l.end() || (l.follow != null && !l.follow.isValid())) {
                l.display.remove();
                it.remove();
                continue;
            }
            if (l.follow != null && l.follow.isValid()) {
                Location to = l.follow.getLocation().add(l.followOffset);
                to.setYaw(0);
                to.setPitch(0);
                l.display.teleport(to);
            }
            // at each key, aim the display at the next one and let the client interpolate the way there
            if (l.next < l.keys.size() && l.age == l.keys.get(l.next - 1).at()) {
                Key target = l.keys.get(l.next);
                l.display.setInterpolationDelay(0);
                l.display.setInterpolationDuration(Math.max(1, target.at() - l.age));
                l.display.setTransformation(target.transformation());
                l.next++;
            }
            l.age++;
        }
    }

    // ------------------------------------------------------------------------------------------------ shapes of effects

    /** A ground decal (ring, disc, sigil) of radius {@code radius} blocks: grows in, holds and turns, shrinks out. */
    public static void decal(Location center, String model, Color color, double radius, int ticks, float spinPerSecond) {
        play(center.clone().add(0, 0.05, 0), model, color, decalKeys(radius, ticks, spinPerSecond));
    }

    /** The same decal kept under an entity (a target marker that follows its player). */
    public static void decalOn(Entity on, String model, Color color, double radius, int ticks, float spinPerSecond) {
        play(on.getLocation().add(0, 0.05, 0), model, color, decalKeys(radius, ticks, spinPerSecond), on, new Vector(0, 0.05, 0), null);
    }

    static List<Key> decalKeys(double radius, int ticks, float spinPerSecond) {
        float s = (float) (radius * 2);   // the flat primitives are 16 units (one block) across at scale 1
        int grow = Math.max(1, Math.min(6, ticks / 4));
        int hold = Math.max(grow + 1, ticks - 4);
        List<Key> keys = new ArrayList<>();
        keys.add(Key.of(0, s * 0.2f));
        keys.add(Key.of(grow, s).turned(spinPerSecond * grow / 20f, 0, 0));
        // a slow spin is cut in steps of at most 90 degrees: the client takes the short way between two rotations
        int steps = Math.max(1, (int) Math.ceil(Math.abs(spinPerSecond) * (hold - grow) / 20f / 90f));
        for (int i = 1; i <= steps; i++) {
            int at = grow + (hold - grow) * i / steps;
            keys.add(Key.of(at, s).turned(spinPerSecond * at / 20f, 0, 0));
        }
        keys.add(Key.of(ticks, 0.01f).turned(spinPerSecond * ticks / 20f, 0, 0));
        return keys;
    }

    /**
     * A beam pivoting on {@code from} (a sweeping laser): {@code yaws} gives its heading (degrees, 0 = +z) every
     * {@code step} ticks; the display stays on {@code follow} and its model is pushed out along its own heading.
     */
    public static void sweep(Location from, Entity follow, Vector offset, String model, Color color, double length, double width,
            double pitch, List<Float> yaws, int step) {
        float w = (float) width / (4f / 16f);   // the beam model is 4 units across
        float half = (float) length / 2f;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < yaws.size(); i++) {
            float yaw = yaws.get(i);
            double rad = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float tx = (float) (-Math.sin(rad) * Math.cos(pr) * half), tz = (float) (Math.cos(rad) * Math.cos(pr) * half);
            float ty = (float) (-Math.sin(pr) * half);
            float ws = i == 0 ? w * 0.15f : w;
            keys.add(new Key(i * step, ws, ws, (float) length, yaw, (float) pitch, 0, tx, ty, tz));
        }
        Key last = keys.get(keys.size() - 1);
        keys.add(new Key(last.at() + 3, 0.01f, 0.01f, (float) length, last.yaw(), last.pitch(), 0, last.tx(), last.ty(), last.tz()));
        play(from, model, color, keys, follow, offset, null);
    }

    /** An expanding ring on the ground from {@code from} to {@code to} blocks over {@code ticks} (a shockwave, a fire ring). */
    public static void wave(Location center, String model, Color color, double from, double to, int ticks, float height) {
        play(center.clone().add(0, 0.05, 0), model, color, List.of(
            Key.of(0, (float) from * 2).scaled((float) from * 2, height, (float) from * 2),
            Key.of(ticks, (float) to * 2).scaled((float) to * 2, height, (float) to * 2),
            Key.of(ticks + 3, 0.01f)));
    }

    /** A column that rises out of the ground at {@code base} to {@code height} blocks (spike, pillar, bolt, cage bar). */
    public static void column(Location base, String model, Color color, double width, double height, int rise, int hold) {
        float w = (float) width, h = (float) height / 2f;   // pillar/spike models are two blocks tall at scale 1
        play(base.clone(), model, color, List.of(
            // the model's base (y 0) sits half a block under its centre: lift it by half its scaled height
            new Key(0, w, 0.02f, w, 0, 0, 0, 0, 0.01f, 0),
            new Key(rise, w, h, w, 15, 0, 0, 0, h * 0.5f, 0),
            new Key(rise + hold, w, h, w, 30, 0, 0, 0, h * 0.5f, 0),
            new Key(rise + hold + 4, 0.01f, 0.01f, 0.01f, 40, 0, 0, 0, 0, 0)));
    }

    /** A beam from {@code from} to {@code to} (laser, drain, chain): the beam model stretched between the two points. */
    public static void beam(Location from, Location to, String model, Color color, double width, int ticks) {
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.1) {
            return;
        }
        Location mid = from.clone().add(d.clone().multiply(0.5));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) Math.toDegrees(-Math.asin(d.getY() / len));
        float w = (float) width / (4f / 16f);   // the beam model is 4 units across
        play(mid, model, color, List.of(
            new Key(0, w * 0.2f, w * 0.2f, (float) len, yaw, pitch, 0, 0, 0, 0),
            new Key(3, w, w, (float) len, yaw, pitch, 0, 0, 0, 0),
            new Key(Math.max(4, ticks - 3), w, w, (float) len, yaw, pitch, 0, 0, 0, 0),
            new Key(ticks, 0.01f, 0.01f, (float) len, yaw, pitch, 0, 0, 0, 0)));
    }

    /**
     * A lightning bolt striking {@code ground}: the bolt model (y -16..32) stretched to {@code height} blocks with its foot on
     * the ground, flashing in and out.
     */
    public static void bolt(Location ground, Color color, double height, double width, int ticks) {
        float sy = (float) height / 3f, sx = (float) width / (6f / 16f);
        // the foot (y -16) is 1.5 model-blocks under the model's centre: place the display 1.5 x sy above the ground
        play(ground.clone().add(0, 1.5 * sy, 0), "bolt", color, List.of(
            new Key(0, sx * 0.3f, sy, sx * 0.3f, 0, 0, 0, 0, 0, 0),
            new Key(2, sx, sy, sx, 25, 0, 0, 0, 0, 0),
            new Key(Math.max(3, ticks - 3), sx, sy, sx, 40, 0, 0, 0, 0, 0),
            new Key(ticks, 0.01f, sy, 0.01f, 60, 0, 0, 0, 0, 0)));
    }

    /** A burst (impact star, orb, shard) that pops out to {@code size} blocks and vanishes, tumbling. */
    public static void burst(Location at, String model, Color color, double size, int ticks) {
        float s = (float) size;
        play(at, model, color, List.of(
            Key.of(0, s * 0.1f),
            Key.of(Math.max(1, ticks / 3), s).turned(40, 20, 10),
            Key.of(ticks, 0.01f).turned(90, 40, 30)));
    }

    /** A spinning shape around a point (vortex, whirlpool), {@code turns} full turns over {@code ticks}. */
    public static void spin(Location center, String model, Color color, double radius, double height, int ticks, int turns,
            Entity follow) {
        float r = (float) radius * 2f, h = (float) height;   // the vortex is one block across and one tall at scale 1
        List<Key> keys = new ArrayList<>();
        int steps = Math.max(3, turns * 3);
        for (int i = 0; i <= steps; i++) {
            int at = Math.round((float) ticks * i / steps);
            float grow = i == 0 ? 0.2f : 1f;
            keys.add(new Key(at, r * grow, h * grow, r * grow, -120f * i, 0, 0, 0, 0, 0));
        }
        keys.add(new Key(ticks + 4, 0.01f, 0.01f, 0.01f, -120f * steps - 60, 0, 0, 0, 0, 0));
        play(center, model, color, keys, follow, new Vector(), null);
    }

    /** A missile: {@code model} of {@code size} blocks that the caller moves every tick (Handle.move) and ends. */
    public static Handle missile(Location at, String model, Color color, double size, int maxTicks) {
        float sz = (float) size;
        return play(at, model, color, List.of(
            Key.of(0, sz * 0.3f),
            Key.of(3, sz).turned(60, 30, 0),
            Key.of(Math.max(4, maxTicks / 2), sz).turned(360, 180, 0),
            Key.of(maxTicks, sz).turned(720, 360, 0)));
    }

    /** An impact: a burst and a ground shockwave ring of radius {@code radius}. */
    public static void impact(Location at, Color color, double radius) {
        burst(at.clone().add(0, 0.6, 0), "burst", color, radius * 1.1, 9);
        wave(at, "ring", color, 0.3, radius, 7, 1.5f);
    }

    /** An effect that stays on an entity (a crystal, an anchor): {@code model} turning slowly, ending with the entity. */
    public static Handle attached(Entity on, Vector offset, String model, Color color, double size, int ticks) {
        float sz = (float) size;
        List<Key> keys = new ArrayList<>();
        keys.add(Key.of(0, sz * 0.2f));
        keys.add(Key.of(6, sz));
        for (int at = 26, i = 1; at < ticks; at += 20, i++) {
            keys.add(Key.of(at, sz).turned(-90f * i, 0, 0));
        }
        keys.add(Key.of(ticks, 0.01f));
        return play(on.getLocation().add(offset), model, color, keys, on, offset, null);
    }

    /**
     * A ring that expands from {@code from} to {@code to} blocks over {@code ticks} built of {@code pieces} wall segments
     * round {@code center}, leaving out the arc within {@code gapHalf} radians of {@code gapAngle} (the fire ring's way out).
     */
    public static void arcWave(Location center, String model, Color color, double from, double to, int ticks, double height,
            int pieces, double gapAngle, double gapHalf) {
        float sy = (float) height / 1.25f;   // the wave model is 20 units (1.25 blocks) tall
        for (int i = 0; i < pieces; i++) {
            double a = 2 * Math.PI * (i + 0.5) / pieces;
            double d = Math.abs(a - gapAngle) % (2 * Math.PI);
            if ((d > Math.PI ? 2 * Math.PI - d : d) < gapHalf) {
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));   // its face looks outward
            float w0 = (float) (2 * Math.PI * from / pieces), w1 = (float) (2 * Math.PI * to / pieces);
            float x0 = (float) (Math.cos(a) * from), z0 = (float) (Math.sin(a) * from);
            float x1 = (float) (Math.cos(a) * to), z1 = (float) (Math.sin(a) * to);
            play(center.clone().add(0, height / 2, 0), model, color, List.of(
                new Key(0, w0, sy * 0.3f, 1f, yaw, 0, 0, x0, 0, z0),
                new Key(3, w0, sy, 1f, yaw, 0, 0, x0, 0, z0),
                new Key(ticks, w1, sy, 1f, yaw, 0, 0, x1, 0, z1),
                new Key(ticks + 3, w1, 0.02f, 1f, yaw, 0, 0, x1, 0, z1)));
        }
    }

    /** A wall of {@code model} segments {@code segment} blocks wide marching from {@code start} along {@code dir}. */
    public static void wall(Location start, Vector dir, List<Double> offsets, double segment, double height, double travel,
            int rise, int ticks, Color color) {
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())) + 180f;   // the crest leans toward -z
        float sy = (float) height / 1.25f, sx = (float) segment;
        float tx = (float) (dir.getX() * travel), tz = (float) (dir.getZ() * travel);
        for (double w : offsets) {
            Location at = start.clone().add(side.clone().multiply(w)).add(0, height / 2, 0);
            play(at, "wave", color, List.of(
                new Key(0, sx, 0.05f, 1.2f, yaw, 0, 0, 0, 0, 0),
                new Key(rise, sx, sy, 1.2f, yaw, 0, 0, 0, 0, 0),
                new Key(rise + ticks, sx, sy, 1.2f, yaw, 0, 0, tx, 0, tz),
                new Key(rise + ticks + 4, sx, 0.02f, 1.2f, yaw, 0, 0, tx, 0, tz)));
        }
    }
}
