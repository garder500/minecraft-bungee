package win.vampyuyu.survival.bastion;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import win.vampyuyu.survival.api.BastionHook;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.dungeon.DungeonService;
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.run.RunManager;
import win.vampyuyu.survival.dungeon.run.WorldFiles;

/**
 * Opens, runs and closes the Bastion instances. A run is: check everything, create {@code sb_bastion_<n>} with its arena,
 * bring the party in, count down, then spend one key of the leader and wake the Guardian. When the fight ends the party
 * is paid in Memory Shards by contribution (a lost fight pays a small fraction) and sent back.
 */
public final class BastionService {
    private final Core core;
    private BastionConfig cfg;
    private final RunManager.WorldFactory worlds;
    private final Map<Integer, BastionRun> runs = new LinkedHashMap<>();
    private final Map<UUID, BastionRun> byPlayer = new HashMap<>();
    private final List<BastionHook> hooks = new ArrayList<>();
    private int counter;
    private boolean shuttingDown;

    public BastionService(Core core, BastionConfig cfg, RunManager.WorldFactory worlds) {
        this.core = core;
        this.cfg = cfg;
        this.worlds = worlds;
    }

    public BastionConfig cfg() { return cfg; }

    public void reload(BastionConfig fresh) { this.cfg = fresh; }

    /** Adds rules to the Bastions (a secret Bastion, a Guardian scaling). Kept until the plugin disables. */
    public void addHook(BastionHook hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    /** False when a hook hides this Bastion from the viewer. */
    boolean visibleTo(Player viewer, String bastionId) {
        for (BastionHook h : hooks) {
            if (!h.visibleTo(viewer, bastionId)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ queries

    BastionRun runOf(UUID player) { return byPlayer.get(player); }

    BastionRun runIn(World world) {
        if (world == null || !SkyblockWorlds.isBastion(world.getName())) {
            return null;
        }
        for (BastionRun r : runs.values()) {
            if (r.worldName.equals(world.getName())) {
                return r;
            }
        }
        return null;
    }

    public List<String> describeRuns() {
        List<String> out = new ArrayList<>();
        for (BastionRun r : runs.values()) {
            out.add(r.worldName + " " + r.bastion.id() + " players=" + r.members.size() + (r.opened ? " fighting" : " opening"));
        }
        return out;
    }

    public boolean inBastion(UUID player) { return byPlayer.containsKey(player); }

    /** Number of keys of the item in the inventory. */
    int keysOf(Player p, String keyItem) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && core.items().idOf(s).filter(keyItem::equals).isPresent()) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** Removes one key. False when the player holds none. */
    boolean takeKey(Player p, String keyItem) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s != null && core.items().idOf(s).filter(keyItem::equals).isPresent()) {
                if (s.getAmount() <= 1) {
                    p.getInventory().setItem(i, null);
                } else {
                    s.setAmount(s.getAmount() - 1);
                    p.getInventory().setItem(i, s);
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ enter

    /** Everything that can refuse an entry, checked before any world exists. Null when the party may go. */
    String check(Player leader, BastionConfig.Bastion b) {
        if (!cfg.enabled) {
            return "The Bastions are closed right now.";
        }
        if (shuttingDown) {
            return "The server is stopping.";
        }
        Optional<BossApi> boss = core.service(BossApi.class);
        if (boss.isEmpty() || !boss.get().bossIds().contains(b.guardian())) {
            return "The Guardian of the " + b.name() + " is not ready yet.";
        }
        if (runs.size() >= cfg.maxConcurrent) {
            return "All the Bastions are busy right now (" + cfg.maxConcurrent + " fights). Try again in a moment.";
        }
        List<UUID> members = membersOf(leader);
        Party party = partyOf(leader);
        if (party != null && !party.isLeader(leader.getUniqueId())) {
            return "Only the party leader can open a Bastion.";
        }
        if (party != null && party.inRun()) {
            return "Your party is already in a run.";
        }
        if (members.size() < b.minPlayers() || members.size() > b.maxPlayers()) {
            return "The " + b.name() + " takes " + b.minPlayers() + " to " + b.maxPlayers() + " players (your party has "
                + members.size() + ").";
        }
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            String name = party == null ? leader.getName() : party.nameOf(id);
            if (p == null || !p.isOnline()) {
                return name + " is offline.";
            }
            if (byPlayer.containsKey(id)) {
                return name + " is already in a Bastion.";
            }
            if (RunManager.inDungeonWorld(p) || SkyblockWorlds.isBastion(p.getWorld().getName())) {
                return name + " is inside a dungeon.";
            }
        }
        if (cfg.isRaid(b.id())) {
            for (UUID id : members) {
                List<String> missing = RaidRules.missing(cfg, core.profiles().section(id, "bastion", BastionData.class, BastionData::new));
                if (!missing.isEmpty()) {
                    String name = party == null ? leader.getName() : party.nameOf(id);
                    return name + " has not cleared " + RaidRules.names(cfg, missing)
                        + ". The Devourer only wakes for those who beat the Guardians.";
                }
            }
        }
        if (keysOf(leader, b.keyItem()) < 1) {
            String key = core.items().def(b.keyItem()).map(d -> d.name()).orElse("key");
            return "You need a " + key + " to open the " + b.name() + ". The party leader spends it when the fight opens.";
        }
        for (BastionHook h : hooks) {
            String refusal = h.refuse(leader, members, b.id());
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }

    private Party partyOf(Player leader) {
        return core.service(DungeonService.class).map(d -> d.parties.partyOf(leader.getUniqueId())).orElse(null);
    }

    private List<UUID> membersOf(Player leader) {
        Party party = partyOf(leader);
        return party == null ? List.of(leader.getUniqueId()) : party.members();
    }

    /** Opens a Bastion for the leader's party. Returns the English refusal, or null when it is opening. */
    public String enter(Player leader, String bastionId) {
        Optional<BastionConfig.Bastion> found = cfg.get(bastionId);
        if (found.isEmpty()) {
            return "There is no Bastion called " + bastionId + ".";
        }
        BastionConfig.Bastion b = found.get();
        String problem = check(leader, b);
        if (problem != null) {
            return problem;
        }
        Party party = partyOf(leader);
        List<UUID> members = membersOf(leader);
        BastionRun run = new BastionRun(++counter, b, leader.getUniqueId(), party == null ? null : party.id);
        World world;
        try {
            world = worlds.create(run.worldName, 0, BastionArena.FLOOR_Y + 1, 0);
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Bastion world " + run.worldName + " failed: " + e);
            world = null;
        }
        if (world == null) {
            return "The Bastion could not be created. Try again in a moment.";
        }
        run.world = world;
        try {
            BastionArena.build(world, b.palette(), b.radius());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Bastion arena " + run.worldName + " failed: " + e);
            removeWorld(world);
            return "The Bastion could not be built. Try again in a moment.";
        }
        runs.put(run.number, run);
        Location arrival = BastionArena.arrival(world, b.radius());
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            run.members.add(id);
            byPlayer.put(id, run);
            run.returns.put(id, p.getLocation().clone());
            p.setGameMode(GameMode.SURVIVAL);
            p.setFireTicks(0);
            p.teleport(arrival);
        }
        if (party != null) {
            core.service(DungeonService.class).ifPresent(d -> d.parties.setRun(party, "bastion-" + run.number));
        }
        announceOpening(run);
        run.handles.add(core.scheduler().later(Math.max(1, cfg.countdownSeconds) * 20L, () -> open(run)));
        run.handles.add(core.scheduler().later(b.timeLimitSeconds() * 20L, () -> timeUp(run)));
        return null;
    }

    private void announceOpening(BastionRun run) {
        BastionConfig.Bastion b = run.bastion;
        for (UUID id : run.members) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            p.showTitle(Title.title(Msg.component("<gold><bold>" + b.name()), Msg.component("<gray>" + b.realm()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            core.msg().mini(p, "<gray>" + b.tagline());
            core.msg().info(p, "The Guardian wakes in " + cfg.countdownSeconds + " seconds. You can leave with /bastion leave.");
        }
    }

    // ------------------------------------------------------------------ the fight

    private void open(BastionRun run) {
        if (run.closed || run.opened) {
            return;
        }
        BastionConfig.Bastion b = run.bastion;
        Player leader = Bukkit.getPlayer(run.leader);
        if (leader == null || !run.members.contains(run.leader)) {
            tell(run, "The party leader left before the Guardian woke. The Bastion closes, no key was spent.");
            close(run, false);
            return;
        }
        if (keysOf(leader, b.keyItem()) < 1) {
            tell(run, "The key is gone from the leader's inventory. The Bastion closes.");
            close(run, false);
            return;
        }
        BossApi boss = core.service(BossApi.class).orElse(null);
        BastionHook.Scaling scaling = BastionHook.Scaling.NONE;
        for (BastionHook h : hooks) {
            BastionHook.Scaling s = h.scaling(leader, b.id());
            if (s != null) {
                scaling = new BastionHook.Scaling(scaling.hpMultiplier() * s.hpMultiplier(),
                    scaling.damageMultiplier() * s.damageMultiplier());
            }
        }
        run.scaling = scaling;
        Optional<BossApi.Fight> fight = boss == null ? Optional.empty()
            : boss.spawnFight(b.guardian(), BastionArena.centre(run.world), leader, b.keyItem(), scaling.hpMultiplier(),
                scaling.damageMultiplier(), r -> finish(run, r));
        if (fight.isEmpty()) {
            tell(run, "The Guardian did not answer. The Bastion closes, no key was spent.");
            close(run, false);
            return;
        }
        run.fight = fight.get();
        if (!takeKey(leader, b.keyItem())) {
            run.fight.abort(); // cannot happen on the main thread; never leave a free fight
            return;
        }
        run.opened = true;
        run.openedAt = System.currentTimeMillis();
        for (BastionHook h : hooks) {
            h.opened(b.id(), leader, new ArrayList<>(run.members), run.scaling);
        }
        for (UUID id : run.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                core.msg().mini(p, "<red>The Guardian wakes! <gray>" + (leader.getName().replace("<", "")) + " spent the key. You have "
                    + b.deathsAllowed() + " lives each.");
            }
        }
    }

    private void timeUp(BastionRun run) {
        if (!run.closed && run.fight != null && !run.finished) {
            tell(run, "Time is up.");
            run.fight.abort();
        } else if (!run.closed && !run.opened) {
            close(run, false);
        }
    }

    /** Called by the boss feature when the fight ended, whatever the reason. Shards of a clear are the resonance feature's. */
    void finish(BastionRun run, BossApi.FightResult result) {
        if (run.finished || run.closed) {
            return;
        }
        run.finished = true;
        BastionConfig.Bastion b = run.bastion;
        java.util.Set<UUID> present = new java.util.LinkedHashSet<>(run.members);
        final double total = damageOf(result, present);
        switch (result.outcome()) {
            case CLEARED -> {
                String time = Msg.duration(result.elapsedMillis());
                List<UUID> winners = new ArrayList<>();
                for (UUID id : present) {
                    if (result.damage().getOrDefault(id, 0.0) > 0) {
                        winners.add(id);
                    }
                }
                for (BastionHook h : hooks) {
                    h.cleared(b.id(), winners, result.elapsedMillis(), run.scaling);
                }
                for (UUID id : present) {
                    double coins = 0;
                    if (result.damage().getOrDefault(id, 0.0) > 0) {
                        BastionData data = core.profiles().section(id, "bastion", BastionData.class, BastionData::new);
                        coins = cfg.isRaid(b.id()) ? RaidRules.firstClearCoins(cfg, data.cleared(b.id())) : 0;
                        data.recordClear(b.id(), result.elapsedMillis());
                    }
                    if (coins > 0) {
                        double paid = coins;
                        core.service(EconomyApi.class).ifPresent(e -> e.add(id, paid, "raid:first-clear"));
                    }
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && coins > 0) {
                        core.msg().ok(p, "First clear of the Raid: +" + Math.round(coins) + " coins.");
                    }
                    if (p != null) {
                        p.showTitle(Title.title(Msg.component("<green><bold>BASTION CLEARED"), Msg.component("<gray>" + time),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
                        core.msg().ok(p, b.name() + " cleared in " + time + ".");
                    }
                }
            }
            case DEFEATED -> {
                Optional<ResonanceApi> resonance = core.service(ResonanceApi.class);
                for (UUID id : present) {
                    double dmg = Math.max(0, result.damage().getOrDefault(id, 0.0));
                    core.profiles().section(id, "bastion", BastionData.class, BastionData::new).recordDefeat(b.id());
                    if (total > 0 && dmg > 0) {
                        resonance.ifPresent(r -> r.payDefeat(id, b.guardian(), result.resonance(), result.progress(), dmg / total));
                    }
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.showTitle(Title.title(Msg.component("<red><bold>THE GUARDIAN HELD"), Msg.component("<gray>"
                            + Math.round(result.progress() * 100) + "% of its strength taken"),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
                        core.msg().warn(p, "The fight is lost. The key is spent.");
                    }
                }
            }
            case FAULT -> {
                for (UUID id : present) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        core.msg().warn(p, "The fight was interrupted by the server. The leader's key is returned.");
                    }
                }
            }
        }
        long delay = result.outcome() == BossApi.Outcome.FAULT || shuttingDown ? 1 : cfg.exitDelaySeconds * 20L;
        for (UUID id : present) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && delay > 1) {
                core.msg().info(p, "You are sent back in " + cfg.exitDelaySeconds + " seconds (or now with /bastion leave).");
            }
        }
        run.handles.add(core.scheduler().later(delay, () -> close(run, false)));
    }

    /** Sum of the damage of the players who are still in the run (the share is taken among them, like the Shard rules). */
    private static double damageOf(BossApi.FightResult result, java.util.Set<UUID> present) {
        double total = 0;
        for (UUID id : present) {
            total += Math.max(0, result.damage().getOrDefault(id, 0.0));
        }
        return total;
    }

    // ------------------------------------------------------------------ deaths and leaving

    /** A member died in the arena. Returns the English line to show them. */
    String died(BastionRun run, Player p) {
        UUID id = p.getUniqueId();
        int n = run.deaths.merge(id, 1, Integer::sum);
        int allowed = run.bastion.deathsAllowed();
        if (!run.opened || n <= allowed) {
            return run.opened ? "You fell. " + (allowed - n + 1) + " lives left." : "You fell.";
        }
        run.eliminated.add(id);
        core.scheduler().nextTick(() -> {
            if (p.isOnline() && !run.closed && run.eliminated.contains(id)) {
                p.setGameMode(GameMode.SPECTATOR);
            }
        });
        if (!run.anyFighting() && run.fight != null && !run.finished) {
            tell(run, "Nobody is left standing.");
            run.fight.abort();
        }
        return "You are out of lives. Watch your friends from above.";
    }

    /** A member leaves (command, quit, kicked from the party). Their rewards are forfeited. */
    public void leave(UUID id, boolean immediate) {
        BastionRun run = byPlayer.remove(id);
        if (run == null) {
            return;
        }
        run.members.remove(id);
        run.eliminated.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            sendOut(run, p, immediate);
        }
        if (run.closed) {
            return;
        }
        if (run.members.isEmpty()) {
            if (run.fight != null && !run.finished) {
                run.fight.abort(); // finish() runs and closes the instance
            } else {
                close(run, immediate);
            }
        } else if (!run.anyFighting() && run.fight != null && !run.finished) {
            tell(run, "Nobody is left standing.");
            run.fight.abort();
        }
    }

    private void tell(BastionRun run, String text) {
        for (UUID id : run.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                core.msg().info(p, text);
            }
        }
    }

    // ------------------------------------------------------------------ closing

    private void sendOut(BastionRun run, Player p, boolean immediate) {
        if (!p.isOnline()) {
            return;
        }
        p.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) {
            p.removePotionEffect(e.getType());
        }
        Location back = run.returns.get(p.getUniqueId());
        if (back != null && back.getWorld() != null && !SkyblockWorlds.isDungeon(back.getWorld().getName())
                && !SkyblockWorlds.isBastion(back.getWorld().getName())) {
            p.teleport(back);
            return;
        }
        exitFallback(p, immediate);
    }

    private void exitFallback(Player p, boolean immediate) {
        if (!immediate && core.service(IslandApi.class).map(i -> i.teleportHub(p)).orElse(false)) {
            return;
        }
        World target = Bukkit.getWorld(SkyblockWorlds.WORLD);
        if (target == null) {
            target = Bukkit.getWorld(SkyblockWorlds.HUB);
        }
        if (target != null) {
            p.teleport(target.getSpawnLocation());
        }
    }

    /** A player found in a Bastion world with no run (crash leftovers). */
    public void sendOutStray(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        exitFallback(p, false);
    }

    /** Idempotent: sends everybody out, frees the party, removes the world. */
    void close(BastionRun run, boolean immediate) {
        if (run.closed) {
            return;
        }
        run.closed = true;
        run.handles.forEach(h -> h.cancel());
        run.handles.clear();
        if (run.fight != null && !run.finished) {
            run.finished = true;
            // not a defeat the players earned: the world goes away (unloading it makes the boss feature refund the key)
        }
        World world = run.world;
        for (UUID id : new ArrayList<>(run.members)) {
            byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                sendOut(run, p, immediate);
            }
        }
        run.members.clear();
        if (world != null) {
            for (Player p : new ArrayList<>(world.getPlayers())) {
                exitFallback(p, immediate);
            }
        }
        if (run.partyId != null) {
            core.service(DungeonService.class).ifPresent(d -> {
                Party party = d.parties.byId(run.partyId);
                if (party != null) {
                    d.parties.setRun(party, null);
                }
            });
        }
        runs.remove(run.number);
        if (world != null) {
            if (immediate) {
                removeWorld(world);
            } else {
                core.scheduler().later(20, () -> removeWorld(world));
            }
        }
    }

    private void removeWorld(World world) {
        String name = world.getName();
        Path container = Bukkit.getWorldContainer().toPath();
        Path folder = container.resolve(name);
        try {
            if (Bukkit.unloadWorld(world, false)) {
                WorldFiles.deleteWorldFolder(container, folder);
            }
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Bastion world " + name + " could not be removed: " + e);
        }
    }

    /** Admin: ends one instance (or all with null). Returns how many were closed. */
    public int abort(String worldName) {
        int n = 0;
        for (BastionRun r : new ArrayList<>(runs.values())) {
            if (worldName == null || r.worldName.equals(worldName)) {
                if (r.fight != null && !r.finished) {
                    r.fight.abort();
                } else {
                    close(r, false);
                }
                n++;
            }
        }
        return n;
    }

    /** Period of {@link #tick} in game ticks. */
    private static final int TICK_PERIOD = 10;

    /**
     * Keeps players on the platform: a fall into the void brings them back. In the Raid it also drops the outer rings of the
     * arena one after the other once the Devourer reaches its last phase (GDD 4.6). Called twice a second.
     */
    void tick() {
        for (BastionRun r : runs.values()) {
            if (r.world == null) {
                continue;
            }
            r.tickCount += TICK_PERIOD;
            advanceCollapse(r);
            Location arrival = RaidRules.safeSpot(r.world, r.bastion.radius(), r.collapseRadius);
            for (Player p : r.world.getPlayers()) {
                if (p.getLocation().getY() < BastionArena.VOID_Y) {
                    p.setFallDistance(0);
                    p.teleport(arrival);
                }
            }
        }
    }

    private void advanceCollapse(BastionRun r) {
        if (!r.opened || r.finished || r.fight == null || !RaidRules.collapsing(cfg, r.bastion.id(), r.fight.phase())) {
            return;
        }
        if (r.collapseRadius < 0) {
            r.collapseRadius = r.bastion.radius();
            r.nextCollapse = r.tickCount;
            tell(r, "The Devourer's grip loosens: the arena is falling apart. Hold the core!");
        }
        if (r.tickCount < r.nextCollapse || r.collapseRadius <= RaidRules.CORE_RADIUS) {
            return;
        }
        try {
            BastionArena.collapseRing(r.world, r.collapseRadius);
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Raid collapse " + r.worldName + ": " + e);
        }
        r.collapseRadius = RaidRules.nextRadius(r.collapseRadius);
        r.nextCollapse = r.tickCount + RaidRules.ringTicks(cfg);
    }

    /** Disable: everybody out at once, worlds removed. The boss feature refunds a fight that was still going. */
    public void shutdown() {
        shuttingDown = true;
        for (BastionRun r : new ArrayList<>(runs.values())) {
            try {
                close(r, true);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Bastion " + r.worldName + ": shutdown failed: " + e);
            }
        }
        runs.clear();
        byPlayer.clear();
    }
}
