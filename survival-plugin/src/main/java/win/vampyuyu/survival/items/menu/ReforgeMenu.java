package win.vampyuyu.survival.items.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.Costs;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.reforge.Reforge;

/** The blacksmith: pay coins to roll a random reforge for the item in the middle slot. */
public final class ReforgeMenu extends ItemMenu {
    private static final int ITEM = 13;
    private static final int BUTTON = 31;

    private final Gear gear;
    private final Costs costs;
    private final Supplier<ItemsSettings> settings;
    private final Random random = new Random();

    public ReforgeMenu(Core core, Player player, Gear gear, Costs costs, Supplier<ItemsSettings> settings) {
        super(core, player, 5, Component.text("Blacksmith", NamedTextColor.DARK_GRAY));
        this.gear = gear;
        this.costs = costs;
        this.settings = settings;
        for (int slot = 0; slot < 45; slot++) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(ITEM, null);
        inventory.setItem(40, Guis.icon(Material.BARRIER, "<red>Close"));
        drawButton();
    }

    /** Price of a reforge for an item rarity: {@code base * (1 + tier)^2}. */
    public static double price(Rarity rarity, double base) {
        double tier = 1 + rarity.ordinal();
        return Math.floor(base * tier * tier / 2.0);
    }

    private boolean accepts(ItemStack stack) {
        Optional<GearInfo> info = gear.read(stack);
        return info.isPresent() && info.get().kind() != ItemKind.OTHER && info.get().kind() != ItemKind.BOOK
            && info.get().kind() != ItemKind.PET && !gear.reforges().rollable(info.get().kind()).isEmpty();
    }

    private void drawButton() {
        ItemStack stack = inventory.getItem(ITEM);
        Optional<GearInfo> info = gear.read(stack);
        if (info.isEmpty() || !accepts(stack)) {
            inventory.setItem(BUTTON, Guis.icon(Material.ANVIL, "<red>Nothing to reforge",
                "<gray>Place a weapon, tool, armor piece or", "<gray>accessory in the slot above.", "",
                "<gray>Reforges add stats that grow with the", "<gray>item's rarity."));
            return;
        }
        double cost = price(info.get().rarity(), settings.get().reforgeCostBase);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current reforge: <white>" + gear.reforges().get(info.get().reforge()).map(Reforge::name).orElse("none"));
        lore.add("<gray>Cost: <gold>" + Costs.price(cost));
        lore.add("");
        lore.add("<gray>Possible reforges:");
        for (Reforge reforge : gear.reforges().rollable(info.get().kind())) {
            StringBuilder stats = new StringBuilder();
            reforge.at(info.get().rarity(), gear.reforges().scale(info.get().rarity())).forEach((stat, value) -> {
                if (stats.length() > 0) {
                    stats.append(", ");
                }
                stats.append(value >= 0 ? "+" : "").append(trim(value)).append(' ').append(stat.displayName());
            });
            lore.add("<dark_gray> - <blue>" + reforge.name() + " <dark_gray>(" + stats + ")");
        }
        lore.add("");
        lore.add("<gray>Reforge stones apply special reforges:");
        lore.add("<gray>click the stone onto the item in your inventory.");
        lore.add("");
        lore.add("<yellow>Click to reforge");
        inventory.setItem(BUTTON, Guis.icon(Material.ANVIL, "<green>Reforge Item", lore.toArray(new String[0])));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    @Override
    protected void click(InventoryClickEvent event, boolean top) {
        if (!top) {
            if (event.getClick().isShiftClick() && moveFromPlayer(event, new int[] {ITEM}, this::accepts)) {
                drawButton();
            }
            return;
        }
        if (event.getSlot() == ITEM) {
            if (inputSlot(event, ITEM, this::accepts)) {
                drawButton();
            }
        } else if (event.getSlot() == BUTTON) {
            reforge();
        } else if (event.getSlot() == 40) {
            core.scheduler().nextTick(player::closeInventory);
        }
    }

    private void reforge() {
        ItemStack stack = inventory.getItem(ITEM);
        Optional<GearInfo> info = gear.read(stack);
        if (info.isEmpty() || !accepts(stack)) {
            return;
        }
        Reforge next = gear.reforges().roll(info.get().kind(), info.get().reforge(), random);
        if (next == null) {
            core.msg().warn(player, "There is no other reforge available for this item.");
            return;
        }
        double cost = price(info.get().rarity(), settings.get().reforgeCostBase);
        if (!costs.charge(player, cost, "items:reforge")) {
            return;
        }
        try {
            gear.setReforge(stack, next.id());
            gear.rebuild(stack);
            inventory.setItem(ITEM, stack);
        } catch (RuntimeException e) {
            costs.refund(player, cost, "items:reforge");
            core.msg().error(player, "Something went wrong, you were refunded.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        core.msg().ok(player, "Your item is now " + next.name() + ".");
        drawButton();
    }

    @Override
    protected void closed(InventoryCloseEvent event) {
        returnSlots(ITEM);
    }
}
