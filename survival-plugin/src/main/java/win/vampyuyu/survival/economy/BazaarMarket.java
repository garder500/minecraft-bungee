package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Bazaar order books: pure logic, no Bukkit. Prices are integers in TICKS of 0.1 coin (10 cents), amounts are
 * item counts, money is in cents, so every match is exact. Matching is price-time priority and the trade price is the
 * resting order's price. The class never touches players: callers escrow first (coins for buy orders, items for sell
 * orders) and hand claims out afterwards. Single threaded (main thread).
 *
 * <p>Value invariants (checked by {@link #escrowedCents} and the audit): a live buy order holds
 * {@code remaining * price * 10} cents in escrow; a live sell order holds {@code remaining} items; fills move value
 * into the {@code claim*} fields of the orders involved; tax is the only value that leaves the book.
 */
final class BazaarMarket {
    static final long CENTS_PER_TICK = 10;

    /** An order. Public fields: this is the persisted form. */
    static final class Order {
        long id;
        String owner = "";
        String ownerName = "";
        String product = "";
        boolean buy;
        long price;
        int total;
        int remaining;
        long claimCents;
        int claimItems;
        long created;
        long seq;
        boolean closed;

        boolean live() {
            return !closed && remaining > 0;
        }

        boolean hasClaim() {
            return claimCents > 0 || claimItems > 0;
        }

        int filled() {
            return total - remaining;
        }
    }

    /** One hour of trading. */
    static final class Bucket {
        long hour;
        long volume;
        long sumCents;
        long high;
        long low;
    }

    /** Price history of one product. */
    static final class History {
        long lastTicks;
        List<Bucket> buckets = new ArrayList<>();
    }

    /** The persisted market. */
    static final class State {
        long nextId = 1;
        long nextSeq = 1;
        long taxBurnedCents;
        List<Order> orders = new ArrayList<>();
        Map<String, History> history = new HashMap<>();
    }

    /** Tunables. */
    record Rules(int taxBp, int maxOrders, int maxAmount, long maxPriceTicks, double band, int historyHours) {}

    /** Why an operation was refused (message shown to the player) or null. */
    record Check(String error) {
        static final Check OK = new Check(null);

        boolean ok() { return error == null; }
    }

    /** One executed trade against a resting order. */
    record Fill(Order resting, int qty, long priceTicks, long grossCents, long taxCents) {}

    /** Outcome of an instant trade. {@code cents} = what the taker pays (buy) or receives net of tax (sell). */
    record Instant(Check check, int items, long cents, List<Fill> fills) {
        static Instant fail(String error) {
            return new Instant(new Check(error), 0, 0, List.of());
        }
    }

    /** Outcome of placing an order. */
    record Placed(Check check, Order order, int filledNow, List<Fill> fills) {
        static Placed fail(String error) {
            return new Placed(new Check(error), null, 0, List.of());
        }
    }

    /** What an owner can collect. */
    record Claim(long cents, Map<String, Integer> items) {
        boolean empty() { return cents <= 0 && items.isEmpty(); }
    }

    private static final Comparator<Order> ASKS = Comparator.<Order>comparingLong(o -> o.price).thenComparingLong(o -> o.seq);
    private static final Comparator<Order> BIDS =
        Comparator.<Order>comparingLong(o -> -o.price).thenComparingLong(o -> o.seq);

    private final State state;
    private Rules rules;
    /** NPC derived reference price in ticks for products that never traded (0 = unknown). */
    private java.util.function.ToLongFunction<String> seed = k -> 0;
    private final Map<String, List<Order>> asks = new HashMap<>();
    private final Map<String, List<Order>> bids = new HashMap<>();

    BazaarMarket(State state, Rules rules) {
        this.state = state;
        this.rules = rules;
        for (Order o : state.orders) {
            if (o.live()) {
                insert(o);
            }
        }
    }

    State state() { return state; }

    /** Where the price band starts for a product with no trading history (derived from the NPC shop tables). */
    void seed(java.util.function.ToLongFunction<String> seed) { this.seed = seed == null ? k -> 0 : seed; }

    void rules(Rules rules) { this.rules = rules; }

    Rules rules() { return rules; }

    private void insert(Order o) {
        List<Order> list = (o.buy ? bids : asks).computeIfAbsent(o.product, k -> new ArrayList<>());
        Comparator<Order> cmp = o.buy ? BIDS : ASKS;
        int lo = 0;
        int hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cmp.compare(list.get(mid), o) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        list.add(lo, o);
    }

    private void unbook(Order o) {
        List<Order> list = (o.buy ? bids : asks).get(o.product);
        if (list != null) {
            list.remove(o);
        }
    }

    // ---------------------------------------------------------------- reading

    List<Order> asks(String product) { return asks.getOrDefault(product, List.of()); }

    List<Order> bids(String product) { return bids.getOrDefault(product, List.of()); }

    /** Cheapest sell order price in ticks, 0 when none. */
    long bestAsk(String product) {
        List<Order> l = asks(product);
        return l.isEmpty() ? 0 : l.get(0).price;
    }

    /** Highest buy order price in ticks, 0 when none. */
    long bestBid(String product) {
        List<Order> l = bids(product);
        return l.isEmpty() ? 0 : l.get(0).price;
    }

    int askDepth(String product) { return asks(product).stream().mapToInt(o -> o.remaining).sum(); }

    int bidDepth(String product) { return bids(product).stream().mapToInt(o -> o.remaining).sum(); }

    /** Price levels {price, total amount, order count} for display, best first, at most {@code max} levels. */
    List<long[]> levels(String product, boolean buySide, int max) {
        Map<Long, long[]> byPrice = new LinkedHashMap<>();
        for (Order o : buySide ? bids(product) : asks(product)) {
            long[] level = byPrice.get(o.price);
            if (level == null) {
                if (byPrice.size() >= max) {
                    break;
                }
                level = new long[] {o.price, 0, 0};
                byPrice.put(o.price, level);
            }
            level[1] += o.remaining;
            level[2]++;
        }
        return new ArrayList<>(byPrice.values());
    }

    List<Order> ordersOf(String owner) {
        List<Order> out = new ArrayList<>();
        for (Order o : state.orders) {
            if (o.owner.equals(owner)) {
                out.add(o);
            }
        }
        out.sort(Comparator.comparingLong((Order o) -> o.created).thenComparingLong(o -> o.id));
        return out;
    }

    Order byId(long id) {
        for (Order o : state.orders) {
            if (o.id == id) {
                return o;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- history

    private History history(String product) {
        return state.history.computeIfAbsent(product, k -> new History());
    }

    private void record(String product, long priceTicks, int qty, long now) {
        History h = history(product);
        h.lastTicks = priceTicks;
        long hour = now / 3_600_000L;
        Bucket b = h.buckets.isEmpty() ? null : h.buckets.get(h.buckets.size() - 1);
        if (b == null || b.hour != hour) {
            b = new Bucket();
            b.hour = hour;
            b.low = priceTicks;
            b.high = priceTicks;
            h.buckets.add(b);
            while (h.buckets.size() > Math.max(2, rules.historyHours())) {
                h.buckets.remove(0);
            }
        }
        b.volume += qty;
        b.sumCents += priceTicks * CENTS_PER_TICK * qty;
        b.high = Math.max(b.high, priceTicks);
        b.low = Math.min(b.low, priceTicks);
    }

    long lastPrice(String product) {
        History h = state.history.get(product);
        return h == null ? 0 : h.lastTicks;
    }

    /**
     * Reference price in ticks that anchors the price band and the menu suggestions: the 24h average, else the last
     * trade, else the NPC derived seed. 0 when nothing is known.
     */
    long reference(String product, long now) {
        long ref = average24h(product, now);
        if (ref == 0) {
            ref = lastPrice(product);
        }
        if (ref == 0) {
            ref = Math.max(0, seed.applyAsLong(product));
        }
        return ref;
    }

    /** Volume weighted average price (ticks) over the last 24 hours, 0 when nothing traded. */
    long average24h(String product, long now) {
        History h = state.history.get(product);
        if (h == null) {
            return 0;
        }
        long since = now / 3_600_000L - 24;
        long volume = 0;
        long cents = 0;
        for (Bucket b : h.buckets) {
            if (b.hour > since) {
                volume += b.volume;
                cents += b.sumCents;
            }
        }
        return volume == 0 ? 0 : cents / volume / CENTS_PER_TICK;
    }

    long volume24h(String product, long now) {
        History h = state.history.get(product);
        if (h == null) {
            return 0;
        }
        long since = now / 3_600_000L - 24;
        return h.buckets.stream().filter(b -> b.hour > since).mapToLong(b -> b.volume).sum();
    }

    /** Average price per hour bucket in ticks, oldest first (for a sparkline). */
    List<Long> sparkline(String product, int max) {
        History h = state.history.get(product);
        List<Long> out = new ArrayList<>();
        if (h != null) {
            for (Bucket b : h.buckets) {
                if (b.volume > 0) {
                    out.add(b.sumCents / b.volume / CENTS_PER_TICK);
                }
            }
        }
        return out.size() > max ? out.subList(out.size() - max, out.size()) : out;
    }

    // ---------------------------------------------------------------- validation

    private Check checkOrder(String owner, String product, boolean buy, long priceTicks, int amount, long now) {
        if (amount < 1 || amount > rules.maxAmount()) {
            return new Check("Amount must be between 1 and " + rules.maxAmount() + ".");
        }
        if (priceTicks < 1 || priceTicks > rules.maxPriceTicks()) {
            return new Check("Price must be between 0.1 and " + Money.plain(rules.maxPriceTicks() * CENTS_PER_TICK)
                + " coins per item.");
        }
        long count = state.orders.stream().filter(o -> o.owner.equals(owner)).count();
        if (count >= rules.maxOrders()) {
            return new Check("You can only have " + rules.maxOrders()
                + " orders at once (including ones waiting to be claimed). Claim or cancel some first.");
        }
        if (rules.band() > 1) {
            long ref = reference(product, now);
            if (ref > 0 && (priceTicks > ref * rules.band() || priceTicks * rules.band() < ref)) {
                return new Check("That price is far from the market (" + Money.plain(ref * CENTS_PER_TICK)
                    + " coins). Orders must stay within a factor of " + rules.band() + ".");
            }
        }
        // An order that would trade with the player's own order is refused (no self trading, no wash trades).
        for (Order o : (buy ? asks(product) : bids(product))) {
            if (o.owner.equals(owner) && (buy ? o.price <= priceTicks : o.price >= priceTicks)) {
                return new Check("That would trade with your own " + (buy ? "sell" : "buy")
                    + " order at " + Money.plain(o.price * CENTS_PER_TICK) + ". Cancel it first.");
            }
        }
        return Check.OK;
    }

    Check checkBuyOrder(String owner, String product, long priceTicks, int amount, long now) {
        return checkOrder(owner, product, true, priceTicks, amount, now);
    }

    Check checkSellOrder(String owner, String product, long priceTicks, int amount, long now) {
        return checkOrder(owner, product, false, priceTicks, amount, now);
    }

    // ---------------------------------------------------------------- instant trades

    private long tax(long grossCents) {
        return Money.basisPoints(grossCents, rules.taxBp());
    }

    /** What an instant buy would cost: walks the sell orders (cheapest first) skipping the buyer's own. */
    Instant quoteBuy(String owner, String product, int amount) {
        if (amount < 1 || amount > rules.maxAmount()) {
            return Instant.fail("Amount must be between 1 and " + rules.maxAmount() + ".");
        }
        List<Fill> fills = new ArrayList<>();
        int left = amount;
        long cents = 0;
        for (Order o : asks(product)) {
            if (left == 0) {
                break;
            }
            if (o.owner.equals(owner)) {
                continue;
            }
            int q = Math.min(left, o.remaining);
            long gross = o.price * CENTS_PER_TICK * q;
            fills.add(new Fill(o, q, o.price, gross, tax(gross)));
            cents += gross;
            left -= q;
        }
        if (left > 0) {
            return Instant.fail(amount - left == 0 ? "Nobody is selling this right now."
                : "Only " + (amount - left) + " are for sale right now.");
        }
        return new Instant(Check.OK, amount, cents, fills);
    }

    /** What an instant sell would pay (net of tax): walks the buy orders (highest first) skipping the seller's own. */
    Instant quoteSell(String owner, String product, int amount) {
        return quoteSell(owner, product, amount, false);
    }

    /** Units other players' buy orders could take right now (the seller's own orders are skipped). */
    int sellableDepth(String owner, String product) {
        int n = 0;
        for (Order o : bids(product)) {
            if (!o.owner.equals(owner)) {
                n += o.remaining;
            }
        }
        return n;
    }

    /**
     * Like {@link #quoteSell(String, String, int)}; with {@code partial} a short book is not an error: the quote covers
     * as many items as the buy orders can take (it only fails when none can be sold).
     */
    Instant quoteSell(String owner, String product, int amount, boolean partial) {
        if (amount < 1 || amount > rules.maxAmount()) {
            return Instant.fail("Amount must be between 1 and " + rules.maxAmount() + ".");
        }
        List<Fill> fills = new ArrayList<>();
        int left = amount;
        long net = 0;
        for (Order o : bids(product)) {
            if (left == 0) {
                break;
            }
            if (o.owner.equals(owner)) {
                continue;
            }
            int q = Math.min(left, o.remaining);
            long gross = o.price * CENTS_PER_TICK * q;
            long tax = tax(gross);
            fills.add(new Fill(o, q, o.price, gross, tax));
            net += gross - tax;
            left -= q;
        }
        if (left > 0 && (!partial || left == amount)) {
            return Instant.fail(amount - left == 0 ? "Nobody is buying this right now."
                : "Only " + (amount - left) + " can be sold right now.");
        }
        return new Instant(Check.OK, amount - left, net, fills);
    }

    /** Executes a quote made on this very tick (no mutation happened since). The taker's coins/items are the caller's. */
    void commitBuy(Instant quote, String product, long now) {
        for (Fill f : quote.fills()) {
            Order sell = f.resting();
            sell.remaining -= f.qty();
            sell.claimCents += f.grossCents() - f.taxCents();
            state.taxBurnedCents += f.taxCents();
            if (sell.remaining == 0) {
                unbook(sell);
            }
            record(product, f.priceTicks(), f.qty(), now);
        }
    }

    void commitSell(Instant quote, String product, long now) {
        for (Fill f : quote.fills()) {
            Order bid = f.resting();
            bid.remaining -= f.qty();
            bid.claimItems += f.qty();
            state.taxBurnedCents += f.taxCents();
            if (bid.remaining == 0) {
                unbook(bid);
            }
            record(product, f.priceTicks(), f.qty(), now);
        }
    }

    // ---------------------------------------------------------------- orders

    /** Places a buy order whose cost ({@code price * 10 * amount} cents) the caller already took, then matches it. */
    Placed placeBuy(String owner, String ownerName, String product, long priceTicks, int amount, long now) {
        Check check = checkBuyOrder(owner, product, priceTicks, amount, now);
        if (!check.ok()) {
            return Placed.fail(check.error());
        }
        Order order = newOrder(owner, ownerName, product, true, priceTicks, amount, now);
        List<Order> book = asks.getOrDefault(product, List.of());
        int filled = 0;
        List<Fill> fills = new ArrayList<>();
        while (order.remaining > 0 && !book.isEmpty() && book.get(0).price <= priceTicks) {
            Order ask = book.get(0);
            int q = Math.min(order.remaining, ask.remaining);
            long gross = ask.price * CENTS_PER_TICK * q;
            long tax = tax(gross);
            fills.add(new Fill(ask, q, ask.price, gross, tax));
            ask.remaining -= q;
            ask.claimCents += gross - tax;
            state.taxBurnedCents += tax;
            order.remaining -= q;
            order.claimItems += q;
            order.claimCents += (priceTicks - ask.price) * CENTS_PER_TICK * q; // price improvement goes back
            filled += q;
            record(product, ask.price, q, now);
            if (ask.remaining == 0) {
                book.remove(0);
            }
        }
        state.orders.add(order);
        if (order.remaining > 0) {
            insert(order);
        }
        return new Placed(Check.OK, order, filled, fills);
    }

    /** Places a sell order whose items the caller already removed, then matches it. */
    Placed placeSell(String owner, String ownerName, String product, long priceTicks, int amount, long now) {
        Check check = checkSellOrder(owner, product, priceTicks, amount, now);
        if (!check.ok()) {
            return Placed.fail(check.error());
        }
        Order order = newOrder(owner, ownerName, product, false, priceTicks, amount, now);
        List<Order> book = bids.getOrDefault(product, List.of());
        int filled = 0;
        List<Fill> fills = new ArrayList<>();
        while (order.remaining > 0 && !book.isEmpty() && book.get(0).price >= priceTicks) {
            Order bid = book.get(0);
            int q = Math.min(order.remaining, bid.remaining);
            long gross = bid.price * CENTS_PER_TICK * q;
            long tax = tax(gross);
            fills.add(new Fill(bid, q, bid.price, gross, tax));
            bid.remaining -= q;
            bid.claimItems += q;
            state.taxBurnedCents += tax;
            order.remaining -= q;
            order.claimCents += gross - tax;
            filled += q;
            record(product, bid.price, q, now);
            if (bid.remaining == 0) {
                book.remove(0);
            }
        }
        state.orders.add(order);
        if (order.remaining > 0) {
            insert(order);
        }
        return new Placed(Check.OK, order, filled, fills);
    }

    private Order newOrder(String owner, String ownerName, String product, boolean buy, long price, int amount, long now) {
        Order o = new Order();
        o.id = state.nextId++;
        o.seq = state.nextSeq++;
        o.owner = owner;
        o.ownerName = ownerName;
        o.product = product;
        o.buy = buy;
        o.price = price;
        o.total = amount;
        o.remaining = amount;
        o.created = now;
        return o;
    }

    /** Cancels the live part of an order: the escrow moves into the order's claim. False when not cancellable. */
    boolean cancel(long id, String owner) {
        Order o = byId(id);
        if (o == null || !o.owner.equals(owner) || !o.live()) {
            return false;
        }
        unbook(o);
        if (o.buy) {
            o.claimCents += o.remaining * o.price * CENTS_PER_TICK;
        } else {
            o.claimItems += o.remaining;
        }
        o.remaining = 0;
        o.closed = true;
        prune(o);
        return true;
    }

    private void prune(Order o) {
        if (!o.live() && !o.hasClaim()) {
            state.orders.remove(o);
        }
    }

    // ---------------------------------------------------------------- claims

    /** Everything the owner could collect now. */
    Claim claimable(String owner) {
        long cents = 0;
        Map<String, Integer> items = new LinkedHashMap<>();
        for (Order o : state.orders) {
            if (o.owner.equals(owner) && o.hasClaim()) {
                cents += o.claimCents;
                if (o.claimItems > 0) {
                    items.merge(o.product, o.claimItems, Integer::sum);
                }
            }
        }
        return new Claim(cents, items);
    }

    /** Claimable value of one order. */
    Claim claimable(Order o) {
        return new Claim(o.claimCents, o.claimItems > 0 ? Map.of(o.product, o.claimItems) : Map.of());
    }

    /**
     * Removes exactly {@code cents} and {@code items} from the owner's claims (oldest orders first), pruning finished
     * orders. The amounts must not exceed {@link #claimable}; returns false and changes nothing otherwise.
     */
    boolean takeClaim(String owner, long cents, Map<String, Integer> items) {
        Claim have = claimable(owner);
        if (cents > have.cents()) {
            return false;
        }
        for (var e : items.entrySet()) {
            if (e.getValue() > have.items().getOrDefault(e.getKey(), 0)) {
                return false;
            }
        }
        long centsLeft = cents;
        Map<String, Integer> itemsLeft = new HashMap<>(items);
        for (Order o : new ArrayList<>(ordersOf(owner))) {
            long c = Math.min(centsLeft, o.claimCents);
            o.claimCents -= c;
            centsLeft -= c;
            int left = itemsLeft.getOrDefault(o.product, 0);
            int i = Math.min(left, o.claimItems);
            o.claimItems -= i;
            if (left > 0) {
                itemsLeft.put(o.product, left - i);
            }
            prune(o);
        }
        return true;
    }

    /** Gives a failed or interrupted claim back to its owner as a settled order. */
    void restoreClaim(String owner, String ownerName, long cents, Map<String, Integer> items, long now) {
        if (cents > 0) {
            Order o = newOrder(owner, ownerName, "coins", true, 1, 1, now);
            o.remaining = 0;
            o.closed = true;
            o.claimCents = cents;
            state.orders.add(o);
        }
        for (var e : items.entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            Order o = newOrder(owner, ownerName, e.getKey(), false, 1, 1, now);
            o.remaining = 0;
            o.closed = true;
            o.claimItems = e.getValue();
            state.orders.add(o);
        }
    }

    // ---------------------------------------------------------------- audit

    /** Coins held in escrow by live buy orders, plus unclaimed coins. */
    long escrowedCents() {
        long sum = 0;
        for (Order o : state.orders) {
            if (o.buy && o.live()) {
                sum += o.remaining * o.price * CENTS_PER_TICK;
            }
            sum += o.claimCents;
        }
        return sum;
    }

    /** Checks structural invariants; returns problems (empty when sound). */
    List<String> verify() {
        List<String> problems = new ArrayList<>();
        for (Order o : state.orders) {
            if (o.remaining < 0 || o.remaining > o.total || o.claimCents < 0 || o.claimItems < 0) {
                problems.add("order " + o.id + " has invalid amounts");
            }
            if (o.live()) {
                List<Order> book = (o.buy ? bids : asks).get(o.product);
                if (book == null || !book.contains(o)) {
                    problems.add("live order " + o.id + " missing from its book");
                }
            }
        }
        for (var side : List.of(asks, bids)) {
            for (var e : side.entrySet()) {
                List<Order> l = e.getValue();
                for (int i = 1; i < l.size(); i++) {
                    Comparator<Order> cmp = l.get(i).buy ? BIDS : ASKS;
                    if (cmp.compare(l.get(i - 1), l.get(i)) > 0) {
                        problems.add("book " + e.getKey() + " is not sorted");
                        break;
                    }
                }
            }
        }
        for (String product : asks.keySet()) {
            long a = bestAsk(product);
            long b = bestBid(product);
            if (a > 0 && b > 0 && a <= b) {
                problems.add("crossed book for " + product + ": ask " + a + " <= bid " + b);
            }
        }
        return problems;
    }
}
