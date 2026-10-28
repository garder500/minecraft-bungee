package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The /skills and /collections menus. Text lives in item names and tooltips only. */
final class SkillsMenus {
    private static final int[] SKILL_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    private static ItemStack pane() {
        return Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private final Core core;
    private final SkillsService service;

    SkillsMenus(Core core, SkillsService service) {
        this.core = core;
        this.service = service;
    }

    // ---------------------------------------------------------------- skills overview

    void openOverview(Player player) {
        SkillsConfig cfg = service.config();
        UUID id = player.getUniqueId();
        Gui gui = core.guis().create(6, "<dark_aqua>Your Skills");
        gui.border(pane());
        gui.set(4, averageIcon(player), null);
        Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length && i < SKILL_SLOTS.length; i++) {
            Skill skill = skills[i];
            gui.set(SKILL_SLOTS[i], skillIcon(id, skill), click -> openSkill(click.player(), skill));
        }
        gui.set(48, Guis.icon(Material.BOOKSHELF, "<gold>Collections", "<gray>Items you gathered and the",
            "<gray>rewards and recipes they unlock.", "", "<yellow>Click to open"), click -> openCategories(click.player()));
        gui.set(49, Guis.icon(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.set(50, Guis.icon(Material.GOLDEN_HELMET, "<gold>Leaderboards", "<gray>Use <white>/skilltop <skill><gray>",
            "<gray>to see the best players."), null);
        if (service.pendingCoins(id) > 0) {
            gui.set(46, Guis.icon(Material.GOLD_INGOT, "<gold>Pending rewards",
                "<gray>" + Msg.coins(service.pendingCoins(id)) + " are waiting for the",
                "<gray>economy to become available.", "", "<yellow>Click to try again"), click -> {
                    service.settle(click.player());
                    openOverview(click.player());
                });
        }
        gui.open(player);
    }

    ItemStack averageIcon(Player player) {
        UUID id = player.getUniqueId();
        SkillsConfig cfg = service.config();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Average level: <green>" + String.format(java.util.Locale.ROOT, "%.2f", service.average(id)));
        StringBuilder counted = new StringBuilder();
        for (Skill s : cfg.averageSkills) {
            if (counted.length() > 0) {
                counted.append(", ");
            }
            counted.append(s.displayName());
        }
        lore.add("<dark_gray>Counted: " + counted);
        for (XpBoosts.Boost boost : service.boosts().active()) {
            lore.add("");
            lore.add("<light_purple>Boost: <white>" + boost.name() + " <gray>x" + trim(boost.multiplier())
                + (boost.skill() == null ? "" : " (" + boost.skill().displayName() + ")")
                + (boost.expiresAt() > 0 ? " for " + Msg.duration(boost.expiresAt() - service.boosts().now()) : ""));
        }
        return Guis.icon(Material.NETHER_STAR, "<aqua>Skill Average", lore.toArray(new String[0]));
    }

    ItemStack skillIcon(UUID id, Skill skill) {
        SkillsConfig cfg = service.config();
        double xp = service.xp(id, skill);
        int max = cfg.maxLevel(skill);
        int level = cfg.levels.levelOf(xp, max);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Level: <green>" + level + "<dark_gray>/" + max);
        if (level >= max) {
            lore.add("<gold>Maximum level reached");
            lore.add("<gray>Total XP: <yellow>" + Msg.number(xp));
        } else {
            double into = xp - cfg.levels.xpFor(level);
            double need = cfg.levels.cost(level + 1);
            lore.add("<gray>XP: <yellow>" + Msg.number(into) + "<dark_gray>/<yellow>" + Msg.number(need));
            lore.add(RewardText.bar(into / need, 20) + " <yellow>" + RewardText.percent(into / need));
            lore.add("");
            lore.add("<gray>Level " + (level + 1) + " rewards:");
            for (String line : RewardText.levelLines(cfg, skill, level + 1)) {
                lore.add("  " + line);
            }
        }
        List<String> totals = totalLines(skill, level);
        if (!totals.isEmpty()) {
            lore.add("");
            lore.add("<gray>Bonuses so far:");
            totals.forEach(l -> lore.add("  " + l));
        }
        lore.add("");
        lore.add("<yellow>Click to see every level");
        return Guis.icon(skill.icon(), "<green>" + skill.displayName(), lore.toArray(new String[0]));
    }

    private List<String> totalLines(Skill skill, int level) {
        List<String> lines = new ArrayList<>();
        for (SkillsConfig.StatReward reward : service.config().skill(skill).stats()) {
            double total = reward.total(level);
            if (total != 0) {
                lines.add(RewardText.statLine(reward.stat(), total));
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------- one skill

    void openSkill(Player player, Skill skill) {
        SkillsConfig cfg = service.config();
        UUID id = player.getUniqueId();
        int max = cfg.maxLevel(skill);
        double xp = service.xp(id, skill);
        int level = cfg.levels.levelOf(xp, max);
        Gui gui = core.guis().create(6, "<dark_aqua>" + skill.displayName() + " Skill");
        gui.border(pane());
        gui.set(4, skillIcon(id, skill), null);
        List<Integer> levels = new ArrayList<>();
        for (int l = 1; l <= max; l++) {
            levels.add(l);
        }
        Gui.Pager<Integer> pager = gui.paginate(levels, gui.innerSlots(), l -> levelIcon(skill, l, level), (click, l) -> { },
            48, 50);
        int perPage = gui.innerSlots().length;
        pager.page(Math.min(level, max - 1) / perPage);
        gui.set(49, Guis.icon(Material.ARROW, "<gray>Back to skills"), click -> openOverview(click.player()));
        gui.open(player);
    }

    ItemStack levelIcon(Skill skill, int l, int current) {
        SkillsConfig cfg = service.config();
        Material material = l <= current ? Material.LIME_STAINED_GLASS_PANE
            : l == current + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String color = l <= current ? "<green>" : l == current + 1 ? "<yellow>" : "<red>";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Total XP needed: <yellow>" + Msg.number(cfg.levels.xpFor(l)));
        lore.add("<gray>From the previous level: <yellow>" + Msg.number(cfg.levels.cost(l)));
        lore.add("");
        lore.add("<gray>Rewards:");
        for (String line : RewardText.levelLines(cfg, skill, l)) {
            lore.add("  " + line);
        }
        lore.add("");
        lore.add(l <= current ? "<green>Unlocked" : l == current + 1 ? "<yellow>Next level" : "<red>Locked");
        ItemStack icon = Guis.icon(material, color + "Level " + l, lore.toArray(new String[0]));
        icon.setAmount(Math.max(1, Math.min(l, 64)));
        return icon;
    }

    // ---------------------------------------------------------------- collections

    void openCategories(Player player) {
        SkillsConfig cfg = service.config();
        UUID id = player.getUniqueId();
        SkillsData data = service.data(id);
        Map<Skill, List<SkillsConfig.CollectionDef>> byCategory = new EnumMap<>(Skill.class);
        for (SkillsConfig.CollectionDef def : cfg.collections.values()) {
            byCategory.computeIfAbsent(def.category(), k -> new ArrayList<>()).add(def);
        }
        Gui gui = core.guis().create(4, "<dark_aqua>Collections");
        gui.border(pane());
        int slot = 10;
        for (var entry : byCategory.entrySet()) {
            if (slot > 16) {
                break;
            }
            Skill category = entry.getKey();
            int reached = 0;
            int total = 0;
            for (SkillsConfig.CollectionDef def : entry.getValue()) {
                reached += def.tierOf(data.collected(def.id()));
                total += def.maxTier();
            }
            double fraction = total == 0 ? 0 : (double) reached / total;
            gui.set(slot++, Guis.icon(category.icon(), "<green>" + category.displayName() + " Collections",
                "<gray>Collections: <white>" + entry.getValue().size(),
                "<gray>Tiers unlocked: <green>" + reached + "<dark_gray>/<green>" + total,
                RewardText.bar(fraction, 20) + " <yellow>" + RewardText.percent(fraction), "", "<yellow>Click to open"),
                click -> openCategory(click.player(), category));
        }
        gui.set(31, Guis.icon(Material.ARROW, "<gray>Back to skills"), click -> openOverview(click.player()));
        gui.open(player);
    }

    void openCategory(Player player, Skill category) {
        SkillsConfig cfg = service.config();
        UUID id = player.getUniqueId();
        List<SkillsConfig.CollectionDef> defs = new ArrayList<>();
        for (SkillsConfig.CollectionDef def : cfg.collections.values()) {
            if (def.category() == category) {
                defs.add(def);
            }
        }
        Gui gui = core.guis().create(6, "<dark_aqua>" + category.displayName() + " Collections");
        gui.border(pane());
        gui.paginate(defs, gui.innerSlots(), def -> collectionIcon(id, def),
            (click, def) -> openCollection(click.player(), def), 48, 50);
        gui.set(49, Guis.icon(Material.ARROW, "<gray>Back to categories"), click -> openCategories(click.player()));
        gui.open(player);
    }

    ItemStack collectionIcon(UUID id, SkillsConfig.CollectionDef def) {
        SkillsData data = service.data(id);
        long amount = data.collected(def.id());
        int tier = def.tierOf(amount);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Collected: <white>" + Msg.grouped(amount));
        if (tier >= def.maxTier()) {
            lore.add("<gold>Collection complete");
        } else {
            long from = tier == 0 ? 0 : def.tiers().get(tier - 1);
            long to = def.tiers().get(tier);
            double fraction = (double) (amount - from) / (to - from);
            lore.add("<gray>Next tier: <white>" + Msg.grouped(to));
            lore.add(RewardText.bar(fraction, 20) + " <yellow>" + RewardText.percent(fraction));
            double coins = def.coinsFor(tier + 1);
            if (coins > 0) {
                lore.add("<gray>Reward: <gold>" + Msg.coins(coins));
            }
        }
        List<String> recipes = recipesAt(def, tier + 1);
        recipes.forEach(r -> lore.add("<gray>Unlocks: <green>" + r));
        lore.add("");
        lore.add("<yellow>Click to see every tier");
        ItemStack icon = Guis.icon(def.icon(), "<yellow>" + def.name() + " <gray>" + (tier == 0 ? "-" : RewardText.roman(tier)),
            lore.toArray(new String[0]));
        return icon;
    }

    /** Names of the recipes whose requirement on this collection is exactly {@code tier}. */
    private List<String> recipesAt(SkillsConfig.CollectionDef def, int tier) {
        List<String> names = new ArrayList<>();
        for (SkillsConfig.RecipeDef recipe : service.config().recipes.values()) {
            Integer need = recipe.requires().get(def.id());
            if (need != null && need == tier) {
                names.add(service.resultName(recipe));
            }
        }
        return names;
    }

    void openCollection(Player player, SkillsConfig.CollectionDef def) {
        UUID id = player.getUniqueId();
        Gui gui = core.guis().create(5, "<dark_aqua>" + def.name() + " Collection");
        gui.border(pane());
        gui.set(4, collectionIcon(id, def), null);
        List<Integer> tiers = new ArrayList<>();
        for (int t = 1; t <= def.maxTier(); t++) {
            tiers.add(t);
        }
        gui.paginate(tiers, gui.innerSlots(), t -> tierIcon(id, def, t), (click, t) -> claimTier(click.player(), def, t),
            39, 41);
        gui.set(40, Guis.icon(Material.ARROW, "<gray>Back"), click -> openCategory(click.player(), def.category()));
        gui.open(player);
    }

    private void claimTier(Player player, SkillsConfig.CollectionDef def, int tier) {
        SkillsData data = service.data(player.getUniqueId());
        if (def.tierOf(data.collected(def.id())) >= tier
                && !data.claimed.contains(SkillsData.tierClaim(def.id(), tier))) {
            if (core.service(EconomyApi.class).isEmpty()) {
                core.msg().warn(player, "The economy is not available right now, try again later.");
                return;
            }
            service.settle(player);
            openCollection(player, def);
        }
    }

    ItemStack tierIcon(UUID id, SkillsConfig.CollectionDef def, int tier) {
        SkillsData data = service.data(id);
        long amount = data.collected(def.id());
        boolean reached = def.tierOf(amount) >= tier;
        boolean claimed = data.claimed.contains(SkillsData.tierClaim(def.id(), tier));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Requires: <white>" + Msg.grouped(def.tiers().get(tier - 1)) + " <gray>collected");
        if (!reached) {
            lore.add("<gray>Missing: <white>" + Msg.grouped(def.tiers().get(tier - 1) - amount));
        }
        lore.add("");
        lore.add("<gray>Rewards:");
        double coins = def.coinsFor(tier);
        if (coins > 0) {
            lore.add("  <gold>+" + Msg.coins(coins));
        }
        for (String recipe : recipesAt(def, tier)) {
            lore.add("  <green>Recipe: " + recipe);
        }
        lore.add("");
        if (!reached) {
            lore.add("<red>Locked");
        } else if (!claimed && coins > 0) {
            lore.add("<gold>Click to claim");
        } else {
            lore.add("<green>Unlocked");
        }
        Material material = !reached ? Material.RED_STAINED_GLASS_PANE
            : claimed || coins <= 0 ? Material.LIME_STAINED_GLASS_PANE : Material.GOLD_BLOCK;
        return Guis.icon(material, (reached ? "<green>" : "<red>") + def.name() + " " + RewardText.roman(tier),
            lore.toArray(new String[0]));
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
