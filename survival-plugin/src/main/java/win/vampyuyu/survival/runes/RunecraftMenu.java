package win.vampyuyu.survival.runes;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.runes.RuneService.Cost;
import win.vampyuyu.survival.runes.RuneService.Outcome;

/**
 * The Runecraft table: one row per rune with a button for each rank, a grinder for Rune Powder and a scraper for the
 * item in hand. Everything is paid from the player's inventory, so the menu has no input slots.
 */
final class RunecraftMenu {
    private static final int GRIND = 8;
    private static final int SCRAPE = 26;
    private static final int HELP = 44;

    private final Core core;
    private final RuneService service;
    private final Player player;
    private final Gui gui;

    private RunecraftMenu(Core core, RuneService service, Player player) {
        this.core = core;
        this.service = service;
        this.player = player;
        this.gui = core.guis().create(6, "<light_purple>Runecraft");
    }

    static void open(Core core, RuneService service, Player player) {
        RunecraftMenu menu = new RunecraftMenu(core, service, player);
        menu.render();
        menu.gui.open(player);
    }

    private void render() {
        RuneConfig config = service.config();
        for (int slot = 0; slot < gui.size(); slot++) {
            gui.clear(slot);
        }
        int row = 0;
        for (Rune rune : config.runes()) {
            int base = row * 9;
            gui.set(base, Guis.icon(rune.realm() ? Material.ECHO_SHARD : Material.AMETHYST_CLUSTER,
                "<light_purple>" + rune.name() + " Rune", describe(rune, config)));
            if (rune.realm()) {
                gui.set(base + 2, button(rune, 0), click -> craft(rune, 0));
            } else {
                for (int rank = 1; rank <= 3; rank++) {
                    int r = rank;
                    gui.set(base + 1 + rank, button(rune, rank), click -> craft(rune, r));
                }
            }
            row++;
        }
        gui.set(GRIND, Guis.icon(Material.GRINDSTONE, "<yellow>Grind Rune Powder",
            "<gray>Turns one <white>Amethyst Shard</white> into <white>" + config.grindOutput() + " Rune Powder</white>.",
            "", Guis.ACCENT + "▸ Click to grind"), click -> {
                report(service.grind(player));
                render();
            });
        gui.set(SCRAPE, Guis.icon(Material.BARRIER, "<red>Scrape the item in hand",
            "<gray>Removes <white>every</white> rune from the weapon or armor", "<gray>you hold. The runes are lost.", "",
            "<red>▸ Shift-click to scrape"), click -> {
                if (!click.shift()) {
                    core.msg().warn(player, "Shift-click to confirm: the runes are lost.");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                Outcome outcome = service.scrape(hand);
                if (outcome.ok()) {
                    player.getInventory().setItemInMainHand(hand);
                }
                report(outcome);
            });
        gui.set(HELP, Guis.icon(Material.BOOK, "<aqua>How runes work",
            "<gray>Craft a rune here, then click it onto a weapon", "<gray>or armor piece in your inventory.", "",
            "<gray>Slots: tier 1 = " + config.slotsFor(1) + ", tier 2 = " + config.slotsFor(2) + ", tier 3 = "
                + config.slotsFor(3) + ", tier 4-5 = " + config.slotsFor(4) + ".",
            "<gray>One rune of each kind per item.", "<gray>Take worn armor off before engraving it."));
    }

    private String[] describe(Rune rune, RuneConfig config) {
        List<String> lines = new ArrayList<>();
        if (rune.realm()) {
            lines.add("<gold>Realm rune <gray>- a new effect, worth a rank III.");
        } else {
            lines.add("<gray>+" + config.value(1) + " / +" + config.value(2) + " / +" + config.value(3) + " <white>"
                + rune.stat().displayName());
        }
        String where = switch (rune.target()) {
            case ARMOR -> "armor";
            case WEAPON -> "weapons";
            case ANY -> "weapons and armor";
        };
        lines.add("<gray>For " + where + ".");
        return lines.toArray(new String[0]);
    }

    private ItemStack button(Rune rune, int rank) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Needs:");
        boolean affordable = true;
        for (Cost cost : service.costs(rune, rank)) {
            int have = RuneService.count(player, cost);
            boolean enough = have >= cost.amount();
            affordable &= enough;
            lore.add((enough ? "<green> ✔ " : "<red> ✘ ") + cost.amount() + " " + cost.label() + " <dark_gray>(" + have + ")");
        }
        lore.add("");
        lore.add(affordable ? Guis.ACCENT + "▸ Click to craft" : "<red>Missing ingredients");
        Material material = rune.realm() ? Material.ECHO_SHARD
            : rank == 1 ? Material.AMETHYST_SHARD : rank == 2 ? Material.ECHO_SHARD : Material.PRISMARINE_CRYSTALS;
        String name = rune.realm() ? rune.name() + " Rune" : rune.name() + " Rune " + Rune.roman(rank);
        return Guis.icon(material, (affordable ? "<green>" : "<gray>") + name, lore.toArray(new String[0]));
    }

    private void craft(Rune rune, int rank) {
        report(service.craft(player, rune, rank));
        render();
    }

    private void report(Outcome outcome) {
        if (outcome.ok()) {
            core.msg().ok(player, outcome.message());
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        } else {
            core.msg().error(player, outcome.message());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
    }
}
