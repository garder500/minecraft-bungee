package win.vampyuyu.survival.boss;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/**
 * One living boss fight: the entity, its virtual health, the damage ledger, phases, ability scheduling, the boss bar
 * and everything it spawned. Main thread only. Created and driven by {@link BossManager}.
 */
public final class BossSession {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Why a session ended. */
    public enum End {
        KILLED, TIMEOUT, DESPAWN, OWNER_LEFT, OWNER_DIED, ADMIN, SHUTDOWN, WORLD_UNLOAD,
        /** The entity disappeared (chunk unloaded, removed by another plugin). */
        ENTITY_LOST,
        /** An exception in the update loop or at spawn: the server's fault, always refunded. */
        ERROR
    }

    /** Passed to end listeners. */
    public record EndInfo(BossSession session, End reason, @Nullable Player killer) {}

    /** Options of one spawn. */
    public record SpawnOptions(boolean personal, boolean worldEvent, int slayerTier, double hpOverride,
            int timeLimitSeconds, @Nullable Consumer<EndInfo> onEnd, @Nullable String refundItem) {
        public static SpawnOptions regular() {
            return new SpawnOptions(false, false, 0, 0, -1, null, null);
        }
    }

    final BossManager manager;
    final Core core;
    final BossDefinition def;
    final BossSettings settings;
    final UUID sessionId = UUID.randomUUID();
    final Location anchor;
    final World world;
    final @Nullable UUID summoner;
    final @Nullable UUID owner;
    final SpawnOptions options;
    final DamageLedger ledger = new DamageLedger();
    final PhaseTracker tracker;
    final Random random = new Random();
    final Set<UUID> minions = new HashSet<>();
    final Map<UUID, Integer> crystals = new HashMap<>();
    private final List<Scheduler.Handle> handles = new ArrayList<>();
    private final Map<Long, Long> nextCast = new HashMap<>();
    private final Set<UUID> barViewers = new HashSet<>();
    private final Map<UUID, Long> contactCooldown = new HashMap<>();
    private final List<Consumer<EndInfo>> endListeners = new ArrayList<>();
    /** Players whose credited damage reached the scaling threshold (they grow the health pool). */
    private final Set<UUID> scalers = new HashSet<>();
    /** Chunk tickets held for the life of the fight, key = packed chunk coordinates. */
    private final Set<Long> heldChunks = new HashSet<>();
    private boolean ticketsUnsupported;
    private long lastEntityChunk = Long.MIN_VALUE;
    private boolean abilityHit;
    private final List<UUID> numbers = new ArrayList<>();
    private double pendingNumber;
    private long nextNumberAt;
    private String lastNameplate = "";

    LivingEntity entity;
    UUID entityId;
    double baseMaxHp;
    double maxHp;
    double hp;
    long ticks;
    long gcdUntil;
    long lastPlayerTick;
    long startMillis = System.currentTimeMillis();
    boolean ended;
    End endReason;
    boolean shielded;
    long shieldDeadline;
    double shieldHealPerTick;
    int shieldNovaTicks;
    double shieldNovaDamage;
    long vulnerableUntil;
    double vulnerableMult = 1.25;
    long casting;
    double baseMoveSpeed;
    private BossBar bar;
    private double enrageNotified;
    private boolean enraged;
    private List<Player> playerCache = List.of();
    private long playerCacheTick = -1;

    BossSession(BossManager manager, BossDefinition def, Location anchor, @Nullable Player summoner,
            SpawnOptions options) {
        this.manager = manager;
        this.core = manager.core();
        this.def = def;
        this.settings = manager.settings();
        this.anchor = anchor.clone();
        this.world = anchor.getWorld();
        this.summoner = summoner == null ? null : summoner.getUniqueId();
        this.owner = options.personal() && summoner != null ? summoner.getUniqueId() : null;
        this.options = options;
        this.tracker = PhaseTracker.of(def.phases());
        if (options.onEnd() != null) {
            endListeners.add(options.onEnd());
        }
    }

    // ---------------------------------------------------------------- start

    /** Spawns the entity and starts the fight. False when the spawn was refused. */
    boolean begin() {
        holdChunks(anchor);
        LivingEntity spawned = BossEntities.spawnBoss(def, settings, anchor, sessionId.toString(), 1.0);
        if (spawned == null) {
            releaseChunks();
            return false;
        }
        entity = spawned;
        entityId = spawned.getUniqueId();
        baseMoveSpeed = BossEntities.moveSpeed(spawned);
        double health = options.hpOverride() > 0 ? options.hpOverride() : def.health() * settings.hpScale();
        baseMaxHp = Math.max(1, health);
        maxHp = baseMaxHp;
        hp = maxHp;
        lastPlayerTick = 0;
        if (owner != null) {
            hideFromEveryoneButOwner(spawned);
        }
        if (spawned instanceof EnderDragon dragon) {
            try {
                dragon.setPodium(anchor);
                dragon.setPhase(EnderDragon.Phase.CIRCLING);
            } catch (RuntimeException e) {
                core.debug("dragon podium/phase unsupported: " + e.getMessage());
            }
        }
        bar = BossBar.bossBar(barTitle(), 1f, BossBar.Color.valueOf(def.barColor()),
            BossBar.Overlay.valueOf(def.barOverlay()));
        Fx.sound(world, anchor, Sound.ENTITY_WITHER_SPAWN, 2f, 0.7f);
        Fx.particle(world, Particle.EXPLOSION_EMITTER, anchor, 1, 0, 0, 0, 0);
        BossDefinition.Phase first = def.phases().get(0);
        announcePhase(first, true);
        castEnter(first);
        return true;
    }

    private void hideFromEveryoneButOwner(org.bukkit.entity.Entity e) {
        try {
            e.setVisibleByDefault(false);
            Player p = owner == null ? null : Bukkit.getPlayer(owner);
            if (p != null) {
                p.showEntity(core.getPlugin(), e);
            }
        } catch (RuntimeException ex) {
            core.debug("personal visibility unsupported: " + ex.getMessage());
        }
    }

    void addEndListener(Consumer<EndInfo> listener) {
        endListeners.add(listener);
    }

    // ---------------------------------------------------------------- accessors

    public BossDefinition definition() { return def; }

    public LivingEntity entity() { return entity; }

    public UUID entityId() { return entityId; }

    public Location anchor() { return anchor.clone(); }

    public World world() { return world; }

    public double hp() { return hp; }

    public double maxHp() { return maxHp; }

    public double fraction() { return BossMath.fraction(hp, maxHp); }

    public DamageLedger ledger() { return ledger; }

    public boolean isEnded() { return ended; }

    public boolean isPersonal() { return owner != null; }

    public @Nullable UUID owner() { return owner; }

    public @Nullable UUID summoner() { return summoner; }

    public boolean isShielded() { return shielded; }

    public int currentPhase() { return tracker.current(); }

    public SpawnOptions options() { return options; }

    public long elapsedMillis() { return System.currentTimeMillis() - startMillis; }

    BossDefinition.Phase phase() { return def.phases().get(tracker.current()); }

    /** Current boss position (the anchor when the entity is gone). */
    public Location location() {
        return entity != null && entity.isValid() ? entity.getLocation() : anchor.clone();
    }

    // ---------------------------------------------------------------- multipliers

    long enrageAfterTicks() {
        return def.arena().enrageSeconds() * 20L;
    }

    double enrageMult() {
        return BossMath.enrageMultiplier(ticks, enrageAfterTicks(), def.arena().enrageDamage(), def.arena().enrageGrowth());
    }

    /** Damage a hit of this boss deals right now, multiplier {@code mult} of the base damage. */
    double hitDamage(double mult) {
        return def.damage() * mult * phase().damageMult() * enrageMult() * settings.damageScale();
    }

    // ---------------------------------------------------------------- players

    /** Fightable players in the arena (cached for the current tick). Only the owner of a personal boss. */
    List<Player> players() {
        if (playerCacheTick == ticks) {
            return playerCache;
        }
        List<Player> list = new ArrayList<>();
        double radius = def.arena().radius() + 8;
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (owner != null && !p.getUniqueId().equals(owner)) {
                    continue;
                }
                if (Fx.targetable(p) && Fx.flat(p.getLocation(), anchor) <= radius) {
                    list.add(p);
                }
            }
        }
        playerCache = list;
        playerCacheTick = ticks;
        return list;
    }

    /** Picks up to {@code count} players by targeting mode. */
    List<Player> targets(String mode, int count) {
        List<Player> list = new ArrayList<>(players());
        Location from = location();
        switch (mode) {
            case "random" -> Collections.shuffle(list, random);
            case "top" -> list.sort((a, b) -> Double.compare(ledger.damageOf(b.getUniqueId()), ledger.damageOf(a.getUniqueId())));
            case "far" -> list.sort((a, b) -> Double.compare(b.getLocation().distanceSquared(from), a.getLocation().distanceSquared(from)));
            case "all" -> { }
            default -> list.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(from), b.getLocation().distanceSquared(from)));
        }
        return mode.equals("all") ? list : list.subList(0, Math.min(Math.max(1, count), list.size()));
    }

    /** Damages a player on behalf of the boss. */
    void hit(Player player, double mult) {
        if (ended || !Fx.targetable(player) || entity == null || !entity.isValid()) {
            return;
        }
        double amount = hitDamage(mult);
        if (amount > 0) {
            abilityHit = true; // the damage listener must not replace this amount with the plain contact damage
            try {
                player.damage(amount, entity);
            } finally {
                abilityHit = false;
            }
        }
    }

    /** Tests only. */
    void setAbilityHitForTest(boolean value) {
        abilityHit = value;
    }

    /** True while {@link #hit} is delivering an ability hit (the damage event is synchronous). */
    boolean inAbilityHit() {
        return abilityHit;
    }

    /**
     * Whether the arena rules (no building, no pearls) bind this player. A fight someone summoned only restrains the
     * summoner and the players who hurt it; a public event (no summoner) restrains everybody around it.
     */
    boolean restrains(Player player) {
        return summoner == null || summoner.equals(player.getUniqueId()) || ledger.contains(player.getUniqueId());
    }

    // ---------------------------------------------------------------- chunks

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * Keeps the chunks around {@code centre} (and around the anchor) loaded for the life of the fight: the boss is not
     * persistent, so an unloaded chunk would silently take it away while players are still walking to the arena.
     */
    private void holdChunks(Location centre) {
        if (ticketsUnsupported || world == null) {
            return;
        }
        Set<Long> wanted = new HashSet<>();
        for (Location base : List.of(anchor, centre)) {
            int cx = base.getBlockX() >> 4;
            int cz = base.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    wanted.add(chunkKey(cx + dx, cz + dz));
                }
            }
        }
        try {
            for (Long key : wanted) {
                if (heldChunks.add(key)) {
                    manager.holdChunk(world, (int) (key >> 32), (int) (long) key);
                }
            }
            for (Long key : new ArrayList<>(heldChunks)) {
                if (!wanted.contains(key)) {
                    heldChunks.remove(key);
                    manager.releaseChunk(world, (int) (key >> 32), (int) (long) key);
                }
            }
        } catch (RuntimeException e) {
            ticketsUnsupported = true;
            core.debug("chunk tickets unsupported: " + e.getMessage());
        }
    }

    private void releaseChunks() {
        if (world == null) {
            return;
        }
        for (Long key : heldChunks) {
            try {
                manager.releaseChunk(world, (int) (key >> 32), (int) (long) key);
            } catch (RuntimeException e) {
                core.debug("chunk ticket release failed: " + e.getMessage());
            }
        }
        heldChunks.clear();
    }

    /** Number of chunk tickets currently held (tests). */
    int heldChunkCount() {
        return heldChunks.size();
    }

    // ---------------------------------------------------------------- scheduling

    Scheduler.Handle later(long delayTicks, Runnable task) {
        Scheduler.Handle[] self = new Scheduler.Handle[1];
        self[0] = core.scheduler().later(Math.max(1, delayTicks), () -> {
            handles.remove(self[0]);
            if (!ended) {
                run(task);
            }
        });
        handles.add(self[0]);
        return self[0];
    }

    /** Repeats {@code step} {@code times} times; {@code done} runs after the last step (not when the session ends). */
    Scheduler.Handle every(long delay, long period, int times, IntConsumer step, @Nullable Runnable done) {
        int[] n = {0};
        Scheduler.Handle[] self = new Scheduler.Handle[1];
        self[0] = core.scheduler().repeating(Math.max(1, delay), Math.max(1, period), () -> {
            if (ended) {
                self[0].cancel();
                return;
            }
            try {
                step.accept(n[0]);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Boss ability step failed for " + def.id(), e);
                n[0] = times;
            }
            if (++n[0] >= times) {
                self[0].cancel();
                handles.remove(self[0]);
                if (done != null && !ended) {
                    run(done);
                }
            }
        });
        handles.add(self[0]);
        return self[0];
    }

    /** Like {@link #every} but {@code step} returns false to stop early. */
    Scheduler.Handle everyWhile(long delay, long period, int max, IntPredicate step) {
        int[] n = {0};
        Scheduler.Handle[] self = new Scheduler.Handle[1];
        self[0] = core.scheduler().repeating(Math.max(1, delay), Math.max(1, period), () -> {
            boolean keep = false;
            if (!ended) {
                try {
                    keep = step.test(n[0]);
                } catch (RuntimeException e) {
                    core.getPlugin().getLogger().log(Level.WARNING, "Boss ability step failed for " + def.id(), e);
                }
            }
            if (!keep || ++n[0] >= max) {
                self[0].cancel();
                handles.remove(self[0]);
            }
        });
        handles.add(self[0]);
        return self[0];
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Boss task failed for " + def.id(), e);
        }
    }

    // ---------------------------------------------------------------- tick

    /** One update, every {@code settings.tickPeriod()} ticks. */
    void tick() {
        if (ended) {
            return;
        }
        ticks += settings.tickPeriod();
        if (entity == null || !entity.isValid() || entity.isDead()) {
            end(End.ENTITY_LOST, null);
            return;
        }
        if (world == null || !world.equals(entity.getWorld())) {
            end(End.ENTITY_LOST, null);
            return;
        }
        List<Player> inArena = players();
        if (!inArena.isEmpty()) {
            lastPlayerTick = ticks;
        }
        Location now = entity.getLocation();
        long chunk = chunkKey(now.getBlockX() >> 4, now.getBlockZ() >> 4);
        if (chunk != lastEntityChunk) {
            lastEntityChunk = chunk;
            holdChunks(now);
        }
        int idle = def.arena().idleDespawnSeconds();
        // a world event is public and has its own time limit; players need time to travel to it
        if (idle > 0 && !options.worldEvent() && ticks - lastPlayerTick > idle * 20L) {
            end(End.DESPAWN, null);
            return;
        }
        int limit = options.timeLimitSeconds() >= 0 ? options.timeLimitSeconds() : def.arena().timeLimitSeconds();
        if (limit > 0 && ticks > limit * 20L) {
            end(End.TIMEOUT, null);
            return;
        }
        leash();
        targetOwner();
        updateEnrage();
        updateShield();
        contactDamage(inArena);
        if (!shielded && ticks >= gcdUntil && ticks >= casting && !inArena.isEmpty()) {
            castNext();
        }
        updateBar();
        updateNameplate();
        flushDamage();
    }

    private void leash() {
        Location at = entity.getLocation();
        double minY = world.getMinHeight() - 4;
        boolean far = BossMath.outsideLeash(at.getX() - anchor.getX(), at.getZ() - anchor.getZ(),
            def.arena().leash(), at.getY(), minY);
        boolean tooLow = at.getY() < anchor.getY() - 48 && !(entity instanceof EnderDragon);
        if (far || tooLow || (!(entity instanceof EnderDragon) && at.getBlock().getType().isOccluding()
            && at.clone().add(0, 1, 0).getBlock().getType().isOccluding())) {
            Fx.particle(world, Particle.PORTAL, at, 40, 0.5, 1, 0.5, 0.5);
            Location back = anchor.clone();
            back.setYaw(at.getYaw());
            entity.teleport(back);
            entity.setFallDistance(0);
            entity.setVelocity(new Vector(0, 0, 0));
            Fx.particle(world, Particle.PORTAL, back, 40, 0.5, 1, 0.5, 0.5);
            Fx.sound(world, back, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        }
    }

    private void targetOwner() {
        if (owner == null || !(entity instanceof Mob mob)) {
            return;
        }
        Player p = Bukkit.getPlayer(owner);
        if (p != null && Fx.targetable(p) && mob.getTarget() != p) {
            mob.setTarget(p);
        }
    }

    private void updateEnrage() {
        long after = enrageAfterTicks();
        if (after <= 0) {
            return;
        }
        long left = after - ticks;
        if (!enraged && left <= 0) {
            enraged = true;
            broadcastNearby(MINI.deserialize("<red><bold>" + plain(def.name()) + " is ENRAGED! <gray>Finish it fast."));
            Fx.sound(world, location(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.5f);
            retune();
        } else if (left > 0 && left <= 600 && enrageNotified < 1) {
            enrageNotified = 1;
            broadcastNearby(MINI.deserialize("<yellow>" + plain(def.name()) + " will enrage in 30 seconds!"));
        }
        if (enraged && ticks % 200 < settings.tickPeriod()) {
            retune();
        }
    }

    void retune() {
        double mult = phase().damageMult() * enrageMult();
        double speed = baseMoveSpeed * phase().speedMult() * (enraged ? 1.2 : 1.0);
        BossEntities.retune(def, settings, entity, mult, speed);
    }

    private void contactDamage(List<Player> inArena) {
        double radius = def.options().get("contact-radius") instanceof Number n ? n.doubleValue()
            : (entity instanceof EnderDragon ? 8 : 0);
        if (radius <= 0) {
            return;
        }
        Location at = entity.getLocation();
        for (Player p : inArena) {
            if (p.getLocation().distanceSquared(at) > radius * radius) {
                continue;
            }
            if (ticks < contactCooldown.getOrDefault(p.getUniqueId(), 0L)) {
                continue;
            }
            contactCooldown.put(p.getUniqueId(), ticks + 24);
            hit(p, 1.0);
            Vector away = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                Fx.push(p, away.normalize().multiply(0.9).setY(0.35), 1.1, 0.5);
            }
        }
    }

    // ---------------------------------------------------------------- abilities

    private void castNext() {
        List<AbilitySpec> list = phase().abilities();
        if (list.isEmpty()) {
            return;
        }
        int phaseIndex = tracker.current();
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            long key = phaseIndex * 64L + i;
            long at = nextCast.computeIfAbsent(key, k -> ticks + 20 + random.nextInt(Math.max(20, list.get(
                (int) (k % 64)).cooldownTicks() / 2)));
            if (ticks >= at) {
                ready.add(i);
            }
        }
        if (ready.isEmpty()) {
            return;
        }
        int index = ready.get(random.nextInt(ready.size()));
        AbilitySpec spec = list.get(index);
        double faster = enraged ? 0.75 : 1.0;
        nextCast.put(phaseIndex * 64L + index, ticks + (long) (spec.cooldownTicks() * faster));
        cast(spec);
    }

    /** Casts an ability now and keeps the boss busy for as long as it takes. */
    void cast(AbilitySpec spec) {
        int busy;
        try {
            busy = Abilities.cast(this, spec);
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Ability " + spec.type().id() + " failed for " + def.id(), e);
            busy = 20;
        }
        gcdUntil = ticks + Math.max(20, busy);
    }

    private void castEnter(BossDefinition.Phase phase) {
        int delay = 10;
        for (AbilitySpec spec : phase.enter()) {
            later(delay, () -> cast(spec));
            delay += 30;
        }
    }

    // ---------------------------------------------------------------- shield

    /** Starts the crystal shield: the boss cannot be hurt until every crystal is destroyed. */
    void startShield(int count, double radius, double height, int hitsPerCrystal, int seconds, double healPerSecond,
            double novaDamage) {
        if (shielded || ended) {
            return;
        }
        shielded = true;
        shieldDeadline = ticks + seconds * 20L;
        shieldHealPerTick = healPerSecond / 20.0;
        shieldNovaDamage = novaDamage;
        crystals.clear();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count + random.nextDouble() * 0.3;
            Location at = anchor.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            try {
                EnderCrystal crystal = (EnderCrystal) world.spawnEntity(at, EntityType.END_CRYSTAL);
                crystal.setShowingBottom(false);
                crystal.setPersistent(false);
                crystal.getPersistentDataContainer().set(Tags.MINION, PersistentDataType.STRING, sessionId.toString());
                crystals.put(crystal.getUniqueId(), hitsPerCrystal);
                manager.registerAux(crystal.getUniqueId(), this);
                if (owner != null) {
                    hideFromEveryoneButOwner(crystal);
                }
                Fx.particle(world, Particle.END_ROD, at, 20, 0.3, 0.3, 0.3, 0.05);
            } catch (RuntimeException e) {
                core.debug("crystal spawn failed: " + e.getMessage());
            }
        }
        if (crystals.isEmpty()) {
            shielded = false;
            return;
        }
        broadcastNearby(MINI.deserialize("<light_purple><bold>" + plain(def.name())
            + " is shielded! <gray>Destroy the " + crystals.size() + " crystals!"));
        Fx.sound(world, location(), Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
    }

    private void updateShield() {
        if (!shielded) {
            return;
        }
        Location boss = location().add(0, entity.getHeight() / 2, 0);
        List<UUID> dead = new ArrayList<>();
        for (UUID id : crystals.keySet()) {
            org.bukkit.entity.Entity crystal = Bukkit.getEntity(id);
            if (crystal == null || !crystal.isValid()) {
                dead.add(id);
                continue;
            }
            Fx.line(world, crystal.getLocation(), boss, Particle.END_ROD, 1.2);
        }
        dead.forEach(crystals::remove);
        hp = Math.min(maxHp, hp + maxHp * shieldHealPerTick * settings.tickPeriod() * crystals.size());
        if (crystals.isEmpty()) {
            endShield(true);
            return;
        }
        if (ticks >= shieldDeadline) {
            endShield(false);
        }
    }

    void crystalHit(UUID crystalId, @Nullable Player by) {
        Integer left = crystals.get(crystalId);
        if (left == null) {
            return;
        }
        org.bukkit.entity.Entity crystal = Bukkit.getEntity(crystalId);
        if (left > 1) {
            crystals.put(crystalId, left - 1);
            if (crystal != null) {
                Fx.particle(world, Particle.CRIT, crystal.getLocation(), 20, 0.4, 0.4, 0.4, 0.3);
                Fx.sound(world, crystal.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.6f);
            }
            return;
        }
        crystals.remove(crystalId);
        manager.unregisterAux(crystalId);
        if (crystal != null) {
            Fx.particle(world, Particle.EXPLOSION, crystal.getLocation(), 3, 0.3, 0.3, 0.3, 0);
            Fx.sound(world, crystal.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.3f);
            crystal.remove();
        }
        if (by != null) {
            broadcastNearby(MINI.deserialize("<aqua>" + plain(by.getName()) + " <gray>shattered a crystal! "
                + (crystals.isEmpty() ? "" : "<white>" + crystals.size() + " left")));
        }
    }

    private void endShield(boolean broken) {
        shielded = false;
        for (UUID id : new ArrayList<>(crystals.keySet())) {
            org.bukkit.entity.Entity crystal = Bukkit.getEntity(id);
            manager.unregisterAux(id);
            if (crystal != null) {
                crystal.remove();
            }
        }
        crystals.clear();
        if (broken) {
            vulnerableUntil = ticks + 160;
            broadcastNearby(MINI.deserialize("<green><bold>The shield shatters! <gray>" + plain(def.name())
                + " is dazed and takes " + Math.round((vulnerableMult - 1) * 100) + "% more damage."));
            Fx.sound(world, location(), Sound.ENTITY_ENDER_DRAGON_HURT, 3f, 0.6f);
            Fx.particle(world, Particle.FLASH, location(), 1, 0, 0, 0, 0);
        } else {
            broadcastNearby(MINI.deserialize("<red><bold>The crystals overload!"));
            Location at = location();
            Fx.particle(world, Particle.EXPLOSION_EMITTER, at, 3, 3, 1, 3, 0);
            Fx.sound(world, at, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
            for (Player p : players()) {
                hit(p, shieldNovaDamage);
            }
        }
    }

    // ---------------------------------------------------------------- damage in

    /**
     * Applies a hit of {@code finalDamage} dealt by {@code attacker}. Returns the damage credited (0 when the boss is
     * shielded or dead).
     */
    double applyHit(Player attacker, double finalDamage) {
        if (ended || shielded) {
            return 0;
        }
        double dmg = finalDamage * settings.incomingScale() * (ticks < vulnerableUntil ? vulnerableMult : 1.0);
        if (!(dmg > 0)) {
            return 0;
        }
        // A player grows the pool only once they dealt a real share of the health (a one-hit alt account must not make
        // the fight harder for the group). It happens BEFORE the hit is credited, otherwise a lethal hit would be
        // capped at the old health and leave the rescaled boss alive.
        UUID id = attacker.getUniqueId();
        double threshold = baseMaxHp * settings.scaleMinShare();
        if (!scalers.contains(id) && ledger.damageOf(id) + dmg >= threshold) {
            scalers.add(id);
            rescale(scalers.size());
        }
        double credited = ledger.record(id, dmg, hp);
        if (credited <= 0) {
            return 0;
        }
        hp -= credited;
        showDamage(credited);
        healthChanged(attacker);
        return credited;
    }

    /**
     * Floating damage number. The generic damage indicators read the real event damage, which is a token amount for a
     * boss, so the boss shows its own: throttled to about four a second, hits in between are added up.
     */
    private void showDamage(double credited) {
        if (!settings.damageNumbers() || world == null || entity == null) {
            return;
        }
        pendingNumber += credited;
        flushDamage();
    }

    private void flushDamage() {
        long now = System.currentTimeMillis();
        if (pendingNumber <= 0 || now < nextNumberAt || entity == null || !entity.isValid()) {
            return;
        }
        double amount = pendingNumber;
        pendingNumber = 0;
        nextNumberAt = now + 250;
        numbers.removeIf(id -> Bukkit.getEntity(id) == null);
        if (numbers.size() >= 12) {
            return;
        }
        List<Player> watching = viewers();
        if (watching.isEmpty()) {
            return;
        }
        double big = Math.min(1, amount / Math.max(1, maxHp * 0.02));
        String colour = big > 0.6 ? "<gold><bold>" : big > 0.2 ? "<yellow>" : "<white>";
        Location at = location().add((random.nextDouble() - 0.5) * 1.6, entity.getHeight() * 0.8 + random.nextDouble(),
            (random.nextDouble() - 0.5) * 1.6);
        try {
            org.bukkit.entity.TextDisplay display = world.spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
                d.text(MINI.deserialize(colour + Msg.number(amount)));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setSeeThrough(true);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setViewRange(0.5f);
            });
            for (Player p : watching) {
                p.showEntity(core.getPlugin(), display);
            }
            numbers.add(display.getUniqueId());
            UUID id = display.getUniqueId();
            core.scheduler().later(22, () -> {
                org.bukkit.entity.Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
                numbers.remove(id);
            });
        } catch (RuntimeException e) {
            core.debug("damage number unsupported: " + e.getMessage());
        }
    }

    /** Keeps the name tag in step with the health pool, once a second at most. */
    private void updateNameplate() {
        if (ticks % 20 >= settings.tickPeriod() || entity == null || !entity.isValid()) {
            return;
        }
        String text = def.name() + " <white>" + Msg.number(hp) + " <red>❤";
        if (!text.equals(lastNameplate)) {
            lastNameplate = text;
            entity.customName(MINI.deserialize(text));
        }
    }

    /** Heals the boss, never above its maximum. */
    void heal(double amount) {
        if (ended || amount <= 0) {
            return;
        }
        hp = Math.min(maxHp, hp + amount);
        mirrorHealth();
    }

    private void rescale(int participants) {
        double mult = BossMath.hpMultiplier(participants, def.arena().minPlayers(), def.arena().scalePerPlayer(),
            def.arena().scaleCap());
        double newMax = baseMaxHp * mult;
        if (Math.abs(newMax - maxHp) > 1e-9) {
            hp = hp * newMax / maxHp;
            maxHp = newMax;
        }
    }

    private void healthChanged(Player attacker) {
        mirrorHealth();
        for (int index : tracker.update(fraction())) {
            if (hp <= 0.0001) {
                break;
            }
            enterPhase(index);
        }
        if (hp <= 0.0001) {
            end(End.KILLED, attacker);
        }
    }

    private void mirrorHealth() {
        if (entity == null || !entity.isValid()) {
            return;
        }
        var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double visual = attribute == null ? 20 : attribute.getValue();
        entity.setHealth(Math.max(1, Math.min(visual, visual * fraction())));
    }

    private void enterPhase(int index) {
        BossDefinition.Phase phase = def.phases().get(index);
        announcePhase(phase, false);
        retune();
        Fx.sound(world, location(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.8f);
        Fx.particle(world, Particle.EXPLOSION_EMITTER, location(), 1, 0, 0, 0, 0);
        gcdUntil = ticks + 30;
        castEnter(phase);
    }

    private void announcePhase(BossDefinition.Phase phase, boolean spawn) {
        if (phase.title().isEmpty() && phase.subtitle().isEmpty()) {
            return;
        }
        Title title = Title.title(MINI.deserialize(phase.title()), MINI.deserialize(phase.subtitle()),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500)));
        for (Player p : viewers()) {
            p.showTitle(title);
        }
    }

    // ---------------------------------------------------------------- bar and messages

    private Component barTitle() {
        StringBuilder text = new StringBuilder(def.name());
        text.append(" <white>").append(Msg.number(hp)).append("<gray>/<white>").append(Msg.number(maxHp)).append(" <red>❤");
        if (shielded) {
            text.append(" <light_purple>[SHIELDED]");
        } else if (ticks < vulnerableUntil) {
            text.append(" <green>[DAZED]");
        } else if (enraged) {
            text.append(" <dark_red>[ENRAGED]");
        }
        return MINI.deserialize(text.toString());
    }

    /** Players who see the bar: in the same world, close to the boss. */
    List<Player> viewers() {
        List<Player> list = new ArrayList<>();
        if (world == null || entity == null) {
            return list;
        }
        Location at = location();
        double range = settings.barRange();
        for (Player p : world.getPlayers()) {
            if (owner != null && !p.getUniqueId().equals(owner)) {
                continue;
            }
            if (p.getLocation().distanceSquared(at) <= range * range) {
                list.add(p);
            }
        }
        return list;
    }

    private void updateBar() {
        if (bar == null) {
            return;
        }
        bar.name(barTitle());
        bar.progress((float) Math.max(0, Math.min(1, fraction())));
        bar.color(shielded ? BossBar.Color.WHITE : BossBar.Color.valueOf(def.barColor()));
        Set<UUID> now = new HashSet<>();
        for (Player p : viewers()) {
            now.add(p.getUniqueId());
            if (barViewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
        for (UUID id : new ArrayList<>(barViewers)) {
            if (!now.contains(id)) {
                barViewers.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    void broadcastNearby(Component message) {
        for (Player p : viewers()) {
            p.sendMessage(message);
        }
    }

    static String plain(String mini) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(MINI.deserialize(mini));
    }

    // ---------------------------------------------------------------- minions

    /** Spawns a minion unless the cap is reached. Returns null when refused. */
    @Nullable LivingEntity spawnMinion(Location at, EntityType type, @Nullable String name, double health,
            double damageMult) {
        minions.removeIf(id -> {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
        if (minions.size() >= def.arena().maxMinions() || ended) {
            return null;
        }
        LivingEntity minion = BossEntities.spawnMinion(at, type, sessionId.toString(), name, health,
            Math.max(0.5, hitDamage(damageMult)), e -> { });
        if (minion == null) {
            return null;
        }
        minions.add(minion.getUniqueId());
        manager.registerAux(minion.getUniqueId(), this);
        if (owner != null) {
            hideFromEveryoneButOwner(minion);
        }
        if (minion instanceof Mob mob) {
            Player target = targets("nearest", 1).stream().findFirst().orElse(null);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        return minion;
    }

    // ---------------------------------------------------------------- end

    /** Ends the fight. Safe to call twice. */
    public void end(End reason, @Nullable Player killer) {
        if (ended) {
            return;
        }
        ended = true;
        endReason = reason;
        handles.forEach(Scheduler.Handle::cancel);
        handles.clear();
        Location at = location();
        if (reason == End.KILLED) {
            Fx.sound(world, at, Sound.ENTITY_ENDER_DRAGON_DEATH, 4f, 0.8f);
            Fx.particle(world, Particle.EXPLOSION_EMITTER, at, 4, 2, 1, 2, 0);
            Fx.particle(world, Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 120, 2, 2, 2, 0.4);
        } else {
            Fx.particle(world, Particle.POOF, at, 40, 1, 1, 1, 0.1);
        }
        removeSpawned();
        releaseChunks();
        if (bar != null) {
            for (UUID id : barViewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
            barViewers.clear();
        }
        EndInfo info = new EndInfo(this, reason, killer);
        manager.sessionEnded(info);
        for (Consumer<EndInfo> listener : endListeners) {
            try {
                listener.accept(info);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Boss end listener failed", e);
            }
        }
    }

    private void removeSpawned() {
        for (UUID id : minions) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            manager.unregisterAux(id);
            if (e != null) {
                e.remove();
            }
        }
        minions.clear();
        for (UUID id : numbers) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        numbers.clear();
        for (UUID id : crystals.keySet()) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            manager.unregisterAux(id);
            if (e != null) {
                e.remove();
            }
        }
        crystals.clear();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /** Participants, best first. */
    public List<Damager> ranking() {
        return ledger.ranking();
    }
}
