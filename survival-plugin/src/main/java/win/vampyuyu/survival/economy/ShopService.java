package win.vampyuyu.survival.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.ToLongFunction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/**
 * NPC merchant trading: buying, selling (single items, a whole inventory, sell-all) and the buyback list.
 * Main thread only. Items are removed from the inventory BEFORE the coins are paid, and an item is only handed out
 * AFTER the coins were taken, so a failure in the middle can never create coins or items.
 */
final class ShopService {
    /** One kind of item in a sale. */
    record Line(String key, int qty, long cents) {}

    /** Outcome of a sale or purchase. */
    record Result(boolean ok, String error, long cents, int items, List<Line> lines) {
        static Result fail(String error) {
            return new Result(false, error, 0, 0, List.of());
        }
    }

    /** An item sold to a merchant that can be bought back for what it paid. */
    record BuybackEntry(ItemStack stack, String key, long paidCents, long at) {}

    private final Core core;
    private final EconomyService economy;
    private final ItemKeys keys;
    private final PricePressure pressure;
    private volatile ShopCatalog catalog = ShopCatalog.empty();
    private volatile int buybackSize = 18;
    private volatile long buybackMillis = 300_000;
    private volatile boolean protectHotbar;
    private final Map<UUID, Deque<BuybackEntry>> buyback = new HashMap<>();

    ShopService(Core core, EconomyService economy, ItemKeys keys, PricePressure pressure) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.pressure = pressure;
    }

    void reload(ConfigurationSection c) {
        catalog = ShopCatalog.parse(c, core.getPlugin().getLogger());
        buybackSize = EconomySettings.clamp(c.getInt("buyback.size", 18), 1, 45);
        buybackMillis = Math.max(10, c.getLong("buyback.seconds", 300)) * 1000;
        protectHotbar = c.getBoolean("sell.protect-hotbar", false);
        pressure.reload(c);
    }

    ShopCatalog catalog() {
        return catalog;
    }

    PricePressure pressure() {
        return pressure;
    }

    long buybackMillis() {
        return buybackMillis;
    }

    // ---------------------------------------------------------------- buying

    Result buy(Player player, ShopCatalog.ShopDef shop, ShopCatalog.ShopItem item, int qty) {
        if (item.buyCents() <= 0 || qty < 1) {
            return Result.fail("This merchant does not sell that.");
        }
        var template = keys.create(item.key(), 1);
        if (template.isEmpty()) {
            return Result.fail("That item is not available right now.");
        }
        OptionalLong total = Money.mul(item.buyCents(), qty);
        if (total.isEmpty()) {
            return Result.fail("That is too many.");
        }
        List<ItemStack> stacks = Inv.stacks(template.get(), qty);
        if (!Inv.canFit(player, stacks)) {
            return Result.fail("Your inventory is full.");
        }
        if (!economy.takeCents(player.getUniqueId(), total.getAsLong(), "shop:buy:" + shop.id())) {
            return Result.fail("You need " + Money.coins(total.getAsLong()) + " (you have "
                + Money.coins(economy.balanceCents(player.getUniqueId())) + ").");
        }
        Inv.give(player, stacks);
        return new Result(true, null, total.getAsLong(), qty, List.of(new Line(item.key(), qty, total.getAsLong())));
    }

    // ---------------------------------------------------------------- selling

    /** Current price for one item, pressure included (rounded down to whole cents). */
    long unitSellCents(String key, long baseCents) {
        return Math.max(0, (long) Math.floor(baseCents * pressure.multiplier(key, 0, System.currentTimeMillis())));
    }

    /**
     * Sells clean items from the storage slots. {@code price} gives the base sell price in cents of a key (0 = not
     * bought); {@code onlyKey} restricts the sale to one key (null = everything the merchant buys).
     */
    Result sell(Player player, String reasonId, ToLongFunction<String> price, String onlyKey, int maxQty) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        Map<String, List<Integer>> slotsByKey = new LinkedHashMap<>();
        Map<String, Integer> qtyByKey = new LinkedHashMap<>();
        int remainingBudget = maxQty <= 0 ? Integer.MAX_VALUE : maxQty;
        for (int slot = 0; slot < storage.length; slot++) {
            if (protectHotbar && slot < 9 && onlyKey == null) {
                continue;
            }
            ItemStack s = storage[slot];
            String key = keys.keyOf(s);
            if (key == null || (onlyKey != null && !onlyKey.equals(key)) || !keys.clean(s) || price.applyAsLong(key) <= 0) {
                continue;
            }
            slotsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
            qtyByKey.merge(key, s.getAmount(), Integer::sum);
        }
        if (slotsByKey.isEmpty()) {
            return Result.fail("You have nothing this merchant buys.");
        }
        long now = System.currentTimeMillis();
        List<Line> lines = new ArrayList<>();
        long total = 0;
        int items = 0;
        Map<String, Integer> toSell = new LinkedHashMap<>();
        for (var e : qtyByKey.entrySet()) {
            int qty = Math.min(e.getValue(), remainingBudget);
            if (qty <= 0) {
                continue;
            }
            remainingBudget -= qty;
            toSell.put(e.getKey(), qty);
            long base = price.applyAsLong(e.getKey());
            double mult = pressure.multiplier(e.getKey(), qty, now);
            long cents = (long) Math.floor(base * (double) qty * mult);
            if (cents <= 0) {
                toSell.remove(e.getKey());
                continue;
            }
            lines.add(new Line(e.getKey(), qty, cents));
            total += cents;
            items += qty;
        }
        if (lines.isEmpty()) {
            return Result.fail("Nothing to sell.");
        }
        long room = economy.settings().maxBalanceCents() - economy.balanceCents(player.getUniqueId());
        if (total > room) {
            return Result.fail("Your purse cannot hold that many coins. Deposit some in the bank first.");
        }
        // Remove first, record for buyback, then pay only for what was really removed.
        long paidTotal = 0;
        int removedItems = 0;
        List<Line> done = new ArrayList<>();
        for (Line line : lines) {
            ItemStack template = keys.create(line.key(), 1).orElse(null);
            if (template == null || !Inv.remove(player, keys, line.key(), line.qty())) {
                continue;
            }
            paidTotal += line.cents();
            removedItems += line.qty();
            done.add(line);
            long perItem = line.cents() / line.qty();
            long remainder = line.cents() - perItem * line.qty();
            int left = line.qty();
            while (left > 0) {
                int n = Math.min(left, template.getMaxStackSize());
                ItemStack stack = template.clone();
                stack.setAmount(n);
                long paid = perItem * n + (left == n ? remainder : 0);
                remember(player.getUniqueId(), new BuybackEntry(stack, line.key(), paid, now));
                left -= n;
            }
            pressure.record(line.key(), line.qty(), now);
        }
        if (paidTotal <= 0) {
            return Result.fail("Your inventory changed, try again.");
        }
        economy.addCents(player.getUniqueId(), paidTotal, "shop:sell:" + reasonId);
        return new Result(true, null, paidTotal, removedItems, done);
    }

    // ---------------------------------------------------------------- buyback

    private void remember(UUID id, BuybackEntry entry) {
        Deque<BuybackEntry> list = buyback.computeIfAbsent(id, k -> new ArrayDeque<>());
        list.addFirst(entry);
        while (list.size() > buybackSize) {
            list.removeLast();
        }
    }

    /** Non-expired entries, newest first. */
    List<BuybackEntry> buybackOf(UUID id) {
        Deque<BuybackEntry> list = buyback.get(id);
        if (list == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        list.removeIf(e -> now - e.at() > buybackMillis);
        return new ArrayList<>(list);
    }

    Result repurchase(Player player, BuybackEntry entry) {
        Deque<BuybackEntry> list = buyback.get(player.getUniqueId());
        if (list == null || !list.contains(entry) || System.currentTimeMillis() - entry.at() > buybackMillis) {
            return Result.fail("That item is no longer available.");
        }
        List<ItemStack> stacks = List.of(entry.stack().clone());
        if (!Inv.canFit(player, stacks)) {
            return Result.fail("Your inventory is full.");
        }
        if (entry.paidCents() > 0 && !economy.takeCents(player.getUniqueId(), entry.paidCents(), "shop:buyback")) {
            return Result.fail("You need " + Money.coins(entry.paidCents()) + " to buy this back.");
        }
        list.remove(entry);
        pressure.record(entry.key(), -entry.stack().getAmount(), System.currentTimeMillis());
        Inv.give(player, stacks);
        return new Result(true, null, entry.paidCents(), entry.stack().getAmount(),
            List.of(new Line(entry.key(), entry.stack().getAmount(), entry.paidCents())));
    }

    void forget(UUID id) {
        buyback.remove(id);
    }
}
