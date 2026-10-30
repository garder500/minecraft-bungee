package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.dungeon.gen.BuiltDungeon;
import win.vampyuyu.survival.dungeon.gen.DoorKind;
import win.vampyuyu.survival.dungeon.gen.Layout;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.gen.RoomBuild;
import win.vampyuyu.survival.dungeon.gen.RoomType;
import win.vampyuyu.survival.dungeon.party.Party;

/**
 * One dungeon run: the world, the players and the state machine. Everything runs on the main thread. The
 * behaviour is split in directors: {@link FightDirector} (rooms, doors, keys), {@link BossDirector},
 * {@link DeathDirector} (ghosts, revive) and {@link Finale} (score, rewards).
 */
public final class DungeonRun {
    static final String PREFIX = "<dark_gray>[<dark_red>Dungeon<dark_gray>] ";
    /** A tick of the run loop is this many server ticks. */
    static final int STEP = 5;

    final RunContext ctx;
    public final String id;
    public final DungeonConfig.Floor floor;
    public final long seed;
    public final Layout layout;
    public final BuiltDungeon built;
    public final UUID partyId;
    final Map<UUID, RunPlayer> players = new LinkedHashMap<>();
    final Map<Integer, RoomRt> rooms = new LinkedHashMap<>();
    final boolean[] doorOpen;
    /** Chest and lever positions already used ({@link Pos#key()}). */
    final Set<Long> used = new HashSet<>();
    /** Armor stands and NPCs spawned for the run, removed with it. */
    final Set<UUID> props = new HashSet<>();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private final Random rng;

    World world;
    volatile RunState state = RunState.GENERATING;
    long tick;
    long countdownEndTick;
    int lastCountdownShown = -1;
    long startMillis;
    long endMillis;
    boolean success;
    String failReason = "";
    int witherKeys;
    int roomsVisited;
    int mobsSpawned;
    int mobsKilled;
    int secretsFound;
    int puzzlesSolved;
    int miniBossesKilled;
    int fightRoomsCleared;
    Runnable closeRequest = () -> { };
    boolean destroying;

    final FightDirector fights;
    final BossDirector boss;
    final DeathDirector deaths;
    final Finale finale;
    Finale.Result result;

    public DungeonRun(RunContext ctx, String id, DungeonConfig.Floor floor, long seed, Layout layout,
            BuiltDungeon built, Party party) {
        this.ctx = ctx;
        this.id = id;
        this.floor = floor;
        this.seed = seed;
        this.layout = layout;
        this.built = built;
        this.partyId = party.id;
        this.rng = new Random(seed ^ 0x5DEECE66DL);
        this.doorOpen = new boolean[layout.doors().size()];
        for (UUID m : party.members()) {
            players.put(m, new RunPlayer(m, party.nameOf(m)));
        }
        this.fights = new FightDirector(this);
        this.boss = new BossDirector(this);
        this.deaths = new DeathDirector(this);
        this.finale = new Finale(this);
    }

    // ------------------------------------------------------------------ accessors

    public RunState state() { return state; }

    public World world() { return world; }

    public Random rng() { return rng; }

    public Keys keys() { return ctx.keys; }

    public int floorNumber() { return floor.number(); }

    public int partySize() { return players.size(); }

    public boolean isParticipant(UUID player) {
        RunPlayer rp = players.get(player);
        return rp != null && !rp.left;
    }

    public List<UUID> participants() {
        List<UUID> out = new ArrayList<>();
        players.forEach((k, v) -> {
            if (!v.left) {
                out.add(k);
            }
        });
        return out;
    }

    public long elapsedMillis() {
        if (startMillis == 0) {
            return 0;
        }
        return (endMillis != 0 ? endMillis : System.currentTimeMillis()) - startMillis;
    }

    RunPlayer player(UUID id) { return players.get(id); }

    Location loc(Pos p) { return new Location(world, p.x() + 0.5, p.y(), p.z() + 0.5); }

    RoomRt room(int index) { return rooms.get(index); }

    /** Online participants that have not left. */
    void each(Consumer<Player> action) {
        for (RunPlayer rp : new ArrayList<>(players.values())) {
            if (rp.left) {
                continue;
            }
            Player p = Bukkit.getPlayer(rp.id);
            if (p != null && p.isOnline()) {
                action.accept(p);
            }
        }
    }

    /** Online participants that are alive (not ghosts, not spectators). */
    void eachAlive(Consumer<Player> action) {
        each(p -> {
            RunPlayer rp = players.get(p.getUniqueId());
            if (rp != null && rp.alive()) {
                action.accept(p);
            }
        });
    }

    int aliveCount() {
        int n = 0;
        for (RunPlayer rp : players.values()) {
            if (rp.alive()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers used by the puzzles and directors

    public void setBlock(Pos p, String data) {
        if (world == null) {
            return;
        }
        try {
            world.getBlockAt(p.x(), p.y(), p.z()).setBlockData(Bukkit.createBlockData(data.contains(":") ? data : "minecraft:" + data), false);
        } catch (IllegalArgumentException e) {
            ctx.log().warning("Dungeon: invalid block data '" + data + "'");
        }
    }

    public void later(long ticks, Runnable r) {
        tasks.add(ctx.scheduler().later(ticks, () -> {
            if (state != RunState.CLOSED) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    ctx.log().warning("Dungeon " + id + ": delayed task failed: " + e);
                }
            }
        }));
        if (tasks.size() > 400) {
            tasks.removeIf(Scheduler.Handle::isCancelled);
        }
    }

    public void sound(Pos p, Sound s, float volume, float pitch) {
        if (world != null) {
            world.playSound(loc(p), s, volume, pitch);
        }
    }

    /** A line of chat in the dungeon style. {@code mini} is MiniMessage written by the feature (no player input). */
    public void say(Player p, String mini) {
        p.sendMessage(Msg.component(PREFIX + mini));
    }

    public void sayAll(String mini) {
        Component c = Msg.component(PREFIX + mini);
        each(p -> p.sendMessage(c));
    }

    void titleAll(String title, String subtitle) {
        Title t = Title.title(Msg.component(title), Msg.component(subtitle),
            Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1400), java.time.Duration.ofMillis(400)));
        each(p -> p.showTitle(t));
    }

    void soundAll(Sound s, float volume, float pitch) {
        each(p -> p.playSound(p.getLocation(), s, volume, pitch));
    }

    /** Damage from the dungeon (puzzles, traps): goes through the normal damage event so the death rules apply. */
    public void hurt(Player p, double amount) {
        RunPlayer rp = players.get(p.getUniqueId());
        if (rp != null && rp.alive() && amount > 0) {
            p.damage(amount);
        }
    }

    public boolean anyoneAt(Pos p) {
        for (RunPlayer rp : players.values()) {
            Player pl = rp.left ? null : Bukkit.getPlayer(rp.id);
            if (pl != null && pl.getWorld() == world) {
                Location l = pl.getLocation();
                if (Math.abs(l.getBlockX() - p.x()) <= 0 && Math.abs(l.getBlockZ() - p.z()) <= 0
                        && Math.abs(l.getBlockY() - p.y()) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Invisible name tag (armor stand marker). */
    public UUID spawnLabel(Location at, String mini) {
        if (world == null || at.getWorld() != world) {
            return null;
        }
        try {
            ArmorStand stand = world.spawn(at, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setSilent(true);
                s.setPersistent(false);
                s.customName(Msg.component(mini));
                s.setCustomNameVisible(true);
                s.getPersistentDataContainer().set(ctx.keys.run, PersistentDataType.STRING, id);
            });
            props.add(stand.getUniqueId());
            return stand.getUniqueId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A harmless villager that can be talked to. */
    public UUID spawnNpc(Location at, String npcId, String mini) {
        if (world == null || at.getWorld() != world) {
            return null;
        }
        try {
            Villager v = world.spawn(at, Villager.class, s -> {
                s.setAI(false);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.setPersistent(false);
                s.setRemoveWhenFarAway(false);
                s.setCollidable(false);
                s.customName(Msg.component(mini));
                s.setCustomNameVisible(true);
                var pdc = s.getPersistentDataContainer();
                pdc.set(ctx.keys.run, PersistentDataType.STRING, id);
                pdc.set(ctx.keys.npc, PersistentDataType.STRING, npcId);
            });
            props.add(v.getUniqueId());
            return v.getUniqueId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A puzzle was solved: reward chest appears, the team is told. */
    public void puzzleSolved(RoomRt room, Player by) {
        if (room.done) {
            return;
        }
        room.done = true;
        puzzlesSolved++;
        sayAll("<green>" + by.getName() + " solved the puzzle <gray>(" + room.build.plan.puzzle.title() + ")<green>!");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        var spec = room.build.puzzle;
        if (spec != null) {
            Pos chest = spec.chest();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        Pos q = chest.add(dx, dy, dz);
                        if (!(dx == 0 && dz == 0 && dy == 0)) {
                            Block b = world.getBlockAt(q.x(), q.y(), q.z());
                            if (b.getType() == org.bukkit.Material.IRON_BARS) {
                                setBlock(q, "air");
                            }
                        }
                    }
                }
            }
            world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc(chest).add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0);
        }
    }

    // ------------------------------------------------------------------ life cycle

    /** The world exists and the blocks are placed: put the players in and start the countdown. */
    void begin(World w) {
        this.world = w;
        for (RoomBuild rb : built.rooms.values()) {
            rooms.put(rb.plan.index, new RoomRt(rb));
        }
        for (int i = 0; i < doorOpen.length; i++) {
            doorOpen[i] = layout.doors().get(i).kind == DoorKind.OPEN;
        }
        state = RunState.COUNTDOWN;
        Location spawn = loc(built.spawn());
        for (RunPlayer rp : new ArrayList<>(players.values())) {
            Player p = Bukkit.getPlayer(rp.id);
            if (p == null || !p.isOnline()) {
                rp.left = true;
                continue;
            }
            prepare(p, rp);
            ctx.teleport(p, spawn);
            rp.room = layout.entrance().index;
            rp.lastSafe = spawn.clone();
        }
        if (participants().isEmpty()) {
            finish(false, "Nobody was online when the dungeon opened.");
            return;
        }
        fights.lockRoom(layout.entrance().index);
        RoomRt entrance = rooms.get(layout.entrance().index);
        entrance.visited = true;
        roomsVisited = 1;
        int countdown = ctx.cfg.readyCountdownSeconds;
        countdownEndTick = tick + countdown * 20L;
        lastCountdownShown = -1;
        sayAll("<gray>Floor " + floor.number() + ": <white>" + floor.name() + "<gray>. The doors open in <white>" + countdown + "<gray> seconds.");
        tasks.add(ctx.scheduler().repeating(STEP, STEP, this::tick));
        if (countdown == 0) {
            startRunning();
        }
    }

    private void prepare(Player p, RunPlayer rp) {
        rp.originalMode = p.getGameMode();
        rp.originalBoard = p.getScoreboard();
        rp.origAllowFlight = p.getAllowFlight();
        rp.origFlying = p.isFlying();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setGliding(false);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setFoodLevel(20);
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : Math.min(max.getValue(), p.getHealth() + max.getValue()));
        if (ctx.cfg.sidebar) {
            rp.sidebar = new Sidebar(Msg.component("<dark_red><bold>CATACOMBS</bold> <gray>F" + floor.number()));
            rp.sidebar.show(p);
        }
        if (ctx.cfg.darknessEffect) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            rp.darkness = true;
        }
    }

    /** Gives the player back to the world: mode, flight, scoreboard. Does not teleport. */
    void release(Player p, RunPlayer rp) {
        try {
            deaths.cleanup(rp);
            if (rp.darkness) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
                rp.darkness = false;
            }
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.setGameMode(rp.originalMode == null ? GameMode.SURVIVAL : rp.originalMode);
            p.setAllowFlight(rp.origAllowFlight || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
            if (rp.sidebar != null) {
                rp.sidebar.hide(p, rp.originalBoard);
                rp.sidebar = null;
            }
            for (RoomRt r : rooms.values()) {
                if (r.bar != null) {
                    p.hideBossBar(r.bar);
                }
            }
            boss.hide(p);
        } catch (RuntimeException e) {
            ctx.log().warning("Dungeon " + id + ": could not restore " + p.getName() + ": " + e);
        }
    }

    /** A participant leaves for good (command, quit, teleported away). */
    public void leave(UUID playerId, boolean sendOut) {
        RunPlayer rp = players.get(playerId);
        if (rp == null || rp.left) {
            return;
        }
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            release(p, rp);
        }
        rp.left = true;
        if (p != null && sendOut) {
            ctx.scheduler().nextTick(() -> RunManager.sendOut(ctx, p));
        }
        if (!state.over()) {
            sayAll("<gray>" + rp.name + " left the dungeon.");
            if (participants().isEmpty() || aliveOrReturning() == 0) {
                finish(false, "Everyone left the dungeon.");
            }
        } else if (participants().isEmpty()) {
            closeRequest.run();
        }
    }

    /** Players that can still fight: alive, or ghosts that will come back. */
    int aliveOrReturning() {
        int n = 0;
        for (RunPlayer rp : players.values()) {
            if (!rp.left && !rp.eliminated) {
                n++;
            }
        }
        return n;
    }

    void startRunning() {
        if (state != RunState.COUNTDOWN) {
            return;
        }
        state = RunState.RUNNING;
        startMillis = System.currentTimeMillis();
        fights.unlockRoom(layout.entrance().index);
        titleAll("<red><bold>GO!", "<gray>Find the blood key and slay the Crypt Lord");
        soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
        fights.spawnInitialGuards();
    }

    /** Ends the run. Idempotent. The world closes after the configured delay. */
    void finish(boolean ok, String reason) {
        if (state.over()) {
            return;
        }
        if (state == RunState.GENERATING) {
            state = ok ? RunState.SUCCESS : RunState.FAILED;
            failReason = reason;
            closeRequest.run();
            return;
        }
        endMillis = System.currentTimeMillis();
        if (startMillis == 0) {
            startMillis = endMillis;
        }
        success = ok;
        failReason = reason;
        state = ok ? RunState.SUCCESS : RunState.FAILED;
        fights.stopAll();
        boss.stop();
        try {
            result = finale.run(ok, reason);
        } catch (RuntimeException e) {
            ctx.log().warning("Dungeon " + id + ": finale failed: " + e);
        }
        long delay = ctx.cfg.closeDelaySeconds * 20L;
        tasks.add(ctx.scheduler().later(delay, closeRequest));
    }

    /** Cancels every task and removes every entity spawned for the run. Players must already be out. */
    void dispose() {
        state = RunState.CLOSING;
        for (Scheduler.Handle h : tasks) {
            h.cancel();
        }
        tasks.clear();
        fights.stopAll();
        boss.stop();
        for (RoomRt r : rooms.values()) {
            r.mobs.clear();
        }
        if (world != null) {
            for (Entity e : new ArrayList<>(world.getEntities())) {
                if (!(e instanceof Player)) {
                    e.remove();
                }
            }
        }
        props.clear();
    }

    // ------------------------------------------------------------------ the run loop

    private void tick() {
        if (state == RunState.CLOSED || state == RunState.CLOSING || world == null) {
            return;
        }
        tick += STEP;
        try {
            checkPlayers();
            if (state == RunState.COUNTDOWN) {
                countdown();
            }
            if (state.active()) {
                locate();
                deaths.tick();
                fights.tick();
                boss.tick();
                timeouts();
            } else if (state.over()) {
                deaths.tickOver();
            }
            if (tick % 20 == 0) {
                hud();
            }
        } catch (RuntimeException e) {
            ctx.log().warning("Dungeon " + id + ": tick failed: " + e);
        }
    }

    private void countdown() {
        long left = countdownEndTick - tick;
        int seconds = (int) Math.ceil(Math.max(0, left) / 20.0);
        if (seconds != lastCountdownShown && seconds > 0 && seconds <= 5) {
            lastCountdownShown = seconds;
            titleAll("<yellow>" + seconds, "<gray>Get ready");
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        }
        if (left <= 0) {
            startRunning();
        }
    }

    /** Drops players that left the world or went offline without the quit event reaching us. */
    private void checkPlayers() {
        for (RunPlayer rp : new ArrayList<>(players.values())) {
            if (rp.left) {
                continue;
            }
            Player p = Bukkit.getPlayer(rp.id);
            if (p == null || !p.isOnline()) {
                leave(rp.id, false);
            } else if (p.getWorld() != world) {
                leave(rp.id, false);
            }
        }
    }

    /** Room entry, fall recovery, rule enforcement. */
    private void locate() {
        eachAlive(p -> {
            RunPlayer rp = players.get(p.getUniqueId());
            Location l = p.getLocation();
            if (p.getAllowFlight() && p.getGameMode() != GameMode.CREATIVE) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
            if (p.isGliding()) {
                p.setGliding(false);
            }
            RoomBuild rb = built.roomAt(l.getX(), l.getZ());
            if (rb != null && rb.plan.index != rp.room) {
                enterRoom(p, rp, rb);
            }
            int floorY = rb != null && rb.plan.type == RoomType.TRAP ? rb.fallY : built.baseY - 12;
            if (l.getY() < floorY) {
                deaths.fell(p, rp);
            }
        });
    }

    private void enterRoom(Player p, RunPlayer rp, RoomBuild rb) {
        rp.room = rb.plan.index;
        RoomRt rt = rooms.get(rb.plan.index);
        if (rt == null) {
            return;
        }
        Location here = p.getLocation();
        Pos best = null;
        double bestD = Double.MAX_VALUE;
        for (Pos landing : rb.landings) {
            double d = landing.distanceSquared(here.getX(), here.getY(), here.getZ());
            if (d < bestD) {
                bestD = d;
                best = landing;
            }
        }
        rp.lastSafe = best != null ? loc(best) : loc(rb.spawn);
        long now = System.currentTimeMillis();
        if (now - rp.lastRoomTitle > 2500) {
            rp.lastRoomTitle = now;
            p.showTitle(Title.title(Component.empty(), Msg.component("<dark_gray>" + rb.plan.type.title()),
                Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(300))));
        }
        if (!rt.visited) {
            rt.visited = true;
            roomsVisited++;
            fights.firstEnter(rt, p);
        }
        fights.onEnter(rt, p);
    }

    private void timeouts() {
        long minutes = elapsedMillis() / 60_000;
        if (state == RunState.RUNNING || state == RunState.BOSS) {
            if (minutes >= ctx.cfg.runTimeoutMinutes) {
                sayAll("<red>The crypt collapses: you ran out of time.");
                finish(false, "Time ran out.");
                return;
            }
            if (state == RunState.BOSS && boss.timedOut()) {
                sayAll("<red>The Crypt Lord's power was too great: the boss timer ran out.");
                finish(false, "The boss was not defeated in time.");
            }
        }
        if (state.active() && aliveOrReturning() == 0) {
            sayAll("<red>The whole party has fallen.");
            finish(false, "The whole party has fallen.");
        }
    }

    // ------------------------------------------------------------------ sidebar

    static String clock(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    void hud() {
        if (!ctx.cfg.sidebar) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.component("<dark_gray>" + floor.name()));
        lines.add(Component.empty());
        lines.add(Msg.component("<gray>Time: <white>" + clock(elapsedMillis())
            + (state == RunState.COUNTDOWN ? " <gray>(starting)" : "")));
        lines.add(Msg.component("<gray>Rooms: <white>" + roomsVisited + "<dark_gray>/<white>" + layout.rooms().size()));
        lines.add(Msg.component("<gray>Secrets: <white>" + secretsFound + "<dark_gray>/<white>" + layout.totalSecrets()));
        lines.add(Msg.component("<gray>Puzzles: <white>" + puzzlesSolved + "<dark_gray>/<white>" + layout.puzzleCount()));
        lines.add(Msg.component("<gray>Wither keys: <white>" + witherKeys));
        lines.add(Msg.component("<gray>Blood key: " + fights.bloodStatus()));
        lines.add(Component.empty());
        for (RunPlayer rp : players.values()) {
            if (lines.size() >= 13) {
                break;
            }
            String state = rp.left ? "<dark_gray>left" : rp.eliminated ? "<red>dead" : rp.ghost ? "<aqua>ghost" : "<green>alive";
            lines.add(Msg.component("<white>" + rp.name + " " + state));
        }
        each(p -> {
            RunPlayer rp = players.get(p.getUniqueId());
            if (rp != null && rp.sidebar != null) {
                rp.sidebar.update(lines);
            }
        });
    }
}
