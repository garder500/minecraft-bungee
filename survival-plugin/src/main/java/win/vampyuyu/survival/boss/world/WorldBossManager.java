package win.vampyuyu.survival.boss.world;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.boss.BossManager;
import win.vampyuyu.survival.boss.BossSession;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Schedules and runs the server-wide world boss events. State survives restarts in {@code boss/worldboss.json}. */
public final class WorldBossManager {
    public static final String FILE = "boss/worldboss.json";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Core core;
    private final BossManager bosses;
    private final Random random = new Random();
    private WorldBossConfig config;
    private WorldBossSchedule schedule;
    private WorldBossState state;
    private Scheduler.Handle loop;
    private BossSession active;
    private WorldBossConfig.Spot activeSpot;

    public WorldBossManager(Core core, BossManager bosses) {
        this.core = core;
        this.bosses = bosses;
        this.config = bosses.config().worldBoss();
        this.schedule = new WorldBossSchedule(config);
        this.state = core.store().read(FILE, WorldBossState.class, WorldBossState::new);
    }

    public void start() {
        loop = core.scheduler().repeating(100, 100, this::check);
    }

    public void stop() {
        if (loop != null) {
            loop.cancel();
        }
        if (active != null) {
            active.end(BossSession.End.SHUTDOWN, null);
        }
        writeNow();
    }

    public void reload() {
        this.config = bosses.config().worldBoss();
        this.schedule = new WorldBossSchedule(config);
    }

    public WorldBossConfig config() { return config; }

    public WorldBossState state() { return state; }

    public Optional<BossSession> activeSession() { return Optional.ofNullable(active); }

    // ---------------------------------------------------------------- loop

    void check() {
        if (!config.enabled() || active != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (state.nextSpawnAt <= 0) {
            state.nextSpawnAt = schedule.initial(now);
            save();
        }
        for (int seconds : schedule.announcements(now, state.nextSpawnAt)) {
            announceSoon(seconds);
        }
        if (schedule.due(now, state.nextSpawnAt)) {
            if (playersOnline() < config.minPlayers() || !spawnEvent(null)) {
                state.nextSpawnAt = schedule.retry(now);
                save();
            }
        }
    }

    int playersOnline() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (SkyblockWorlds.isSkyblock(p.getWorld().getName())) {
                n++;
            }
        }
        return n;
    }

    private void announceSoon(int seconds) {
        String when = Msg.duration(seconds * 1000L);
        Component line = MINI.deserialize("<gold><bold>WORLD BOSS <gray>- a powerful boss will appear in <yellow>" + when
            + "<gray>. Use <white>/worldboss status<gray> for the location.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
            if (seconds <= 60) {
                p.showTitle(Title.title(MINI.deserialize("<gold><bold>WORLD BOSS"),
                    MINI.deserialize("<gray>Appearing in <yellow>" + when),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        }
    }

    // ---------------------------------------------------------------- events

    /** Starts an event now. {@code bossId} null picks from the pool. False when it could not spawn. */
    public boolean spawnEvent(@Nullable String bossId) {
        List<WorldBossConfig.Spot> spots = allSpots();
        if (active != null || spots.isEmpty()) {
            return false;
        }
        String id = bossId != null ? bossId : config.pool().isEmpty() ? null : schedule.pick(random);
        Optional<BossDefinition> def = id == null ? Optional.empty() : bosses.config().definition(id);
        if (def.isEmpty()) {
            return false;
        }
        WorldBossConfig.Spot spot = spots.get(random.nextInt(spots.size()));
        World world = Bukkit.getWorld(spot.world());
        if (world == null) {
            core.getPlugin().getLogger().warning("World boss spot world '" + spot.world() + "' is not loaded");
            return false;
        }
        Location at = resolve(world, spot);
        if (at == null) {
            core.getPlugin().getLogger().warning("World boss spot '" + spot.name() + "' has no solid ground with a clear "
                + CLEARANCE + " block column (is the hub built?); the event is retried later");
            return false;
        }
        Optional<BossSession> session = bosses.spawn(def.get(), at, null, new BossSession.SpawnOptions(false, true, 0,
            0, config.durationMinutes() * 60, this::eventEnded, null));
        if (session.isEmpty()) {
            return false;
        }
        active = session.get();
        activeSpot = spot;
        long now = System.currentTimeMillis();
        state.lastSpawnAt = now;
        state.lastBoss = def.get().id();
        state.nextSpawnAt = schedule.afterSpawn(now);
        save();
        String name = def.get().name();
        Component line = MINI.deserialize("<gold><bold>WORLD BOSS <gray>- " + name + " <gray>has appeared at <yellow>"
            + MINI.escapeTags(spot.name()) + " <gray>(" + at.getBlockX() + ", " + at.getBlockY() + ", "
            + at.getBlockZ() + ")! It leaves in <yellow>" + config.durationMinutes() + " minutes<gray>. "
            + "<click:run_command:'/worldboss warp'><hover:show_text:'Teleport to the fight'><green><bold>[Click to teleport]"
            + "</bold></green></hover></click>");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
            p.showTitle(Title.title(MINI.deserialize(name), MINI.deserialize("<gray>A world boss has appeared!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        }
        return true;
    }

    /** Clearance (free blocks above the ground) a boss needs to spawn. */
    static final int CLEARANCE = 10;

    /** Spots from boss.yml followed by the ones added in game. */
    List<WorldBossConfig.Spot> allSpots() {
        List<WorldBossConfig.Spot> all = new ArrayList<>(config.locations());
        for (WorldBossState.SpotRecord r : state.spots) {
            all.add(new WorldBossConfig.Spot(r.world, r.x, r.y, r.z, r.name));
        }
        return all;
    }

    /**
     * The real spawn point of a spot: {@code y <= -900} means x and z are offsets from the world spawn, and the boss
     * always stands on the highest solid block with a clear column, so a guessed Y can never bury it or hang it over
     * the void. Null when the column has no ground.
     */
    @Nullable Location resolve(World world, WorldBossConfig.Spot spot) {
        try {
            Location base = spot.y() <= -900 ? world.getSpawnLocation().add(spot.x(), 0, spot.z())
                : new Location(world, spot.x(), spot.y(), spot.z());
            return snap(world, base, 12, 32);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ground under {@code base}: scans from {@code up} above to {@code down} below. Loads the chunk. */
    static @Nullable Location snap(World world, Location base, int up, int down) {
        int x = base.getBlockX();
        int z = base.getBlockZ();
        world.getChunkAt(x >> 4, z >> 4);
        int top = Math.min(world.getMaxHeight() - CLEARANCE - 2, base.getBlockY() + up);
        int floor = Math.max(world.getMinHeight(), base.getBlockY() - down);
        int y = GroundSnap.find(top, floor, CLEARANCE, by -> !world.getBlockAt(x, by, z).isPassable());
        if (y == Integer.MIN_VALUE) {
            return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    // ---------------------------------------------------------------- admin spots and warp

    /** Saves an event spot at a location. Returns the English refusal or null. */
    public @Nullable String addSpot(String name, Location at) {
        if (name.isBlank() || at.getWorld() == null) {
            return "A spot needs a name.";
        }
        if (state.spots.stream().anyMatch(r -> r.name.equalsIgnoreCase(name))) {
            return "A spot named '" + name + "' already exists.";
        }
        state.spots.add(new WorldBossState.SpotRecord(name, at.getWorld().getName(), at.getBlockX() + 0.5,
            at.getBlockY(), at.getBlockZ() + 0.5));
        save();
        return null;
    }

    public boolean removeSpot(String name) {
        boolean removed = state.spots.removeIf(r -> r.name.equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    /** Teleports a player next to the running event. Returns the English refusal or null. */
    public @Nullable String warp(Player player) {
        BossSession session = active;
        if (session == null || session.isEnded()) {
            return "There is no world boss event right now.";
        }
        if (core.service(win.vampyuyu.survival.api.DungeonApi.class).map(d -> d.inDungeon(player)).orElse(false)) {
            return "You cannot do that inside a dungeon.";
        }
        Location anchor = session.anchor();
        World world = anchor.getWorld();
        if (world == null) {
            return "The event world is not available.";
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            Location base = anchor.clone().add(Math.cos(angle) * 22, 0, Math.sin(angle) * 22);
            Location spot = snap(world, base, 6, 12);
            if (spot != null) {
                spot.setDirection(anchor.toVector().subtract(spot.toVector()));
                player.teleport(spot);
                return null;
            }
        }
        return "No safe place to land near the boss. Walk there instead.";
    }

    private void eventEnded(BossSession.EndInfo info) {
        BossSession session = info.session();
        WorldBossState.Entry entry = new WorldBossState.Entry();
        entry.at = System.currentTimeMillis();
        entry.boss = session.definition().id();
        entry.killed = info.reason() == BossSession.End.KILLED;
        List<Damager> ranking = session.ledger().top(config.leaderboardSize());
        List<String> lines = new ArrayList<>();
        for (Damager d : ranking) {
            String name = nameOf(d.player());
            entry.top.add(new WorldBossState.Row(name, d.damage()));
            lines.add("<yellow>#" + (lines.size() + 1) + " <white>" + MINI.escapeTags(name) + " <gray>- <red>" + Msg.number(d.damage()));
        }
        state.history.add(0, entry);
        while (state.history.size() > config.historySize()) {
            state.history.remove(state.history.size() - 1);
        }
        active = null;
        activeSpot = null;
        if (info.reason() == BossSession.End.SHUTDOWN) {
            return; // stop() writes the state itself, the scheduler is closed
        }
        save();
        String message = entry.killed
            ? session.definition().name() + " <green>was defeated!" + (lines.isEmpty() ? "" : " <gray>Top damage:\n " + String.join("\n ", lines))
            : session.definition().name() + " <gray>has left the world.";
        Component line = MINI.deserialize("<gold><bold>WORLD BOSS <gray>- " + message);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(line));
    }

    private String nameOf(java.util.UUID id) {
        return Optional.ofNullable(Bukkit.getPlayer(id)).map(Player::getName).orElseGet(() -> core.profiles().lastName(id));
    }

    /** Ends the running event without rewards. */
    public boolean cancelEvent() {
        if (active == null) {
            return false;
        }
        active.end(BossSession.End.ADMIN, null);
        return true;
    }

    /** Sets the next event to {@code minutes} from now. */
    public void scheduleIn(int minutes) {
        state.nextSpawnAt = System.currentTimeMillis() + Math.max(0, minutes) * 60_000L;
        save();
    }

    // ---------------------------------------------------------------- status

    /** MiniMessage lines for /worldboss status. */
    public List<String> status() {
        List<String> lines = new ArrayList<>();
        if (!config.enabled()) {
            lines.add("<gray>World boss events are disabled.");
            return lines;
        }
        if (active != null) {
            BossSession s = active;
            lines.add("<gold>Active: " + s.definition().name() + " <gray>- <red>" + Math.round(s.fraction() * 100) + "% <gray>health");
            if (activeSpot != null) {
                Location where = s.anchor();
                lines.add("<gray>Location: <yellow>" + MINI.escapeTags(activeSpot.name()) + " <gray>("
                    + where.getBlockX() + ", " + where.getBlockY() + ", " + where.getBlockZ() + ") <green>/worldboss warp");
            }
            List<Damager> top = s.ledger().top(config.leaderboardSize());
            if (!top.isEmpty()) {
                lines.add("<gray>Top damage:");
                for (int i = 0; i < top.size(); i++) {
                    lines.add(" <yellow>#" + (i + 1) + " <white>" + MINI.escapeTags(nameOf(top.get(i).player()))
                        + " <gray>- <red>" + Msg.number(top.get(i).damage()));
                }
            }
        } else {
            long left = state.nextSpawnAt - System.currentTimeMillis();
            lines.add("<gray>Next world boss in <yellow>" + (state.nextSpawnAt <= 0 ? "soon" : Msg.duration(Math.max(0, left)))
                + (playersOnline() < config.minPlayers() ? " <dark_gray>(needs " + config.minPlayers() + " players online)" : ""));
        }
        if (!state.history.isEmpty()) {
            WorldBossState.Entry last = state.history.get(0);
            lines.add("<gray>Last event: <white>" + MINI.escapeTags(last.boss) + " <gray>- "
                + (last.killed ? "<green>defeated" : "<red>escaped")
                + (last.top.isEmpty() ? "" : " <gray>(best: <white>" + MINI.escapeTags(last.top.get(0).name) + "<gray>)"));
        }
        return lines;
    }

    private WorldBossState snapshot() {
        WorldBossState copy = new WorldBossState();
        copy.nextSpawnAt = state.nextSpawnAt;
        copy.lastSpawnAt = state.lastSpawnAt;
        copy.lastBoss = state.lastBoss;
        copy.history = new ArrayList<>(state.history);
        copy.spots = new ArrayList<>(state.spots);
        return copy;
    }

    private void save() {
        WorldBossState snapshot = snapshot();
        core.scheduler().async(() -> write(snapshot));
    }

    private void writeNow() {
        write(snapshot());
    }

    private void write(WorldBossState snapshot) {
        synchronized (WorldBossManager.class) {
            try {
                core.store().write(FILE, snapshot);
            } catch (java.io.IOException | RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not save the world boss state", e);
            }
        }
    }
}
