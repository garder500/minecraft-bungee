package win.vampyuyu.survival.menu;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The Fairy Souls menu: progress per zone, the next milestone and a button that points at the nearest soul. */
final class FairyGui {
    private static final int[] ZONE_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private final MenuRuntime rt;
    private final FairySouls fairy;
    private Consumer<Player> back = p -> { };

    FairyGui(MenuRuntime rt, FairySouls fairy) {
        this.rt = rt;
        this.fairy = fairy;
    }

    void backTo(Consumer<Player> back) {
        this.back = back;
    }

    int total() {
        return fairy.total();
    }

    void open(Player player) {
        FairyData data = rt.fairy(player.getUniqueId());
        MenuConfig cfg = rt.config();
        Gui gui = rt.core.guis().create(5, "<dark_gray>Fairy Souls");
        gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int found = data.found.size();
        int total = fairy.total();
        gui.set(4, Guis.icon(Material.PINK_DYE, "<light_purple>Fairy Souls",
            lore(player, data, found, total, cfg).toArray(String[]::new)));
        List<SoulLogic.ZoneProgress> zones = fairy.zoneProgress(player);
        for (int i = 0; i < zones.size() && i < ZONE_SLOTS.length; i++) {
            SoulLogic.ZoneProgress zone = zones.get(i);
            boolean done = zone.found() >= zone.total();
            gui.set(ZONE_SLOTS[i], Guis.icon(icon(zone.zone()), (done ? "<green>" : "<light_purple>") + label(zone.zone()),
                "<gray>Found: " + (done ? "<green>" : "<light_purple>") + zone.found() + "<gray>/" + zone.total(),
                (done ? "<green>" : "<light_purple>") + SoulLogic.bar(zone.found(), zone.total(), 16),
                "", done ? "<green>Every soul found here!" : "<gray>Souls are hidden here. Keep looking!"));
        }
        gui.set(31, Guis.icon(Material.COMPASS, "<aqua>Find the Nearest Soul",
            "<gray>Points you toward the closest", "<gray>soul you have not collected.", "",
            "<yellow>Click to point the way!"), click -> {
                click.close();
                rt.core.scheduler().nextTick(() -> fairy.hint(player));
            });
        gui.set(40, Guis.icon(Material.ARROW, "<green>Go Back", "<gray>To Skyblock Menu"), click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private List<String> lore(Player player, FairyData data, int found, int total, MenuConfig cfg) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        lore.add("<gray>Hidden souls give you permanent stats.");
        lore.add("");
        lore.add("<gray>Found: <light_purple>" + found + "<gray>/" + total);
        lore.add("<light_purple>" + SoulLogic.bar(found, total, 20));
        lore.add("");
        if (cfg.fairyEvery <= 0) {
            lore.add("<gray>Milestone rewards are switched off.");
        } else {
            int number = SoulLogic.milestones(found, cfg.fairyEvery) + 1;
            lore.add("<gray>Next reward in <light_purple>" + SoulLogic.untilNext(found, cfg.fairyEvery)
                + "<gray> souls <dark_gray>(milestone " + number + ")");
            double coins = SoulLogic.milestoneCoins(number, cfg.fairyCoinsPerMilestone, cfg.fairyCoinsIncrease);
            if (coins > 0) {
                lore.add("<dark_gray> - <gold>+" + Msg.number(coins) + " coins");
            }
            if (!cfg.fairyCycle.isEmpty()) {
                SoulLogic.StatStep step = cfg.fairyCycle.get((number - 1) % cfg.fairyCycle.size());
                lore.add("<dark_gray> - <#" + String.format("%06x", step.stat().color().value()) + ">+"
                    + Msg.number(step.amount()) + " " + step.stat().displayName());
            }
            lore.add("<gray>Milestones reached: <light_purple>" + data.paidMilestones);
        }
        return lore;
    }

    static String label(String zone) {
        return switch (zone) {
            case "hub" -> "The Hub";
            case "world" -> "Your Island";
            case "nether" -> "The Nether";
            case "end" -> "The End";
            default -> zone;
        };
    }

    private static Material icon(String zone) {
        return switch (zone) {
            case "hub" -> Material.BELL;
            case "world" -> Material.GRASS_BLOCK;
            case "nether" -> Material.NETHERRACK;
            case "end" -> Material.END_STONE;
            default -> Material.PAPER;
        };
    }
}
