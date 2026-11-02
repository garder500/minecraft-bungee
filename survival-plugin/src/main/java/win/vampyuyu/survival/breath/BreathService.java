package win.vampyuyu.survival.breath;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.BreathApi;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.breath.BreathEvent.Effect;
import win.vampyuyu.survival.breath.BreathEvent.Type;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The pulsation of the world. Every {@code breath.interval-minutes} the Celestial Breath draws an event, announces it
 * (GDD 19.3), applies it while it lasts and puts it away. Main thread only, except the pure reads of the factors.
 */
public final class BreathService implements BreathApi {
    private static final String FILE = "breath.json";
    private static final long TICK_SECONDS = 20;
    private static final long MIN_QUIET_MILLIS = 60_000;

    private final Core core;
    private final Random rng = new Random();
    private final NamespacedKey invaderKey;
    private final Set<UUID> invaders = new HashSet<>();
    private volatile BreathConfig config;
    private volatile BreathState state;
    private volatile BreathEvent active;
    private Scheduler.Handle tick;
    private Scheduler.Handle spawner;

    public BreathService(Core core, BreathConfig config) {
        this.core = core;
        this.config = config;
        this.invaderKey = new NamespacedKey(core.getPlugin(), "breath_invader");
        this.state = core.store().read(FILE, BreathState.class, BreathState::new);
    }

    public BreathConfig config() {
        return config;
    }

    public NamespacedKey invaderKey() {
        return invaderKey;
    }

    public BreathState state() {
        return state;
    }

    /** The running event, or null. */
    public BreathEvent event() {
        return active;
    }

    public void reload(BreathConfig fresh) {
        config = fresh;
        if (active != null) {
            BreathEvent again = fresh.events().get(active.id());
            if (again == null) {
                end(false);
            } else {
                active = again;
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    /** Resumes a running event after a restart and starts the clock. */
    public void start() {
        long now = System.currentTimeMillis();
        BreathEvent running = state.active.isEmpty() ? null : config.events().get(state.active);
        if (running != null && state.endsAt > now) {
            active = running;
            if (running.has(Type.INVADE)) {
                startSpawner();
            }
        } else if (!state.active.isEmpty()) {
            state.active = "";
            save();
        }
        if (state.nextAt == 0) {
            state.nextAt = now + config.firstDelayMinutes() * 60_000L;
            save();
        }
        tick = core.scheduler().repeating(TICK_SECONDS * 20, TICK_SECONDS * 20, this::tick);
    }

    public void stop() {
        if (tick != null) {
            tick.cancel();
            tick = null;
        }
        stopSpawner();
        removeInvaders();
        active = null;
    }

    void tick() {
        if (!config.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (active != null && now >= state.endsAt) {
            end(true);
        } else if (active == null && now >= state.nextAt) {
            BreathEvent next = BreathRules.pick(config.ordered(), state.last, rng);
            if (next != null) {
                begin(next);
            }
        }
    }

    /** Starts an event now (the clock, or {@code /breath start}). One pulsation. */
    public void begin(BreathEvent event) {
        if (active != null) {
            end(false);
        }
        long now = System.currentTimeMillis();
        state.cycle++;
        state.active = event.id();
        state.last = event.id();
        state.endsAt = now + event.minutes() * 60_000L;
        state.nextAt = Math.max(now + config.intervalMinutes() * 60_000L, state.endsAt + MIN_QUIET_MILLIS);
        state.fractureSeed = event.has(Type.FRACTURE) ? rng.nextLong() : 0;
        active = event;
        save();
        if (event.has(Type.INVADE)) {
            startSpawner();
        }
        announce(event);
    }

    /** Puts the running event away. {@code announce} says whether the players are told. */
    public void end(boolean announce) {
        BreathEvent was = active;
        if (was == null) {
            return;
        }
        active = null;
        state.active = "";
        state.endsAt = 0;
        save();
        stopSpawner();
        removeInvaders();
        if (announce) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(Msg.plain("CELESTIAL BREATH", NamedTextColor.DARK_PURPLE).append(
                    Msg.plain(" - " + was.title() + " has passed. The sky breathes out.", NamedTextColor.GRAY)));
            }
        }
    }

    private void save() {
        try {
            core.store().write(FILE, state);
        } catch (java.io.IOException | RuntimeException e) {
            core.getPlugin().getLogger().warning("breath: cannot save " + FILE + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- announcement

    /** The banner (GDD 19.3), to everyone online. */
    private void announce(BreathEvent event) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            banner(p, event);
            p.showTitle(Title.title(Msg.plain("CELESTIAL BREATH", NamedTextColor.DARK_PURPLE),
                Msg.plain(event.title(), NamedTextColor.LIGHT_PURPLE)));
            p.playSound(p, org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
        }
    }

    /** Sends the banner of an event to one player (also on join while it runs). */
    public void banner(Player p, BreathEvent event) {
        p.sendMessage(Msg.plain("CELESTIAL BREATH", NamedTextColor.DARK_PURPLE).append(
            Msg.plain(" - " + event.title(), NamedTextColor.LIGHT_PURPLE)));
        for (Effect e : event.effects()) {
            NamedTextColor color = e.gain() ? NamedTextColor.GREEN : e.cost() ? NamedTextColor.RED : NamedTextColor.YELLOW;
            p.sendMessage(Msg.plain("  " + BreathRules.line(e), color));
        }
        p.sendMessage(Msg.plain("  Ends in " + Msg.duration(Math.max(0, state.endsAt - System.currentTimeMillis())), NamedTextColor.GRAY));
    }

    // ---------------------------------------------------------------- BreathApi

    @Override
    public boolean active() {
        return active != null;
    }

    @Override
    public int cycle() {
        return state.cycle;
    }

    @Override
    public double yieldFactor(String resourceId) {
        return BreathRules.factor(active, Type.YIELD, resourceId);
    }

    @Override
    public double machineFactor() {
        return BreathRules.factor(active, Type.MACHINE, "*");
    }

    @Override
    public double monsterFactor() {
        return BreathRules.factor(active, Type.MONSTER, "*");
    }

    @Override
    public int fragments(UUID player) {
        return data(player).fragments;
    }

    @Override
    public int keys(UUID player) {
        return data(player).keys;
    }

    @Override
    public boolean consumeKey(UUID player) {
        boolean[] used = {false};
        edit(player, d -> {
            if (d.keys > 0) {
                d.keys--;
                used[0] = true;
            }
        });
        return used[0];
    }

    // ---------------------------------------------------------------- profile

    public BreathData data(UUID id) {
        return core.profiles().section(id, "breath", BreathData.class, BreathData::new);
    }

    void edit(UUID id, Consumer<BreathData> change) {
        core.profiles().edit(id, "breath", BreathData.class, BreathData::new, change);
    }

    /**
     * One draw for an Interpulsation Fragment (GDD 19.4): at most one per pulsation per player, so a key (several
     * Fragments) takes several cycles. Returns true when the player found one.
     */
    boolean rollFragment(Player player, double chance) {
        int cycle = state.cycle;
        if (!BreathRules.mayFindFragment(data(player.getUniqueId()).lastFragmentCycle, cycle) || !(rng.nextDouble() < chance)) {
            return false;
        }
        int perKey = config.fragmentsPerKey();
        boolean[] key = {false};
        edit(player.getUniqueId(), d -> {
            d.fragments++;
            d.found++;
            d.lastFragmentCycle = cycle;
            int[] made = BreathRules.assemble(d.fragments, perKey);
            if (made[0] > 0) {
                d.keys += made[0];
                d.fragments = made[1];
                key[0] = true;
            }
        });
        player.sendMessage(Msg.plain("An Interpulsation Fragment settles in your hand, cold as the space between two breaths.",
            NamedTextColor.LIGHT_PURPLE));
        if (key[0]) {
            player.sendMessage(Msg.plain("The Fragments fit together: you hold an Interpulsation Key.", NamedTextColor.GOLD));
        }
        return true;
    }

    // ---------------------------------------------------------------- fractures

    /** The running Fracture effect, or null. */
    public Effect fracture() {
        BreathEvent e = active;
        return e == null ? null : e.effects().stream().filter(x -> x.type() == Type.FRACTURE).findFirst().orElse(null);
    }

    /** Enters the temporary dungeon of the running Fracture. Returns a message when it cannot, null when it starts. */
    public String enterFracture(Player player) {
        Effect fracture = fracture();
        if (fracture == null) {
            return "No Fracture is open right now.";
        }
        Realm realm = fracture.realm();
        String world = realm == null ? null : BreathConfig.worldOf(realm);
        if (world == null || !player.getWorld().getName().equals(world)) {
            return "The Fracture is in the " + (realm == null ? "?" : realm.title()) + ": go there first.";
        }
        Optional<DungeonApi> dungeons = core.service(DungeonApi.class);
        if (dungeons.isEmpty()) {
            return "Dungeons are not available.";
        }
        return dungeons.get().startRun(player, fracture.floor(), state.fractureSeed);
    }

    /** A cleared dungeon run: pays the Fracture's memory loot once per player and per Fracture. */
    void onDungeonClear(UUID id, int floor, long seed) {
        Effect fracture = fracture();
        Player player = Bukkit.getPlayer(id);
        if (fracture == null || player == null || state.fractureSeed == 0 || seed != state.fractureSeed || floor != fracture.floor()) {
            return;
        }
        int cycle = state.cycle;
        boolean[] first = {false};
        edit(id, d -> {
            if (d.lastFractureCycle != cycle) {
                d.lastFractureCycle = cycle;
                d.fracturesCleared++;
                first[0] = true;
            }
        });
        if (!first[0]) {
            return;
        }
        long shards = config.fractureShardsPerFloor() * floor;
        core.service(ResonanceApi.class).ifPresent(r -> r.grantShards(id, shards));
        player.sendMessage(Msg.plain("The Fracture gives up memories of an older world: +" + shards + " Memory Shards.",
            NamedTextColor.AQUA));
        rollFragment(player, config.fractureFragmentChance());
    }

    // ---------------------------------------------------------------- invasions

    private void startSpawner() {
        stopSpawner();
        long period = config.invaderRespawnSeconds() * 20L;
        spawner = core.scheduler().repeating(40, period, this::spawnInvaders);
    }

    private void stopSpawner() {
        if (spawner != null) {
            spawner.cancel();
            spawner = null;
        }
    }

    /** Removes every immune cell (end of the event, shutdown). */
    void removeInvaders() {
        for (UUID id : invaders) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        invaders.clear();
    }

    public boolean isInvader(Entity e) {
        return e != null && e.getPersistentDataContainer().has(invaderKey, PersistentDataType.INTEGER);
    }

    public int invaderCount() {
        return invaders.size();
    }

    private void spawnInvaders() {
        BreathEvent event = active;
        if (event == null) {
            return;
        }
        for (Iterator<UUID> it = invaders.iterator(); it.hasNext();) {
            Entity e = Bukkit.getEntity(it.next());
            if (e == null || e.isDead()) {
                it.remove();
            }
        }
        for (Effect effect : event.effects()) {
            if (effect.type() != Type.INVADE) {
                continue;
            }
            World world = Bukkit.getWorld(BreathConfig.worldOfTarget(effect.target()));
            if (world == null) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                int near = 0;
                for (UUID id : invaders) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null && e.getWorld() == world && e.getLocation().distanceSquared(player.getLocation()) < 40 * 40) {
                        near++;
                    }
                }
                for (int i = near; i < config.invadersPerPlayer() && invaders.size() < config.invadersMax(); i++) {
                    spawnOne(player.getLocation());
                }
            }
        }
    }

    private void spawnOne(Location around) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = 10 + rng.nextDouble() * 10;
        World world = around.getWorld();
        int x = (int) Math.floor(around.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(around.getZ() + Math.sin(angle) * distance);
        int y = world.getHighestBlockYAt(x, z);
        var ground = world.getBlockAt(x, y, z);
        if (!ground.getType().isSolid() || ground.isLiquid()) {
            return;
        }
        Location at = new Location(world, x + 0.5, y + 1, z + 0.5);
        Class<? extends Entity> type = mobClass();
        int cycle = state.cycle;
        Entity spawned = world.spawn(at, type, e -> {
            e.getPersistentDataContainer().set(invaderKey, PersistentDataType.INTEGER, cycle);
            e.setPersistent(false);
            e.setGlowing(true);
            e.customName(Msg.plain(config.invaderName(), NamedTextColor.WHITE));
            e.setCustomNameVisible(true);
            if (e instanceof Mob mob) {
                mob.setRemoveWhenFarAway(true);
                mob.setCanPickupItems(false);
                var health = mob.getAttribute(Attribute.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(config.invaderHealth());
                    mob.setHealth(config.invaderHealth());
                }
                mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            }
            if (e instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            }
        });
        invaders.add(spawned.getUniqueId());
    }

    private Class<? extends Entity> mobClass() {
        try {
            EntityType type = EntityType.valueOf(config.invaderType());
            Class<? extends Entity> cls = type.getEntityClass();
            if (cls != null && Monster.class.isAssignableFrom(cls)) {
                return cls;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the default
        }
        return Zombie.class;
    }

    /** An immune cell killed by a player: a few Memory Shards (capped per pulsation) and a rare Fragment. */
    void onInvaderKilled(Player killer) {
        int cycle = state.cycle;
        long perKill = config.invaderShards();
        long cap = config.invaderShardsCap();
        long[] paid = {0};
        edit(killer.getUniqueId(), d -> {
            if (d.invadersCycle != cycle) {
                d.invadersCycle = cycle;
                d.invaderKills = 0;
                d.invaderShards = 0;
            }
            d.invaderKills++;
            paid[0] = Math.max(0, Math.min(perKill, cap - d.invaderShards));
            d.invaderShards += paid[0];
        });
        if (paid[0] > 0) {
            core.service(ResonanceApi.class).ifPresent(r -> r.grantShards(killer.getUniqueId(), paid[0]));
            killer.sendActionBar(Msg.plain("+" + paid[0] + " Memory Shards", NamedTextColor.AQUA));
        }
        rollFragment(killer, config.invaderFragmentChance());
    }

    /** The route guard of the Sky Gates: the reason a Gate is shut, or null. */
    String routeGuard(Realm realm) {
        BreathEvent event = active;
        if (event == null) {
            return null;
        }
        for (Effect e : event.effects()) {
            if (e.type() == Type.ROUTE && e.target().equals(realm.id())) {
                return "The Sky Gate to the " + realm.title() + " is torn shut by the Breath for "
                    + Msg.duration(Math.max(0, state.endsAt - System.currentTimeMillis())) + ".";
            }
        }
        return null;
    }
}
