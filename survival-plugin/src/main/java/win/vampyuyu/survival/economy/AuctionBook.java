package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auction house state and rules: pure logic, no Bukkit, money in cents, single threaded (main thread).
 * Escrow model: a bid's coins are held by the auction until it is outbid (the refund becomes a claim) or it sells
 * (the seller's net becomes a claim); a listed item is held as the serialised stack until claimed by the winner or
 * returned to the seller. Nothing is ever paid out directly; every payout is a claim collected with the two phase
 * protocol of {@link DurableFile}.
 */
final class AuctionBook {
    /** Lifecycle of a listing. */
    static final String ACTIVE = "ACTIVE";
    static final String SOLD = "SOLD";
    static final String EXPIRED = "EXPIRED";
    static final String CANCELLED = "CANCELLED";

    /** One listing. Persisted as is. */
    static final class Auction {
        long id;
        String seller = "";
        String sellerName = "";
        String item = "";
        String itemKey = "";
        String itemName = "";
        String category = "MISC";
        int amount = 1;
        boolean bin;
        long startCents;
        long bidCents;
        String bidder = "";
        String bidderName = "";
        int bids;
        long created;
        long endsAt;
        int extensions;
        String state = ACTIVE;
        long finalCents;
        long taxCents;
        boolean sellerClaimed;
        boolean buyerClaimed;
        /** Decoded stack cache, never persisted. */
        transient org.bukkit.inventory.ItemStack cache;

        boolean active() { return ACTIVE.equals(state); }

        /** Net amount the seller gets from a sold listing. */
        long sellerNet() { return finalCents - taxCents; }
    }

    /** One finished sale for the history. */
    static final class Sale {
        long t;
        String itemKey = "";
        String name = "";
        int amount = 1;
        long cents;
        boolean bin;
    }

    /** The persisted state. */
    static final class State {
        long nextId = 1;
        List<Auction> auctions = new ArrayList<>();
        /** Owner uuid to cents owed (outbid refunds). */
        Map<String, Long> refunds = new HashMap<>();
        List<Sale> sales = new ArrayList<>();
        long taxBurnedCents;
        long feesBurnedCents;
    }

    /** Tunables. */
    record Rules(int maxListings, long minPriceCents, long maxPriceCents, int saleTaxBp, int incrementPercent,
            long incrementMinCents, long snipeWindowMs, long snipeExtendMs, int maxExtensions, int historySize) {}

    /** Refusal reason or null. */
    record Check(String error) {
        static final Check OK = new Check(null);

        boolean ok() { return error == null; }
    }

    /** What an owner can collect. */
    record Claim(long cents, List<Auction> items) {
        boolean empty() { return cents <= 0 && items.isEmpty(); }
    }

    private final State state;
    private Rules rules;

    AuctionBook(State state, Rules rules) {
        this.state = state;
        this.rules = rules;
    }

    State state() { return state; }

    Rules rules() { return rules; }

    void rules(Rules rules) { this.rules = rules; }

    Auction byId(long id) {
        for (Auction a : state.auctions) {
            if (a.id == id) {
                return a;
            }
        }
        return null;
    }

    List<Auction> active(long now) {
        List<Auction> out = new ArrayList<>();
        for (Auction a : state.auctions) {
            if (a.active() && a.endsAt > now) {
                out.add(a);
            }
        }
        return out;
    }

    int activeCount(String seller) {
        return (int) state.auctions.stream().filter(a -> a.active() && a.seller.equals(seller)).count();
    }

    /** Admin reset: forgets every auction a player sold or bid on, and the refunds waiting for them. */
    void wipe(String owner) {
        state.auctions.removeIf(a -> a.seller.equals(owner) || a.bidder.equals(owner));
        state.refunds.remove(owner);
    }

    List<Auction> ownedBy(String owner) {
        List<Auction> out = new ArrayList<>();
        for (Auction a : state.auctions) {
            if (a.seller.equals(owner) || (a.bidder.equals(owner) && (a.active() || SOLD.equals(a.state)))) {
                out.add(a);
            }
        }
        out.sort(Comparator.comparingLong((Auction a) -> a.created).reversed());
        return out;
    }

    // ---------------------------------------------------------------- listing

    Check checkList(String seller, long priceCents) {
        if (priceCents < rules.minPriceCents() || priceCents > rules.maxPriceCents()) {
            return new Check("The price must be between " + Money.coins(rules.minPriceCents()) + " and "
                + Money.coins(rules.maxPriceCents()) + ".");
        }
        if (activeCount(seller) >= rules.maxListings()) {
            return new Check("You already have " + rules.maxListings() + " active auctions.");
        }
        return Check.OK;
    }

    /** Creates a listing (the item and fee are the caller's business). Throws nothing; check first with {@link #checkList}. */
    Auction list(String seller, String sellerName, String item, String itemKey, String itemName, String category,
            int amount, boolean bin, long priceCents, long durationMs, long now) {
        Auction a = new Auction();
        a.id = state.nextId++;
        a.seller = seller;
        a.sellerName = sellerName;
        a.item = item;
        a.itemKey = itemKey;
        a.itemName = itemName;
        a.category = category;
        a.amount = amount;
        a.bin = bin;
        a.startCents = priceCents;
        a.created = now;
        a.endsAt = now + durationMs;
        state.auctions.add(a);
        return a;
    }

    // ---------------------------------------------------------------- bidding

    /** The lowest acceptable next bid. */
    long minBid(Auction a) {
        if (a.bids == 0) {
            return a.startCents;
        }
        long step = Math.max(rules.incrementMinCents(), Money.basisPoints(a.bidCents, rules.incrementPercent() * 100));
        return a.bidCents + step;
    }

    Check checkBid(Auction a, String bidder, long cents, long now) {
        if (a == null || !a.active() || a.endsAt <= now) {
            return new Check("That auction has ended.");
        }
        if (a.bin) {
            return new Check("That is a Buy It Now listing, it cannot be bid on.");
        }
        if (a.seller.equals(bidder)) {
            return new Check("You cannot bid on your own auction.");
        }
        if (a.bidder.equals(bidder)) {
            return new Check("You are already the highest bidder.");
        }
        if (cents < minBid(a)) {
            return new Check("The minimum bid is " + Money.coins(minBid(a)) + ".");
        }
        if (cents > rules.maxPriceCents()) {
            return new Check("That bid is too high.");
        }
        return Check.OK;
    }

    /**
     * Places a bid whose coins the caller already took. The previous bidder's coins become a refund claim. A bid in
     * the final seconds extends the auction (anti snipe), at most {@code maxExtensions} times.
     *
     * @return the previous top bidder's uuid (to notify) or "" when there was none
     */
    String bid(Auction a, String bidder, String bidderName, long cents, long now) {
        String previous = a.bidder;
        if (a.bids > 0 && !a.bidder.isEmpty()) {
            state.refunds.merge(a.bidder, a.bidCents, Long::sum);
        }
        a.bidder = bidder;
        a.bidderName = bidderName;
        a.bidCents = cents;
        a.bids++;
        if (a.endsAt - now <= rules.snipeWindowMs() && a.extensions < rules.maxExtensions()) {
            a.endsAt = now + rules.snipeExtendMs();
            a.extensions++;
        }
        return previous;
    }

    Check checkBuyNow(Auction a, String buyer, long now) {
        if (a == null || !a.active() || a.endsAt <= now) {
            return new Check("That listing has ended.");
        }
        if (!a.bin) {
            return new Check("That is an auction, place a bid instead.");
        }
        if (a.seller.equals(buyer)) {
            return new Check("You cannot buy your own listing. Cancel it to get the item back.");
        }
        return Check.OK;
    }

    /** Completes a Buy It Now whose price the caller already took. */
    void buyNow(Auction a, String buyer, String buyerName, long now) {
        a.bidder = buyer;
        a.bidderName = buyerName;
        a.bidCents = a.startCents;
        a.bids = 1;
        sell(a, now);
    }

    private void sell(Auction a, long now) {
        a.state = SOLD;
        a.finalCents = a.bidCents;
        a.taxCents = Money.basisPoints(a.finalCents, rules.saleTaxBp());
        state.taxBurnedCents += a.taxCents;
        Sale s = new Sale();
        s.t = now;
        s.itemKey = a.itemKey;
        s.name = a.itemName;
        s.amount = a.amount;
        s.cents = a.finalCents;
        s.bin = a.bin;
        state.sales.add(s);
        while (state.sales.size() > rules.historySize()) {
            state.sales.remove(0);
        }
    }

    /** Ends every listing whose time is up. Returns the listings that changed. */
    List<Auction> settleDue(long now) {
        List<Auction> changed = new ArrayList<>();
        for (Auction a : state.auctions) {
            if (a.active() && a.endsAt <= now) {
                if (a.bids > 0 && !a.bidder.isEmpty()) {
                    sell(a, now);
                } else {
                    a.state = EXPIRED;
                }
                changed.add(a);
            }
        }
        return changed;
    }

    /** Cancels an unbid listing: the item goes back to a claim. */
    Check cancel(Auction a, String seller, long now) {
        if (a == null || !a.active() || a.endsAt <= now) {
            return new Check("That auction is not active.");
        }
        if (!a.seller.equals(seller)) {
            return new Check("That is not your auction.");
        }
        if (a.bids > 0) {
            return new Check("It already has bids, it cannot be cancelled.");
        }
        a.state = CANCELLED;
        return Check.OK;
    }

    // ---------------------------------------------------------------- claims

    /** Everything an owner can collect: sold proceeds and refunds as coins, won and returned items. */
    Claim claimable(String owner) {
        long cents = state.refunds.getOrDefault(owner, 0L);
        List<Auction> items = new ArrayList<>();
        for (Auction a : state.auctions) {
            if (SOLD.equals(a.state)) {
                if (a.seller.equals(owner) && !a.sellerClaimed) {
                    cents += a.sellerNet();
                }
                if (a.bidder.equals(owner) && !a.buyerClaimed) {
                    items.add(a);
                }
            } else if ((EXPIRED.equals(a.state) || CANCELLED.equals(a.state)) && a.seller.equals(owner)
                    && !a.sellerClaimed) {
                items.add(a);
            }
        }
        return new Claim(cents, items);
    }

    /** What a claim took, enough to undo it exactly. */
    record Taken(String owner, long cents, long refundCents, List<Auction> proceeds, List<Auction> items) {}

    /**
     * Collects the listed items and, when {@code coins} is set, ALL of the owner's coins (refunds and sold
     * proceeds). Returns null (nothing changed) when an item is not claimable by {@code owner} any more.
     */
    Taken takeClaim(String owner, List<Auction> items, boolean coins) {
        Claim have = claimable(owner);
        for (Auction a : items) {
            if (!have.items().contains(a)) {
                return null;
            }
        }
        long refund = 0;
        List<Auction> proceeds = new ArrayList<>();
        long cents = 0;
        if (coins) {
            Long r = state.refunds.remove(owner);
            refund = r == null ? 0 : r;
            cents = refund;
            for (Auction a : state.auctions) {
                if (SOLD.equals(a.state) && a.seller.equals(owner) && !a.sellerClaimed) {
                    a.sellerClaimed = true;
                    proceeds.add(a);
                    cents += a.sellerNet();
                }
            }
        }
        for (Auction a : items) {
            if (SOLD.equals(a.state) && a.bidder.equals(owner)) {
                a.buyerClaimed = true;
            } else {
                a.sellerClaimed = true;
            }
        }
        prune();
        return new Taken(owner, cents, refund, proceeds, new ArrayList<>(items));
    }

    /** Puts a failed or interrupted claim back exactly as it was. */
    void restore(Taken t) {
        if (t.refundCents() > 0) {
            state.refunds.merge(t.owner(), t.refundCents(), Long::sum);
        }
        for (Auction a : t.proceeds()) {
            a.sellerClaimed = false;
            readd(a);
        }
        for (Auction a : t.items()) {
            if (SOLD.equals(a.state) && a.bidder.equals(t.owner())) {
                a.buyerClaimed = false;
            } else {
                a.sellerClaimed = false;
            }
            readd(a);
        }
    }

    private void readd(Auction a) {
        if (!state.auctions.contains(a)) {
            state.auctions.add(a);
        }
    }

    private void prune() {
        state.auctions.removeIf(a -> switch (a.state) {
            case SOLD -> a.sellerClaimed && a.buyerClaimed;
            case EXPIRED, CANCELLED -> a.sellerClaimed;
            default -> false;
        });
    }

    // ---------------------------------------------------------------- audit

    /** Coins held by the house: active bids, unclaimed seller proceeds and unclaimed refunds. */
    long escrowedCents() {
        long sum = state.refunds.values().stream().mapToLong(Long::longValue).sum();
        for (Auction a : state.auctions) {
            if (a.active() && a.bids > 0) {
                sum += a.bidCents;
            } else if (SOLD.equals(a.state) && !a.sellerClaimed) {
                sum += a.sellerNet();
            }
        }
        return sum;
    }

    List<String> verify() {
        List<String> problems = new ArrayList<>();
        for (Auction a : state.auctions) {
            if (a.bidCents < 0 || a.startCents <= 0) {
                problems.add("auction " + a.id + " has invalid prices");
            }
            if (a.active() && a.bids > 0 && a.bidder.isEmpty()) {
                problems.add("auction " + a.id + " has bids but no bidder");
            }
            if (SOLD.equals(a.state) && (a.bidder.isEmpty() || a.finalCents <= 0)) {
                problems.add("sold auction " + a.id + " has no buyer or price");
            }
        }
        return problems;
    }
}
