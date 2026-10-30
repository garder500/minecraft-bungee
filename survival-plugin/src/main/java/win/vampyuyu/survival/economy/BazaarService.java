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

    private record InFlight(UUID owner, String ownerName, long cents, Map<String, Integer> items) {}

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

    BazaarService(Core core, EconomyService economy, ItemKeys keys, DurableFile.Env env) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.env = env;
    }

    void enable(ConfigurationSection config) {
        BazaarMarket.State state = core.store().read(FILE, BazaarMarket.State.class, BazaarMarket.State::new);
        market = new BazaarMarket(state, rules(config));
        file = new DurableFile(core.store(), FILE, market::state, env, () -> {
            core.profiles().saveDirty();
            core.profiles().awaitWrites();
        }, core.getPlugin().getLogger(), config.getLong("bazaar.persist-delay-ticks", 10));
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

    BazaarMarket market() { return market; }

    /** Final write on disable. In-flight claims whose delivery never ran are put back first. */
    void close(DurableFile.Live live) {
        closing = true;
        if (live != null) {
            live.shutdown();
        }
        for (InFlight f : inFlight) {
            market.restoreClaim(f.owner().toString(), f.ownerName(), f.cents(), f.items(), System.currentTimeMillis());
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

    Outcome instantBuy(Player player, String product, int amount) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            return refused;
        }
        var quote = market.quoteBuy(id(player), product, amount);
        if (!quote.check().ok()) {
            return Outcome.fail(quote.check().error());
        }
        ItemStack template = keys.create(product, 1).orElseThrow();
        if (!Inv.canFit(player, Inv.stacks(template, amount))) {
            return Outcome.fail("Your inventory cannot hold " + amount + " items.");
        }
        if (!economy.takeCents(player.getUniqueId(), quote.cents(), "bazaar:buy:" + product)) {
            return Outcome.fail("You need " + Money.coins(quote.cents()) + " for that.");
        }
        market.commitBuy(quote, product, System.currentTimeMillis());
        Inv.give(player, Inv.stacks(template, amount));
        persist();
        return Outcome.ok("Bought " + amount + "x " + keys.name(product) + " for " + Money.coins(quote.cents()) + ".");
    }

    Outcome instantSell(Player player, String product, int amount) {
        Outcome refused = gate(player, product);
        if (refused != null) {
            return refused;
        }
        if (Inv.count(player, keys, product) < amount) {
            return Outcome.fail("You do not carry " + amount + " unmodified " + keys.name(product) + ".");
        }
        var quote = market.quoteSell(id(player), product, amount);
        if (!quote.check().ok()) {
            return Outcome.fail(quote.check().error());
        }
        if (economy.settings().maxBalanceCents() - economy.balanceCents(player.getUniqueId()) < quote.cents()) {
            return Outcome.fail("Your purse cannot hold that many coins. Deposit some in the bank first.");
        }
        if (!Inv.remove(player, keys, product, amount)) {
            return Outcome.fail("Your inventory changed, try again.");
        }
        Inv.saveData(player, saveData);
        market.commitSell(quote, product, System.currentTimeMillis());
        economy.addCents(player.getUniqueId(), quote.cents(), "bazaar:sell:" + product);
        persist();
        return Outcome.ok("Sold " + amount + "x " + keys.name(product) + " for " + Money.coins(quote.cents())
            + " (tax included).");
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
    void claim(Player player, java.util.function.Consumer<Outcome> done) {
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
        InFlight flight = new InFlight(uid, player.getName(), cents, new LinkedHashMap<>(items));
        inFlight.add(flight);
        long grantedCents = cents;
        file.request(ok -> {
            if (closing) {
                return; // close() already put the claim back into the final file
            }
            inFlight.remove(flight);
            claiming.remove(uid);
            if (!ok) {
                market.restoreClaim(uid.toString(), flight.ownerName(), flight.cents(), flight.items(),
                    System.currentTimeMillis());
                persist();
                Player p = Bukkit.getPlayer(uid);
                if (p != null) {
                    done.accept(Outcome.fail("The Bazaar could not save, your claim was kept. Try again."));
                }
                return;
            }
            Player p = Bukkit.getPlayer(uid);
            if (p == null) {
                market.restoreClaim(uid.toString(), flight.ownerName(), flight.cents(), flight.items(),
                    System.currentTimeMillis());
                persist();
                return;
            }
            deliver(p, flight);
            StringBuilder text = new StringBuilder("Claimed");
            if (grantedCents > 0) {
                text.append(' ').append(Money.coins(grantedCents));
            }
            flight.items().forEach((k, n) -> text.append(' ').append(n).append("x ").append(keys.name(k)).append(','));
            if (text.charAt(text.length() - 1) == ',') {
                text.setLength(text.length() - 1);
            }
            done.accept(Outcome.ok(text + "."));
        });
    }

    private void deliver(Player p, InFlight flight) {
        if (flight.cents() > 0) {
            long credited = economy.addCents(p.getUniqueId(), flight.cents(), "bazaar:claim");
            if (credited < flight.cents()) {
                // the purse reached the cap during the write window: the rest stays claimable
                market.restoreClaim(p.getUniqueId().toString(), p.getName(), flight.cents() - credited, Map.of(),
                    System.currentTimeMillis());
                persist();
            }
            core.profiles().saveDirty();
        }
        flight.items().forEach((key, n) -> Inv.giveKey(p, keys, key, n));
        if (!flight.items().isEmpty()) {
            Inv.saveData(p, saveData);
        }
    }

    // ---------------------------------------------------------------- audit

    @Override
    public void audit(EconomyAudit.Report report) {
        long inFlightCents = inFlight.stream().mapToLong(InFlight::cents).sum();
        report.extras.put("Bazaar escrow and unclaimed coins", market.escrowedCents() + inFlightCents);
        for (String problem : market.verify()) {
            report.problems.add("bazaar: " + problem);
        }
    }
}
