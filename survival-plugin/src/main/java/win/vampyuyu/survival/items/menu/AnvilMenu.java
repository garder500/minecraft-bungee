package win.vampyuyu.survival.items.menu;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.items.Costs;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.gear.Keys;

/**
 * Replaces the vanilla anvil in Skyblock worlds. Left slot: the item (or a book). Right slot: an enchanted book, or
 * a second copy of the same item to merge its enchants. The middle slot previews the result and its price; clicking
 * it pays and delivers the result. The price is in coins or in experience levels ({@code costs.anvil.mode}).
 */
public final class AnvilMenu extends ItemMenu {
    private static final int LEFT = 11;
    private static final int RIGHT = 15;
    private static final int RESULT = 13;

    /** A computed anvil result (pure data apart from the stack). */
    public record Preview(@Nullable ItemStack result, double price, String message) {
        public boolean ok() { return result != null; }
    }

    private final Gear gear;
    private final Costs costs;
    private final Supplier<ItemsSettings> settings;

    public AnvilMenu(Core core, Player player, Gear gear, Costs costs, Supplier<ItemsSettings> settings) {
        super(core, player, 3, Component.text("Anvil", NamedTextColor.DARK_GRAY));
        this.gear = gear;
        this.costs = costs;
        this.settings = settings;
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(LEFT, null);
        inventory.setItem(RIGHT, null);
        inventory.setItem(22, Guis.icon(Material.BARRIER, "<red>Close"));
        refreshResult();
    }

    private boolean accepts(ItemStack stack) {
        return gear.read(stack).map(info -> info.kind() != ItemKind.PET && info.kind() != ItemKind.ACCESSORY
            && info.kind() != ItemKind.OTHER).orElse(false);
    }

    private boolean acceptsRight(ItemStack stack) {
        return gear.read(stack).isPresent();
    }

    // ---------------------------------------------------------------- pure combination

    /** Computes the result of combining {@code left} and {@code right}. Neither stack is modified. */
    public Preview preview(@Nullable ItemStack left, @Nullable ItemStack right) {
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return new Preview(null, 0, "Place an item on the left and a book or a matching item on the right.");
        }
        Optional<GearInfo> leftInfo = gear.read(left);
        Optional<GearInfo> rightInfo = gear.read(right);
        if (leftInfo.isEmpty() || rightInfo.isEmpty()) {
            return new Preview(null, 0, "Those items cannot be combined.");
        }
        ItemsSettings s = settings.get();
        double unit = s.anvilCostPerLevel;
        Optional<String> stored = gear.items().getString(right, Keys.BOOK);
        ItemStack out = left.clone();
        Map<String, Integer> merged = new LinkedHashMap<>(leftInfo.get().enchants());
        double price = 0;
        if (stored.isPresent()) {
            var book = EnchantRules.parseBook(stored.get());
            if (book.isEmpty()) {
                return new Preview(null, 0, "That book is blank.");
            }
            Optional<Enchant> enchant = gear.enchants().get(book.get().getKey());
            if (leftInfo.get().kind() == ItemKind.BOOK) {
                return bookPlusBook(left, right, leftInfo.get(), book.get(), s);
            }
            EnchantRules.Result result = EnchantRules.combine(merged, gear.enchants(), enchant.orElse(null),
                book.get().getValue(), leftInfo.get().kind());
            if (!result.ok()) {
                return new Preview(null, 0, result.refusal().message());
            }
            merged.put(enchant.get().id(), result.level());
            price = EnchantRules.anvilCost(result.level(), unit, enchant.get().ultimate());
        } else if (rightInfo.get().id().equals(leftInfo.get().id()) && leftInfo.get().kind() != ItemKind.BOOK) {
            int improved = 0;
            for (Map.Entry<String, Integer> entry : rightInfo.get().enchants().entrySet()) {
                Optional<Enchant> enchant = gear.enchants().get(entry.getKey());
                EnchantRules.Result result = EnchantRules.combine(merged, gear.enchants(), enchant.orElse(null),
                    entry.getValue(), leftInfo.get().kind());
                if (result.ok()) {
                    merged.put(entry.getKey(), result.level());
                    price += EnchantRules.anvilCost(result.level(), unit, enchant.get().ultimate());
                    improved++;
                }
            }
            if (improved == 0) {
                return new Preview(null, 0, "The second item adds nothing.");
            }
        } else {
            return new Preview(null, 0, "Use an enchanted book, or a second copy of the same item.");
        }
        gear.setEnchants(out, merged);
        gear.rebuild(out);
        return new Preview(out, Math.floor(price), "Ready.");
    }

    private Preview bookPlusBook(ItemStack left, ItemStack right, GearInfo leftInfo, Map.Entry<String, Integer> rightBook,
            ItemsSettings s) {
        Optional<String> leftStored = gear.items().getString(left, Keys.BOOK);
        var leftBook = EnchantRules.parseBook(leftStored.orElse(null));
        if (leftBook.isEmpty() || !leftBook.get().getKey().equals(rightBook.getKey())) {
            return new Preview(null, 0, "Books must hold the same enchant to be merged.");
        }
        Optional<Enchant> enchant = gear.enchants().get(rightBook.getKey());
        if (enchant.isEmpty() || !leftBook.get().getValue().equals(rightBook.getValue())
                || leftBook.get().getValue() >= enchant.get().maxLevel()) {
            return new Preview(null, 0, "Only two books of the same level (below the maximum) merge.");
        }
        int level = leftBook.get().getValue() + 1;
        ItemStack out = left.clone();
        gear.items().set(out, Keys.BOOK, enchant.get().id() + ":" + level);
        gear.rebuild(out);
        return new Preview(out, Math.floor(EnchantRules.anvilCost(level, s.anvilCostPerLevel, enchant.get().ultimate())),
            "Ready.");
    }

    private boolean levelMode() { return settings.get().anvilCostMode.equals("levels"); }

    private int levelPrice(double coins) {
        return (int) Math.max(1, Math.ceil(coins / Math.max(1, settings.get().anvilCostPerLevel) * 2));
    }

    private void refreshResult() {
        Preview preview = preview(inventory.getItem(LEFT), inventory.getItem(RIGHT));
        if (!preview.ok()) {
            inventory.setItem(RESULT, Guis.icon(Material.RED_STAINED_GLASS_PANE, "<red>No result", "<gray>" + preview.message()));
            return;
        }
        ItemStack show = preview.result().clone();
        var meta = show.getItemMeta();
        var lore = meta.lore() == null ? new java.util.ArrayList<Component>() : new java.util.ArrayList<>(meta.lore());
        lore.add(Component.empty());
        String price = levelMode() ? levelPrice(preview.price()) + " experience levels" : Costs.price(preview.price());
        lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Cost: <gold>" + price)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<yellow>Click to combine")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        show.setItemMeta(meta);
        inventory.setItem(RESULT, show);
    }

    @Override
    protected void click(InventoryClickEvent event, boolean top) {
        if (!top) {
            if (event.getClick().isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                boolean book = clicked != null && gear.items().getString(clicked, Keys.BOOK).isPresent();
                if (moveFromPlayer(event, book && inventory.getItem(RIGHT) == null ? new int[] {RIGHT, LEFT}
                        : new int[] {LEFT, RIGHT}, this::acceptsRight)) {
                    refreshResult();
                }
            }
            return;
        }
        switch (event.getSlot()) {
            case LEFT -> {
                if (inputSlot(event, LEFT, this::accepts)) {
                    refreshResult();
                }
            }
            case RIGHT -> {
                if (inputSlot(event, RIGHT, this::acceptsRight)) {
                    refreshResult();
                }
            }
            case RESULT -> confirm();
            case 22 -> core.scheduler().nextTick(player::closeInventory);
            default -> { }
        }
    }

    private void confirm() {
        Preview preview = preview(inventory.getItem(LEFT), inventory.getItem(RIGHT));
        if (!preview.ok()) {
            core.msg().warn(player, preview.message());
            return;
        }
        boolean levels = levelMode();
        int levelCost = levels ? levelPrice(preview.price()) : 0;
        if (levels) {
            if (player.getLevel() < levelCost && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                core.msg().error(player, "You need " + levelCost + " experience levels.");
                return;
            }
        } else if (!costs.charge(player, preview.price(), "items:anvil")) {
            return;
        }
        ItemStack result = preview.result();
        inventory.setItem(LEFT, null);
        inventory.setItem(RIGHT, null);
        if (levels && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - levelCost);
        }
        giveBack(result);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1f);
        core.msg().ok(player, "Combined. " + (levels ? "-" + levelCost + " levels" : Costs.price(preview.price())));
        refreshResult();
    }

    @Override
    protected void closed(InventoryCloseEvent event) {
        returnSlots(LEFT, RIGHT);
    }
}
