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
        if (active != null || config.locations().isEmpty()) {
            return false;
        }
        String id = bossId != null ? bossId : config.pool().isEmpty() ? null : schedule.pick(random);
        Optional<BossDefinition> def = id == null ? Optional.empty() : bosses.config().definition(id);
        if (def.isEmpty()) {
            return false;
        }
        WorldBossConfig.Spot spot = schedule.pickSpot(random);
        World world = Bukkit.getWorld(spot.world());
        if (world == null) {
            core.getPlugin().getLogger().warning("World boss spot world '" + spot.world() + "' is not loaded");
            return false;
        }
        // y = -999 means "x and z are offsets from the world spawn"
        Location at = spot.y() <= -900 ? world.getSpawnLocation().add(spot.x(), 2, spot.z())
            : new Location(world, spot.x(), spot.y(), spot.z());
        try {
            world.getChunkAt(at);
        } catch (RuntimeException e) {
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
            + MINI.escapeTags(spot.name()) + " <gray>(" + Math.round(spot.x()) + ", " + Math.round(spot.y()) + ", "
            + Math.round(spot.z()) + ")! It leaves in <yellow>" + config.durationMinutes() + " minutes<gray>.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(line);
            p.showTitle(Title.title(MINI.deserialize(name), MINI.deserialize("<gray>A world boss has appeared!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        }
        return true;
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
                lines.add("<gray>Location: <yellow>" + MINI.escapeTags(activeSpot.name()) + " <gray>("
                    + Math.round(activeSpot.x()) + ", " + Math.round(activeSpot.y()) + ", " + Math.round(activeSpot.z()) + ")");
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
