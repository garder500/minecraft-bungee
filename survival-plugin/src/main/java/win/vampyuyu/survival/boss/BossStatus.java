package win.vampyuyu.survival.boss;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Owner of the boss afflictions ({@link StatusKind}): the potion-free replacement of Slowness, Blindness, Poison, Wither,
 * Levitation, Slow Falling, freeze and fire ticks. Each player's statuses become transient attribute modifiers (movement speed,
 * gravity, jump strength) under the {@code vampyuyu:boss_status_*} keys and a damage tick once a second; all of them are taken
 * back when they run out, and at once on death, quit, world change, milk, the end of a fight ({@link #clear(List)}) and plugin
 * stop. Nothing here draws: the look of a status (a screen veil, an aura) is the pack's, through {@link Look}.
 *
 * <p>Abilities reach it through the static {@link #apply}; before {@link #install} (unit tests) it does nothing.
 */
public final class BossStatus implements Listener {
    /** How a status is shown: the pack's screen veil and aura (drawn by the boss VFX, never vanilla particles). */
    public interface Look {
        void show(Player player, StatusKind kind, int ticks);

        void clear(Player player);
    }

    static final NamespacedKey SPEED = new NamespacedKey("vampyuyu", "boss_status_speed");
    static final NamespacedKey GRAVITY = new NamespacedKey("vampyuyu", "boss_status_gravity");
    static final NamespacedKey JUMP = new NamespacedKey("vampyuyu", "boss_status_jump");

    private static BossStatus instance;

    private final Core core;
    private final Map<UUID, Map<StatusKind, StatusMath.Active>> active = new HashMap<>();
    private Look look;
    private Scheduler.Handle task;
    private long now;

    private BossStatus(Core core) {
        this.core = core;
    }

    /** The pack veils of a player's statuses (ScreenVeil ids), the void last so it is drawn on top. */
    private final java.util.function.Function<Player, List<Integer>> veils = this::veilIds;

    List<Integer> veilIds(Player player) {
        Map<StatusKind, StatusMath.Active> map = active.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<>(java.util.Comparator.comparingInt((Integer i) -> i == 5 ? 99 : i));
        for (Map.Entry<StatusKind, StatusMath.Active> e : map.entrySet()) {
            if (e.getValue().live(now)) {
                ids.add(veil(e.getKey()));
            }
        }
        return List.copyOf(ids);
    }

    /** The ScreenVeil id of a status: 5 void, 6 frost, 7 decay, 8 burn, 9 warped. */
    static int veil(StatusKind kind) {
        return switch (kind) {
            case BLIND -> 5;
            case FROST -> 6;
            case DECAY, WITHERING -> 7;
            case BURN -> 8;
            case SLOW, ROOT, FLOAT, LIFT -> 9;
        };
    }

    /** Starts the service (BossFeature.enable). */
    static BossStatus install(Core core) {
        uninstall();
        instance = new BossStatus(core);
        core.listen(instance);
        win.vampyuyu.survival.core.ScreenVeil.register(instance.veils);
        instance.task = core.scheduler().repeating(1, 1, instance::tick);
        return instance;
    }

    /** Stops it and gives every player back their plain attributes (BossFeature.disable). */
    static void uninstall() {
        if (instance == null) {
            return;
        }
        if (instance.task != null) {
            instance.task.cancel();
        }
        win.vampyuyu.survival.core.ScreenVeil.unregister(instance.veils);
        for (UUID id : List.copyOf(instance.active.keySet())) {
            Player p = instance.core.getPlugin().getServer().getPlayer(id);
            if (p != null) {
                instance.reset(p);
            }
        }
        instance.active.clear();
        instance = null;
    }

    /** Sets how statuses are drawn (the boss VFX). */
    static void look(Look look) {
        if (instance != null) {
            instance.look = look;
        }
    }

    /**
     * Lays {@code kind} on {@code player} for {@code ticks} at {@code level} (1 = weakest); {@code dps} overrides the
     * kind's damage per second (0 keeps the kind's). A refresh keeps the longer and the stronger of the two.
     */
    public static void apply(Player player, StatusKind kind, int ticks, int level, double dps) {
        if (instance == null || player == null || ticks <= 0 || !player.isValid() || player.isDead()) {
            return;
        }
        instance.add(player, kind, ticks, level, dps > 0 ? dps : kind.dps());
    }

    public static void apply(Player player, StatusKind kind, int ticks, int level) {
        apply(player, kind, ticks, level, 0);
    }

    /** True while {@code player} carries {@code kind} (tests and abilities that read it, e.g. the warm braziers). */
    public static boolean has(Player player, StatusKind kind) {
        if (instance == null || player == null) {
            return false;
        }
        Map<StatusKind, StatusMath.Active> map = instance.active.get(player.getUniqueId());
        StatusMath.Active a = map == null ? null : map.get(kind);
        return a != null && a.live(instance.now);
    }

    /** Takes every status off these players (the end of a fight). */
    public static void clear(List<Player> players) {
        if (instance == null) {
            return;
        }
        for (Player p : players) {
            instance.clearOne(p);
        }
    }

    /** Takes {@code kind} off {@code player} (a brazier warming a frozen player, a cleanse). */
    public static void remove(Player player, StatusKind kind) {
        if (instance == null || player == null) {
            return;
        }
        Map<StatusKind, StatusMath.Active> map = instance.active.get(player.getUniqueId());
        if (map != null && map.remove(kind) != null) {
            instance.refresh(player, map);
        }
    }

    private void add(Player player, StatusKind kind, int ticks, int level, double dps) {
        Map<StatusKind, StatusMath.Active> map = active.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(StatusKind.class));
        map.put(kind, StatusMath.merge(map.get(kind), new StatusMath.Active(Math.max(1, level), now + ticks, dps), now));
        refresh(player, map);
        if (win.vampyuyu.survival.core.ScreenVeil.unowned(player)) {
            player.sendActionBar(win.vampyuyu.survival.core.ScreenVeil.of(player));   // at once, not at the next resend
        }
        if (look != null) {
            look.show(player, kind, (int) (map.get(kind).until() - now));
        }
    }

    void tick() {
        now++;
        var it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<StatusKind, StatusMath.Active>> entry = it.next();
            Player p = core.getPlugin().getServer().getPlayer(entry.getKey());
            Map<StatusKind, StatusMath.Active> map = entry.getValue();
            if (p == null || !p.isValid()) {
                it.remove();
                continue;
            }
            boolean expired = map.values().removeIf(a -> !a.live(now));
            if (now % 20 == 0) {
                for (Map.Entry<StatusKind, StatusMath.Active> e : Map.copyOf(map).entrySet()) {
                    double dmg = StatusMath.tickDamage(e.getKey(), e.getValue().dps(), p.getHealth());
                    if (dmg > 0 && !p.isDead()) {
                        p.damage(dmg); // an EntityDamageEvent: Ward and armour still act on it, like a potion tick
                    }
                }
            }
            if (expired) {
                refresh(p, map);
            }
            if (map.isEmpty()) {
                reset(p);
                it.remove();
                if (win.vampyuyu.survival.core.ScreenVeil.unowned(p)) {
                    p.sendActionBar(net.kyori.adventure.text.Component.empty());   // take our veil down at once
                }
            } else if (now % 10 == 0 && win.vampyuyu.survival.core.ScreenVeil.unowned(p)) {
                // no HUD writes this player's action bar (the items feature is off): carry the veils ourselves
                p.sendActionBar(win.vampyuyu.survival.core.ScreenVeil.of(p));
            }
        }
    }

    private void refresh(Player p, Map<StatusKind, StatusMath.Active> map) {
        set(p.getAttribute(Attribute.MOVEMENT_SPEED), SPEED, StatusMath.speed(map, now), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        set(p.getAttribute(Attribute.GRAVITY), GRAVITY, StatusMath.gravity(map, now), AttributeModifier.Operation.ADD_NUMBER);
        set(p.getAttribute(Attribute.JUMP_STRENGTH), JUMP, StatusMath.noJump(map, now) ? -1.0 : 0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if (map.isEmpty() && look != null) {
            look.clear(p);
        }
    }

    private void clearOne(Player p) {
        if (active.remove(p.getUniqueId()) != null) {
            reset(p);
        }
    }

    private void reset(Player p) {
        for (var pair : List.of(Map.entry(Attribute.MOVEMENT_SPEED, SPEED), Map.entry(Attribute.GRAVITY, GRAVITY),
                Map.entry(Attribute.JUMP_STRENGTH, JUMP))) {
            AttributeInstance inst = p.getAttribute(pair.getKey());
            if (inst != null && inst.getModifier(pair.getValue()) != null) {
                inst.removeModifier(pair.getValue());
            }
        }
        if (look != null) {
            look.clear(p);
        }
    }

    private static void set(AttributeInstance instance, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1e-9) {
                return;
            }
            instance.removeModifier(key);
        }
        if (Math.abs(amount) > 1e-9) {
            instance.addTransientModifier(new AttributeModifier(key, amount, op));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        clearOne(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearOne(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        clearOne(event.getPlayer());
    }

    /** Milk cleared every potion: it clears every boss status too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            clearOne(event.getPlayer());
        }
    }
}
