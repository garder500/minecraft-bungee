package win.vampyuyu.survival.dungeon.run;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.core.Msg;

/**
 * The final fight. By default a scripted Crypt Lord with phases takes over (hp and damage follow the floor). From the
 * floor named by {@code boss-external-from-floor}, when the boss feature is present and knows {@code crypt_lord}, that
 * boss is spawned instead (the kill is reported through {@code BossKillEvent}; its hp does not follow the floor).
 */
final class BossDirector {
    static final String BOSS_ID = "crypt_lord";

    private final DungeonRun run;
    private boolean entered;
    private boolean spawned;
    private boolean external;
    private boolean finished;
    private UUID main;
    private BossBar bar;
    private long spawnedAtMillis;
    private final Set<UUID> adds = new HashSet<>();
    private boolean phase2;
    private boolean phase3;
    private boolean shielded;
    private long nextAttackTick;
    private int nextAttack;
    private DungeonConfig.BossKit kit;
    /** The attack whose warning is showing, null between two attacks. */
    private Attack pending;

    /** An attack being warned about: what it will hit is fixed when the warning starts, so the marks tell the truth. */
    private record Attack(BossAttacks.Kind kind, List<Location> spots, Location to, long landsAt) {}

    BossDirector(DungeonRun run) {
        this.run = run;
    }

    private DungeonConfig.BossKit kit() {
        if (kit == null) {
            kit = run.ctx.cfg.boss(run.floorNumber());
        }
        return kit;
    }

    /** The name of this floor's boss. */
    String name() {
        return kit().name();
    }

    /** True while this monster is the boss and its shield is up: the hit must be refused before it reaches the health pool. */
    boolean shields(Entity e) {
        return shielded && main != null && main.equals(e.getUniqueId());
    }

    /** Tests: this monster is the boss, shielded for as long as {@code add} lives (the mock server cannot spawn the real ones). */
    void shieldedForTest(LivingEntity boss, LivingEntity add) {
        spawned = true;
        main = boss.getUniqueId();
        bar = BossBar.bossBar(Msg.component(kit().name()), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        adds.add(add.getUniqueId());
        shielded = true;
        phase2 = true;
        phase3 = true;
        nextAttackTick = Long.MAX_VALUE;
    }

    /** Admin shortcut for tests: takes the boss down to a share of its health. */
    boolean hurtTo(int percent) {
        if (main == null || !(Bukkit.getEntity(main) instanceof LivingEntity boss)) {
            return false;
        }
        var max = boss.getAttribute(Attribute.MAX_HEALTH);
        boss.setHealth(Math.max(1, max.getValue() * percent / 100.0));
        Double pool = run.ctx.keys.hpMaxOf(boss);
        if (pool != null) {
            run.ctx.keys.setHp(boss, pool * percent / 100.0);
        }
        return true;
    }

    /** Attacks are for who is in the boss room: a teammate still in another room of the same world sees and takes nothing. */
    private boolean inBossRoom(Player p) {
        RunPlayer rp = run.player(p.getUniqueId());
        return rp != null && run.layout.boss() != null && rp.room == run.layout.boss().index;
    }

    /** Tests: an attack lands on this spot, as if its warning were over. */
    void landForTest(LivingEntity boss, BossAttacks.Kind kind, Location spot) {
        land(boss, new Attack(kind, List.of(spot), spot, run.tick));
    }

    /** What the shield asks for, for the player who just hit it. */
    String shieldGoal() {
        return kit().phaseGoal();
    }

    boolean isExternal() { return external; }

    boolean spawned() { return spawned; }

    int addCount() { return adds.size(); }

    void onFirstEnter(Player first) {
        if (entered || run.state != RunState.RUNNING) {
            return;
        }
        entered = true;
        run.state = RunState.BOSS;
        run.titleAll(kit().color() + "<bold>" + kit().name(), "<gray>" + kit().tagline());
        run.soundAll(Sound.ENTITY_WITHER_AMBIENT, 1f, 0.5f);
        run.later(100, () -> spawn(first.getUniqueId()));
    }

    private void spawn(UUID summoner) {
        if (spawned || run.state != RunState.BOSS || run.world == null) {
            return;
        }
        var rt = run.rooms.get(run.layout.boss().index);
        if (rt == null || rt.build.bossSpawn == null) {
            run.finish(false, "The boss room is broken.");
            return;
        }
        Location at = run.loc(rt.build.bossSpawn);
        Player who = Bukkit.getPlayer(summoner);
        spawned = true;
        spawnedAtMillis = System.currentTimeMillis();
        int from = run.ctx.cfg.bossExternalFromFloor;
        var api = from > 0 && run.floorNumber() >= from ? run.ctx.bosses() : java.util.Optional.<win.vampyuyu.survival.api.BossApi>empty();
        if (api.isPresent()) {
            try {
                if (api.get().bossIds().contains(BOSS_ID) && api.get().spawn(BOSS_ID, at, who)) {
                    external = true;
                    run.sayAll(kit().color() + kit().name() + " rises!");
                    run.soundAll(Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
                    return;
                }
            } catch (RuntimeException e) {
                run.ctx.log().warning("Dungeon: BossApi refused crypt_lord, using the scripted boss: " + e);
            }
        }
        scripted(rt, at);
    }

    private void scripted(RoomRt rt, Location at) {
        LivingEntity e = run.fights.spawn(rt, spec(), "boss", at);
        if (e == null) {
            run.sayAll("<red>" + kit().name() + " could not be summoned.");
            run.finish(false, "The boss could not be spawned.");
            return;
        }
        var scale = e.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(1.4); // it must read as the boss across the room
        }
        main = e.getUniqueId();
        bar = BossBar.bossBar(Msg.component(kit().color() + "<bold>" + kit().name()), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        run.each(p -> p.showBossBar(bar));
        nextAttackTick = run.tick + run.ctx.cfg.bossPauseTicks;
        run.sayAll(kit().color() + "<bold>" + kit().name() + " rises!");
        java.util.Set<String> taught = new HashSet<>();
        for (DungeonConfig.BossAttack attack : kit().attacks()) {
            if (taught.add(attack.hint())) {
                run.sayAll(attack.hint());
            }
        }
        run.soundAll(Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
    }

    /** The floor's boss, with the health and damage of the Crypt Lord: the floor multipliers do the rest. */
    private MobSpec spec() {
        MobSpec base = MobRoster.CRYPT_LORD;
        EntityType type = base.type();
        Material weapon = base.weapon();
        try {
            type = EntityType.valueOf(kit().entity());
            weapon = Material.valueOf(kit().weapon());
        } catch (IllegalArgumentException e) {
            run.ctx.log().warning("Dungeon: floor " + run.floorNumber() + " boss has a bad entity or weapon: " + e.getMessage());
        }
        return new MobSpec(base.id(), kit().name(), type, base.health(), base.damage(), base.speed(), weapon, base.armored(),
            false, 1, 0, kit().color());
    }

    boolean timedOut() {
        return spawned && !finished && System.currentTimeMillis() - spawnedAtMillis > run.ctx.cfg.bossTimeoutMinutes * 60_000L;
    }

    void tick() {
        if (!spawned || finished || external || main == null) {
            return;
        }
        Entity e = Bukkit.getEntity(main);
        if (e == null || !e.isValid() || e.isDead()) {
            died();
            return;
        }
        if (!(e instanceof LivingEntity boss)) {
            return;
        }
        var max = boss.getAttribute(Attribute.MAX_HEALTH);
        double ratio = max == null || max.getValue() <= 0 ? 0 : boss.getHealth() / max.getValue();
        if (bar != null) {
            bar.progress((float) Math.max(0, Math.min(1, ratio)));
        }
        if (!shielded && !phase2 && ratio <= 0.66) {
            phase2 = true;
            raiseShield(boss);
        }
        if (!shielded && !phase3 && ratio <= 0.33) {
            phase3 = true;
            raiseShield(boss);
        }
        for (UUID id : new java.util.ArrayList<>(adds)) {
            Entity a = Bukkit.getEntity(id);
            if (a == null || !a.isValid() || a.isDead()) {
                adds.remove(id);
            }
        }
        if (shielded) {
            bar.name(Msg.component("<aqua><bold>Shielded: <white>" + kit().phaseGoal() + " <gray>(" + adds.size() + " left)"));
            if (adds.isEmpty()) {
                dropShield(boss);
            }
        }
        if (pending != null) {
            if (run.tick >= pending.landsAt()) {
                land(boss, pending);
                pending = null;
                nextAttackTick = run.tick + run.ctx.cfg.bossPauseTicks / (phase3 ? 2 : 1);
            } else {
                draw(pending);
            }
        } else if (run.tick >= nextAttackTick) {
            warn(boss);
        }
    }

    /** A new goal: nothing hurts the boss until its shield is broken, and the bar says how. */
    private void raiseShield(LivingEntity boss) {
        var rt = run.rooms.get(run.layout.boss().index);
        boolean anchors = "ANCHORS".equals(kit().phaseKind());
        int count = anchors ? Math.min(run.ctx.cfg.maxMobsPerRoom, 2 + run.partySize() / 2)
            : Math.min(run.ctx.cfg.maxMobsPerRoom, 2 + run.partySize() + run.floorNumber() / 2);
        for (int i = 0; i < count && run.fights.liveMobs() < run.ctx.cfg.maxMobsPerRun; i++) {
            LivingEntity add = run.fights.spawn(rt, anchors ? MobRoster.ANCHOR : MobRoster.pick(run.floorNumber(), run.rng()), "boss_add", null);
            if (add != null) {
                adds.add(add.getUniqueId());
                if (anchors) {
                    add.setAI(false);
                    add.setGlowing(true);
                }
            }
        }
        if (adds.isEmpty()) {
            return; // no room for anything to break: no shield that could never fall
        }
        shielded = true;
        boss.setGlowing(true);
        bar.color(BossBar.Color.BLUE);
        run.titleWhere(this::inBossRoom, "<aqua><bold>SHIELDED", "<white>" + kit().phaseGoal());
        run.sayAll("<aqua>" + kit().name() + " is shielded! <white>To hurt it, " + kit().phaseGoal() + ".");
        run.soundAll(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);
    }

    private void dropShield(LivingEntity boss) {
        shielded = false;
        boss.setGlowing(false);
        bar.color(BossBar.Color.RED);
        bar.name(Msg.component(kit().color() + "<bold>" + kit().name()));
        run.sayAll("<green><bold>The shield breaks!</bold> <white>Strike now!");
        run.soundAll(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
    }

    /** Starts the next attack: a warning everybody can read, and marks on the floor that show exactly what it will hit. */
    private void warn(LivingEntity boss) {
        List<DungeonConfig.BossAttack> attacks = kit().attacks();
        DungeonConfig.BossAttack attack = attacks.get(nextAttack++ % attacks.size());
        BossAttacks.Kind kind;
        try {
            kind = BossAttacks.Kind.valueOf(attack.kind());
        } catch (IllegalArgumentException e) {
            kind = BossAttacks.Kind.SLAM;
        }
        List<Location> spots = new java.util.ArrayList<>();
        Location to = null;
        Location c = boss.getLocation();
        switch (kind) {
            case MARKS -> run.eachAlive(p -> {
                if (inBossRoom(p)) {
                    spots.add(p.getLocation());
                }
            });
            case CHARGE -> {
                Player target = nearest(c);
                spots.add(c);
                to = target == null ? c : rushEnd(c, target.getLocation());
            }
            default -> spots.add(c);
        }
        boss.setAI(false); // it stands still while the marks are up, so they stay true
        pending = new Attack(kind, spots, to, run.tick + run.ctx.cfg.bossWarningTicks);
        run.titleWhere(this::inBossRoom, "", (kind == BossAttacks.Kind.NOVA ? "<green>" : "<red>") + "<bold>" + attack.warning());
        run.each(p -> {
            if (inBossRoom(p)) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            }
        });
        draw(pending);
    }

    private Player nearest(Location c) {
        Player[] best = new Player[1];
        run.eachAlive(p -> {
            if (p.getWorld() == c.getWorld() && inBossRoom(p) && (best[0] == null
                    || p.getLocation().distanceSquared(c) < best[0].getLocation().distanceSquared(c))) {
                best[0] = p;
            }
        });
        return best[0];
    }

    /** Where a rush through {@code target} stops: at the first wall, at most 16 blocks away. */
    private Location rushEnd(Location from, Location target) {
        Vector step = target.toVector().subtract(from.toVector()).setY(0);
        if (step.lengthSquared() < 0.01) {
            return from;
        }
        step.normalize().multiply(0.5);
        Location at = from.clone();
        for (int i = 0; i < 32; i++) {
            Location next = at.clone().add(step);
            if (next.getBlock().isSolid() || next.clone().add(0, 1, 0).getBlock().isSolid()) {
                break;
            }
            at = next;
        }
        return at;
    }

    /** Marks that read on every palette: a thick line of coloured dust with bright flames (danger) or sparks (safe) in it. */
    private void draw(Attack attack) {
        boolean safe = attack.kind() == BossAttacks.Kind.NOVA;
        if (attack.kind() == BossAttacks.Kind.CHARGE) {
            Location a = attack.spots().get(0);
            double length = a.distance(attack.to());
            Vector dir = attack.to().toVector().subtract(a.toVector());
            if (length > 0.01) {
                dir.normalize();
            }
            for (double d = 0; d <= length; d += 0.4) {
                mark(a.clone().add(dir.clone().multiply(d)), false);
            }
            return;
        }
        double radius = BossAttacks.radius(attack.kind());
        int points = (int) (radius * 16);
        for (Location c : attack.spots()) {
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                mark(c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), safe);
            }
        }
    }

    private void mark(Location at, boolean safe) {
        Location low = at.clone().add(0, 0.15, 0);
        run.world.spawnParticle(Particle.DUST, low, 1, 0.03, 0, 0.03, 0, new Particle.DustOptions(safe ? Color.LIME : Color.RED, 2.2f));
        run.world.spawnParticle(safe ? Particle.HAPPY_VILLAGER : Particle.FLAME, low, 1, 0, 0, 0, 0);
    }

    /** The attack lands where it said it would: damage depends on the floor, and each player is hit at most once. */
    private void land(LivingEntity boss, Attack attack) {
        boss.setAI(true);
        Location c = attack.spots().isEmpty() ? boss.getLocation() : attack.spots().get(0);
        double dmg = 4 + 2.5 * run.floorNumber();
        run.eachAlive(p -> {
            if (p.getWorld() != c.getWorld() || !inBossRoom(p)) {
                return;
            }
            Location at = p.getLocation();
            boolean hit = false;
            if (attack.kind() == BossAttacks.Kind.CHARGE) {
                hit = BossAttacks.hitsLine(c.getX(), c.getZ(), attack.to().getX(), attack.to().getZ(), at.getX(), at.getZ());
            } else if (attack.kind() == BossAttacks.Kind.NOVA) {
                hit = BossAttacks.hitsCircle(attack.kind(), at.getX() - c.getX(), at.getZ() - c.getZ());
            } else {
                for (Location spot : attack.spots()) {
                    hit |= BossAttacks.hitsCircle(attack.kind(), at.getX() - spot.getX(), at.getZ() - spot.getZ());
                }
            }
            if (hit) {
                Vector away = at.toVector().subtract(boss.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.001) {
                    away.normalize().multiply(0.8);
                }
                p.setVelocity(away.setY(0.5));
                run.hurt(p, dmg);
            }
        });
        if (attack.kind() == BossAttacks.Kind.CHARGE) {
            boss.teleport(attack.to());
            run.world.playSound(attack.to(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
        } else {
            for (Location spot : attack.spots()) {
                run.world.spawnParticle(Particle.EXPLOSION, spot, 3, 1.5, 0.1, 1.5, 0);
            }
            run.world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        }
    }

    /** Boss feature reported the kill of {@code crypt_lord} in this world. */
    void externalKilled() {
        if (external) {
            died();
        }
    }

    void mainDied(Entity e) {
        if (main != null && main.equals(e.getUniqueId())) {
            died();
        }
    }

    void addDied(Entity e) {
        adds.remove(e.getUniqueId());
    }

    private void died() {
        if (finished || run.state != RunState.BOSS) {
            return;
        }
        finished = true;
        for (UUID id : new java.util.ArrayList<>(adds)) {
            Entity a = Bukkit.getEntity(id);
            if (a != null) {
                a.remove();
                run.mobsSpawned--; // dismissed with the boss, not part of the kill ratio
            }
        }
        adds.clear();
        run.world.playSound(run.loc(run.layout.boss() == null ? run.built.spawn() : run.built.room(run.layout.boss().index).bossSpawn),
            Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        run.sayAll("<gold><bold>" + kit().name() + " has fallen!");
        run.finish(true, "");
    }

    void hide(Player p) {
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    void stop() {
        if (bar != null) {
            BossBar b = bar;
            run.each(p -> p.hideBossBar(b));
        }
        for (UUID id : new java.util.ArrayList<>(adds)) {
            Entity a = Bukkit.getEntity(id);
            if (a != null) {
                a.remove();
            }
        }
        adds.clear();
        if (!external && main != null && !run.success) {
            Entity e = Bukkit.getEntity(main);
            if (e != null) {
                e.remove();
            }
        }
    }
}
