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
        Gui gui = rt.core.guis().create(6, "<dark_gray>Wisps");
        int found = data.found.size();
        int total = fairy.total();
        gui.set(4, Guis.icon(Material.PINK_DYE, "<light_purple>Wisps",
            lore(player, data, found, total, cfg).toArray(String[]::new)));
        gui.model(4, "menu/wisps");
        List<SoulLogic.ZoneProgress> zones = fairy.zoneProgress(player);
        for (int i = 0; i < zones.size() && i < ZONE_SLOTS.length; i++) {
            SoulLogic.ZoneProgress zone = zones.get(i);
            boolean done = zone.found() >= zone.total();
            gui.set(ZONE_SLOTS[i], Guis.icon(icon(zone.zone()), (done ? "<green>" : "<light_purple>") + label(zone.zone()),
                "<gray>Found " + Guis.progress(zone.found(), zone.total()),
                done ? "<green>Every wisp found here" : "<gray>Wisps are hidden here. Keep looking."));
        }
        gui.set(31, Guis.icon(Material.COMPASS, "<aqua>Find the Nearest Wisp",
            "<gray>Points to the closest wisp you have not found.", Guis.ACCENT + "▸ Point the way"), click -> {
                click.close();
                rt.core.scheduler().nextTick(() -> fairy.hint(player));
            });
        gui.back("To the Isles", click -> back.accept(player));
        gui.open(player);
        rt.play(player, "click");
    }

    private List<String> lore(Player player, FairyData data, int found, int total, MenuConfig cfg) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        lore.add("<gray>Hidden wisps give you permanent stats.");
        lore.add("<gray>Found " + Guis.progress(found, total));
        if (cfg.fairyEvery <= 0) {
            lore.add("<gray>Milestone rewards are switched off.");
        } else {
            int number = SoulLogic.milestones(found, cfg.fairyEvery) + 1;
            lore.add("<gray>Next reward in <light_purple>" + SoulLogic.untilNext(found, cfg.fairyEvery)
                + "<gray> wisps <dark_gray>(milestone " + number + ")");
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
