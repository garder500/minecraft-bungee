package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/**
 * The Bazaar: product catalogue, escrow of coins and items, instant trades, orders and claims on top of
 * {@link BazaarMarket}. Main thread only. Items are identified by registry key and always re-created from it.
 * Persistence is described in {@link DurableFile}; claims are two phase (removed from the file first, delivered
 * when the file is durable).
 */
final class BazaarService implements EconomyAudit.Contributor {
    static final String FILE = "economy/bazaar.json";

    /** Bazaar categories in menu order. */
    enum Category {
        FARMING("Farming", Material.GOLDEN_HOE),
        MINING("Mining", Material.DIAMOND_PICKAXE),
        WOODS("Woods", Material.OAK_LOG),
        COMBAT("Combat", Material.IRON_SWORD),
        FISHING("Fishing", Material.FISHING_ROD),
        MISC("Misc", Material.NETHER_STAR);

        final String label;
        final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    /** Result of an action: a message for the player. */
    record Outcome(boolean ok, String message) {
        static Outcome ok(String m) { return new Outcome(true, m); }

        static Outcome fail(String m) { return new Outcome(false, m); }
    }

    /** Value that already left the market book but is not with its owner yet (see {@link #park}). */
    private record InFlight(UUID owner, String ownerName, long cents, Map<String, Integer> items, String reason) {}

    /** What {@link #deliver} really handed over. */
    private record Delivered(long cents, Map<String, Integer> items) {}

    private final Core core;
    private final EconomyService economy;
    private final ItemKeys keys;
    private final DurableFile.Env env;
    private BazaarMarket market;
    private DurableFile file;
    private boolean enabled = true;
    /** Set first thing in {@link #close}: a claim callback that fires after it must not deliver anything. */
    private boolean closing;
    private final Map<String, Category> products = new LinkedHashMap<>();
    private final Set<UUID> claiming = new HashSet<>();
    private final List<InFlight> inFlight = new ArrayList<>();
    private boolean saveData = true;
    private java.util.function.Supplier<ShopCatalog> npc = ShopCatalog::empty;

    BazaarService(Core core, EconomyService economy, ItemKeys keys, DurableFile.Env env) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.env = env;
    }

    void enable(ConfigurationSection config) {
        BazaarMarket.State state = core.store().read(FILE, BazaarMarket.State.class, BazaarMarket.State::new);
        market = new BazaarMarket(state, rules(config));
        market.seed(this::seedTicks);
        file = new DurableFile(core.store(), FILE, market::state, env,
            () -> core.profiles().saveDirty(), () -> core.profiles().awaitWrites(), core.getPlugin().getLogger(),
            config.getLong("bazaar.persist-delay-ticks", 10));
        reload(config);
    }

    void reload(ConfigurationSection c) {
        enabled = c.getBoolean("bazaar.enabled", true);
        saveData = c.getBoolean("safety.save-player-data", true);
        market.rules(rules(c));
        file.delay(c.getLong("bazaar.persist-delay-ticks", 10));
        products.clear();
        ConfigurationSection cats = c.getConfigurationSection("bazaar.categories");
        for (Category cat : Category.values()) {
            for (String key : cats == null ? List.<String>of() : cats.getStringList(cat.name().toLowerCase(Locale.ROOT))) {
                products.putIfAbsent(key, cat);
            }
        }
    }

    private static BazaarMarket.Rules rules(ConfigurationSection c) {
        return new BazaarMarket.Rules(
            EconomySettings.clamp((int) Math.round(c.getDouble("bazaar.tax-percent", 1.25) * 100), 0, 5000),
            EconomySettings.clamp(c.getInt("bazaar.max-orders", 14), 1, 200),
            EconomySettings.clamp(c.getInt("bazaar.max-amount", 71_680), 1, 10_000_000),
            Math.max(1, Math.min(1_000_000_000_000L, Math.round(c.getDouble("bazaar.max-price", 100_000_000) * 10))),
            Math.max(0, c.getDouble("bazaar.price-band", 5.0)),
            EconomySettings.clamp(c.getInt("bazaar.history-hours", 72), 24, 24 * 14));
    }

    boolean enabled() { return enabled && !closing; }

    /** Where the NPC prices come from (the shop tables); the Bazaar uses them as price floor, ceiling and seed. */
    void npcPrices(java.util.function.Supplier<ShopCatalog> catalog) {
        this.npc = catalog == null ? ShopCatalog::empty : catalog;
    }

    private static long ticks(long cents) {
        return cents <= 0 ? 0 : Math.max(1, cents / BazaarMarket.CENTS_PER_TICK);
    }

    /** What the best NPC pays for the product, in ticks (0 = no NPC buys it): selling below this is pointless. */
    long npcFloorTicks(String key) {
        return ticks(npc.get().bestSell(key).orElse(0));
    }

    /** What the cheapest NPC asks for the product, in ticks (0 = no NPC sells it): buying above this is pointless. */
    long npcCeilingTicks(String key) {
        return ticks(npc.get().lowestBuy(key).orElse(0));
    }

    /** The band anchor of a product that never traded: between the NPC floor and ceiling, or whichever exists. */
    long seedTicks(String key) {
        long floor = npcFloorTicks(key);
        long ceiling = npcCeilingTicks(key);
        if (floor > 0 && ceiling > 0) {
            return Math.max(1, Math.round(Math.sqrt((double) floor * ceiling)));
        }
        return Math.max(floor, ceiling);
    }

    /** Reference price for the menus and the price band, in ticks (0 when nothing is known). */
    long reference(String key, long now) {
        return market.reference(key, now);
    }

    BazaarMarket market() { return market; }

    /** Final write on disable. In-flight claims whose delivery never ran are put back first. */
    void close(DurableFile.Live live) {
        closing = true;
        if (live != null) {
            live.shutdown();
        }
        for (InFlight f : inFlight) {
            restore(f);
        }
        inFlight.clear();
        file.close();
    }

    // ---------------------------------------------------------------- catalogue

    /** Products that exist right now (custom ids may not be registered yet). */
    List<String> products(Category category) {
        List<String> out = new ArrayList<>();
        for (var e : products.entrySet()) {
            if ((category == null || e.getValue() == category) && keys.known(e.getKey())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Every configured product id, whether or not its feature is loaded. */
    Set<String> productKeys() {
        return java.util.Collections.unmodifiableSet(products.keySet());
    }

    boolean isProduct(String key) {
        return key != null && products.containsKey(key) && keys.known(key);
    }

    Category categoryOf(String key) {
        return products.getOrDefault(key, Category.MISC);
    }

    /** Products whose key or name contains the text. */
    List<String> search(String text) {
        String q = text.toLowerCase(Locale.ROOT).trim().replace('_', ' ');
        List<String> out = new ArrayList<>();
        for (String key : products(null)) {
            if (key.toLowerCase(Locale.ROOT).replace('_', ' ').contains(q)
                    || keys.name(key).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(key);
            }
        }
        return out;
    }

    /** One product for typed text: exact key/name first, then a single partial match. */
    Optional<String> find(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String exact = text.trim();
        for (String key : products(null)) {
            if (key.equalsIgnoreCase(exact.replace(' ', '_')) || keys.name(key).equalsIgnoreCase(exact)) {
                return Optional.of(key);
            }
        }
        List<String> found = search(text);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    // ---------------------------------------------------------------- helpers

    private Outcome gate(Player player, String product) {
        if (!enabled()) {
            return Outcome.fail("The Bazaar is closed right now.");
        }
        if (!isProduct(product)) {
            return Outcome.fail("The Bazaar does not trade that.");
        }
        return null;
    }

    private void persist() {
        file.request(null);
    }

    private String id(Player p) {
        return p.getUniqueId().toString();
    }

    static String price(long ticks) {
        return Money.plain(ticks * BazaarMarket.CENTS_PER_TICK);
    }

    // ---------------------------------------------------------------- instant trades

    /**
     * Buys from the cheapest sell orders. The coins are taken now (so the debit reaches the disk before the market
     * file does), the items are handed over only once the market file that records the fill is durable: a crash in
     * between loses the coins, it never lets the same items exist twice. {@code done} gets the result message, either
     * at once (refusal) or when the goods are delivered.
     */
    void instantBuy(Player player, String product, int amount, Consumer<Outcome> done) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            done.accept(refused);
            return;
        }
        var quote = market.quoteBuy(id(player), product, amount);
        if (!quote.check().ok()) {
            done.accept(Outcome.fail(quote.check().error()));
            return;
        }
        ItemStack template = keys.create(product, 1).orElseThrow();
        if (!Inv.canFit(player, Inv.stacks(template, amount))) {
            done.accept(Outcome.fail("Your inventory cannot hold " + amount + " items."));
            return;
        }
        if (!economy.takeCents(player.getUniqueId(), quote.cents(), "bazaar:buy:" + product)) {
            done.accept(Outcome.fail("You need " + Money.coins(quote.cents()) + " for that."));
            return;
        }
        market.commitBuy(quote, product, System.currentTimeMillis());
        notifyFills(quote.fills());
        long paid = quote.cents();
        InFlight flight = new InFlight(player.getUniqueId(), player.getName(), 0, Map.of(product, amount),
            "bazaar:buy:" + product);
        park(flight, null, d -> {
            int got = d.items().getOrDefault(product, 0);
            String text = "Bought " + got + "x " + keys.name(product) + " for " + Money.coins(paid) + ".";
            return got < amount ? Outcome.ok(text + " " + (amount - got)
                + " did not fit and wait in /bz claim.") : Outcome.ok(text);
        }, "The Bazaar could not save. Your " + keys.name(product) + " is waiting in /bz claim.", done);
    }

    /**
     * Sells to the highest buy orders. The items leave the inventory now (and the player data is saved), the coins
     * are paid only once the market file that records the fill is durable: a crash in between loses the coins, it
     * never pays the seller while the buyer's order is still alive on disk.
     */
    void instantSell(Player player, String product, int amount, Consumer<Outcome> done) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            done.accept(refused);
            return;
        }
        if (Inv.count(player, keys, product) < amount) {
            done.accept(Outcome.fail("You do not carry " + amount + " unmodified " + keys.name(product) + "."));
            return;
        }
        var quote = market.quoteSell(id(player), product, amount);
        if (!quote.check().ok()) {
            done.accept(Outcome.fail(quote.check().error()));
            return;
        }
        sell(player, product, quote, done);
    }

    /**
     * Sells every clean unit of the product the player carries, as far as the buy orders go (never fails for a short
     * book; only when nothing at all can be sold).
     */
    void instantSellAll(Player player, String product, Consumer<Outcome> done) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            done.accept(refused);
            return;
        }
        int carry = Inv.count(player, keys, product);
        if (carry < 1) {
            done.accept(Outcome.fail("You carry no unmodified " + keys.name(product) + "."));
            return;
        }
        int depth = market.sellableDepth(id(player), product);
        var quote = market.quoteSell(id(player), product, Math.min(carry, Math.max(1, depth)), true);
        if (!quote.check().ok()) {
            done.accept(Outcome.fail(quote.check().error()));
            return;
        }
        sell(player, product, quote, done);
    }

    /**
     * Sells every Bazaar product in the inventory to the existing buy orders (instant sells, one per product).
     * {@code done} gets one summary message when the last proceeds are delivered.
     */
    void sellInventory(Player player, Consumer<Outcome> done) {
        if (!enabled()) {
            done.accept(Outcome.fail("The Bazaar is closed right now."));
            return;
        }
        Set<String> carried = new java.util.LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            String key = keys.keyOf(stack);
            if (key != null && isProduct(key) && keys.clean(stack)) {
                carried.add(key);
            }
        }
        List<String> sellable = new ArrayList<>();
        for (String key : carried) {
            if (market.sellableDepth(id(player), key) > 0) {
                sellable.add(key);
            }
        }
        if (sellable.isEmpty()) {
            done.accept(Outcome.fail(carried.isEmpty() ? "You carry nothing the Bazaar trades."
                : "Nobody has a buy order for what you carry."));
            return;
        }
        List<String> keysToSell = new ArrayList<>();
        List<BazaarMarket.Instant> quotes = new ArrayList<>();
        for (String key : sellable) {
            int carry = Inv.count(player, keys, key);
            int depth = market.sellableDepth(id(player), key);
            var quote = market.quoteSell(id(player), key, Math.min(carry, depth), true);
            if (quote.check().ok()) {
                keysToSell.add(key);
                quotes.add(quote);
            }
        }
        if (quotes.isEmpty()) {
            done.accept(Outcome.fail("Nothing could be sold, try again."));
            return;
        }
        int[] sold = {0};
        long[] coins = {0};
        int[] okCount = {0};
        int[] pending = {quotes.size()};
        for (int i = 0; i < quotes.size(); i++) {
            int items = quotes.get(i).items();
            long net = quotes.get(i).cents();
            sell(player, keysToSell.get(i), quotes.get(i), o -> {
                if (o.ok()) {
                    sold[0] += items;
                    coins[0] += net;
                    okCount[0]++;
                }
                if (--pending[0] == 0) {
                    done.accept(okCount[0] == 0 ? Outcome.fail("Nothing could be sold, try again.")
                        : Outcome.ok("Sold " + sold[0] + " items of " + okCount[0] + " products for "
                            + Money.coins(coins[0]) + " (tax included)."));
                }
            });
        }
    }

    private void sell(Player player, String product, BazaarMarket.Instant quote, Consumer<Outcome> done) {
        int amount = quote.items();
        if (economy.settings().maxBalanceCents() - economy.balanceCents(player.getUniqueId()) < quote.cents()) {
            done.accept(Outcome.fail("Your purse cannot hold that many coins. Deposit some in the bank first."));
            return;
        }
        if (!Inv.remove(player, keys, product, amount)) {
            done.accept(Outcome.fail("Your inventory changed, try again."));
            return;
        }
        Inv.saveData(player, saveData);
        market.commitSell(quote, product, System.currentTimeMillis());
        notifyFills(quote.fills());
        long net = quote.cents();
        InFlight flight = new InFlight(player.getUniqueId(), player.getName(), net, Map.of(), "bazaar:sell:" + product);
        park(flight, null, d -> d.cents() >= net
            ? Outcome.ok("Sold " + amount + "x " + keys.name(product) + " for " + Money.coins(net) + " (tax included).")
            : Outcome.ok("Sold " + amount + "x " + keys.name(product) + " for " + Money.coins(net)
                + " (tax included). Your purse is full: " + Money.coins(net - d.cents()) + " waits in /bz claim."),
            "The Bazaar could not save. Your " + Money.coins(net) + " is waiting in /bz claim.", done);
    }

    /** Tells every online owner of a resting order that part of it was filled. */
    private void notifyFills(List<BazaarMarket.Fill> fills) {
        Map<Long, Integer> byOrder = new LinkedHashMap<>();
        Map<Long, BazaarMarket.Order> orders = new LinkedHashMap<>();
        for (BazaarMarket.Fill f : fills) {
            byOrder.merge(f.resting().id, f.qty(), Integer::sum);
            orders.put(f.resting().id, f.resting());
        }
        byOrder.forEach((orderId, qty) -> {
            BazaarMarket.Order o = orders.get(orderId);
            Player owner = online(o.owner);
            if (owner == null) {
                return;
            }
            boolean done = o.remaining == 0;
            core.msg().info(owner, "Your " + (o.buy ? "buy" : "sell") + " order #" + o.id + " for "
                + keys.name(o.product) + " " + (done ? "was filled" : "was filled for " + qty + " more") + " ("
                + o.filled() + "/" + o.total + "). Collect it with /bz claim.");
            Sfx.notice(owner);
        });
    }

    private static Player online(String uuid) {
        try {
            return Bukkit.getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- orders

    Outcome placeBuy(Player player, String product, long ticks, int amount) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            return refused;
        }
        long now = System.currentTimeMillis();
        var check = market.checkBuyOrder(id(player), product, ticks, amount, now);
        if (!check.ok()) {
            return Outcome.fail(check.error());
        }
        long cost = ticks * BazaarMarket.CENTS_PER_TICK * amount;
        if (!economy.takeCents(player.getUniqueId(), cost, "bazaar:order:buy:" + product)) {
            return Outcome.fail("You need " + Money.coins(cost) + " to place that order.");
        }
        var placed = market.placeBuy(id(player), player.getName(), product, ticks, amount, now);
        if (!placed.check().ok()) {
            economy.addCents(player.getUniqueId(), cost, "bazaar:order:refund");
            return Outcome.fail(placed.check().error());
        }
        persist();
        notifyFills(placed.fills());
        return Outcome.ok("Buy order #" + placed.order().id + " placed: " + amount + "x " + keys.name(product) + " at "
            + price(ticks) + " each (" + Money.coins(cost) + " held)."
            + (placed.filledNow() > 0 ? " " + placed.filledNow() + " were bought instantly, use /bz claim." : ""));
    }

    Outcome placeSell(Player player, String product, long ticks, int amount) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            return refused;
        }
        long now = System.currentTimeMillis();
        var check = market.checkSellOrder(id(player), product, ticks, amount, now);
        if (!check.ok()) {
            return Outcome.fail(check.error());
        }
        if (Inv.count(player, keys, product) < amount) {
            return Outcome.fail("You do not carry " + amount + " unmodified " + keys.name(product) + ".");
        }
        if (!Inv.remove(player, keys, product, amount)) {
            return Outcome.fail("Your inventory changed, try again.");
        }
        Inv.saveData(player, saveData);
        var placed = market.placeSell(id(player), player.getName(), product, ticks, amount, now);
        if (!placed.check().ok()) {
            Inv.giveKey(player, keys, product, amount);
            return Outcome.fail(placed.check().error());
        }
        persist();
        notifyFills(placed.fills());
        return Outcome.ok("Sell order #" + placed.order().id + " placed: " + amount + "x " + keys.name(product) + " at "
            + price(ticks) + " each."
            + (placed.filledNow() > 0 ? " " + placed.filledNow() + " were sold instantly, use /bz claim." : ""));
    }

    Outcome cancel(Player player, long orderId) {
        if (!enabled()) {
            return Outcome.fail("The Bazaar is closed right now.");
        }
        if (!market.cancel(orderId, id(player))) {
            return Outcome.fail("Order #" + orderId + " cannot be cancelled (not yours, or already finished).");
        }
        persist();
        return Outcome.ok("Order #" + orderId + " cancelled. Use /bz claim to collect the refund.");
    }

    // ---------------------------------------------------------------- claims

    boolean busy(Player player) {
        return claiming.contains(player.getUniqueId());
    }

    BazaarMarket.Claim claimable(Player player) {
        return market.claimable(id(player));
    }

    /** Collects everything that fits. {@code done} receives the message once delivered (or refused). */
    void claim(Player player, Consumer<Outcome> done) {
        if (!enabled()) {
            done.accept(Outcome.fail("The Bazaar is closed right now."));
            return;
        }
        UUID uid = player.getUniqueId();
        if (!claiming.add(uid)) {
            done.accept(Outcome.fail("Your last claim is still being saved."));
            return;
        }
        BazaarMarket.Claim have = market.claimable(id(player));
        if (have.empty()) {
            claiming.remove(uid);
            done.accept(Outcome.fail("You have nothing to claim."));
            return;
        }
        long room = Math.max(0, economy.settings().maxBalanceCents() - economy.balanceCents(uid));
        long cents = Math.min(have.cents(), room);
        Map<String, Integer> items = Inv.fit(player, keys, have.items());
        if (cents <= 0 && items.isEmpty()) {
            claiming.remove(uid);
            done.accept(Outcome.fail(have.items().isEmpty() ? "Your purse cannot hold more coins."
                : "Your inventory is full. Make some room and claim again."));
            return;
        }
        if (!market.takeClaim(id(player), cents, items)) {
            claiming.remove(uid);
            done.accept(Outcome.fail("Nothing to claim."));
            return;
        }
        InFlight flight = new InFlight(uid, player.getName(), cents, new LinkedHashMap<>(items), "bazaar:claim");
        park(flight, () -> claiming.remove(uid), d -> {
            StringBuilder text = new StringBuilder("Claimed");
            if (d.cents() > 0) {
                text.append(' ').append(Money.coins(d.cents()));
            }
            d.items().forEach((k, n) -> text.append(' ').append(n).append("x ").append(keys.name(k)).append(','));
            if (text.charAt(text.length() - 1) == ',') {
                text.setLength(text.length() - 1);
            }
            return Outcome.ok(text + ".");
        }, "The Bazaar could not save, your claim was kept. Try again.", done);
    }

    /**
     * Two phase hand over of value that already left the book: it is parked, the market file is requested, and only
     * when that file is durable is the owner paid / given the goods. A failed write, a player who left, or a plugin
     * disable puts the value back as a settled claim (so it can never exist twice). {@code release} runs when the
     * write finishes, {@code ok} builds the message from what was really delivered.
     */
    private void park(InFlight flight, Runnable release, java.util.function.Function<Delivered, Outcome> ok,
            String saveFailed, Consumer<Outcome> done) {
        inFlight.add(flight);
        file.request(saved -> {
            if (closing) {
                return; // close() already put the value back into the final file
            }
            inFlight.remove(flight);
            if (release != null) {
                release.run();
            }
            Player p = Bukkit.getPlayer(flight.owner());
            if (!saved) {
                restore(flight);
                persist();
                if (p != null) {
                    done.accept(Outcome.fail(saveFailed));
                }
                return;
            }
            if (p == null) {
                restore(flight);
                persist();
                return;
            }
            done.accept(ok.apply(deliver(p, flight)));
        });
    }

    private void restore(InFlight f) {
        market.restoreClaim(f.owner().toString(), f.ownerName(), f.cents(), f.items(), System.currentTimeMillis());
    }

    /** Pays and hands over what fits; whatever does not stays claimable. */
    private Delivered deliver(Player p, InFlight flight) {
        long credited = 0;
        boolean leftover = false;
        if (flight.cents() > 0) {
            credited = economy.addCents(p.getUniqueId(), flight.cents(), flight.reason());
            if (credited < flight.cents()) {
                // the purse reached the cap during the write window: the rest stays claimable
                market.restoreClaim(p.getUniqueId().toString(), p.getName(), flight.cents() - credited, Map.of(),
                    System.currentTimeMillis());
                leftover = true;
            }
            core.profiles().saveDirty();
        }
        Map<String, Integer> fitted = flight.items().isEmpty() ? Map.of() : Inv.fit(p, keys, flight.items());
        fitted.forEach((key, n) -> Inv.giveKey(p, keys, key, n));
        if (!fitted.isEmpty()) {
            Inv.saveData(p, saveData);
        }
        Map<String, Integer> rest = new LinkedHashMap<>();
        flight.items().forEach((key, n) -> {
            int missing = n - fitted.getOrDefault(key, 0);
            if (missing > 0) {
                rest.put(key, missing);
            }
        });
        if (!rest.isEmpty()) {
            market.restoreClaim(p.getUniqueId().toString(), p.getName(), 0, rest, System.currentTimeMillis());
            leftover = true;
        }
        if (leftover) {
            persist();
        }
        return new Delivered(credited, fitted);
    }

    /** Tells a joining player that fills or refunds wait for them. */
    void remind(Player player) {
        if (!enabled()) {
            return;
        }
        BazaarMarket.Claim claim = market.claimable(id(player));
        if (!claim.empty()) {
            int items = claim.items().values().stream().mapToInt(Integer::intValue).sum();
            core.msg().info(player, "Your Bazaar orders have something for you: "
                + (items > 0 ? items + " item(s) " : "") + (claim.cents() > 0 ? Money.coins(claim.cents()) + " " : "")
                + "waiting in /bz claim.");
            Sfx.notice(player);
        }
    }

    // ---------------------------------------------------------------- audit

    @Override
    public void audit(EconomyAudit.Report report) {
        long inFlightCents = inFlight.stream().mapToLong(InFlight::cents).sum();
        report.extras.put("Bazaar escrow and unclaimed coins", market.escrowedCents() + inFlightCents);
        report.burns.put("Bazaar tax burned", market.state().taxBurnedCents);
        for (String problem : market.verify()) {
            report.problems.add("bazaar: " + problem);
        }
    }
}
