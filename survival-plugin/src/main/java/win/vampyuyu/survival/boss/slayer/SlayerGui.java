package win.vampyuyu.survival.boss.slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The /slayer menus: pick a slayer, pick a tier, confirm the payment. Text is tooltip only. */
public final class SlayerGui {
    private static final int[] SLAYER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final Material[] TIER_ICONS = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
        Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.PURPLE_CONCRETE};
    private static final String[] TIER_COLORS = {"green", "yellow", "gold", "red", "dark_purple"};

    private final Core core;
    private final SlayerManager manager;

    public SlayerGui(Core core, SlayerManager manager) {
        this.core = core;
        this.manager = manager;
    }

    public void openMain(Player player) {
        Gui gui = core.guis().create(4, "<dark_red>Slayer");
        gui.border(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        BossData data = manager.data(player);
        gui.set(4, Guis.icon(Material.BOOK, "<gold>Slayer Quests",
            "<gray>Pay coins, hunt the matching mobs,", "<gray>and a personal boss comes for you.",
            "<gray>Beat it for Slayer XP, perks and rare drops.", "", "<dark_gray>Only you can hurt your boss."));
        List<SlayerDefinition> slayers = new ArrayList<>(manager.slayers());
        for (int i = 0; i < slayers.size() && i < SLAYER_SLOTS.length; i++) {
            SlayerDefinition def = slayers.get(i);
            gui.set(SLAYER_SLOTS[i], slayerIcon(def, data), click -> openTiers(click.player(), def));
        }
        BossData.Quest quest = data.quest;
        if (quest == null) {
            gui.set(31, Guis.icon(Material.GRAY_DYE, "<gray>No active quest", "<dark_gray>Pick a slayer above to start one."));
        } else {
            SlayerDefinition def = manager.slayer(quest.slayer).orElse(null);
            String name = def == null ? quest.slayer : def.name();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>State: <white>" + quest.state);
            lore.add("<gray>Progress: <white>" + Math.round(quest.progress) + "<gray>/<white>" + Math.round(quest.required));
            lore.add("<dark_gray>" + SlayerMath.bar(quest.progress / Math.max(1, quest.required), 30));
            if (BossData.Quest.READY.equals(quest.state)) {
                lore.add("");
                lore.add("<yellow>Click to spawn your boss");
            }
            lore.add("<red>Shift-click to cancel (no refund)");
            gui.set(31, Guis.icon(Material.ENDER_EYE, "<yellow>" + name + " " + SlayerDefinition.roman(quest.tier),
                lore.toArray(new String[0])), click -> {
                    if (click.shift()) {
                        manager.cancel(click.player());
                        core.msg().info(click.player(), "Quest cancelled.");
                    } else {
                        String refusal = manager.spawnBoss(click.player());
                        if (refusal != null) {
                            core.msg().warn(click.player(), refusal);
                        }
                    }
                    click.close();
                });
        }
        gui.open(player);
    }

    private ItemStack slayerIcon(SlayerDefinition def, BossData data) {
        long xp = data.slayerXp.getOrDefault(def.id(), 0L);
        int level = SlayerMath.level(xp, def.levelXp());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + def.description());
        lore.add("");
        lore.add("<gray>Level: <yellow>" + level + "<gray>/" + def.levelXp().size());
        lore.add("<dark_gray>" + SlayerMath.bar(SlayerMath.levelProgress(xp, def.levelXp()), 24)
            + " <gray>" + Msg.number(xp) + " XP");
        long next = SlayerMath.toNext(xp, def.levelXp());
        if (next > 0) {
            lore.add("<gray>Next level in <white>" + Msg.number(next) + " XP");
        }
        lore.add("");
        lore.add("<gray>Perks:");
        for (SlayerDefinition.Perk perk : def.perks()) {
            boolean got = perk.level() <= level;
            lore.add((got ? "<green>✔ " : "<dark_gray>✘ ") + "<white>+" + Msg.number(perk.amount()) + " "
                + perk.stat().displayName() + " <gray>(level " + perk.level() + ")");
        }
        StringBuilder kills = new StringBuilder("<gray>Kills: ");
        for (int t = 1; t <= def.tiers().size(); t++) {
            kills.append("<white>").append(SlayerDefinition.roman(t)).append("<gray>:")
                .append(data.slayerKills.getOrDefault(def.id() + ":" + t, 0)).append(' ');
        }
        lore.add("");
        lore.add(kills.toString());
        lore.add("");
        lore.add("<yellow>Click to choose a tier");
        return Guis.icon(def.icon(), "<red>" + def.name(), lore.toArray(new String[0]));
    }

    public void openTiers(Player player, SlayerDefinition def) {
        Gui gui = core.guis().create(3, "<dark_red>" + def.name());
        gui.border(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        BossData data = manager.data(player);
        int level = SlayerQuests.level(data, def);
        double coins = core.service(EconomyApi.class).map(e -> e.coins(player.getUniqueId())).orElse(0.0);
        for (SlayerDefinition.Tier tier : def.tiers()) {
            boolean locked = level < tier.unlockLevel();
            boolean poor = coins < tier.cost();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Cost: <gold>" + Msg.coins(tier.cost()));
            lore.add("<gray>Hunt: <white>" + Msg.number(tier.requiredXp()) + " <gray>progress");
            lore.add("<gray>Boss health: <red>" + Msg.number(tier.boss().health()));
            lore.add("<gray>Boss damage: <red>" + Msg.number(tier.boss().damage()));
            lore.add("<gray>Time limit: <white>" + Msg.duration(tier.timeLimitSeconds() * 1000L));
            lore.add("<gray>Reward: <light_purple>" + Msg.number(tier.slayerXp()) + " Slayer XP");
            List<String> drops = dropLines(tier.boss());
            if (!drops.isEmpty()) {
                lore.add("");
                lore.add("<gray>Drops:");
                lore.addAll(drops);
            }
            lore.add("");
            if (locked) {
                lore.add("<red>Locked: reach level " + tier.unlockLevel());
            } else if (poor) {
                lore.add("<red>You cannot afford this");
            } else {
                lore.add("<yellow>Click to start");
            }
            int slot = 11 + tier.tier() - 1;
            gui.set(slot, Guis.icon(locked ? Material.BARRIER : TIER_ICONS[tier.tier() - 1],
                "<" + TIER_COLORS[tier.tier() - 1] + ">Tier " + SlayerDefinition.roman(tier.tier()),
                lore.toArray(new String[0])), click -> {
                    if (locked) {
                        core.msg().warn(click.player(), "Reach " + def.name() + " level " + tier.unlockLevel() + " first.");
                    } else {
                        openConfirm(click.player(), def, tier.tier());
                    }
                });
        }
        gui.set(18, Guis.icon(Material.ARROW, "<yellow>Back"), click -> openMain(click.player()));
        gui.open(player);
    }

    private List<String> dropLines(BossDefinition boss) {
        List<String> lines = new ArrayList<>();
        for (BossDefinition.LootEntry entry : boss.loot().entries()) {
            String name = entry.item();
            if (entry.item().startsWith("custom:")) {
                name = core.items().def(entry.item().substring(7)).map(d -> d.name()).orElse(entry.item().substring(7));
            } else {
                name = entry.item().substring(entry.item().indexOf(':') + 1).toLowerCase(Locale.ROOT).replace('_', ' ');
            }
            String chance = entry.chance() >= 1 ? "guaranteed" : percent(entry.chance());
            lines.add("<dark_gray> - <white>" + name + " <gray>(" + chance + ")");
        }
        return lines;
    }

    static String percent(double chance) {
        double p = chance * 100;
        return (p >= 10 ? String.valueOf(Math.round(p)) : String.format(Locale.ROOT, "%.2f", p).replaceAll("0+$", "")
            .replaceAll("\\.$", "")) + "%";
    }

    private void openConfirm(Player player, SlayerDefinition def, int tier) {
        Gui gui = core.guis().create(3, "<dark_red>Confirm " + SlayerDefinition.roman(tier));
        gui.border(Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        SlayerDefinition.Tier t = def.tier(tier);
        gui.set(11, Guis.icon(Material.LIME_STAINED_GLASS_PANE, "<green>Pay " + Msg.coins(t.cost()),
            "<gray>Start " + def.name() + " " + SlayerDefinition.roman(tier), "<dark_gray>Coins are not refunded."),
            click -> {
                String refusal = manager.start(click.player(), def, tier);
                if (refusal != null) {
                    core.msg().warn(click.player(), refusal);
                }
                click.close();
            });
        gui.set(15, Guis.icon(Material.RED_STAINED_GLASS_PANE, "<red>Back"), click -> openTiers(click.player(), def));
        gui.open(player);
    }
}
