package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.core.Msg;

/**
 * Deaths in a run: the first {@code deaths-allowed} deaths turn a player into a ghost (spectator) that comes back
 * after a while or when a teammate stands sneaking next to the body; the next one is final. Nothing is ever dropped.
 */
final class DeathDirector {
    private final DungeonRun run;

    DeathDirector(DungeonRun run) {
        this.run = run;
    }

    boolean ghostsAllowed() { return run.ctx.cfg.deathsAllowed > 0; }

    int deathsOf(RunPlayer rp) { return rp.totalDeaths; }

    int teamDeaths() {
        int n = 0;
        for (RunPlayer rp : run.players.values()) {
            n += rp.totalDeaths;
        }
        return n;
    }

    /**
     * Called for lethal damage. Returns true when the death was handled here (the caller cancels the damage).
     */
    boolean onLethal(Player p) {
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || rp.left || !run.state.active()) {
            return false;
        }
        if (rp.ghost || rp.eliminated) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (rp.invulnerableUntil > now) {
            return true;
        }
        rp.totalDeaths++;
        Location at = p.getLocation();
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        for (var effect : new ArrayList<>(p.getActivePotionEffects())) {
            if (effect.getType() != org.bukkit.potion.PotionEffectType.DARKNESS) {
                p.removePotionEffect(effect.getType());
            }
        }
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        if (rp.ghostDeaths < run.ctx.cfg.deathsAllowed) {
            rp.ghostDeaths++;
            rp.ghost = true;
            rp.ghostUntil = now + run.ctx.cfg.ghostSeconds * 1000L;
            rp.body = at.clone();
            p.setGameMode(GameMode.SPECTATOR);
            spawnBody(p, rp);
            run.sayAll("<red>" + rp.name + " died! <gray>(" + rp.ghostDeaths + "/" + run.ctx.cfg.deathsAllowed
                + " ghost lives) A teammate can revive them by sneaking next to the body.");
            p.showTitle(net.kyori.adventure.title.Title.title(Msg.component("<red><bold>YOU DIED"),
                Msg.component("<gray>You are a ghost for " + run.ctx.cfg.ghostSeconds + " seconds")));
        } else {
            rp.eliminated = true;
            rp.ghost = false;
            p.setGameMode(GameMode.SPECTATOR);
            run.sayAll("<dark_red>" + rp.name + " has fallen for good.");
            p.showTitle(net.kyori.adventure.title.Title.title(Msg.component("<dark_red><bold>ELIMINATED"),
                Msg.component("<gray>You watch the rest of the run as a spectator")));
            if (run.aliveOrReturning() == 0) {
                run.sayAll("<red>The whole party has fallen.");
                run.finish(false, "The whole party has fallen.");
            }
        }
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_DEATH, 1f, 0.8f);
        return true;
    }

    private void spawnBody(Player p, RunPlayer rp) {
        try {
            ArmorStand stand = p.getWorld().spawn(rp.body.clone().subtract(0, 0.9, 0), ArmorStand.class, s -> {
                s.setInvulnerable(true);
                s.setGravity(false);
                s.setSmall(false);
                s.setArms(false);
                s.setBasePlate(false);
                s.setSilent(true);
                s.setPersistent(false);
                s.setMarker(false);
                s.customName(Msg.component("<aqua>" + rp.name + "'s body <gray>(sneak next to it)"));
                s.setCustomNameVisible(true);
                s.getPersistentDataContainer().set(run.ctx.keys.run, PersistentDataType.STRING, run.id);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta meta) {
                    meta.setOwningPlayer(p);
                    head.setItemMeta(meta);
                }
                s.getEquipment().setHelmet(head);
                s.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            });
            rp.bodyStand = stand.getUniqueId();
            run.props.add(stand.getUniqueId());
        } catch (RuntimeException e) {
            rp.bodyStand = null;
        }
    }

    /** Removes the body marker and the revive bar of a player. */
    void cleanup(RunPlayer rp) {
        if (rp.bodyStand != null) {
            Entity e = Bukkit.getEntity(rp.bodyStand);
            if (e != null) {
                e.remove();
            }
            run.props.remove(rp.bodyStand);
            rp.bodyStand = null;
        }
        if (rp.reviveBar != null) {
            BossBar bar = rp.reviveBar;
            Player p = Bukkit.getPlayer(rp.id);
            if (p != null) {
                p.hideBossBar(bar);
            }
            rp.reviveBar = null;
        }
        rp.reviveStart = 0;
    }

    /** Brings a ghost back. {@code where} is the body or the last safe point. */
    void revive(Player ghost, RunPlayer rp, Location where, String by) {
        if (!rp.ghost) {
            return;
        }
        rp.ghost = false;
        cleanup(rp);
        Location to = where != null && where.getWorld() == run.world ? where : rp.lastSafe;
        if (to == null) {
            to = run.loc(run.built.spawn());
        }
        ghost.setGameMode(GameMode.ADVENTURE);
        run.ctx.teleport(ghost, to);
        var max = ghost.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            ghost.setHealth(Math.max(1, Math.min(max.getValue(), max.getValue() * run.ctx.cfg.reviveHealthPercent / 100.0)));
        }
        ghost.setFallDistance(0);
        rp.invulnerableUntil = System.currentTimeMillis() + 3000;
        run.world.spawnParticle(Particle.TOTEM_OF_UNDYING, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
        run.world.playSound(to, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
        run.sayAll("<green>" + rp.name + " is back " + (by == null ? "on their own." : "thanks to " + by + "."));
    }

    /** The spirit leaf: revives the ghost that would come back last. Returns the revived name or null. */
    String reviveWithItem(Player user) {
        RunPlayer best = null;
        for (RunPlayer rp : run.players.values()) {
            if (rp.ghost && !rp.left && (best == null || rp.ghostUntil > best.ghostUntil)) {
                best = rp;
            }
        }
        if (best == null) {
            return null;
        }
        Player ghost = Bukkit.getPlayer(best.id);
        if (ghost == null) {
            return null;
        }
        revive(ghost, best, best.body, user.getName());
        return best.name;
    }

    void tick() {
        long now = System.currentTimeMillis();
        for (RunPlayer rp : new ArrayList<>(run.players.values())) {
            if (rp.left || !rp.ghost) {
                continue;
            }
            Player ghost = Bukkit.getPlayer(rp.id);
            if (ghost == null) {
                continue;
            }
            // keep the ghost near its body and inside the dungeon
            if (rp.body != null && ghost.getWorld() == run.world
                    && (ghost.getLocation().distanceSquared(rp.body) > 40 * 40 || ghost.getLocation().getY() < run.built.baseY - 10)) {
                run.ctx.teleport(ghost, rp.body.clone().add(0, 1, 0));
            }
            if (now >= rp.ghostUntil) {
                revive(ghost, rp, rp.lastSafe != null ? rp.lastSafe : rp.body, null);
            }
        }
        // revivers
        for (RunPlayer reviver : new ArrayList<>(run.players.values())) {
            if (!reviver.alive()) {
                continue;
            }
            Player p = Bukkit.getPlayer(reviver.id);
            if (p == null) {
                continue;
            }
            RunPlayer target = null;
            if (p.isSneaking()) {
                for (RunPlayer rp : run.players.values()) {
                    if (rp.ghost && !rp.left && rp.body != null && rp.body.getWorld() == p.getWorld()
                            && rp.body.distanceSquared(p.getLocation()) <= 2.6 * 2.6) {
                        target = rp;
                        break;
                    }
                }
            }
            if (target == null) {
                if (reviver.reviveStart != 0) {
                    reviver.reviveStart = 0;
                    if (reviver.reviveBar != null) {
                        p.hideBossBar(reviver.reviveBar);
                        reviver.reviveBar = null;
                    }
                }
                continue;
            }
            if (reviver.reviveStart == 0) {
                reviver.reviveStart = now;
                reviver.reviveBar = BossBar.bossBar(Msg.component("<green>Reviving " + target.name), 0f,
                    BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                p.showBossBar(reviver.reviveBar);
            }
            double progress = (now - reviver.reviveStart) / (run.ctx.cfg.reviveSeconds * 1000.0);
            if (reviver.reviveBar != null) {
                reviver.reviveBar.progress((float) Math.max(0, Math.min(1, progress)));
            }
            if (progress >= 1) {
                Player ghost = Bukkit.getPlayer(target.id);
                reviver.reviveStart = 0;
                if (reviver.reviveBar != null) {
                    p.hideBossBar(reviver.reviveBar);
                    reviver.reviveBar = null;
                }
                if (ghost != null) {
                    revive(ghost, target, target.body, reviver.name);
                }
            }
        }
    }

    /** After the end of the run ghosts are brought back so nobody stays a spectator in the closing world. */
    void tickOver() {
        for (RunPlayer rp : new ArrayList<>(run.players.values())) {
            if (rp.ghost && !rp.left) {
                Player ghost = Bukkit.getPlayer(rp.id);
                if (ghost != null) {
                    revive(ghost, rp, rp.body, null);
                }
            }
        }
    }

    /** A player fell into the void: damage and back to the last safe point. */
    void fell(Player p, RunPlayer rp) {
        UUID id = p.getUniqueId();
        double dmg = run.ctx.cfg.voidDamage;
        p.setFallDistance(0);
        Location back = rp.lastSafe != null ? rp.lastSafe : run.loc(run.built.spawn());
        if (dmg > 0) {
            run.hurt(p, dmg);
        }
        RunPlayer now = run.player(id);
        if (now != null && now.alive()) {
            run.ctx.teleport(p, back);
            p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            run.say(p, "<gray>You fell. Back to the last safe spot.");
        } else if (now != null && now.ghost) {
            // died of the fall: the body lies where it fell, bring the ghost to a floor
            now.body = back.clone();
            run.ctx.teleport(p, back.clone().add(0, 1, 0));
            if (now.bodyStand != null) {
                Entity stand = Bukkit.getEntity(now.bodyStand);
                if (stand != null) {
                    stand.teleport(back.clone().subtract(0, 0.9, 0));
                }
            }
        }
    }
}
