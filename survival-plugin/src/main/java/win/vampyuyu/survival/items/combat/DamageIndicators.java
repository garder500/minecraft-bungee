package win.vampyuyu.survival.items.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.items.ItemsSettings;

/**
 * Floating damage numbers: short lived {@code TextDisplay} entities that rise and vanish. Entities are not
 * persistent, carry a PDC tag so leftovers can be purged, and a small pool is reused to avoid spawn churn.
 * Colours avoid the red channel value 78 that the resource pack's HUD shader reserves.
 */
public final class DamageIndicators {
    /** PDC key marking an indicator entity. */
    public static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "sb_indicator");

    private static final TextColor[] CRIT_COLORS = {NamedTextColor.WHITE, NamedTextColor.YELLOW, NamedTextColor.GOLD,
        NamedTextColor.RED};

    private final Core core;
    private volatile ItemsSettings settings;
    private final Deque<TextDisplay> pool = new ArrayDeque<>();
    private final List<TextDisplay> live = new ArrayList<>();
    private long windowStart;
    private int windowCount;

    public DamageIndicators(Core core, ItemsSettings settings) {
        this.core = core;
        this.settings = settings;
    }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    // ---------------------------------------------------------------- pure formatting

    /** "1,234", or "12.3k" from 100k up. */
    public static String number(double amount) {
        double rounded = Math.max(0, Math.round(amount));
        return rounded >= 100_000 ? Msg.number(rounded) : Msg.grouped(rounded);
    }

    /** The text of a hit: gray for normal hits, a gold/red alternating "✧1,234✧" for critical hits. */
    public static Component text(double amount, boolean crit) {
        String digits = number(amount);
        if (!crit) {
            return Component.text(digits, NamedTextColor.GRAY);
        }
        String full = "✧" + digits + "✧";
        Component out = Component.empty();
        for (int i = 0; i < full.length(); i++) {
            out = out.append(Component.text(String.valueOf(full.charAt(i)), CRIT_COLORS[i % CRIT_COLORS.length]));
        }
        return out.decoration(TextDecoration.BOLD, true);
    }

    // ---------------------------------------------------------------- entities

    /** Shows a number above {@code at}. Safe to call for any location; does nothing when disabled or rate limited. */
    public void show(Location at, double amount, boolean crit) {
        ItemsSettings s = settings;
        if (!s.indicators || at == null || at.getWorld() == null || amount < 0.5) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - windowStart > 1000) {
            windowStart = now;
            windowCount = 0;
        }
        if (++windowCount > 60) {
            return;
        }
        World world = at.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spot = at.clone().add(random.nextDouble(-0.5, 0.5), 0.3 + random.nextDouble(0.4), random.nextDouble(-0.5, 0.5));
        try {
            TextDisplay display = acquire(world, spot);
            if (display == null) {
                return;
            }
            display.text(text(amount, crit));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setViewRange(0.4f);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(crit ? 1.4f : 1.0f), new AxisAngle4f()));
            core.scheduler().nextTick(() -> animate(display, s.indicatorTicks, crit));
            core.scheduler().later(s.indicatorTicks, () -> release(display, s));
        } catch (RuntimeException e) {
            core.debug("Damage indicator failed: " + e);
        }
    }

    private TextDisplay acquire(World world, Location spot) {
        TextDisplay pooled;
        while ((pooled = pool.poll()) != null) {
            if (pooled.isValid() && pooled.getWorld().equals(world)) {
                pooled.teleport(spot);
                live.add(pooled);
                return pooled;
            }
            if (pooled.isValid()) {
                pooled.remove();
            }
        }
        TextDisplay created = world.spawn(spot, TextDisplay.class, display -> {
            display.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
            display.setPersistent(false);
        });
        live.add(created);
        return created;
    }

    private void animate(TextDisplay display, int ticks, boolean crit) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(0, crit ? 1.1f : 0.8f, 0), new AxisAngle4f(),
            new Vector3f(crit ? 1.0f : 0.7f), new AxisAngle4f()));
    }

    private void release(TextDisplay display, ItemsSettings s) {
        live.remove(display);
        if (!display.isValid()) {
            return;
        }
        if (pool.size() < s.indicatorPool) {
            display.text(Component.empty());
            pool.add(display);
        } else {
            display.remove();
        }
    }

    /** Removes every indicator this instance created (disable). */
    public void clear() {
        for (TextDisplay display : new ArrayList<>(live)) {
            if (display.isValid()) {
                display.remove();
            }
        }
        live.clear();
        for (TextDisplay display : pool) {
            if (display.isValid()) {
                display.remove();
            }
        }
        pool.clear();
    }

    /** Removes tagged leftovers of a previous run in the given world (chunks that were saved with them). */
    public static int purge(World world) {
        int removed = 0;
        try {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE)) {
                    entity.remove();
                    removed++;
                }
            }
        } catch (RuntimeException ignored) {
            // world unloading
        }
        return removed;
    }

    /** True when the entity is one of ours. */
    public static boolean isIndicator(Entity entity) {
        return entity.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }

    Scheduler scheduler() { return core.scheduler(); }
}
