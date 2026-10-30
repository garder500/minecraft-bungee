package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.gen.BuiltDungeon;
import win.vampyuyu.survival.dungeon.gen.DoorKind;
import win.vampyuyu.survival.dungeon.gen.Pos;
import win.vampyuyu.survival.dungeon.gen.RoomBuild;
import win.vampyuyu.survival.dungeon.gen.RoomPlan;
import win.vampyuyu.survival.dungeon.gen.RoomType;

/** Rooms, doors, keys, fights and the Watcher's guards of one run. */
final class FightDirector {
    private static final String LOCK = "iron_bars";

    private final DungeonRun run;
    private final int[] locks;
    private final Map<Long, Integer> doorByBlock = new HashMap<>();
    final Set<UUID> guards = new HashSet<>();
    int guardsTotal;
    int guardsSpawned;
    int guardKills;
    boolean bloodKey;
    private boolean bloodOpened;
    private boolean guardsStarted;

    FightDirector(DungeonRun run) {
        this.run = run;
        this.locks = new int[run.layout.doors().size()];
        BuiltDungeon built = run.built;
        for (BuiltDungeon.DoorBuild d : built.doors) {
            for (Pos p : d.blocks()) {
                doorByBlock.put(p.key(), d.index());
            }
        }
    }

    // ------------------------------------------------------------------ doors

    /** Index of the doorway that contains the block, -1 if none. */
    int doorAt(Block b) {
        return doorByBlock.getOrDefault(Pos.key(b.getX(), b.getY(), b.getZ()), -1);
    }

    private void fillDoor(int index, String block) {
        for (Pos p : run.built.doors.get(index).blocks()) {
            run.setBlock(p, block);
        }
    }

    /** Locks the plain doorways of a room (fights, countdown). Special doors keep their own rules. */
    void lockRoom(int room) {
        for (BuiltDungeon.DoorBuild d : run.built.doors) {
            if (d.plan().touches(room) && d.plan().kind == DoorKind.OPEN) {
                if (locks[d.index()]++ == 0) {
                    fillDoor(d.index(), LOCK);
                }
            }
        }
    }

    void unlockRoom(int room) {
        for (BuiltDungeon.DoorBuild d : run.built.doors) {
            if (d.plan().touches(room) && d.plan().kind == DoorKind.OPEN && locks[d.index()] > 0) {
                if (--locks[d.index()] == 0) {
                    fillDoor(d.index(), "air");
                }
            }
        }
    }

    void openDoor(int index) {
        if (run.doorOpen[index] && locks[index] == 0) {
            return;
        }
        run.doorOpen[index] = true;
        fillDoor(index, "air");
        BuiltDungeon.DoorBuild d = run.built.doors.get(index);
        Pos centre = d.blocks().get(d.blocks().size() / 2);
        run.sound(centre, Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.7f);
        if (run.world != null) {
            run.world.spawnParticle(Particle.CLOUD, run.loc(centre).add(0, 1, 0), 30, 1.2, 1.2, 1.2, 0.02);
        }
    }

    /** Right click on a door block. Returns true when the block belongs to a door (the click is consumed). */
    boolean clickDoor(Player p, Block b) {
        int i = doorAt(b);
        if (i < 0) {
            return false;
        }
        var plan = run.layout.doors().get(i);
        if (run.doorOpen[i] && locks[i] == 0) {
            return true;
        }
        switch (plan.kind) {
            case WITHER -> {
                if (run.witherKeys > 0) {
                    run.witherKeys--;
                    openDoor(i);
                    run.sayAll("<dark_gray>" + p.getName() + " <gray>unlocked a <dark_gray>Wither Door<gray>. Keys left: <white>"
                        + run.witherKeys);
                } else {
                    run.say(p, "<gray>This door is sealed by <dark_gray>Wither<gray> magic. Clear a mob room to claim a <white>Wither Key<gray>.");
                    run.sound(run.built.doors.get(i).blocks().get(0), Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
                }
            }
            case BLOOD -> {
                if (bloodKey) {
                    openBloodDoor();
                } else {
                    int left = guardsLeft();
                    run.say(p, "<red>The blood door is sealed. <gray>The Watcher's guards still live (<white>" + left + "<gray> left).");
                }
            }
            case OPEN -> run.say(p, "<gray>The doors are locked until the fight is over.");
            default -> { }
        }
        return true;
    }

    /** A hidden lever was pulled. Returns true when it belongs to a secret door. */
    boolean clickLever(Player p, Block b) {
        for (RoomBuild rb : run.built.rooms.values()) {
            for (Map.Entry<Integer, Pos> e : rb.hiddenLevers.entrySet()) {
                Pos q = e.getValue();
                if (q.x() == b.getX() && q.y() == b.getY() && q.z() == b.getZ()) {
                    if (run.used.add(q.key())) {
                        run.secretsFound++;
                        RunPlayer rp = run.player(p.getUniqueId());
                        if (rp != null) {
                            rp.secrets++;
                        }
                        run.sayAll("<light_purple>" + p.getName() + " found a secret! <gray>(" + run.secretsFound + "/"
                            + run.layout.totalSecrets() + ")");
                        run.soundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                        openDoor(e.getKey());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** A hidden or reward chest. Returns true when the block is one of the run's chests. */
    boolean clickChest(Player p, Block b) {
        Pos found = null;
        boolean secret = false;
        for (RoomBuild rb : run.built.rooms.values()) {
            for (Pos q : rb.secretChests) {
                if (q.x() == b.getX() && q.y() == b.getY() && q.z() == b.getZ()) {
                    found = q;
                    secret = true;
                }
            }
            if (rb.puzzle != null) {
                Pos q = rb.puzzle.chest();
                if (q.x() == b.getX() && q.y() == b.getY() && q.z() == b.getZ()) {
                    RoomRt rt = run.rooms.get(rb.plan.index);
                    if (rt == null || !rt.done) {
                        run.say(p, "<gray>The chest is locked behind the puzzle.");
                        return true;
                    }
                    found = q;
                }
            }
        }
        if (found == null) {
            return false;
        }
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || !rp.alive()) {
            return true;
        }
        if (!run.used.add(found.key())) {
            return true;
        }
        run.setBlock(found, "air");
        run.world.spawnParticle(Particle.HAPPY_VILLAGER, run.loc(found).add(0, 0.8, 0), 20, 0.4, 0.4, 0.4, 0);
        run.sound(found, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        if (secret) {
            run.secretsFound++;
            rp.secrets++;
            run.sayAll("<light_purple>" + p.getName() + " found a secret chest! <gray>(" + run.secretsFound + "/"
                + run.layout.totalSecrets() + ")");
        }
        run.finale.chestLoot(p, secret ? 1 : 2);
        return true;
    }

    // ------------------------------------------------------------------ guards and the blood key

    int guardsLeft() { return Math.max(0, guardsTotal - guardKills); }

    String bloodStatus() {
        if (bloodOpened) {
            return "<red>used";
        }
        if (bloodKey) {
            return "<green>ready";
        }
        return guardsStarted ? "<gray>guards: <white>" + guardsLeft() : "<dark_gray>-";
    }

    void spawnInitialGuards() {
        RunConfigView cfg = new RunConfigView(run);
        guardsTotal = cfg.guards();
        guardsStarted = true;
        int now = Math.max(1, (guardsTotal + 2) / 3);
        for (int i = 0; i < now; i++) {
            spawnGuard(run.layout.entrance().index);
        }
        run.sayAll("<gray>The <dark_red>Watcher<gray> has sent <white>" + guardsTotal
            + "<gray> guards to find you. Slay them all to forge the <red>blood key<gray>.");
    }

    private void spawnGuard(int room) {
        if (guardsSpawned >= guardsTotal) {
            return;
        }
        RoomRt rt = run.rooms.get(room);
        if (rt == null) {
            return;
        }
        LivingEntity e = spawn(rt, MobRoster.WATCHER_GUARD, "guard", null);
        if (e != null) {
            guards.add(e.getUniqueId());
            guardsSpawned++;
        } else {
            guardsTotal = Math.max(guardsSpawned, guardsTotal - 1); // a guard that cannot spawn must not hold the key back
        }
    }

    private void spawnRemainingGuards(int room) {
        int guard = 0;
        while (guardsSpawned < guardsTotal && guard++ < 12) {
            spawnGuard(room);
        }
    }

    /** Called when a guard dies: forges the key after the last one. */
    void guardDied(UUID id) {
        if (!guards.remove(id)) {
            return;
        }
        guardKills++;
        checkBloodKey();
    }

    private void checkBloodKey() {
        if (guardsStarted && !bloodKey && guardsSpawned >= guardsTotal && guards.isEmpty()) {
            bloodKey = true;
            run.sayAll("<red><bold>The blood key is forged!</bold> <gray>Open the blood door next to the blood room.");
            run.soundAll(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
            RoomRt blood = run.rooms.get(run.layout.blood().index);
            if (blood != null && blood.visited) {
                openBloodDoor();
            }
        }
    }

    void openBloodDoor() {
        if (bloodOpened) {
            return;
        }
        for (var d : run.layout.doors()) {
            if (d.kind == DoorKind.BLOOD) {
                bloodOpened = true;
                openDoor(run.layout.doors().indexOf(d));
                run.sayAll("<red>The blood door grinds open...");
                run.soundAll(Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ room entry

    void firstEnter(RoomRt rt, Player p) {
        RoomPlan plan = rt.build.plan;
        switch (plan.type) {
            case MOB -> startFight(rt, MobRoster.waves(run.floorNumber(), run.partySize(),
                run.ctx.cfg.maxMobsPerRoom, run.rng()), false);
            case MINIBOSS -> startFight(rt, miniBossWaves(), true);
            case PUZZLE -> {
                rt.puzzle = PuzzleRuntime.create(run, rt);
                if (rt.puzzle != null) {
                    run.say(p, "<yellow>" + plan.puzzle.title() + ": " + rt.puzzle.instructions());
                    rt.puzzle.onFirstEnter();
                }
            }
            case FAIRY -> fairy(rt, p);
            case SHOP -> {
                if (rt.build.merchant != null) {
                    Pos m = rt.build.merchant;
                    run.spawnNpc(new Location(run.world, m.x() + 0.5, m.y(), m.z() + 0.5), "merchant", "<gold>Crypt Merchant");
                    run.say(p, "<gold>A wandering merchant offers supplies. <gray>Right-click him to trade.");
                }
            }
            case BLOOD -> {
                spawnRemainingGuards(plan.index);
                if (rt.build.bloodSign != null) {
                    run.spawnLabel(run.loc(rt.build.bloodSign).add(0, 0.5, 0), "<red><bold>BLOOD DOOR");
                }
                run.say(p, "<red>The air is thick with blood. <gray>Slay the Watcher's guards, then open the door.");
                if (bloodKey) {
                    openBloodDoor();
                }
            }
            case BOSS -> run.boss.onFirstEnter(p);
            case SECRET -> run.say(p, "<light_purple>A secret room! <gray>Look around for hidden chests.");
            default -> { }
        }
        if (plan.type != RoomType.ENTRANCE && plan.type != RoomType.BOSS && plan.type != RoomType.BLOOD
                && guardsStarted && guardsSpawned < guardsTotal && run.state == RunState.RUNNING) {
            spawnGuard(plan.index);
        }
    }

    void onEnter(RoomRt rt, Player p) {
        if (rt.puzzle != null && !rt.puzzle.solved && rt.build.plan.type == RoomType.PUZZLE) {
            RunPlayer rp = run.player(p.getUniqueId());
            long now = System.currentTimeMillis();
            Long last = rp == null ? null : rp.hintAt.get(rt.index());
            if (rp != null && (last == null || now - last > 60_000)) {
                rp.hintAt.put(rt.index(), now);
                run.say(p, "<yellow>" + rt.puzzle.instructions());
            }
        }
    }

    private void fairy(RoomRt rt, Player first) {
        if (rt.fairyUsed) {
            return;
        }
        rt.fairyUsed = true;
        rt.done = true;
        run.sayAll("<light_purple>A fairy blesses the party: <gray>regeneration, speed and strength for two minutes.");
        run.eachAlive(pl -> {
            var max = pl.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                pl.setHealth(max.getValue());
            }
            pl.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 120, 0, false, true));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 120, 0, false, true));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 120, 0, false, true));
            pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        });
        if (rt.build.fairy != null) {
            run.world.spawnParticle(Particle.END_ROD, run.loc(rt.build.fairy), 60, 1.5, 1.0, 1.5, 0.02);
        }
    }

    // ------------------------------------------------------------------ fights

    private List<List<MobSpec>> miniBossWaves() {
        List<MobSpec> boss = new ArrayList<>();
        boss.add(MobRoster.WARLORD);
        int adds = Math.min(run.ctx.cfg.maxMobsPerRoom - 1, 2 + run.floorNumber() / 2 + run.partySize());
        for (int i = 0; i < adds; i++) {
            boss.add(MobRoster.pick(run.floorNumber(), run.rng()));
        }
        return List.of(boss);
    }

    void startFight(RoomRt rt, List<List<MobSpec>> waves, boolean mini) {
        rt.state = RoomRt.State.ACTIVE;
        rt.waves = waves;
        rt.wave = 0;
        rt.queue.clear();
        rt.queue.addAll(waves.get(0));
        rt.spawnedTotal = 0;
        rt.level = mini ? 1 : 0;
        int total = 0;
        for (List<MobSpec> w : waves) {
            total += w.size();
        }
        rt.fightTotal = total;
        lockRoom(rt.index());
        rt.bar = BossBar.bossBar(Msg.component(mini ? "<dark_red><bold>Mini-boss" : "<red>Room fight"), 1f,
            mini ? BossBar.Color.PURPLE : BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        run.eachAlive(p -> p.showBossBar(rt.bar));
        run.sayAll(mini ? "<dark_red>A mini-boss blocks the way! <gray>The doors lock." : "<red>The doors lock! <gray>Defeat every enemy.");
        run.soundAll(Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.6f);
    }

    int liveMobs() {
        int n = guards.size() + run.boss.addCount();
        for (RoomRt r : run.rooms.values()) {
            n += r.mobs.size();
        }
        return n;
    }

    private Player nearestAlive(Location at) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (RunPlayer rp : run.players.values()) {
            if (!rp.alive()) {
                continue;
            }
            Player p = Bukkit.getPlayer(rp.id);
            if (p == null || p.getWorld() != at.getWorld()) {
                continue;
            }
            double d = p.getLocation().distanceSquared(at);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Spawns one monster of a room fight (or a guard). */
    LivingEntity spawn(RoomRt rt, MobSpec spec, String kind, Location where) {
        Location at = where;
        if (at == null) {
            List<Pos> spots = rt.build.mobSpawns;
            if (spots.isEmpty()) {
                at = run.loc(rt.build.spawn);
            } else {
                Pos pick = spots.get(run.rng().nextInt(spots.size()));
                for (int i = 0; i < 3; i++) {
                    Pos alt = spots.get(run.rng().nextInt(spots.size()));
                    Player near = nearestAlive(run.loc(alt));
                    Player nearPick = nearestAlive(run.loc(pick));
                    if (near == null || nearPick == null
                            || near.getLocation().distanceSquared(run.loc(alt)) > nearPick.getLocation().distanceSquared(run.loc(pick))) {
                        pick = alt;
                    }
                }
                at = run.loc(pick);
            }
        }
        double party = MobRoster.partyHealth(run.partySize(), run.ctx.cfg.partyHealthScale);
        double dmgParty = MobRoster.partyDamage(run.partySize(), run.ctx.cfg.partyDamageScale);
        LivingEntity e = run.ctx.mobFactory.spawn(run.world, at, spec, run.id, rt.index(), kind, run.floorNumber(),
            run.floor.mobLevel(), run.floor.hp() * party, run.floor.damage() * dmgParty, nearestAlive(at));
        if (e == null) {
            return null;
        }
        run.mobsSpawned++;
        run.world.spawnParticle(Particle.SOUL, e.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
        return e;
    }

    void tick() {
        for (RoomRt rt : run.rooms.values()) {
            if (rt.state == RoomRt.State.ACTIVE) {
                updateFight(rt);
            }
        }
        checkBloodKey();
        // the guards roam: keep them hunting and forget those that vanished
        for (UUID id : new ArrayList<>(guards)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) {
                guardDied(id);
            } else if (run.tick % 40 == 0) {
                retarget(e);
            }
        }
    }

    private void retarget(Entity e) {
        if (e instanceof Mob m) {
            LivingEntity t = m.getTarget();
            if (t == null || t.isDead() || !(t instanceof Player pl) || !run.isParticipant(pl.getUniqueId())) {
                Player p = nearestAlive(m.getLocation());
                if (p != null && p.getLocation().distanceSquared(m.getLocation()) < 48 * 48) {
                    m.setTarget(p);
                }
            }
        }
    }

    private void updateFight(RoomRt rt) {
        // forget monsters that vanished (void, despawn) so a room can never lock forever
        for (UUID id : new ArrayList<>(rt.mobs)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) {
                rt.mobs.remove(id);
                rt.removed++;
                run.mobsSpawned--; // it vanished: it can never be killed, so it must not weigh on the score
            } else {
                if (e.getLocation().getY() < run.built.baseY - 8) {
                    e.remove();
                    rt.mobs.remove(id);
                    rt.removed++;
                    run.mobsSpawned--;
                } else if (run.tick % 40 == 0) {
                    retarget(e);
                }
            }
        }
        // staggered spawning, within the caps
        int budget = 2;
        while (budget-- > 0 && !rt.queue.isEmpty() && rt.mobs.size() < run.ctx.cfg.maxMobsPerRoom
                && liveMobs() < run.ctx.cfg.maxMobsPerRun) {
            MobSpec spec = rt.queue.poll();
            boolean mini = rt.level == 1 && spec == MobRoster.WARLORD;
            LivingEntity e = spawn(rt, spec, mini ? "miniboss" : "mob", null);
            if (e != null) {
                rt.mobs.add(e.getUniqueId());
                rt.spawnedTotal++;
            } else {
                rt.removed++;
            }
        }
        if (rt.bar != null) {
            int remaining = rt.queue.size() + rt.mobs.size()
                + rt.waves.stream().skip(rt.wave + 1L).mapToInt(List::size).sum();
            rt.bar.progress(Math.max(0f, Math.min(1f, rt.fightTotal == 0 ? 0f : (float) remaining / rt.fightTotal)));
            rt.bar.name(Msg.component((rt.level == 1 ? "<dark_red><bold>Mini-boss" : "<red>Enemies") + " <gray>" + remaining + " left"));
        }
        if (rt.queue.isEmpty() && rt.mobs.isEmpty()) {
            if (rt.wave + 1 < rt.waves.size()) {
                if (rt.nextWaveAtTick == 0) {
                    rt.nextWaveAtTick = run.tick + 30;
                } else if (run.tick >= rt.nextWaveAtTick) {
                    rt.wave++;
                    rt.nextWaveAtTick = 0;
                    rt.queue.addAll(rt.waves.get(rt.wave));
                    run.sayAll("<red>Another wave arrives... <gray>(" + (rt.wave + 1) + "/" + rt.waves.size() + ")");
                }
            } else {
                clear(rt);
            }
        }
    }

    private void clear(RoomRt rt) {
        rt.state = RoomRt.State.CLEARED;
        rt.done = true;
        run.fightRoomsCleared++;
        unlockRoom(rt.index());
        if (rt.bar != null) {
            BossBar bar = rt.bar;
            run.each(p -> p.hideBossBar(bar));
        }
        run.soundAll(Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.1f);
        run.sayAll("<green>Room cleared!");
        if (rt.build.plan.keyCarrier) {
            run.witherKeys++;
            run.sayAll("<dark_gray><bold>A Wither Key</bold> <gray>drops from the last enemy! Keys: <white>" + run.witherKeys);
            run.soundAll(Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        }
    }

    /** A monster of this run died. {@code killer} may be null. */
    void mobDied(Entity e, Player killer) {
        String kind = run.keys().kindOf(e);
        if (kind == null) {
            return;
        }
        RunPlayer rp = killer == null ? null : run.player(killer.getUniqueId());
        switch (kind) {
            case "guard" -> {
                run.mobsKilled++;
                guardDied(e.getUniqueId());
            }
            case "boss" -> {
                run.mobsKilled++;
                run.boss.mainDied(e);
            }
            case "boss_add" -> {
                run.mobsKilled++;
                run.boss.addDied(e);
            }
            default -> {
                Integer room = run.keys().roomOf(e);
                RoomRt rt = room == null ? null : run.rooms.get(room);
                if (rt != null && rt.mobs.remove(e.getUniqueId())) {
                    run.mobsKilled++;
                    if (kind.equals("miniboss")) {
                        run.miniBossesKilled++;
                    }
                }
            }
        }
        if (rp != null) {
            rp.kills++;
        }
    }

    void stopAll() {
        for (RoomRt rt : run.rooms.values()) {
            if (rt.bar != null) {
                BossBar bar = rt.bar;
                run.each(p -> p.hideBossBar(bar));
            }
            for (UUID id : new ArrayList<>(rt.mobs)) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
            rt.mobs.clear();
            rt.queue.clear();
            if (rt.state == RoomRt.State.ACTIVE) {
                rt.state = RoomRt.State.CLEARED;
            }
        }
        for (UUID id : new ArrayList<>(guards)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        guards.clear();
        if (run.world != null) {
            for (int i = 0; i < locks.length; i++) {
                if (locks[i] > 0) {
                    locks[i] = 0;
                    fillDoor(i, "air");
                }
            }
        }
    }

    /** Small adapter so the guard formula stays readable. */
    private record RunConfigView(DungeonRun run) {
        int guards() {
            return MobRoster.guards(run.floorNumber(), run.ctx.cfg.watcherGuardsBase, run.ctx.cfg.watcherGuardsPerFloor,
                run.partySize());
        }
    }
}
