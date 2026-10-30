package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.api.WingsApi;
import win.vampyuyu.survival.core.Core;

/**
 * The Auction House: listing, bidding, Buy It Now, settlement and claims on top of {@link AuctionBook}. Main thread
 * only. Escrow rules and the crash model are in {@link DurableFile}: coins and items are made durable on the player's
 * side first, then the auction file records them; claims leave the auction file first and reach the player only once
 * that file is durable.
 */
final class AuctionService implements EconomyAudit.Contributor {
    static final String FILE = "economy/auctions.json";

    /** Auction categories in menu order. */
    enum Category {
        WEAPONS("Weapons", Material.IRON_SWORD),
        ARMOR("Armor", Material.IRON_CHESTPLATE),
        TOOLS("Tools", Material.IRON_PICKAXE),
        BLOCKS("Blocks", Material.GRASS_BLOCK),
        CONSUMABLES("Consumables", Material.GOLDEN_APPLE),
        MISC("Misc", Material.ENDER_CHEST);

        final String label;
        final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    /** Sort orders of the browse menu. */
    enum Sort {
        ENDING("Ending soon"), CHEAPEST("Lowest price"), PRICIEST("Highest price"), NEWEST("Newest");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Listing fee rule and the other settings that are not part of {@link AuctionBook.Rules}. */
    record Settings(boolean enabled, List<Integer> durations, int defaultHours, double feeBasePercent,
            double feePerDayPercent, long minFeeCents, long maxFeeCents, Set<Material> blocked, Set<String> blockedIds,
            int maxItemChars, long persistDelay) {
        long fee(long priceCents, int hours) {
            double pct = feeBasePercent + feePerDayPercent * (hours / 24.0);
            long fee = Math.round(priceCents * pct / 100.0);
            return Math.max(minFeeCents, Math.min(maxFeeCents, fee));
        }
    }

    private record InFlight(UUID owner, String ownerName, AuctionBook.Taken taken, List<ItemStack> stacks) {}

    private final Core core;
    private final EconomyService economy;
    private final ItemKeys keys;
    private final DurableFile.Env env;
    private AuctionBook book;
    private DurableFile file;
    private Settings settings;
    private boolean closing;
    private boolean saveData = true;
    private final Set<UUID> claiming = new HashSet<>();
    private final List<InFlight> inFlight = new ArrayList<>();

    AuctionService(Core core, EconomyService economy, ItemKeys keys, DurableFile.Env env) {
        this.core = core;
        this.economy = economy;
        this.keys = keys;
        this.env = env;
    }

    void enable(ConfigurationSection config) {
        AuctionBook.State state = core.store().read(FILE, AuctionBook.State.class, AuctionBook.State::new);
        book = new AuctionBook(state, rules(config));
        file = new DurableFile(core.store(), FILE, book::state, env, () -> {
            core.profiles().saveDirty();
            core.profiles().awaitWrites();
        }, core.getPlugin().getLogger(), config.getLong("auction.persist-delay-ticks", 10));
        reload(config);
    }

    void reload(ConfigurationSection c) {
        saveData = c.getBoolean("safety.save-player-data", true);
        book.rules(rules(c));
        List<Integer> hours = new ArrayList<>();
        for (int h : c.getIntegerList("auction.durations-hours")) {
            if (h >= 1 && h <= 24 * 14 && !hours.contains(h)) {
                hours.add(h);
            }
        }
        if (hours.isEmpty()) {
            hours = List.of(1, 6, 12, 24, 48);
        }
        java.util.Collections.sort(hours);
        int def = c.getInt("auction.default-duration-hours", 12);
        if (!hours.contains(def)) {
            def = hours.get(0);
        }
        Set<Material> blocked = new HashSet<>();
        for (String name : c.getStringList("auction.blocked-materials")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                blocked.add(m);
            }
        }
        Set<String> ids = new HashSet<>(c.getStringList("auction.blocked-items"));
        long minFee = Math.max(0, Math.round(c.getDouble("auction.listing-fee.min", 25) * 100));
        long maxFee = Math.max(minFee, Math.round(c.getDouble("auction.listing-fee.max", 5_000_000) * 100));
        settings = new Settings(c.getBoolean("auction.enabled", true), List.copyOf(hours), def,
            Math.max(0, c.getDouble("auction.listing-fee.base-percent", 1.0)),
            Math.max(0, c.getDouble("auction.listing-fee.per-day-percent", 0.5)), minFee, maxFee, blocked, ids,
            EconomySettings.clamp(c.getInt("auction.max-item-chars", 200_000), 1000, 5_000_000),
            c.getLong("auction.persist-delay-ticks", 10));
        file.delay(settings.persistDelay());
    }

    private static AuctionBook.Rules rules(ConfigurationSection c) {
        return new AuctionBook.Rules(
            EconomySettings.clamp(c.getInt("auction.max-listings", 7), 1, 100),
            Math.max(1, Math.round(c.getDouble("auction.min-price", 10) * 100)),
            Math.max(100, Math.min(1_000_000_000_000L, Math.round(c.getDouble("auction.max-price", 1_000_000_000) * 100))),
            EconomySettings.clamp((int) Math.round(c.getDouble("auction.tax-percent", 1.0) * 100), 0, 5000),
            EconomySettings.clamp((int) Math.round(c.getDouble("auction.bid-increment-percent", 5)), 1, 100),
            Math.max(1, Math.round(c.getDouble("auction.bid-increment-min", 1) * 100)),
            Math.max(0, c.getLong("auction.anti-snipe-window-seconds", 120)) * 1000,
            Math.max(1, c.getLong("auction.anti-snipe-extend-seconds", 120)) * 1000,
            EconomySettings.clamp(c.getInt("auction.max-extensions", 10), 0, 1000),
            EconomySettings.clamp(c.getInt("auction.history-size", 300), 10, 5000));
    }

    boolean enabled() { return settings.enabled() && !closing; }

    AuctionBook book() { return book; }

    Settings settings() { return settings; }

    /** Final write on disable. In-flight claims whose delivery never ran are put back first. */
    void close(DurableFile.Live live) {
        closing = true;
        if (live != null) {
            live.shutdown();
        }
        for (InFlight f : inFlight) {
            book.restore(f.taken());
        }
        inFlight.clear();
        file.close();
    }

    private void persist() {
        file.request(null);
    }

    private static String id(Player p) {
        return p.getUniqueId().toString();
    }

    // ---------------------------------------------------------------- items

    /** Decoded copy of a listing's stack, or null when the stored text cannot be read. */
    ItemStack stack(AuctionBook.Auction a) {
        if (a.cache == null) {
            a.cache = ItemCodec.decode(a.item);
        }
        return a.cache == null ? null : a.cache.clone();
    }

    /** A readable plain name for a stack. */
    String displayName(ItemStack stack) {
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the key name
        }
        String key = keys.keyOf(stack);
        return key == null ? "?" : keys.name(key);
    }

    Category categoryOf(ItemStack stack) {
        String n = stack.getType().name();
        if (n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")
                || n.equals("SHIELD") || n.endsWith("_SPEAR")) {
            return Category.WEAPONS;
        }
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.equals("ELYTRA") || n.equals("TURTLE_HELMET") || n.endsWith("_HORSE_ARMOR")) {
            return Category.ARMOR;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.equals("FISHING_ROD") || n.equals("SHEARS") || n.equals("FLINT_AND_STEEL")
                || n.equals("CARROT_ON_A_STICK") || n.equals("WARPED_FUNGUS_ON_A_STICK")) {
            return Category.TOOLS;
        }
        if (stack.getType().isEdible() || n.contains("POTION") || n.equals("MILK_BUCKET")) {
            return Category.CONSUMABLES;
        }
        if (stack.getType().isBlock() && keys.keyOf(stack) != null && ItemKeys.looksVanilla(keys.keyOf(stack))) {
            return Category.BLOCKS;
        }
        return Category.MISC;
    }

    /** Why this stack may not be listed, or null. */
    String refuseItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "Hold the item you want to sell in your main hand.";
        }
        if (settings.blocked().contains(stack.getType())) {
            return "That item cannot be auctioned.";
        }
        String key = keys.keyOf(stack);
        if (key != null && settings.blockedIds().contains(key)) {
            return "That item cannot be auctioned.";
        }
        boolean wings = core.service(WingsApi.class).map(w -> {
            try {
                return w.isWings(stack);
            } catch (RuntimeException e) {
                return false;
            }
        }).orElse(false);
        if (wings) {
            return "Wings are bound to you and cannot be sold.";
        }
        return null;
    }

    // ---------------------------------------------------------------- queries

    /** Active listings, filtered and sorted. */
    List<AuctionBook.Auction> browse(Category category, String search, Sort sort, long now) {
        List<AuctionBook.Auction> out = new ArrayList<>();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        for (AuctionBook.Auction a : book.active(now)) {
            if (category != null && !category.name().equals(a.category)) {
                continue;
            }
            if (!q.isEmpty() && !a.itemName.toLowerCase(Locale.ROOT).contains(q)
                    && !a.itemKey.toLowerCase(Locale.ROOT).replace('_', ' ').contains(q)) {
                continue;
            }
            out.add(a);
        }
        out.sort(switch (sort) {
            case ENDING -> java.util.Comparator.comparingLong((AuctionBook.Auction a) -> a.endsAt);
            case CHEAPEST -> java.util.Comparator.comparingLong(this::price);
            case PRICIEST -> java.util.Comparator.comparingLong(this::price).reversed();
            case NEWEST -> java.util.Comparator.comparingLong((AuctionBook.Auction a) -> a.created).reversed();
        });
        return out;
    }

    /** Current price of a listing: the top bid, or the start price. */
    long price(AuctionBook.Auction a) {
        return a.bin || a.bids == 0 ? a.startCents : a.bidCents;
    }

    /** Average of the recent sales of the same item key, 0 when there are none. */
    long averageSale(String itemKey) {
        long sum = 0;
        long n = 0;
        for (AuctionBook.Sale s : book.state().sales) {
            if (s.itemKey.equals(itemKey) && s.amount > 0) {
                sum += s.cents / s.amount;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    // ---------------------------------------------------------------- actions

    private BazaarService.Outcome gate() {
        return enabled() ? null : BazaarService.Outcome.fail("The Auction House is closed right now.");
    }

    BazaarService.Outcome list(Player player, long priceCents, boolean bin, int hours) {
        var refused = gate();
        if (refused != null) {
            return refused;
        }
        if (!settings.durations().contains(hours)) {
            return BazaarService.Outcome.fail("Pick a duration of " + settings.durations().stream()
                .map(h -> h + "h").toList() + ".");
        }
        long now = System.currentTimeMillis();
        var check = book.checkList(id(player), priceCents);
        if (!check.ok()) {
            return BazaarService.Outcome.fail(check.error());
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        String why = refuseItem(hand);
        if (why != null) {
            return BazaarService.Outcome.fail(why);
        }
        String encoded;
        try {
            encoded = ItemCodec.encode(hand);
        } catch (RuntimeException e) {
            return BazaarService.Outcome.fail("That item cannot be listed.");
        }
        if (encoded.length() > settings.maxItemChars()) {
            return BazaarService.Outcome.fail("That item carries too much data to be auctioned.");
        }
        long fee = settings.fee(priceCents, hours);
        if (fee > 0 && !economy.takeCents(player.getUniqueId(), fee, "auction:fee")) {
            return BazaarService.Outcome.fail("You need " + Money.coins(fee) + " for the listing fee.");
        }
        String key = keys.keyOf(hand);
        String name = displayName(hand);
        Category category = categoryOf(hand);
        int amount = hand.getAmount();
        player.getInventory().setItemInMainHand(null);
        Inv.saveData(player, saveData);
        AuctionBook.Auction a = book.list(id(player), player.getName(), encoded, key == null ? "" : key, name,
            category.name(), amount, bin, priceCents, hours * 3_600_000L, now);
        book.state().feesBurnedCents += fee;
        persist();
        return BazaarService.Outcome.ok("Listed " + amount + "x " + name + " as " + (bin ? "a Buy It Now" : "an auction")
            + " #" + a.id + " at " + Money.coins(priceCents) + " for " + hours + "h (fee " + Money.coins(fee) + ").");
    }

    BazaarService.Outcome bid(Player player, long auctionId, long cents) {
        var refused = gate();
        if (refused != null) {
            return refused;
        }
        long now = System.currentTimeMillis();
        AuctionBook.Auction a = book.byId(auctionId);
        var check = book.checkBid(a, id(player), cents, now);
        if (!check.ok()) {
            return BazaarService.Outcome.fail(check.error());
        }
        if (!economy.takeCents(player.getUniqueId(), cents, "auction:bid:" + auctionId)) {
            return BazaarService.Outcome.fail("You need " + Money.coins(cents) + " to place that bid.");
        }
        long refunded = a.bids > 0 ? a.bidCents : 0;
        String previous = book.bid(a, id(player), player.getName(), cents, now);
        persist();
        if (!previous.isEmpty()) {
            notify(previous, "You were outbid on " + a.itemName + " (#" + a.id + "). Your "
                + Money.coins(refunded) + " is waiting in /ah claim.");
        }
        return BazaarService.Outcome.ok("Bid " + Money.coins(cents) + " on " + a.itemName + " (#" + a.id + ").");
    }

    BazaarService.Outcome buyNow(Player player, long auctionId) {
        var refused = gate();
        if (refused != null) {
            return refused;
        }
        long now = System.currentTimeMillis();
        AuctionBook.Auction a = book.byId(auctionId);
        var check = book.checkBuyNow(a, id(player), now);
        if (!check.ok()) {
            return BazaarService.Outcome.fail(check.error());
        }
        long cost = a.startCents;
        if (!economy.takeCents(player.getUniqueId(), cost, "auction:buy:" + auctionId)) {
            return BazaarService.Outcome.fail("You need " + Money.coins(cost) + " to buy that.");
        }
        book.buyNow(a, id(player), player.getName(), now);
        persist();
        notify(a.seller, player.getName() + " bought your " + a.itemName + " for " + Money.coins(cost)
            + ". Collect it with /ah claim.");
        return BazaarService.Outcome.ok("Bought " + a.itemName + " for " + Money.coins(cost) + ". Collect it with /ah claim.");
    }

    BazaarService.Outcome cancel(Player player, long auctionId) {
        if (!enabled()) {
            return BazaarService.Outcome.fail("The Auction House is closed right now.");
        }
        var check = book.cancel(book.byId(auctionId), id(player), System.currentTimeMillis());
        if (!check.ok()) {
            return BazaarService.Outcome.fail(check.error());
        }
        persist();
        return BazaarService.Outcome.ok("Auction #" + auctionId + " cancelled. Collect the item with /ah claim.");
    }

    /** Ends every listing whose time is up (called on a repeating task). */
    void tick() {
        if (closing || book == null) {
            return;
        }
        List<AuctionBook.Auction> changed = book.settleDue(System.currentTimeMillis());
        if (changed.isEmpty()) {
            return;
        }
        persist();
        for (AuctionBook.Auction a : changed) {
            if (AuctionBook.SOLD.equals(a.state)) {
                notify(a.seller, "Your " + a.itemName + " sold for " + Money.coins(a.finalCents) + " to " + a.bidderName
                    + ". Collect with /ah claim.");
                notify(a.bidder, "You won " + a.itemName + " for " + Money.coins(a.finalCents)
                    + "! Collect it with /ah claim.");
            } else {
                notify(a.seller, "Your " + a.itemName + " did not sell. Collect it back with /ah claim.");
            }
        }
    }

    private void notify(String owner, String text) {
        try {
            Player p = Bukkit.getPlayer(UUID.fromString(owner));
            if (p != null) {
                core.msg().info(p, text);
            }
        } catch (IllegalArgumentException ignored) {
            // not a uuid
        }
    }

    /** Tells a joining player that something waits for them. */
    void remind(Player player) {
        if (!enabled()) {
            return;
        }
        AuctionBook.Claim claim = book.claimable(id(player));
        if (!claim.empty()) {
            core.msg().info(player, "You have " + (claim.items().isEmpty() ? "" : claim.items().size() + " item(s) ")
                + (claim.cents() > 0 ? Money.coins(claim.cents()) + " " : "") + "waiting in /ah claim.");
        }
    }

    // ---------------------------------------------------------------- claims

    boolean busy(Player player) {
        return claiming.contains(player.getUniqueId());
    }

    AuctionBook.Claim claimable(Player player) {
        return book.claimable(id(player));
    }

    /** Collects everything that fits. {@code done} receives the message once delivered (or refused). */
    void claim(Player player, Consumer<BazaarService.Outcome> done) {
        if (!enabled()) {
            done.accept(BazaarService.Outcome.fail("The Auction House is closed right now."));
            return;
        }
        UUID uid = player.getUniqueId();
        if (!claiming.add(uid)) {
            done.accept(BazaarService.Outcome.fail("Your last claim is still being saved."));
            return;
        }
        AuctionBook.Claim have = book.claimable(id(player));
        if (have.empty()) {
            claiming.remove(uid);
            done.accept(BazaarService.Outcome.fail("You have nothing to claim."));
            return;
        }
        long room = Math.max(0, economy.settings().maxBalanceCents() - economy.balanceCents(uid));
        boolean coins = have.cents() > 0 && have.cents() <= room;
        List<AuctionBook.Auction> fitting = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        Inventory sim = simulated(player);
        for (AuctionBook.Auction a : have.items()) {
            ItemStack s = stack(a);
            if (s == null) {
                continue; // unreadable, stays in the book and shows up in the audit
            }
            ItemStack[] before = sim.getContents();
            if (!sim.addItem(s.clone()).isEmpty()) {
                sim.setContents(before);
                continue;
            }
            fitting.add(a);
            stacks.add(s);
        }
        if (!coins && fitting.isEmpty()) {
            claiming.remove(uid);
            done.accept(BazaarService.Outcome.fail(have.items().isEmpty()
                ? "Your purse cannot hold that many coins. Deposit some in the bank first."
                : "Your inventory is full. Make some room and claim again."));
            return;
        }
        AuctionBook.Taken taken = book.takeClaim(id(player), fitting, coins);
        if (taken == null) {
            claiming.remove(uid);
            done.accept(BazaarService.Outcome.fail("Nothing to claim."));
            return;
        }
        InFlight flight = new InFlight(uid, player.getName(), taken, stacks);
        inFlight.add(flight);
        file.request(ok -> {
            if (closing) {
                return; // close() already put the claim back into the final file
            }
            inFlight.remove(flight);
            claiming.remove(uid);
            if (!ok) {
                book.restore(taken);
                persist();
                if (Bukkit.getPlayer(uid) != null) {
                    done.accept(BazaarService.Outcome.fail("The Auction House could not save, your claim was kept. Try again."));
                }
                return;
            }
            Player p = Bukkit.getPlayer(uid);
            if (p == null) {
                book.restore(taken);
                persist();
                return;
            }
            if (taken.cents() > 0) {
                economy.addCents(uid, taken.cents(), "auction:claim");
                core.profiles().saveDirty();
            }
            for (ItemStack s : flight.stacks()) {
                Inv.give(p, List.of(s));
            }
            if (!flight.stacks().isEmpty()) {
                Inv.saveData(p, saveData);
            }
            StringBuilder text = new StringBuilder("Claimed");
            if (taken.cents() > 0) {
                text.append(' ').append(Money.coins(taken.cents()));
            }
            if (!flight.stacks().isEmpty()) {
                text.append(taken.cents() > 0 ? " and " : " ").append(flight.stacks().size()).append(" item(s)");
            }
            done.accept(BazaarService.Outcome.ok(text + "."));
        });
    }

    private static Inventory simulated(Player player) {
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 36 && i < storage.length; i++) {
            sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        }
        return sim;
    }

    // ---------------------------------------------------------------- audit

    @Override
    public void audit(EconomyAudit.Report report) {
        long inFlightCents = inFlight.stream().mapToLong(f -> f.taken().cents()).sum();
        report.extras.put("Auction escrow and unclaimed coins", book.escrowedCents() + inFlightCents);
        for (String problem : book.verify()) {
            report.problems.add("auction: " + problem);
        }
        for (AuctionBook.Auction a : book.state().auctions) {
            if (stack(a) == null) {
                report.problems.add("auction: listing " + a.id + " holds an item that cannot be decoded");
            }
        }
    }
}
