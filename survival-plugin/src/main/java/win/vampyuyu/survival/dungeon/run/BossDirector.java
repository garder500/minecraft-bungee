package win.vampyuyu.survival.dungeon.run;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
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
    private long nextSlamTick;

    BossDirector(DungeonRun run) {
        this.run = run;
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
        run.titleAll("<dark_red><bold>THE CRYPT LORD", "<gray>It stirs...");
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
                    run.sayAll("<dark_red>The Crypt Lord rises!");
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
        LivingEntity e = run.fights.spawn(rt, MobRoster.CRYPT_LORD, "boss", at);
        if (e == null) {
            run.sayAll("<red>The Crypt Lord could not be summoned.");
            run.finish(false, "The boss could not be spawned.");
            return;
        }
        main = e.getUniqueId();
        bar = BossBar.bossBar(Msg.component("<dark_red><bold>Crypt Lord"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        run.each(p -> p.showBossBar(bar));
        nextSlamTick = run.tick + 120;
        run.sayAll("<dark_red><bold>The Crypt Lord rises!</bold> <gray>Defeat him to clear the floor.");
        run.soundAll(Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
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
        if (!phase2 && ratio <= 0.66) {
            phase2 = true;
            phase("<dark_red>\"Rise, my children!\"");
        }
        if (!phase3 && ratio <= 0.33) {
            phase3 = true;
            phase("<dark_red>\"Enough! Bleed for me!\"");
        }
        if (run.tick >= nextSlamTick) {
            nextSlamTick = run.tick + (phase3 ? 100 : 160);
            slam(boss);
        }
        for (UUID id : new java.util.ArrayList<>(adds)) {
            Entity a = Bukkit.getEntity(id);
            if (a == null || !a.isValid() || a.isDead()) {
                adds.remove(id);
            }
        }
    }

    private void phase(String line) {
        run.sayAll(line);
        run.soundAll(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
        var rt = run.rooms.get(run.layout.boss().index);
        int count = Math.min(run.ctx.cfg.maxMobsPerRoom, 2 + run.partySize() + run.floorNumber() / 2);
        for (int i = 0; i < count && run.fights.liveMobs() < run.ctx.cfg.maxMobsPerRun; i++) {
            LivingEntity add = run.fights.spawn(rt, MobRoster.pick(run.floorNumber(), run.rng()), "boss_add", null);
            if (add != null) {
                adds.add(add.getUniqueId());
            }
        }
    }

    /** A ground slam: everyone close takes damage and is thrown into the air. */
    private void slam(LivingEntity boss) {
        Location c = boss.getLocation();
        run.world.spawnParticle(Particle.EXPLOSION, c, 3, 1.5, 0.1, 1.5, 0);
        run.world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        double dmg = 4 + 2.5 * run.floorNumber();
        run.eachAlive(p -> {
            if (p.getWorld() == c.getWorld() && p.getLocation().distanceSquared(c) < 49) {
                Vector away = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (away.lengthSquared() > 0.001) {
                    away.normalize().multiply(0.8);
                }
                p.setVelocity(away.setY(0.7));
                run.hurt(p, dmg);
            }
        });
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
        run.sayAll("<gold><bold>The Crypt Lord has fallen!");
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
