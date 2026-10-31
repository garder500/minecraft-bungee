package win.vampyuyu.survival.menu;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.menu.QuestEngine.Completion;

/**
 * Glue between game events and the {@link QuestEngine}: records progress (main thread, Skyblock worlds only), pays
 * rewards exactly once and keeps unpaid rewards pending when the economy or skills feature is unavailable.
 */
final class QuestService {
    private final MenuRuntime rt;

    QuestService(MenuRuntime rt) {
        this.rt = rt;
    }

    private boolean active() {
        return rt.config().questsEnabled;
    }

    LocalDate today() {
        return rt.config().dailyClock.dayOf(java.time.Instant.now());
    }

    // ---------------------------------------------------------------- recording

    /** Records something the player did. Ignored outside Skyblock worlds, in creative/spectator and for offline players. */
    void record(Player player, QuestType type, String subject, int value) {
        if (!active() || value <= 0 || !player.isOnline() || !MenuRuntime.inSkyblock(player.getWorld())) {
            return;
        }
        switch (player.getGameMode()) {
            case CREATIVE, SPECTATOR -> {
                return;
            }
            default -> { }
        }
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        engine.ensureDay(data, today(), rt.config().dailyCount);
        List<Completion> done = new ArrayList<>(engine.apply(data, new QuestEngine.Event(type, subject, value)));
        finish(player, data, done, true);
    }

    /** Re-checks absolute quests (island ownership, skill levels), rolls the day over and pays what is pending. */
    void sync(Player player) {
        if (!active() || !player.isOnline()) {
            return;
        }
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        migrate(player, data);
        boolean changed = engine.ensureDay(data, today(), rt.config().dailyCount);
        List<Completion> done = new ArrayList<>(engine.sync(data, def -> probe(player, def)));
        finish(player, data, done, changed || !done.isEmpty());
        flushPending(player);
    }

    /**
     * A player who started before the quest plan keeps what their data proves, once and without paying anything: the
     * tier quests up to the generator tier they reached, and the cobblestone mining quests up to the generator blocks
     * they broke. Everything else (ores, forging, placing) counts again from the plan's first quest.
     */
    private void migrate(Player player, QuestData data) {
        QuestEngine engine = rt.engine();
        if (data.planMigrated || engine.catalog().categories().isEmpty()) {
            return;
        }
        data.planMigrated = true;
        var api = rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class);
        if (api.isEmpty()) {
            data.planMigrated = false; // the quarry feature is not loaded yet: try again at the next sync
            return;
        }
        int tier = api.get().tier(player.getUniqueId());
        long blocks = api.get().blocksMined(player.getUniqueId());
        for (QuestDef def : engine.catalog().tutorial()) {
            boolean proven = switch (def.type()) {
                case GENERATOR_TIER -> def.amount() <= tier;
                case BREAK_BLOCK -> def.target().equalsIgnoreCase("COBBLESTONE") && def.amount() <= blocks;
                default -> false;
            };
            if (proven) {
                data.completed.add(def.id());
            }
        }
        rt.dirty(player.getUniqueId());
    }

    private OptionalInt probe(Player player, QuestDef def) {
        UUID id = player.getUniqueId();
        return switch (def.type()) {
            case GENERATOR_TIER -> rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class)
                .map(api -> OptionalInt.of(api.tier(id))).orElse(OptionalInt.empty());
            case ISLAND_CREATE -> rt.islands().map(api -> OptionalInt.of(api.islandOf(id).isPresent() ? 1 : 0))
                .orElse(OptionalInt.empty());
            case SKILL_LEVEL -> rt.skills().map(api -> {
                String target = def.target();
                if (target.isBlank() || target.equalsIgnoreCase("ANY")) {
                    int best = 0;
                    for (Skill skill : Skill.values()) {
                        best = Math.max(best, api.level(id, skill));
                    }
                    return OptionalInt.of(best);
                }
                try {
                    return OptionalInt.of(api.level(id, Skill.valueOf(target.toUpperCase(Locale.ROOT))));
                } catch (IllegalArgumentException e) {
                    return OptionalInt.empty();
                }
            }).orElse(OptionalInt.empty());
            default -> OptionalInt.empty();
        };
    }

    private void finish(Player player, QuestData data, List<Completion> done, boolean dirty) {
        QuestEngine engine = rt.engine();
        List<Completion> all = new ArrayList<>(done);
        // completions can unlock absolute quests that are already satisfied
        if (!done.isEmpty()) {
            all.addAll(engine.sync(data, def -> probe(player, def)));
        }
        for (Completion completion : all) {
            pay(player, data, completion);
        }
        int plan = (int) all.stream().filter(c -> !c.daily()).count();
        if (plan > 0) {
            record(player, QuestType.EVENT, "quest_done", plan); // the "complete N quests" quests count these
        }
        Optional<QuestEngine.StreakBonus> bonus = engine.claimDailyBonus(data, today());
        bonus.ifPresent(b -> payBonus(player, data, b));
        if (dirty || bonus.isPresent()) {
            rt.dirty(player.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- rewards

    private void pay(Player player, QuestData data, Completion completion) {
        QuestDef def = completion.def();
        QuestDef.Rewards rewards = def.rewards();
        String reason = (completion.daily() ? "daily:" : "quest:") + def.id();
        List<String> parts = new ArrayList<>();
        if (rewards.coins() > 0) {
            parts.add("<gold>+" + Msg.number(rewards.coins()) + " coins");
            giveCoins(player, data, rewards.coins(), reason);
        }
        rewards.skillXp().forEach((skillName, xp) -> {
            Optional<Skill> skill = skill(skillName);
            if (skill.isPresent() && xp > 0) {
                parts.add("<aqua>+" + Msg.number(xp) + " " + skill.get().displayName() + " XP");
                giveXp(player, data, skill.get(), xp, reason);
            }
        });
        rewards.items().forEach((itemId, amount) -> {
            if (rt.core.items().def(itemId).isPresent()) {
                parts.add("<green>" + amount + "x " + rt.core.items().def(itemId).get().name());
                give(player, rt.core.items().create(itemId, amount));
            } else {
                rt.core.debug("Quest " + def.id() + ": reward item '" + itemId + "' does not exist; skipped");
            }
        });
        announce(player, def, completion.daily(), parts);
    }

    private void payBonus(Player player, QuestData data, QuestEngine.StreakBonus bonus) {
        MenuConfig cfg = rt.config();
        double coins = cfg.dailyBonusCoins + cfg.dailyStreakCoins * Math.min(bonus.streak(), cfg.dailyStreakCap);
        List<String> parts = new ArrayList<>();
        if (coins > 0) {
            giveCoins(player, data, coins, "daily:bonus");
            parts.add("<gold>+" + Msg.number(coins) + " coins");
        }
        if (cfg.dailyBonusXp > 0) {
            for (Skill skill : new Skill[] {Skill.COMBAT, Skill.MINING, Skill.FARMING, Skill.FORAGING}) {
                // one bonus chunk spread evenly keeps it simple and fair
                giveXp(player, data, skill, cfg.dailyBonusXp / 4, "daily:bonus");
            }
            parts.add("<aqua>+" + Msg.number(cfg.dailyBonusXp) + " XP");
        }
        player.sendMessage(Component.empty());
        rt.core.msg().mini(player, "<green><bold>ALL DAILY QUESTS DONE!</bold> <gray>Streak: <yellow>" + bonus.streak()
            + (bonus.streak() == 1 ? " day" : " days") + (parts.isEmpty() ? "" : " <dark_gray>| " + String.join("<gray>, ", parts)));
        rt.play(player, "quest-complete");
    }

    private Optional<Skill> skill(String name) {
        try {
            return Optional.of(Skill.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void giveCoins(Player player, QuestData data, double coins, String reason) {
        Optional<win.vampyuyu.survival.api.EconomyApi> economy = rt.economy();
        if (economy.isPresent()) {
            economy.get().add(player.getUniqueId(), coins, reason);
        } else {
            data.pendingCoins += coins;
        }
    }

    private void giveXp(Player player, QuestData data, Skill skill, double xp, String reason) {
        Optional<win.vampyuyu.survival.api.SkillsApi> skills = rt.skills();
        if (skills.isPresent()) {
            skills.get().addXp(player, skill, xp, reason);
        } else {
            data.pendingXp.merge(skill.name(), xp, Double::sum);
        }
    }

    private void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        left.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** Pays rewards that were earned while a feature was off. Each unit is zeroed BEFORE it is paid. */
    void flushPending(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        if (data.pendingCoins > 0 && rt.economy().isPresent()) {
            double coins = data.pendingCoins;
            data.pendingCoins = 0;
            rt.dirty(player.getUniqueId());
            rt.economy().get().add(player.getUniqueId(), coins, "quest:pending");
            rt.core.msg().ok(player, "You received " + Msg.coins(coins) + " from earlier quests.");
        }
        if (!data.pendingXp.isEmpty() && rt.skills().isPresent()) {
            Map<String, Double> pending = new HashMap<>(data.pendingXp);
            data.pendingXp.clear();
            rt.dirty(player.getUniqueId());
            pending.forEach((name, xp) -> skill(name).ifPresent(s -> rt.skills().get().addXp(player, s, xp, "quest:pending")));
        }
    }

    private void announce(Player player, QuestDef def, boolean daily, List<String> rewards) {
        Component header = Component.text(daily ? "DAILY QUEST COMPLETE" : "QUEST COMPLETE", NamedTextColor.GREEN,
            TextDecoration.BOLD);
        player.sendMessage(Component.empty());
        player.sendMessage(header.append(Component.text("  " + def.name(), NamedTextColor.YELLOW)
            .decoration(TextDecoration.BOLD, false)));
        if (!rewards.isEmpty()) {
            player.sendMessage(Msg.component("<gray>Rewards: " + String.join("<gray>, ", rewards)));
        }
        if (!daily) {
            QuestData data = rt.quests(player.getUniqueId());
            List<QuestDef> next = rt.engine().activeTutorial(data);
            if (!next.isEmpty()) {
                player.sendMessage(Msg.component("<gray>Next: <yellow>" + escape(next.get(0).name())
                    + " <dark_gray>- " + escape(next.get(0).description())));
            }
        }
        player.showTitle(Title.title(Component.text(daily ? "Daily Quest" : "Quest Complete", NamedTextColor.GREEN),
            Component.text(def.name(), NamedTextColor.YELLOW),
            Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1500),
                java.time.Duration.ofMillis(400))));
        rt.play(player, "quest-complete");
    }

    private static String escape(String text) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(text);
    }

    // ---------------------------------------------------------------- queries used by the sidebar and GUI

    Optional<QuestEngine.Tracked> tracked(Player player) {
        if (!active()) {
            return Optional.empty();
        }
        return rt.engine().tracked(rt.quests(player.getUniqueId()));
    }

    /** Tracks a quest in the sidebar; returns false for an unknown or inactive quest. */
    boolean track(Player player, String key) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        if (key.startsWith("daily:")) {
            String id = key.substring(6);
            boolean ok = engine.dailyQuests(data).stream().anyMatch(d -> d.id().equals(id))
                && !data.dailyDone.contains(id);
            if (!ok) {
                return false;
            }
        } else {
            Optional<QuestDef> def = engine.catalog().tutorialQuest(key);
            if (def.isEmpty() || !engine.isActive(data, def.get())) {
                return false;
            }
        }
        data.tracked = key;
        rt.dirty(player.getUniqueId());
        return true;
    }

    /** Called for every player command (typed or run by a menu). */
    void command(Player player, String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            lower = lower.substring(lower.indexOf(':') + 1);
        }
        // PlayerCommandPreprocessEvent fires before dispatch: an unknown command, or one the player may not run,
        // must not count (a disabled bazaar would otherwise still complete its quest).
        org.bukkit.command.Command command = org.bukkit.Bukkit.getCommandMap().getCommand(lower);
        if (command == null || !command.testPermissionSilent(player)) {
            return;
        }
        StringBuilder subject = new StringBuilder(lower);
        subject.append('|').append(command.getName().toLowerCase(Locale.ROOT));
        for (String alias : command.getAliases()) {
            subject.append('|').append(alias.toLowerCase(Locale.ROOT));
        }
        record(player, QuestType.COMMAND, subject.toString(), 1);
    }
}
