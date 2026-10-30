package win.vampyuyu.survival.wings;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The flight model. A repeating task (every 2 ticks) looks at each online player: when valid Wings are worn and the
 * rules allow it, {@code allowFlight} is switched on (and only then; a flag we did not set is never touched);
 * flying drains the {@link Energy} bar, resting refills it, running dry ends the flight with a slow fall instead of
 * fall damage. Per player state lives in {@link State}, main thread only.
 */
public final class FlightManager implements Listener {
    /** Player PDC key (namespace vampyuyu): epoch millis until which flight is blocked. Set by any feature, self healing. */
    public static final String NO_FLY_KEY = "no_fly_until";

    private static final long TICK_PERIOD = 2;
    private static final String STAT_SOURCE = "wings";

    /** Mutable per player flight state. */
    static final class State {
        final UUID id;
        Energy energy;
        int level;
        boolean ownsFlight;
        boolean wasFlying;
        boolean emergency;
        boolean slowFallOurs;
        long emergencyStart;
        long lastTick;
        long lastFlight;
        long lastBeat;
        long fullSince;
        long lastMessage;
        boolean lowWarned;
        boolean onIsland;
        boolean statsApplied;
        double lastX, lastY, lastZ;
        boolean moved;
        BossBar bar;
        boolean barShown;
        String barTitle = "";
        float barProgress = -1;
        String lastWorld = "";
        /** Last solid ground the player stood on (world name, so no World is kept alive). */
        String safeWorld;
        double safeX, safeY, safeZ;
        float safeYaw, safeAngle;
        /** Until when void damage is turned into a rescue (during and shortly after a slow fall). */
        long rescueUntil;
        boolean bossNear;
        boolean noGroundBelow;
        boolean gliding;
        long lastDash;
        long lastBurst;

        State(UUID id) {
            this.id = id;
        }
    }

    private final Core core;
    private final WingsService service;
    private volatile WingsSettings settings;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private Scheduler.Handle task;
    private long ticks;

    public FlightManager(Core core, WingsService service, WingsSettings settings) {
        this.core = core;
        this.service = service;
        this.settings = settings;
    }

    void settings(WingsSettings settings) {
        this.settings = settings;
    }

    public void start() {
        task = core.scheduler().repeating(20, TICK_PERIOD, this::tick);
    }

    /** Stops the task and restores every player we touched (plugin disable). */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (State st : new ArrayList<>(states.values())) {
            Player p = Bukkit.getPlayer(st.id);
            if (p != null) {
                finish(p, st, true, System.currentTimeMillis());
            }
        }
        states.clear();
        combatUntil.clear();
        noFallUntil.clear();
    }

    // ---------------------------------------------------------------- queries (tests, GUI)

    boolean hasState(UUID id) {
        return states.containsKey(id);
    }

    State state(UUID id) {
        return states.get(id);
    }

    /** Remaining combat tag in milliseconds. */
    public long combatRemaining(UUID id) {
        return Math.max(0, combatUntil.getOrDefault(id, 0L) - System.currentTimeMillis());
    }

    /** Current energy of a player wearing the Wings, -1 when not tracked. */
    public double energyOf(UUID id) {
        State st = states.get(id);
        return st == null ? -1 : st.energy.value();
    }

    /** Clears the combat tag (tests, admin tools). */
    void combatRemainingReset(UUID id) {
        combatUntil.remove(id);
    }

    /** Applies a combat tag (also used by the listener). */
    public void tagCombat(UUID id) {
        if (settings.combatSeconds > 0) {
            combatUntil.put(id, System.currentTimeMillis() + settings.combatSeconds * 1000L);
        }
    }

    /** True when something forbids flight at the player's position (PDC flag, scoreboard tag or region). */
    public boolean inNoFlyZone(Player p, long now) {
        Long until = p.getPersistentDataContainer().get(new org.bukkit.NamespacedKey("vampyuyu", NO_FLY_KEY),
            PersistentDataType.LONG);
        if (until != null && until > now) {
            return true;
        }
        if (!settings.noFlyTag.isEmpty() && p.getScoreboardTags().contains(settings.noFlyTag)) {
            return true;
        }
        State st = states.get(p.getUniqueId());
        if (st != null && st.bossNear) {
            return true;
        }
        if (settings.regions.isEmpty()) {
            return false;
        }
        Location l = p.getLocation();
        return settings.inRegion(l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    /** The verdict for a player right now, ignoring the energy lockout when {@code st} is null. */
    public FlightRules.Verdict verdict(Player p, int wornLevel, State st, long now) {
        GameMode gm = p.getGameMode();
        return FlightRules.decide(new FlightRules.Input(wornLevel > 0, p.isDead(),
            gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE, settings.worldAllowed(p.getWorld().getName()),
            inNoFlyZone(p, now), combatUntil.getOrDefault(p.getUniqueId(), 0L) - now,
            st != null && st.energy.exhausted()));
    }

    // ---------------------------------------------------------------- main loop

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(p, now);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Wings tick failed for " + p.getName() + ": " + e);
            }
        }
        if (ticks % 10 == 0) {  // every second: ownership enforcement and housekeeping
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    service.scan(p);
                } catch (RuntimeException e) {
                    core.getPlugin().getLogger().warning("Wings scan failed for " + p.getName() + ": " + e);
                }
            }
            combatUntil.values().removeIf(t -> t < now);
            noFallUntil.values().removeIf(t -> t < now);
        }
    }

    void tickPlayer(Player p, long now) {
        UUID id = p.getUniqueId();
        State st = states.get(id);
        int level = service.wornLevel(p);
        if (level == 0) {
            if (st != null) {
                if (st.ownsFlight) {
                    stop(p, st, WingsSettings.Reason.UNEQUIP, now);
                }
                if (st.emergency) {
                    emergencyTick(p, st, now);
                }
                if (!st.emergency) {
                    finish(p, st, true, now);  // the bar and the lockout survive the swap
                    states.remove(id);
                }
            }
            return;
        }
        if (st == null) {
            st = begin(p, level, now);
        }
        if (st.level != level) {
            st.level = level;
            applyStats(p, st);
            st.energy.setCapacity(capacity(p, level));
            if (st.ownsFlight) {
                p.setFlySpeed(settings.flySpeed(level));
            }
        }
        double dt = Math.max(0.01, Math.min(0.5, (now - st.lastTick) / 1000.0));
        st.lastTick = now;
        if (ticks % 10 == 0) {
            st.energy.setCapacity(capacity(p, level));
            st.onIsland = isOnIsland(p);
            st.bossNear = nearBoss(p);
        }
        if (ticks % 5 == 0) {
            recordSafeGround(p, st);
        }
        FlightRules.Verdict verdict = verdict(p, level, st, now);
        if (st.emergency) {
            emergencyTick(p, st, now);
        }
        if (p.isGliding() && glideRefused(verdict)) {
            endGlide(p, st, verdict.reason(), now);
        }

        if (verdict == FlightRules.Verdict.OK) {
            if (!p.getAllowFlight()) {
                p.setAllowFlight(true);
                p.setFlySpeed(settings.flySpeed(level));
                st.ownsFlight = true;
            } else if (st.ownsFlight && Math.abs(p.getFlySpeed() - settings.flySpeed(level)) > 1e-4) {
                p.setFlySpeed(settings.flySpeed(level));
            }
        } else if (st.ownsFlight) {
            stop(p, st, verdict.reason(), now);
        }
        if (!st.lastWorld.equals(p.getWorld().getName())) {
            st.lastWorld = p.getWorld().getName();
            if (verdict == FlightRules.Verdict.WORLD) {
                message(p, st, now, "The Wings lie dormant in this world.");
            }
        }

        boolean flying = st.ownsFlight && p.isFlying();
        Location loc = p.getLocation();
        double dx = loc.getX() - st.lastX, dy = loc.getY() - st.lastY, dz = loc.getZ() - st.lastZ;
        st.moved = dx * dx + dy * dy + dz * dz > 0.0016;
        st.lastX = loc.getX();
        st.lastY = loc.getY();
        st.lastZ = loc.getZ();
        st.gliding = p.isGliding();
        if (flying) {
            flyTick(p, st, level, dt, now);
        } else if (st.gliding && !p.isOnGround()) {
            st.wasFlying = false;
            glideTick(p, st, level, dt, now);
        } else {
            st.wasFlying = false;
            restTick(p, st, level, dt, now);
        }
        if (ticks % 10 == 0) {
            st.noGroundBelow = (flying || st.gliding || st.emergency) && st.energy.fraction() < 0.35
                && noGroundBelow(p);
        }
        updateBar(p, st, verdict, now);
    }

    private static boolean glideRefused(FlightRules.Verdict v) {
        return v == FlightRules.Verdict.WORLD || v == FlightRules.Verdict.ZONE || v == FlightRules.Verdict.COMBAT
            || v == FlightRules.Verdict.EXHAUSTED;
    }

    /** Ends a running glide where the Wings must stay quiet; the reason decides whether a slow fall takes over. */
    private void endGlide(Player p, State st, WingsSettings.Reason reason, long now) {
        p.setGliding(false);
        st.gliding = false;
        if (st.ownsFlight) {  // the flight allowance must not end the slow fall on the next tick
            st.ownsFlight = false;
            st.wasFlying = false;
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setFlySpeed(0.1f);
        }
        if (reason == null) {
            return;
        }
        if (settings.emergencyReasons.contains(reason) && !p.isOnGround()) {
            startEmergency(p, st, now);  // a glide keeps the fall distance low, so do not test it
        }
        switch (reason) {
            case ENERGY -> message(p, st, now, "Out of flight energy! A gentle wind carries you down.");
            case COMBAT -> message(p, st, now, "Your Wings falter: you are in combat.");
            case ZONE -> message(p, st, now, "You cannot glide in this area.");
            case WORLD -> message(p, st, now, "The Wings lie dormant in this world.");
            default -> { }
        }
    }

    /** Gliding costs a little energy; a steep dive gives some back once the level allows it. */
    private void glideTick(Player p, State st, int level, double dt, long now) {
        st.lastFlight = now;  // the regeneration delay starts when the glide ends
        double drain = settings.drainGlide * settings.drainMultiplier(level) * dt;
        if (drain > 0 && st.energy.drain(drain)) {
            endGlide(p, st, WingsSettings.Reason.ENERGY, now);
            return;
        }
        if (level >= settings.diveLevel && settings.diveRegen > 0 && isDiving(p)) {
            st.energy.regen(settings.diveRegen * dt, settings.resumeThreshold(st.energy.capacity()));
        }
        if (st.moved && service.data(p.getUniqueId()).trail && settings.trailEnabled) {
            trail(p, level, false);
        }
    }

    private static boolean isDiving(Player p) {
        return p.getLocation().getPitch() > 25 && p.getVelocity().getY() < -0.4;
    }

    /** A boss (vanilla Boss or the boss scoreboard tag) close by counts as a no-fly zone. */
    boolean nearBoss(Player p) {
        int r = settings.noFlyBossRadius;
        if (r <= 0) {
            return false;
        }
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof LivingEntity && !(e instanceof Player) && !e.isDead()
                && (e instanceof org.bukkit.entity.Boss
                || (!settings.bossTag.isEmpty() && e.getScoreboardTags().contains(settings.bossTag)))) {
                return true;
            }
        }
        return false;
    }

    /** True when nothing solid lies below the player (the void): used for the warning and the rescue. */
    boolean noGroundBelow(Player p) {
        try {
            Location l = p.getLocation();
            if (l.getY() <= l.getWorld().getMinHeight()) {
                return true;
            }
            return l.getWorld().rayTraceBlocks(l, new Vector(0, -1, 0), l.getY() - l.getWorld().getMinHeight(),
                FluidCollisionMode.ALWAYS, true) == null;
        } catch (RuntimeException e) {
            return false;  // a server without ray tracing (test doubles): assume ground
        }
    }

    private void recordSafeGround(Player p, State st) {
        if (st.emergency || p.isFlying() || p.isGliding() || !p.isOnGround() || p.isInsideVehicle() || p.isInWater()
            || p.isDead()) {
            return;
        }
        Location l = p.getLocation();
        if (l.getWorld() == null || l.getY() <= l.getWorld().getMinHeight() + 1) {
            return;
        }
        st.safeWorld = l.getWorld().getName();
        st.safeX = l.getX();
        st.safeY = l.getY();
        st.safeZ = l.getZ();
        st.safeYaw = l.getYaw();
        st.safeAngle = l.getPitch();
    }

    private void flyTick(Player p, State st, int level, double dt, long now) {
        if (!st.wasFlying) {
            st.wasFlying = true;
            if (st.energy.full() || st.energy.fraction() > 0.5) {
                sound(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.25f, 1.7f);
            }
        }
        st.lastFlight = now;
        boolean sprint = p.isSprinting();
        double drain = settings.drainPerSecond(level, sprint, !st.moved, st.onIsland) * dt;
        if (st.energy.drain(drain)) {
            stop(p, st, WingsSettings.Reason.ENERGY, now);
            return;
        }
        double low = settings.lowFraction;
        if (!st.lowWarned && st.energy.fraction() < low) {
            st.lowWarned = true;
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.7f);
        }
        WingsData data = service.data(p.getUniqueId());
        if (st.moved) {
            if (data.trail && settings.trailEnabled) {
                trail(p, level, sprint);
            }
            long interval = Math.max(2, settings.beatIntervalTicks / (sprint ? 2 : 1)) * 50L;
            if (data.sounds && settings.sounds && now - st.lastBeat >= interval) {
                st.lastBeat = now;
                sound(p, Sound.ENTITY_PHANTOM_FLAP, 0.22f, sprint ? 1.7f : 1.3f);
            }
        }
    }

    private void restTick(Player p, State st, int level, double dt, long now) {
        if (st.energy.full()) {
            if (st.fullSince == 0) {
                st.fullSince = now;
            }
            return;
        }
        boolean grounded = p.isOnGround() || p.isInsideVehicle() || p.isInWater();
        if (!grounded || p.isFlying() || now - st.lastFlight < settings.regenDelayTicks * 50L) {
            return;
        }
        st.fullSince = 0;
        st.energy.regen(settings.regenPerSecond(level, st.onIsland) * dt,
            settings.resumeThreshold(st.energy.capacity()));
        if (st.lowWarned && st.energy.fraction() > settings.lowFraction * 1.5) {
            st.lowWarned = false;
        }
    }

    private void trail(Player p, int level, boolean sprint) {
        WingsSettings.Trail t = settings.trail(level);
        if (t.count() <= 0) {
            return;
        }
        Location at = p.getLocation();
        Vector back = at.getDirection().setY(0);
        if (back.lengthSquared() > 1e-6) {
            back.normalize().multiply(-0.45);
        }
        at.add(back).add(0, 1.1, 0);
        int count = sprint ? t.count() * 2 : t.count();
        p.getWorld().spawnParticle(t.particle(), at, count, t.spread(), 0.25, t.spread(), 0.0);
    }

    private void sound(Player p, Sound sound, float volume, float pitch) {
        p.getWorld().playSound(p.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private boolean isOnIsland(Player p) {
        return core.service(IslandApi.class).map(api -> api.islandAt(p.getLocation()).isPresent()).orElse(false);
    }

    // ---------------------------------------------------------------- start, stop, emergency

    private State begin(Player p, int level, long now) {
        State st = new State(p.getUniqueId());
        st.level = level;
        applyStats(p, st);
        double capacity = capacity(p, level);
        WingsData data = service.data(p.getUniqueId());
        st.energy = new Energy(capacity);
        if (data.energy >= 0 || data.exhausted) {
            // the bar and the lockout come back as they were left, plus the time away at the ground recharge rate
            st.energy.restore(data.energy >= 0 ? Math.min(capacity, data.energy) : 0, data.exhausted);
            if (data.energySavedAt > 0 && now > data.energySavedAt) {
                double away = Math.min(3600, (now - data.energySavedAt) / 1000.0);
                st.energy.regen(away * settings.regenGround * settings.regenMultiplier(level),
                    settings.resumeThreshold(capacity));
            }
        }
        data.energy = -1;  // consumed: the live bar is the truth until the Wings leave the chest slot again
        data.exhausted = false;
        data.energySavedAt = 0;
        core.profiles().markDirty(p.getUniqueId());
        st.bossNear = nearBoss(p);
        st.lastTick = now;
        st.lastWorld = p.getWorld().getName();
        st.onIsland = isOnIsland(p);
        Location l = p.getLocation();
        st.lastX = l.getX();
        st.lastY = l.getY();
        st.lastZ = l.getZ();
        states.put(p.getUniqueId(), st);
        return st;
    }

    /** Capacity of the bar: the Flight Energy stat when the stats engine exists, never below the Wings' own value. */
    double capacity(Player p, int level) {
        double own = Stat.FLIGHT_ENERGY.base() + settings.capacityBonus(level);
        return core.service(StatsApi.class).map(api -> Math.max(own, api.stat(p, Stat.FLIGHT_ENERGY))).orElse(own);
    }

    private void applyStats(Player p, State st) {
        core.service(StatsApi.class).ifPresent(api -> {
            api.addModifier(p.getUniqueId(), STAT_SOURCE, Stat.FLIGHT_ENERGY, settings.capacityBonus(st.level));
            settings.statBonuses(st.level).forEach((stat, amount) -> api.addModifier(p.getUniqueId(), STAT_SOURCE, stat, amount));
            api.recompute(p);
            st.statsApplied = true;
        });
    }

    /** Ends the flight now. The reason decides whether the slow fall protects the player. */
    void stop(Player p, State st, WingsSettings.Reason reason, long now) {
        boolean wasFlying = p.isFlying();
        boolean air = !p.isOnGround();
        st.ownsFlight = false;
        st.wasFlying = false;
        if (p.getAllowFlight()) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.setFlySpeed(0.1f);
        if (reason != null && settings.emergencyReasons.contains(reason) && air
            && (wasFlying || p.getFallDistance() > 2)) {
            startEmergency(p, st, now);
        }
        if (reason == null) {
            return;
        }
        switch (reason) {
            case ENERGY -> message(p, st, now, "Out of flight energy! A gentle wind carries you down.");
            case COMBAT -> message(p, st, now, "Your Wings falter: you are in combat.");
            case ZONE -> message(p, st, now, "You cannot fly in this area.");
            case WORLD -> message(p, st, now, "The Wings lie dormant in this world.");
            default -> { }
        }
    }

    private void startEmergency(Player p, State st, long now) {
        if (st.emergency) {
            return;
        }
        st.emergency = true;
        st.emergencyStart = now;
        st.rescueUntil = now + settings.emergencyMaxSeconds * 1000L + 5000L;
        noFallUntil.put(p.getUniqueId(), now + settings.emergencyMaxSeconds * 1000L + 3000L);
        PotionEffect existing = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if (existing == null || existing.getDuration() < 40) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, settings.emergencyMaxSeconds * 20, 0,
                false, false, true));
            st.slowFallOurs = true;
        }
    }

    private void emergencyTick(Player p, State st, long now) {
        boolean landed = p.isOnGround() || p.isInWater() || p.isClimbing() || p.isInsideVehicle();
        boolean timeout = now - st.emergencyStart > settings.emergencyMaxSeconds * 1000L;
        if (settings.voidRescue && !landed && !st.ownsFlight && (p.getLocation().getY()
            <= p.getWorld().getMinHeight() + 2 || (timeout && noGroundBelow(p)))) {
            rescue(p, st, now);  // the void is below: never let the slow fall run out over it
            return;
        }
        if (st.ownsFlight || landed || timeout) {
            endEmergency(p, st, now);
        }
    }

    /**
     * Brings a player who would fall into the void back to the last solid ground they stood on in this world, else
     * to their island home, else to the world spawn. Returns false when nothing could be done.
     */
    boolean rescue(Player p, State st, long now) {
        World world = p.getWorld();
        boolean done = false;
        if (st.safeWorld != null && st.safeWorld.equals(world.getName())) {
            done = p.teleport(new Location(world, st.safeX, st.safeY, st.safeZ, st.safeYaw, st.safeAngle));
        }
        if (!done) {
            done = core.service(IslandApi.class).map(api -> api.teleportHome(p)).orElse(false);
        }
        if (!done) {
            done = p.teleport(world.getSpawnLocation());
        }
        if (!done) {
            return false;
        }
        p.setFallDistance(0);
        p.setVelocity(new Vector(0, 0, 0));
        endEmergency(p, st, now);
        noFallUntil.put(p.getUniqueId(), now + 3000L);
        st.rescueUntil = now + 3000L;
        core.msg().info(p, "The Wings caught you above the void and carried you to safety.");
        return true;
    }

    private void endEmergency(Player p, State st, long now) {
        if (st.slowFallOurs) {
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
        st.slowFallOurs = false;
        st.emergency = false;
        st.rescueUntil = now + 4000L;
        noFallUntil.put(p.getUniqueId(), now + 1500L);  // the landing tick still reports the fall distance
    }

    /** Drops everything we set on the player: flight, speed, slow fall, bar, stat modifier. */
    void finish(Player p, State st, boolean saveEnergy, long now) {
        if (st.ownsFlight) {
            st.ownsFlight = false;
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.setFlySpeed(0.1f);
        if (st.slowFallOurs) {
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            st.slowFallOurs = false;
        }
        st.emergency = false;
        hideBar(p, st);
        if (st.statsApplied) {
            core.service(StatsApi.class).ifPresent(api -> {
                api.removeModifiers(p.getUniqueId(), STAT_SOURCE);
                if (p.isOnline()) {
                    api.recompute(p);
                }
            });
            st.statsApplied = false;
        }
        if (saveEnergy) {
            WingsData data = service.data(p.getUniqueId());
            data.energy = st.energy.full() ? -1 : st.energy.value();
            data.exhausted = st.energy.exhausted();
            data.energySavedAt = now;
            core.profiles().markDirty(p.getUniqueId());
        }
    }

    /** Re-reads level and speed now (after an upgrade, admin give or revoke). */
    public void refresh(Player p) {
        tickPlayer(p, System.currentTimeMillis());
    }

    private void message(Player p, State st, long now, String text) {
        if (now - st.lastMessage >= 3000) {
            st.lastMessage = now;
            core.msg().warn(p, text);
        }
    }

    // ---------------------------------------------------------------- boss bar

    private void updateBar(Player p, State st, FlightRules.Verdict verdict, long now) {
        WingsData data = service.data(p.getUniqueId());
        boolean busy = p.isFlying() || st.gliding || !st.energy.full();
        if (st.energy.full() && st.fullSince == 0) {
            st.fullSince = now;
        }
        boolean linger = st.energy.full() && now - st.fullSince < settings.barHideSeconds * 1000L;
        boolean blocked = verdict == FlightRules.Verdict.COMBAT || verdict == FlightRules.Verdict.ZONE
            || verdict == FlightRules.Verdict.EXHAUSTED;
        boolean show = data.bar && verdict != FlightRules.Verdict.WORLD && (blocked || busy || linger);
        if (!show) {
            hideBar(p, st);
            return;
        }
        BossBar.Color color;
        String title;
        long combat = combatUntil.getOrDefault(p.getUniqueId(), 0L) - now;
        if (verdict == FlightRules.Verdict.COMBAT) {
            title = "Wings disabled: combat " + ((combat + 999) / 1000) + "s";
            color = BossBar.Color.RED;
        } else if (verdict == FlightRules.Verdict.ZONE) {
            title = "Wings disabled in this area";
            color = BossBar.Color.RED;
        } else if (verdict == FlightRules.Verdict.EXHAUSTED) {
            title = "Recharging... " + Math.round(st.energy.value()) + "/" + Math.round(st.energy.capacity());
            color = BossBar.Color.PURPLE;
        } else {
            title = "Flight Energy " + Math.round(st.energy.value()) + "/" + Math.round(st.energy.capacity());
            double perSecond = p.isFlying() ? settings.drainPerSecond(st.level, p.isSprinting(), !st.moved, st.onIsland)
                : (st.gliding ? settings.drainGlide * settings.drainMultiplier(st.level) : 0);
            if (perSecond > 0) {
                title += "  (~" + Math.round(st.energy.value() / perSecond) + "s left)";
            }
            title += "  (Wings Lv " + st.level + ")";
            double f = st.energy.fraction();
            color = f < settings.lowFraction ? BossBar.Color.RED : (f < 0.5 ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
            if (st.noGroundBelow) {
                title = "NO GROUND BELOW! " + title;
                color = BossBar.Color.RED;
            }
        }
        float progress = (float) Math.max(0, Math.min(1, st.energy.fraction()));
        if (st.bar == null) {
            st.bar = BossBar.bossBar(Component.text(title, NamedTextColor.GOLD), progress, color,
                BossBar.Overlay.NOTCHED_20);
            st.barTitle = title;
            st.barProgress = progress;
        } else {
            if (!title.equals(st.barTitle)) {
                st.bar.name(Component.text(title, NamedTextColor.GOLD));
                st.barTitle = title;
            }
            if (Math.abs(progress - st.barProgress) > 0.002f) {
                st.bar.progress(progress);
                st.barProgress = progress;
            }
            if (st.bar.color() != color) {
                st.bar.color(color);
            }
        }
        if (!st.barShown) {
            p.showBossBar(st.bar);
            st.barShown = true;
        }
    }

    private void hideBar(Player p, State st) {
        if (st.bar != null && st.barShown) {
            p.hideBossBar(st.bar);
        }
        st.barShown = false;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player p)) {
            return;
        }
        State st = states.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if ((st != null && st.emergency) || noFallUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
            event.setCancelled(true);
            return;
        }
        if (st != null && st.level >= settings.landingLevel && now - st.lastBurst >= settings.landingCooldownMillis
            && service.wornLevel(p) > 0) {
            st.lastBurst = now;  // landing burst: a cushion of wind absorbs the fall
            event.setCancelled(true);
            Location at = p.getLocation();
            p.getWorld().spawnParticle(Particle.CLOUD, at, 24, 0.6, 0.1, 0.6, 0.05);
            sound(p, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.8f);
            core.msg().info(p, "A burst of wind from your Wings cushions the landing.");
        }
    }

    /** Void damage during or just after a slow fall becomes a rescue instead of a death. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player p)
            || !settings.voidRescue) {
            return;
        }
        State st = states.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (st != null && (st.emergency || st.rescueUntil > now) && rescue(p, st, now)) {
            event.setCancelled(true);
        }
    }

    /** Rocket boosts follow the same rules as gliding and cost energy. */
    @EventHandler(ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        Player p = event.getPlayer();
        int level = service.wornLevel(p);
        State st = states.get(p.getUniqueId());
        if (level == 0 || st == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (glideRefused(verdict(p, level, st, now))) {
            event.setCancelled(true);
            message(p, st, now, "The Wings refuse the firework here.");
            return;
        }
        if (st.energy.value() < settings.fireworkCost) {
            event.setCancelled(true);
            message(p, st, now, "Not enough flight energy for a rocket boost.");
            return;
        }
        st.energy.drain(settings.fireworkCost);
    }

    /** Swap-hands key in the air: the dash (unlocked by level). Only consumes the key when it dashes or is on cooldown. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        State st = states.get(p.getUniqueId());
        if (st == null || !settings.dashEnabled || st.level < settings.dashLevel
            || (p.isOnGround() && !p.isFlying()) || p.isInsideVehicle()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (verdict(p, st.level, st, now) != FlightRules.Verdict.OK) {
            return;
        }
        event.setCancelled(true);
        if (now - st.lastDash < settings.dashCooldownMillis) {
            message(p, st, now, "Your Wings are still recovering from the dash.");
            return;
        }
        if (st.energy.value() < settings.dashCost + 1) {
            message(p, st, now, "Not enough flight energy to dash.");
            return;
        }
        st.lastDash = now;
        st.energy.drain(settings.dashCost);
        p.setVelocity(p.getLocation().getDirection().normalize().multiply(settings.dashPower));
        sound(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.5f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.05);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (settings.combatSeconds <= 0) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            damager = shooter;
        }
        if (event.getEntity() instanceof Player victim && isTaggingSource(damager, victim)) {
            tagCombat(victim.getUniqueId());
        }
        if (settings.combatTagAttackers && damager instanceof Player attacker && event.getEntity() instanceof Player
            && settings.combatFromPlayers && !attacker.equals(event.getEntity())) {
            tagCombat(attacker.getUniqueId());
        }
    }

    boolean isTaggingSource(Entity damager, Player victim) {
        if (damager instanceof Player other) {
            return settings.combatFromPlayers && !other.equals(victim);
        }
        if (isBoss(damager)) {
            return settings.combatFromBosses;
        }
        return damager instanceof LivingEntity && settings.combatFromMobs;
    }

    /** A vanilla boss, a mob carrying the boss scoreboard tag, or a mob with at least the configured max health. */
    boolean isBoss(Entity e) {
        if (e instanceof org.bukkit.entity.Boss) {
            return true;
        }
        if (!settings.bossTag.isEmpty() && e.getScoreboardTags().contains(settings.bossTag)) {
            return true;
        }
        if (e instanceof LivingEntity living && living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            return living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() >= settings.bossMinHealth;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        State st = states.get(p.getUniqueId());
        if (st == null || !st.ownsFlight || !event.isFlying()) {
            return;
        }
        if (!service.data(p.getUniqueId()).doubleJump) {
            event.setCancelled(true);  // double jump is off: use /wings fly
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player p)) {
            return;
        }
        int level = service.wornLevel(p);
        if (level == 0) {
            return;
        }
        FlightRules.Verdict v = verdict(p, level, states.get(p.getUniqueId()), System.currentTimeMillis());
        if (glideRefused(v)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmor(PlayerArmorChangeEvent event) {
        Player p = event.getPlayer();
        core.scheduler().nextTick(() -> {
            if (p.isOnline()) {
                tickPlayer(p, System.currentTimeMillis());
            }
        });
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        core.scheduler().nextTick(() -> {
            if (p.isOnline()) {
                tickPlayer(p, System.currentTimeMillis());
            }
        });
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        State st = states.get(event.getPlayer().getUniqueId());
        if (st != null) {
            st.ownsFlight = false;  // the game mode rewrites the abilities itself
            st.wasFlying = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        State st = states.get(p.getUniqueId());
        if (st != null) {
            WingsData data = service.data(p.getUniqueId());
            data.energy = -1;  // a respawn restores the bar
            data.exhausted = false;
            data.energySavedAt = 0;
            finish(p, st, false, System.currentTimeMillis());
            states.remove(p.getUniqueId());
        }
        combatUntil.remove(p.getUniqueId());
        noFallUntil.remove(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later(2, () -> {
            if (p.isOnline()) {
                service.scan(p);
                tickPlayer(p, System.currentTimeMillis());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        WingsData data = service.data(p.getUniqueId());
        if (!data.prefsInitialised) {
            data.prefsInitialised = true;
            data.trail = settings.prefTrail;
            data.bar = settings.prefBar;
            data.doubleJump = settings.prefDoubleJump;
            data.sounds = settings.prefSounds;
            core.profiles().markDirty(p.getUniqueId());
        }
        core.scheduler().later(5, () -> {
            if (p.isOnline()) {
                service.scan(p);
                tickPlayer(p, System.currentTimeMillis());
            }
        });
    }

    /** Runs before the profile store saves the player, so the energy is part of the saved document. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        State st = states.remove(p.getUniqueId());
        if (st != null) {
            finish(p, st, true, System.currentTimeMillis());
        }
        combatUntil.remove(p.getUniqueId());
        noFallUntil.remove(p.getUniqueId());
    }

    // ---------------------------------------------------------------- command helpers

    /** /wings fly: starts or stops flight without the double jump. Returns a message to show on refusal, null on success. */
    public String toggleFly(Player p) {
        State st = states.get(p.getUniqueId());
        int level = service.wornLevel(p);
        if (level == 0 || st == null) {
            return "Wear The Wings in your chest slot first.";
        }
        FlightRules.Verdict v = verdict(p, level, st, System.currentTimeMillis());
        switch (v) {
            case OK -> { }
            case WORLD -> {
                return "The Wings lie dormant in this world.";
            }
            case ZONE -> {
                return "You cannot fly in this area.";
            }
            case COMBAT -> {
                return "You cannot fly while in combat.";
            }
            case EXHAUSTED -> {
                return "Out of energy: wait for the bar to recharge.";
            }
            default -> {
                return "You cannot fly right now.";
            }
        }
        if (!p.getAllowFlight()) {
            tickPlayer(p, System.currentTimeMillis());
        }
        if (!st.ownsFlight) {
            return "You cannot fly right now.";
        }
        p.setFlying(!p.isFlying());
        return null;
    }
}
