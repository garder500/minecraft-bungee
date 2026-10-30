package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.DungeonConfig;
import win.vampyuyu.survival.dungeon.DungeonData;
import win.vampyuyu.survival.dungeon.Grade;
import win.vampyuyu.survival.dungeon.RewardDelivery;
import win.vampyuyu.survival.dungeon.RewardTable;
import win.vampyuyu.survival.dungeon.ScoreCalculator;

/** End of a run: score, rewards, statistics, leaderboard. */
final class Finale {
    /** What a finished run produced (kept for the tests and the admin command). */
    record Result(ScoreCalculator.Score score, Map<UUID, List<RewardTable.Drop>> drops, double coins, double xp) {}

    private final DungeonRun run;

    Finale(DungeonRun run) {
        this.run = run;
    }

    ScoreCalculator.Inputs inputs(boolean ok) {
        return new ScoreCalculator.Inputs(run.floorNumber(), ok, run.elapsedMillis() / 1000.0, run.floor.parSeconds(),
            run.layout.rooms().size(), run.roomsVisited, run.layout.fightRoomCount(), run.fightRoomsCleared,
            run.layout.puzzleCount(), run.puzzlesSolved, run.mobsSpawned, run.mobsKilled, run.layout.totalSecrets(),
            run.secretsFound, run.floor.secretsNeededPercent(), run.deaths.teamDeaths(), run.miniBossesKilled);
    }

    Result run(boolean ok, String reason) {
        DungeonConfig cfg = run.ctx.cfg;
        ScoreCalculator.Score score = ScoreCalculator.compute(inputs(ok), cfg);
        Grade grade = score.grade();
        long millis = run.elapsedMillis();
        List<String> names = new ArrayList<>();
        for (RunPlayer rp : run.players.values()) {
            if (!rp.left) {
                names.add(rp.name);
            }
        }
        double coins = ok ? RewardTable.coins(cfg, run.floor, grade) : 0;
        double xp = ok ? RewardTable.xp(cfg, run.floor, grade) : 0;
        Map<UUID, List<RewardTable.Drop>> drops = new LinkedHashMap<>();
        Random rng = new Random();
        for (RunPlayer rp : run.players.values()) {
            if (rp.left) {
                continue;
            }
            Player p = Bukkit.getPlayer(rp.id);
            final int secrets = rp.secrets;
            final int kills = rp.kills;
            final int deaths = rp.totalDeaths;
            run.ctx.core.profiles().edit(rp.id, "dungeon", DungeonData.class, DungeonData::new,
                d -> d.record(run.floorNumber(), ok, millis, score.total(), grade, secrets, kills, deaths));
            if (!ok || rp.rewarded) {
                continue;
            }
            rp.rewarded = true;
            List<RewardTable.Drop> mine = RewardTable.roll(cfg, run.floorNumber(), grade, rng);
            drops.put(rp.id, mine);
            if (coins > 0) {
                run.ctx.economy().ifPresent(e -> e.add(rp.id, coins, "dungeon:floor" + run.floorNumber()));
            }
            if (xp > 0 && p != null) {
                run.ctx.skills().ifPresent(s -> s.addXp(p, Skill.COMBAT, xp, "dungeon:floor" + run.floorNumber()));
            }
            for (RewardTable.Drop d : mine) {
                RewardDelivery.give(run.ctx.core, rp.id, p, d.item(), d.amount());
            }
        }
        announce(ok, reason, score, millis, coins, xp);
        if (ok) {
            // the board writes a file: off the main thread, the rank is announced when it is known
            final ScoreCalculator.Score s = score;
            run.ctx.scheduler().async(() -> {
                int rank = run.ctx.leaderboard.record(run.floorNumber(), millis, s.total(), s.grade(), names);
                if (rank > 0) {
                    run.ctx.scheduler().sync(() -> {
                        if (run.state != RunState.CLOSED) {
                            run.sayAll("<gold>New leaderboard entry: <white>#" + rank + " <gray>on floor " + run.floorNumber() + ".");
                        }
                    });
                }
            });
            run.later(50, () -> run.each(p -> recap(p, grade, coins, xp, drops.getOrDefault(p.getUniqueId(), List.of()))));
        }
        return new Result(score, drops, coins, xp);
    }

    private void announce(boolean ok, String reason, ScoreCalculator.Score score, long millis, double coins, double xp) {
        Grade g = score.grade();
        run.each(p -> {
            p.sendMessage(Msg.component("<dark_gray><strikethrough>                                                  "));
            p.sendMessage(Msg.component(ok ? "<gold><bold>  CATACOMBS FLOOR " + run.floorNumber() + " CLEARED"
                : "<red><bold>  CATACOMBS FLOOR " + run.floorNumber() + " FAILED"));
            if (!ok && !reason.isBlank()) {
                p.sendMessage(Msg.component("<gray>  " + reason));
            }
            p.sendMessage(Msg.component("<gray>  Time: <white>" + DungeonRun.clock(millis) + "<gray>   Deaths: <white>"
                + run.deaths.teamDeaths() + "<gray>   Secrets: <white>" + run.secretsFound + "/" + run.layout.totalSecrets()));
            p.sendMessage(Msg.component(String.format("<gray>  Speed <white>%.0f<gray>  Exploration <white>%.0f<gray>  Skill <white>%.0f<gray>  Bonus <white>%.0f",
                score.speed(), score.exploration(), score.skill(), score.bonus())));
            p.sendMessage(Msg.component("<gray>  Score: <white>" + score.total() + " <gray>Grade: " + g.color() + "<bold>" + g.label()));
            if (ok) {
                p.sendMessage(Msg.component("<gray>  Reward: <gold>" + Msg.coins(coins) + "<gray> and <aqua>" + Msg.number(xp)
                    + " Combat XP<gray> (the items are in your inventory)"));
            }
            p.sendMessage(Msg.component("<dark_gray><strikethrough>                                                  "));
            p.showTitle(net.kyori.adventure.title.Title.title(
                Msg.component(ok ? g.color() + "<bold>" + g.label() : "<red><bold>FAILED"),
                Msg.component(ok ? "<gray>Score " + score.total() : "<gray>" + (reason.isBlank() ? "Try again" : reason))));
            p.playSound(p.getLocation(), ok ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.8f, 1f);
        });
    }

    /** The reward chest: a read-only recap of what was just handed out. */
    private void recap(Player p, Grade grade, double coins, double xp, List<RewardTable.Drop> drops) {
        DungeonConfig.Tier tier = run.ctx.cfg.tiers.get(grade);
        Guis guis = run.ctx.core.guis();
        var gui = guis.create(3, grade.color() + "<bold>" + (tier == null ? "Reward Chest" : tier.chest()));
        gui.fill(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        gui.set(4, Guis.icon(Material.GOLD_INGOT, "<gold>" + Msg.coins(coins), "<gray>Added to your balance",
            "<aqua>+" + Msg.number(xp) + " Combat XP"));
        int slot = 10;
        for (RewardTable.Drop d : drops) {
            if (slot > 16) {
                break;
            }
            ItemStack s = RewardDelivery.make(run.ctx.core, d.item(), d.amount());
            if (s != null) {
                s.setAmount(Math.min(64, d.amount()));
                gui.set(slot++, s);
            }
        }
        gui.set(22, Guis.icon(Material.BARRIER, "<red>Close"), c -> c.close());
        gui.open(p);
    }

    /** A hidden chest or puzzle chest: a small payout, straight to the opener. */
    void chestLoot(Player p, int rolls) {
        DungeonConfig cfg = run.ctx.cfg;
        double coins = Math.floor((80 + run.rng().nextInt(60)) * run.floor.reward() * rolls);
        List<RewardTable.Drop> items = RewardTable.rollN(cfg, run.floorNumber(), Grade.B, rolls, run.rng());
        run.ctx.economy().ifPresent(e -> {
            if (coins > 0) {
                e.add(p.getUniqueId(), coins, "dungeon:chest");
            }
        });
        StringBuilder sb = new StringBuilder("<gray>You found <gold>" + Msg.coinsShort(coins));
        for (RewardTable.Drop d : items) {
            RewardDelivery.give(run.ctx.core, p.getUniqueId(), p, d.item(), d.amount());
            sb.append("<gray>, <white>").append(d.amount()).append("x ").append(d.item().replace("mc:", "").replace('_', ' ').toLowerCase());
        }
        run.say(p, sb.toString());
    }
}
