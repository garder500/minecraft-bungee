package win.vampyuyu.survival.movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.movement.MovementMath.Vec;

/**
 * Movement toolkit (GDD 12.5) shared by the Realms and the bosses: controlled knockback, pull toward a point, levitation
 * columns, slow-fall and jump-boost zones, with the common safety rules. A player is "guarded" while a zone covers them
 * or for a few seconds after a push: falling into the void then brings them back to the last solid spot they stood on,
 * and fall damage and void damage never kill. Every zone shows its telegraph (particles) before it acts, ends by itself,
 * and removes its potion effects when the player leaves it.
 *
 * <p>Prototype: the numbers are meant to be validated on the dev server with {@code /vmove} (see
 * docs/skyblock-notes/movement.md).
 */
public final class MovementService implements Listener {
    public enum Kind { LIFT, SLOW_FALL, JUMP, PULL }

    /** A zone: a sphere (pull) or a column (the others). Mutable counters are only touched on the main thread. */
    public static final class Zone {
        final int id;
        final Kind kind;
        final Location center;
        final double radius;
        final double height;
        final double strength;
        final int amp;
        final long telegraphUntil;
        final long endsAt;
        int anchorsLeft;
        final int anchorsTotal;

        Zone(int id, Kind kind, Location center, double radius, double height, double strength, int amp,
             long telegraphUntil, long endsAt, int anchors) {
            this.id = id;
            this.kind = kind;
            this.center = center;
            this.radius = radius;
            this.height = height;
            this.strength = strength;
            this.amp = amp;
            this.telegraphUntil = telegraphUntil;
            this.endsAt = endsAt;
            this.anchorsLeft = anchors;
            this.anchorsTotal = anchors;
        }

        public int id() { return id; }

        public Kind kind() { return kind; }
    }

    private static final int PERIOD = 2;
    private static final long GUARD_AFTER_PUSH_TICKS = 200;
    private static final int EFFECT_TICKS = 10;

    private final Core core;
    private final Map<Integer, Zone> zones = new HashMap<>();
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private final Map<UUID, Long> guardedUntil = new HashMap<>();
    /** Effects this service put on a player, to remove them when the player leaves the zone. */
    private final Map<UUID, Set<PotionEffectType>> owned = new HashMap<>();
    private int nextId = 1;
    private long tick;
    private win.vampyuyu.survival.core.Scheduler.Handle task;

    public MovementService(Core core) {
        this.core = core;
    }

    public void start() {
        task = core.scheduler().repeating(PERIOD, PERIOD, this::tick);
        core.listen(this);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(owned.keySet())) {
            Player p = core.getPlugin().getServer().getPlayer(id);
            if (p != null) {
                clearOwned(p, Set.of());
            }
        }
        zones.clear();
        lastSafe.clear();
        guardedUntil.clear();
        owned.clear();
    }

    // ---------------------------------------------------------------- one-shot pushes

    /** Pushes {@code victim} away from {@code source} (capped, see {@link MovementMath#knockback}). */
    public void knockback(Player victim, Location source, double strength, double lift) {
        Location at = victim.getLocation();
        Vec v = MovementMath.knockback(source.getX(), source.getZ(), at.getX(), at.getZ(), strength, lift);
        victim.setVelocity(new Vector(v.x(), v.y(), v.z()));
        guard(victim);
    }

    /** Shoves {@code victim} along a direction (for a wind gust), capped. */
    public void push(Player victim, Vector direction, double strength) {
        Vector d = direction.clone();
        if (d.lengthSquared() < 1e-9) {
            return;
        }
        d.normalize().multiply(Math.max(0, Math.min(MovementMath.MAX_KNOCKBACK, strength)));
        Vec v = MovementMath.cap(new Vec(d.getX(), d.getY(), d.getZ()));
        victim.setVelocity(new Vector(v.x(), v.y(), v.z()));
        guard(victim);
    }

    // ---------------------------------------------------------------- zones

    /**
     * Opens a zone. It shows its telegraph for {@code telegraphTicks}, then acts for {@code durationTicks}. For a PULL
     * zone {@code anchors} is the number of anchors that can be destroyed with {@link #breakAnchor} to switch it off.
     */
    public Zone open(Kind kind, Location center, double radius, double height, double strength, int amp,
                     int telegraphTicks, int durationTicks, int anchors) {
        long now = tick;
        Zone z = new Zone(nextId++, kind, center.clone(), Math.max(0.5, radius), Math.max(0, height), strength, amp,
            now + Math.max(0, telegraphTicks), now + Math.max(0, telegraphTicks) + Math.max(1, durationTicks), Math.max(0, anchors));
        zones.put(z.id, z);
        return z;
    }

    /** Destroys one anchor of a zone; returns what is left. At zero the pull is off. */
    public int breakAnchor(int zoneId) {
        Zone z = zones.get(zoneId);
        if (z == null) {
            return 0;
        }
        z.anchorsLeft = Math.max(0, z.anchorsLeft - 1);
        return z.anchorsLeft;
    }

    public boolean close(int zoneId) {
        return zones.remove(zoneId) != null;
    }

    public void closeAll() {
        zones.clear();
    }

    public List<Zone> zones() {
        return new ArrayList<>(zones.values());
    }

    private void guard(Player p) {
        guardedUntil.put(p.getUniqueId(), tick + GUARD_AFTER_PUSH_TICKS / PERIOD);
    }

    private boolean guarded(Player p) {
        Long until = guardedUntil.get(p.getUniqueId());
        return until != null && until > tick;
    }

    private void tick() {
        tick++;
        Iterator<Zone> it = zones.values().iterator();
        while (it.hasNext()) {
            if (it.next().endsAt <= tick) {
                it.remove();
            }
        }
        Map<UUID, Set<PotionEffectType>> wanted = new HashMap<>();
        for (Zone z : zones.values()) {
            boolean active = tick >= z.telegraphUntil;
            if (z.center.getWorld() == null) {
                continue;
            }
            telegraph(z, active);
            if (!active) {
                continue;
            }
            for (Player p : z.center.getWorld().getPlayers()) {
                if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || !inside(z, p.getLocation())) {
                    continue;
                }
                guard(p);
                act(z, p, wanted);
            }
        }
        for (Player p : core.getPlugin().getServer().getOnlinePlayers()) {
            clearOwned(p, wanted.getOrDefault(p.getUniqueId(), Set.of()));
            noteSafeSpot(p);
        }
        guardedUntil.values().removeIf(t -> t <= tick);
    }

    private boolean inside(Zone z, Location at) {
        if (z.kind == Kind.PULL) {
            return at.distanceSquared(z.center) <= z.radius * z.radius;
        }
        return MovementMath.inColumn(at.getX(), at.getY(), at.getZ(), z.center.getX(), z.center.getY(), z.center.getZ(),
            z.radius, z.height);
    }

    private void act(Zone z, Player p, Map<UUID, Set<PotionEffectType>> wanted) {
        switch (z.kind) {
            case LIFT -> give(p, wanted, PotionEffectType.LEVITATION, MovementMath.levitationAmp(z.amp));
            case SLOW_FALL -> give(p, wanted, PotionEffectType.SLOW_FALLING, 0);
            case JUMP -> give(p, wanted, PotionEffectType.JUMP_BOOST, MovementMath.jumpAmp(z.amp));
            case PULL -> {
                Location at = p.getLocation().add(0, 1, 0);
                // The counter-measure: sneaking or sprinting away cancels part of the pull; slow falling is not a defence.
                double resist = p.isSneaking() ? 0.5 : 0;
                Vec d = MovementMath.pull(at.getX(), at.getY(), at.getZ(), z.center.getX(), z.center.getY(), z.center.getZ(),
                    z.strength, z.radius, resist, z.anchorsLeft, z.anchorsTotal);
                Vector cur = p.getVelocity();
                Vec next = MovementMath.applyPull(new Vec(cur.getX(), cur.getY(), cur.getZ()), d);
                p.setVelocity(new Vector(next.x(), next.y(), next.z()));
            }
        }
    }

    private void give(Player p, Map<UUID, Set<PotionEffectType>> wanted, PotionEffectType type, int amp) {
        PotionEffect existing = p.getPotionEffect(type);
        // Never override an effect that comes from elsewhere with a stronger one: only refresh our own.
        boolean ours = owned.getOrDefault(p.getUniqueId(), Set.of()).contains(type);
        if (existing == null || ours) {
            p.addPotionEffect(new PotionEffect(type, EFFECT_TICKS, amp, true, false, false));
            owned.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(type);
            wanted.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(type);
        }
    }

    /** Removes the effects this service gave that no zone asks for any more. */
    private void clearOwned(Player p, Set<PotionEffectType> keep) {
        Set<PotionEffectType> mine = owned.get(p.getUniqueId());
        if (mine == null) {
            return;
        }
        Iterator<PotionEffectType> it = mine.iterator();
        while (it.hasNext()) {
            PotionEffectType t = it.next();
            if (!keep.contains(t)) {
                p.removePotionEffect(t);
                it.remove();
            }
        }
        if (mine.isEmpty()) {
            owned.remove(p.getUniqueId());
        }
    }

    private void telegraph(Zone z, boolean active) {
        if (tick % 5 != 0) {
            return;
        }
        Location c = z.center;
        Particle particle = active ? Particle.END_ROD : Particle.SMOKE;
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double y = z.kind == Kind.PULL ? 0 : (tick / 5 % 5) * Math.max(0.5, z.height) / 5;
            c.getWorld().spawnParticle(particle, c.getX() + Math.cos(a) * z.radius, c.getY() + y, c.getZ() + Math.sin(a) * z.radius, 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- safety

    private void noteSafeSpot(Player p) {
        Location at = p.getLocation();
        if (at.getWorld() == null || !p.isOnGround() || at.getY() < at.getWorld().getMinHeight()) {
            return;
        }
        var feet = at.getBlock();
        if (MovementMath.isSafeSpot(p.isOnGround(), feet.getType().isAir() || feet.isPassable(), feet.isLiquid(), at.getY(), at.getWorld().getMinHeight())) {
            lastSafe.put(p.getUniqueId(), at);
        }
    }

    public Location lastSafe(Player p) {
        return lastSafe.get(p.getUniqueId());
    }

    /** A guarded player who would die by falling or in the void is brought back to the last platform, with no damage. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !guarded(p)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location back = lastSafe.get(p.getUniqueId());
            if (back != null && back.getWorld() == p.getWorld()) {
                event.setCancelled(true);
                p.setFallDistance(0);
                p.setVelocity(new Vector(0, 0, 0));
                p.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
                core.msg().warn(p, "You are brought back to the last platform you stood on.");
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL || event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            event.setDamage(MovementMath.safeDamage(p.getHealth(), event.getDamage()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        owned.remove(id);
        lastSafe.remove(id);
        guardedUntil.remove(id);
    }
}
