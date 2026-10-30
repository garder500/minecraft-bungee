package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide faucet and sink table: how many coins entered and left purses and banks, grouped by the first two
 * segments of the ledger reason ({@code shop:sell}, {@code bank:interest}, {@code admin:give}...). Thread safe (the
 * economy publishes from any thread). Persisted next to the sell pressure. It answers "where do the coins come
 * from" for tuning; movements between a player and a market (escrow, claims, payments) are listed as transfers
 * because they cancel out over time.
 */
final class Flows {
    /** Persisted form. */
    static final class State {
        long since;
        Map<String, long[]> flows = new HashMap<>();
    }

    /** One row of the table. */
    record Row(String group, long faucet, long sink, boolean transfer) {
        long net() { return faucet - sink; }
    }

    private final ConcurrentHashMap<String, long[]> flows = new ConcurrentHashMap<>();
    private volatile long since = System.currentTimeMillis();

    /** The group of a ledger reason: its first two colon separated segments. */
    static String group(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String[] parts = reason.split(":");
        return parts.length == 1 ? parts[0] : parts[0] + ":" + parts[1];
    }

    /** True for groups that only move coins between a purse and a market, a bank account or another player. */
    static boolean isTransfer(String group) {
        return group.startsWith("pay:") || group.equals("bank:deposit") || group.equals("bank:withdraw")
            || group.startsWith("bazaar:") || group.equals("auction:bid") || group.equals("auction:buy")
            || group.equals("auction:claim");
    }

    /** Records a purse change (positive = coins created for the player, negative = coins taken). */
    void record(String reason, long delta) {
        if (delta == 0) {
            return;
        }
        flows.compute(group(reason), (k, v) -> {
            long[] row = v == null ? new long[2] : v;
            if (delta > 0) {
                row[0] += delta;
            } else {
                row[1] -= delta;
            }
            return row;
        });
    }

    long since() { return since; }

    /** Rows sorted by size, largest movement first. */
    List<Row> rows() {
        List<Row> out = new ArrayList<>();
        flows.forEach((k, v) -> {
            long[] copy = v.clone();
            out.add(new Row(k, copy[0], copy[1], isTransfer(k)));
        });
        out.sort((a, b) -> Long.compare(b.faucet() + b.sink(), a.faucet() + a.sink()));
        return out;
    }

    /** Coins created outside transfers (shop sales, interest, admin gifts, rewards of other features). */
    long minted() {
        return rows().stream().filter(r -> !r.transfer()).mapToLong(Row::faucet).sum();
    }

    /** Coins destroyed outside transfers (shop purchases, fees, death, upgrades). */
    long burned() {
        return rows().stream().filter(r -> !r.transfer()).mapToLong(Row::sink).sum();
    }

    State export() {
        State s = new State();
        s.since = since;
        flows.forEach((k, v) -> s.flows.put(k, v.clone()));
        return s;
    }

    void load(State s) {
        flows.clear();
        if (s == null) {
            return;
        }
        if (s.since > 0) {
            since = s.since;
        }
        if (s.flows != null) {
            s.flows.forEach((k, v) -> {
                if (v != null && v.length == 2 && v[0] >= 0 && v[1] >= 0) {
                    flows.put(k, v.clone());
                }
            });
        }
    }
}
