package win.vampyuyu.survival.items.accessory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.items.Costs;
import win.vampyuyu.survival.items.menu.ItemMenu;

/** The accessory bag: unlocked slots hold accessories, the bottom row shows Magical Power and the upgrade button. */
public final class AccessoryMenu extends ItemMenu {
    private static final int INFO = 49;
    private static final int UPGRADE = 51;
    private static final int CLOSE = 53;

    private final AccessoryService service;
    private final Costs costs;
    private final UUID id;
    private int unlocked;

    public AccessoryMenu(Core core, Player player, AccessoryService service, Costs costs) {
        super(core, player, 6, Component.text("Accessory Bag", NamedTextColor.DARK_PURPLE));
        this.service = service;
        this.costs = costs;
        this.id = player.getUniqueId();
        render();
    }

    private void render() {
        unlocked = service.slots(id);
        List<ItemStack> stacks = service.items(id);
        for (int slot = 0; slot < 45; slot++) {
            if (slot < unlocked) {
                inventory.setItem(slot, slot < stacks.size() ? stacks.get(slot) : null);
            } else {
                inventory.setItem(slot, Guis.icon(Material.RED_STAINED_GLASS_PANE, "<red>Locked slot",
                    "<gray>Buy slot upgrades with the button below."));
            }
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(INFO, info());
        inventory.setItem(UPGRADE, upgradeIcon());
        inventory.setItem(CLOSE, Guis.icon(Material.BARRIER, "<red>Close"));
    }

    private ItemStack info() {
        AccessoryMath.Result result = service.result(id);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Accessories counted: <green>" + result.counted().size());
        if (!result.ignored().isEmpty()) {
            lore.add("<gray>Duplicates ignored: <red>" + result.ignored().size());
        }
        lore.add("");
        lore.add("<gray>Stat bonus from Magical Power:");
        if (result.powerStats().isEmpty()) {
            lore.add("<dark_gray>None");
        }
        result.powerStats().forEach((stat, value) -> {
            if (value != 0) {
                lore.add("<gray> " + stat.displayName() + ": <green>+" + trim(value) + (stat.percent() ? "%" : ""));
            }
        });
        lore.add("");
        lore.add("<dark_gray>Only the best accessory of each family counts.");
        return Guis.icon(Material.NETHER_STAR, "<gold>Magical Power: <white>" + result.magicalPower() + " ★",
            lore.toArray(new String[0]));
    }

    private ItemStack upgradeIcon() {
        if (unlocked >= service.settings().bagMaxSlots) {
            return Guis.icon(Material.EMERALD_BLOCK, "<green>All slots unlocked");
        }
        double cost = service.nextUpgradeCost(id);
        return Guis.icon(Material.EMERALD, "<green>Unlock " + service.settings().bagSlotsPerUpgrade + " more slots",
            "<gray>Cost: <gold>" + Costs.price(cost), "<gray>Slots: <white>" + unlocked + "/" + service.settings().bagMaxSlots,
            "", "<yellow>Click to buy");
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 10) / 10.0);
    }

    private void save() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < unlocked; slot++) {
            stacks.add(inventory.getItem(slot));
        }
        service.store(id, stacks);
        inventory.setItem(INFO, info());
    }

    @Override
    protected void click(InventoryClickEvent event, boolean top) {
        if (!top) {
            ClickType type = event.getClick();
            if (type.isShiftClick() && event.getCurrentItem() != null) {
                int[] free = java.util.stream.IntStream.range(0, unlocked).toArray();
                if (moveFromPlayer(event, free, service::accepts)) {
                    save();
                }
            }
            return;
        }
        int slot = event.getSlot();
        if (slot < unlocked) {
            if (inputSlot(event, slot, service::accepts)) {
                save();
            } else if (!service.accepts(player.getItemOnCursor()) && !player.getItemOnCursor().getType().isAir()) {
                core.msg().error(player, "Only accessories fit in the bag.");
            }
            return;
        }
        if (slot == UPGRADE && unlocked < service.settings().bagMaxSlots) {
            double cost = service.nextUpgradeCost(id);
            if (!costs.charge(player, cost, "items:bag_upgrade")) {
                return;
            }
            if (!service.addUpgrade(id)) {
                costs.refund(player, cost, "items:bag_upgrade");
                return;
            }
            int before = unlocked;
            unlocked = service.slots(id);
            List<ItemStack> kept = new ArrayList<>();
            for (int i = 0; i < before; i++) {
                kept.add(inventory.getItem(i));
            }
            render();
            for (int i = 0; i < kept.size(); i++) {
                inventory.setItem(i, kept.get(i));
            }
            save();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            core.msg().ok(player, "Unlocked " + (unlocked - before) + " more accessory slots (" + unlocked + " total).");
        } else if (slot == CLOSE) {
            core.scheduler().nextTick(player::closeInventory);
        }
    }

    @Override
    protected void closed(InventoryCloseEvent event) {
        // The bag is saved after every change; nothing is held in the menu that is not already persisted.
        service.store(id, bagNow());
    }

    private List<ItemStack> bagNow() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < unlocked; slot++) {
            stacks.add(inventory.getItem(slot));
        }
        return stacks;
    }

    /** Component used by the stats menu to describe the bag in one line. */
    public static Component summary(int power, int counted) {
        return Component.text("Magical Power " + power + " (" + counted + " accessories)", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
    }

    /** Pure: the sum of one stat over a list of entries (tests). */
    static double sum(List<AccessoryMath.Entry> entries, Stat stat) {
        return entries.stream().mapToDouble(e -> e.stats().getOrDefault(stat, 0.0)).sum();
    }

    static String grouped(double v) { return Msg.grouped(v); }
}
