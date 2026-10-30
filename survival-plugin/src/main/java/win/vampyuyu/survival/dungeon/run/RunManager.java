package win.vampyuyu.survival.dungeon.run;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.dungeon.DungeonData;
import win.vampyuyu.survival.dungeon.gen.BlockApplier;
import win.vampyuyu.survival.dungeon.gen.BuiltDungeon;
import win.vampyuyu.survival.dungeon.gen.DungeonBuilder;
import win.vampyuyu.survival.dungeon.gen.DungeonVoidGenerator;
import win.vampyuyu.survival.dungeon.gen.Layout;
import win.vampyuyu.survival.dungeon.gen.LayoutGenerator;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.party.PartyService;

/**
 * Creates, tracks and destroys the dungeon worlds. A run is: reserve a slot, generate the plan off the main thread,
 * create {@code sb_dungeon_<n>}, place the blocks a few milliseconds per tick, start. Destroying a run teleports
 * everybody out, unloads the world and deletes its folder (never anything else, see {@link WorldFiles}).
 */
public final class RunManager {
    /** Creates the empty world of a run. Replaceable in tests. */
    public interface WorldFactory {
        World create(String name, int spawnX, int spawnY, int spawnZ);
    }

    private final RunContext ctx;
    private final WorldFactory worlds;
    private final Map<String, DungeonRun> runs = new LinkedHashMap<>();
    private final Map<UUID, DungeonRun> byPlayer = new HashMap<>();
    private final Map<String, BlockApplier> appliers = new HashMap<>();
    private final Map<String, BossBar> progressBars = new HashMap<>();
    private final Map<String, Boolean> cancelledBuilds = new HashMap<>();
    private int building;
    private int counter;
    private boolean shuttingDown;

    public RunManager(RunContext ctx, WorldFactory worlds) {
        this.ctx = ctx;
        this.worlds = worlds;
    }

    /** The real world factory: void generator, typed game rules. */
    public static WorldFactory voidFactory() {
        return (name, x, y, z) -> {
            WorldCreator creator = new WorldCreator(name);
            creator.generator(new DungeonVoidGenerator(x, y, z));
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            World w = creator.createWorld();
            if (w == null) {
                return null;
            }
            w.setGameRule(GameRules.ADVANCE_TIME, false);
            w.setGameRule(GameRules.ADVANCE_WEATHER, false);
            w.setGameRule(GameRules.SPAWN_MOBS, false);
            w.setGameRule(GameRules.MOB_GRIEFING, false);
            w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            w.setGameRule(GameRules.KEEP_INVENTORY, true);
            w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
            w.setDifficulty(Difficulty.NORMAL);
            w.setPVP(false);
            w.setTime(18000);
            w.setSpawnLocation(x, y, z);
            return w;
        };
    }

    // ------------------------------------------------------------------ queries

    public synchronized DungeonRun runOf(UUID player) { return byPlayer.get(player); }

    public synchronized DungeonRun run(String id) { return runs.get(id); }

    public synchronized DungeonRun runInWorld(World world) {
        if (world == null) {
            return null;
        }
        for (DungeonRun r : runs.values()) {
            if (r.world == world || (r.world != null && r.world.getName().equals(world.getName()))) {
                return r;
            }
        }
        return null;
    }

    public synchronized List<DungeonRun> all() { return new ArrayList<>(runs.values()); }

    /** Runs alive plus those still being generated. */
    public synchronized int load() { return runs.size() + building; }

    /** True for a player standing in any dungeon world (participant or not). */
    public static boolean inDungeonWorld(Player p) {
        return p != null && p.getWorld() != null && SkyblockWorlds.isDungeon(p.getWorld().getName());
    }

    // ------------------------------------------------------------------ start

    /** Everything that can refuse a start, checked before any resource is taken. Null when the party may go. */
    public String check(Party party, UUID starter, int floorNumber) {
        DungeonConfig cfg = ctx.cfg;
        DungeonConfig.Floor floor = cfg.floor(floorNumber);
        if (floor == null) {
            return "There is no floor " + floorNumber + ". Floors: 1-" + cfg.floorCount() + ".";
        }
        if (!party.isLeader(starter)) {
            return "Only the party leader can start a run.";
        }
        if (party.inRun()) {
            return "Your party is already in a run.";
        }
        if (load() >= cfg.maxConcurrentRuns) {
            return "All the crypts are busy right now (" + cfg.maxConcurrentRuns + " runs). Try again in a moment.";
        }
        for (UUID m : party.members()) {
            Player p = Bukkit.getPlayer(m);
            if (p == null || !p.isOnline()) {
                return party.nameOf(m) + " is offline.";
            }
            if (runOf(m) != null) {
                return party.nameOf(m) + " is already in a dungeon.";
            }
            if (inDungeonWorld(p)) {
                return party.nameOf(m) + " is inside a dungeon world.";
            }
            if (cfg.requirePreviousFloor && floorNumber > 1) {
                DungeonData d = ctx.core.profiles().section(m, "dungeon", DungeonData.class, DungeonData::new);
                if (!d.cleared(floorNumber - 1)) {
                    return party.nameOf(m) + " has not cleared floor " + (floorNumber - 1) + " yet.";
                }
            }
            if (floor.minCombatLevel() > 0 && ctx.skills().isPresent()) {
                int level = ctx.skills().get().level(m, Skill.COMBAT);
                if (level < floor.minCombatLevel()) {
                    return party.nameOf(m) + " needs Combat level " + floor.minCombatLevel() + " (has " + level + ").";
                }
            }
        }
        return null;
    }

    /** Starts a run for the party. Returns a failure message or null when the run is on its way. */
    public String start(Party party, UUID starter, int floorNumber, Long forcedSeed) {
        String problem = check(party, starter, floorNumber);
        if (problem != null) {
            return problem;
        }
        DungeonConfig cfg = ctx.cfg;
        DungeonConfig.Floor floor = cfg.floor(floorNumber);
        final String id;
        synchronized (this) {
            id = nextId();
            building++;
            cancelledBuilds.put(id, false);
        }
        ctx.parties.setRun(party, id);
        long seed = forcedSeed != null ? forcedSeed : ThreadLocalRandom.current().nextLong();
        BossBar bar = BossBar.bossBar(Msg.component("<gray>Building floor " + floorNumber + "..."), 0f,
            BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        progressBars.put(id, bar);
        List<UUID> members = party.members();
        for (UUID m : members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) {
                p.showBossBar(bar);
                ctx.core.msg().info(p, "Opening the crypt, floor " + floorNumber + ": " + floor.name() + ". Hold on...");
            }
        }
        ctx.scheduler().async(() -> {
            Layout layout;
            BuiltDungeon built;
            try {
                layout = LayoutGenerator.generate(floor, seed);
                built = DungeonBuilder.build(layout, floor, cfg.baseY);
            } catch (Throwable t) {
                ctx.log().warning("Dungeon: generation of floor " + floorNumber + " failed: " + t);
                ctx.scheduler().sync(() -> abortBuild(id, party.id, "The crypt could not be generated."));
                return;
            }
            final Layout l = layout;
            final BuiltDungeon b = built;
            ctx.scheduler().sync(() -> world(id, party.id, floor, seed, l, b));
        });
        return null;
    }

    /** The folder holding the world folders, or null when the server cannot say (never guess a path to delete from). */
    private static Path container() {
        try {
            File f = Bukkit.getWorldContainer();
            return f == null ? null : f.toPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private synchronized String nextId() {
        Path container = container();
        while (true) {
            counter++;
            String name = SkyblockWorlds.dungeon(Integer.toString(counter));
            if (Bukkit.getWorld(name) == null && (container == null || !java.nio.file.Files.exists(container.resolve(name)))) {
                return Integer.toString(counter);
            }
        }
    }

    private void abortBuild(String id, UUID partyId, String message) {
        BossBar bar = progressBars.remove(id);
        Party party = ctx.parties.byId(partyId);
        synchronized (this) {
            if (cancelledBuilds.remove(id) != null) {
                building = Math.max(0, building - 1);
            }
        }
        if (party != null) {
            ctx.parties.setRun(party, null);
            for (UUID m : party.members()) {
                Player p = Bukkit.getPlayer(m);
                if (p != null) {
                    if (bar != null) {
                        p.hideBossBar(bar);
                    }
                    ctx.core.msg().error(p, message);
                }
            }
        }
    }

    private void world(String id, UUID partyId, DungeonConfig.Floor floor, long seed, Layout layout, BuiltDungeon built) {
        Party party = ctx.parties.byId(partyId);
        boolean cancelled;
        synchronized (this) {
            cancelled = shuttingDown || party == null || Boolean.TRUE.equals(cancelledBuilds.get(id));
        }
        if (cancelled) {
            abortBuild(id, partyId, "The run was cancelled.");
            return;
        }
        String name = SkyblockWorlds.dungeon(id);
        Pos spawn = built.spawn();
        World w;
        try {
            w = worlds.create(name, spawn.x(), spawn.y(), spawn.z());
        } catch (RuntimeException e) {
            ctx.log().warning("Dungeon: could not create world " + name + ": " + e);
            w = null;
        }
        if (w == null) {
            abortBuild(id, partyId, "The dungeon world could not be created. Tell an administrator.");
            scheduleDelete(null, name);
            return;
        }
        final World world = w;
        DungeonRun run = new DungeonRun(ctx, id, floor, seed, layout, built, party);
        run.world = world;
        run.closeRequest = () -> destroy(run);
        synchronized (this) {
            runs.put(id, run);
            for (UUID m : run.players.keySet()) {
                byPlayer.put(m, run);
            }
            building = Math.max(0, building - 1);
            cancelledBuilds.remove(id);
        }
        BossBar bar = progressBars.get(id);
        BlockApplier applier = new BlockApplier(ctx.core.getPlugin(), ctx.scheduler(), world, built.buffer,
            ctx.cfg.maxMsPerTick, ctx.cfg.blocksPerCheck,
            percent -> {
                if (bar != null) {
                    bar.progress(Math.max(0f, Math.min(1f, percent / 100f)));
                    bar.name(Msg.component("<gray>Building floor " + floor.number() + "... <white>" + percent + "%"));
                }
            },
            () -> {
                BossBar b = progressBars.remove(id);
                if (b != null) {
                    run.each(p -> p.hideBossBar(b));
                }
                synchronized (this) {
                    appliers.remove(id);
                }
                if (run.state == RunState.GENERATING) {
                    run.begin(world);
                }
            },
            error -> {
                ctx.log().warning("Dungeon " + id + ": block placement failed: " + error);
                run.finish(false, "The crypt could not be built.");
            });
        synchronized (this) {
            appliers.put(id, applier);
        }
        applier.start();
    }

    // ------------------------------------------------------------------ destroy

    /** Sends a player out of the dungeon: to the hub when the island feature is there, else the main world. */
    static void sendOut(RunContext ctx, Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        boolean ok = false;
        var islands = ctx.islands();
        if (islands.isPresent()) {
            ok = ctx.teleportWith(() -> {
                try {
                    return islands.get().teleportHub(p);
                } catch (RuntimeException e) {
                    return false;
                }
            });
        }
        if (!ok) {
            World main = Bukkit.getWorlds().stream().filter(x -> !SkyblockWorlds.isDungeon(x.getName())).findFirst().orElse(null);
            if (main != null) {
                ctx.teleport(p, main.getSpawnLocation());
            }
        }
    }

    /** Ends a run now (admin or last player gone) and closes its world. */
    public void abort(DungeonRun run, String reason) {
        run.finish(false, reason);
        destroy(run);
    }

    /** Teleports everybody out and removes the world. Idempotent. */
    void destroy(DungeonRun run) {
        synchronized (this) {
            if (run.destroying || run.state == RunState.CLOSED) {
                return;
            }
            run.destroying = true;
        }
        run.state = RunState.CLOSING;
        World world = run.world;
        Path folder = world == null ? null : world.getWorldFolder().toPath();
        String name = SkyblockWorlds.dungeon(run.id);
        BlockApplier applier;
        synchronized (this) {
            applier = appliers.remove(run.id);
        }
        BossBar bar = progressBars.remove(run.id);
        if (applier != null) {
            applier.cancel();
        }
        for (UUID id : new ArrayList<>(run.players.keySet())) {
            run.leave(id, false);
        }
        run.dispose();
        if (world != null) {
            for (Player p : new ArrayList<>(world.getPlayers())) {
                if (bar != null) {
                    p.hideBossBar(bar);
                }
                sendOut(ctx, p);
            }
        }
        Party party = ctx.parties.byId(run.partyId);
        if (party != null) {
            ctx.parties.setRun(party, null);
        }
        if (applier != null) {
            applier.releaseTickets();
        }
        synchronized (this) {
            runs.remove(run.id);
            byPlayer.values().removeIf(r -> r == run);
        }
        unload(run, world, name, folder, 0);
    }

    private void unload(DungeonRun run, World world, String name, Path folder, int attempt) {
        if (world == null) {
            run.state = RunState.CLOSED;
            return;
        }
        for (Player p : new ArrayList<>(world.getPlayers())) {
            sendOut(ctx, p);
        }
        boolean ok;
        try {
            ok = Bukkit.unloadWorld(world, false);
        } catch (RuntimeException e) {
            ok = false;
        }
        if (ok) {
            run.state = RunState.CLOSED;
            scheduleDelete(folder, name);
            return;
        }
        if (attempt >= 6) {
            ctx.log().warning("Dungeon: world " + name + " could not be unloaded; its folder is removed at the next start");
            run.state = RunState.CLOSED;
            return;
        }
        ctx.scheduler().later(40, () -> unload(run, world, name, folder, attempt + 1));
    }

    private void scheduleDelete(Path folder, String name) {
        Path container = container();
        if (container == null) {
            return;
        }
        Path target = folder != null ? folder : container.resolve(name);
        Runnable job = () -> {
            if (!WorldFiles.isDungeonWorldName(name)) {
                return;
            }
            Path real = WorldFiles.isDungeonWorldName(target.getFileName().toString()) ? target : container.resolve(name);
            if (!WorldFiles.deleteWorldFolder(container, real)) {
                ctx.log().warning("Dungeon: could not delete " + real);
            }
        };
        if (shuttingDown) {
            job.run();
        } else {
            ctx.scheduler().async(job);
        }
    }

    // ------------------------------------------------------------------ leftovers and shutdown

    /** Deletes sb_dungeon_* folders that no loaded world uses (crash leftovers). Safe to call at enable. */
    public void cleanOrphans() {
        Path container = container();
        if (container == null) {
            return;
        }
        List<String> loaded = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            loaded.add(w.getName());
        }
        ctx.scheduler().async(() -> deleteOrphans(container, loaded));
    }

    /** Synchronous variant used by the tests and at disable. Returns the number of folders removed. */
    public int deleteOrphans(Path container, List<String> loadedWorlds) {
        int n = 0;
        for (Path folder : WorldFiles.findDungeonFolders(container, 4)) {
            String name = folder.getFileName().toString();
            if (loadedWorlds.contains(name)) {
                continue;
            }
            if (WorldFiles.deleteWorldFolder(container, folder)) {
                n++;
            }
        }
        if (n > 0) {
            ctx.log().info("Dungeon: removed " + n + " leftover world folder(s)");
        }
        return n;
    }

    /** Disable: ends every run, sends everybody out, deletes the worlds synchronously. */
    public void shutdown() {
        List<DungeonRun> list;
        synchronized (this) {
            shuttingDown = true;
            list = new ArrayList<>(runs.values());
            for (String id : new ArrayList<>(cancelledBuilds.keySet())) {
                cancelledBuilds.put(id, true);
            }
        }
        for (DungeonRun run : list) {
            try {
                World world = run.world;
                Path folder = world == null ? null : world.getWorldFolder().toPath();
                run.closeRequest = () -> { };
                run.destroying = true;
                run.state = RunState.CLOSING;
                for (UUID id : new ArrayList<>(run.players.keySet())) {
                    run.leave(id, false);
                }
                run.dispose();
                BlockApplier applier;
                synchronized (this) {
                    applier = appliers.remove(run.id);
                }
                if (applier != null) {
                    applier.cancel();
                }
                if (world != null) {
                    for (Player p : new ArrayList<>(world.getPlayers())) {
                        sendOut(ctx, p);
                    }
                    if (applier != null) {
                        applier.releaseTickets();
                    }
                    boolean unloaded = Bukkit.unloadWorld(world, false);
                    Path container = container();
                    if (unloaded && container != null) {
                        WorldFiles.deleteWorldFolder(container, folder != null ? folder : container.resolve(world.getName()));
                    }
                }
                Party party = ctx.parties.byId(run.partyId);
                if (party != null) {
                    ctx.parties.setRun(party, null);
                }
                run.state = RunState.CLOSED;
            } catch (RuntimeException e) {
                ctx.log().warning("Dungeon " + run.id + ": shutdown failed: " + e);
            }
        }
        synchronized (this) {
            runs.clear();
            byPlayer.clear();
            appliers.clear();
        }
        progressBars.clear();
        Path container = container();
        if (container != null) {
            deleteOrphans(container, Bukkit.getWorlds().stream().map(World::getName).toList());
        }
    }

    /** A participant quit or was removed: leave the run, no teleport (the server does not care about offline players). */
    public void playerGone(UUID id) {
        DungeonRun run = runOf(id);
        if (run != null) {
            run.leave(id, false);
            synchronized (this) {
                byPlayer.remove(id);
            }
        }
    }

    /** Participant asked to leave (command). */
    public boolean leave(Player p) {
        DungeonRun run = runOf(p.getUniqueId());
        if (run == null) {
            return false;
        }
        run.leave(p.getUniqueId(), true);
        synchronized (this) {
            byPlayer.remove(p.getUniqueId());
        }
        return true;
    }
}
