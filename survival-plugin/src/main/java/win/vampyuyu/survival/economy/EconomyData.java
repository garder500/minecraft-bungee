package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's {@code "economy"} profile section. Plain Gson class: every field has a default so old documents load.
 * All amounts are in cents (1 coin = 100 cents).
 */
public final class EconomyData {
    /** One ledger line. {@code after} is the purse balance after the change, which makes the chain auditable. */
    public static final class Entry {
        public long t;
        public long delta;
        public long after;
        public String reason = "";

        public Entry() {}

        public Entry(long t, long delta, long after, String reason) {
            this.t = t;
            this.delta = delta;
            this.after = after;
            this.reason = reason;
        }
    }

    /** One bank history line. {@code after} is the bank balance after it. */
    public static final class BankEntry {
        public long t;
        public String kind = "";
        public long delta;
        public long after;

        public BankEntry() {}

        public BankEntry(long t, String kind, long delta, long after) {
            this.t = t;
            this.kind = kind;
            this.delta = delta;
            this.after = after;
        }
    }

    /** Purse. */
    public long cents;
    /** Bank balance. */
    public long bankCents;
    /** Index into the configured bank tiers. */
    public int bankTier;
    /** Start of the next un-paid interest period (epoch millis, 0 = no clock running). */
    public long bankInterestAt;
    /** Lifetime interest received. */
    public long bankInterestTotal;
    /** Recent purse changes, oldest first (ring buffer, trimmed by the service). */
    public List<Entry> ledger = new ArrayList<>();
    /** Recent bank changes, oldest first. */
    public List<BankEntry> bankHistory = new ArrayList<>();
    /** Pay limits: UTC day number and amount sent that day. */
    public long paySentDay;
    public long paySentToday;
    public long lastPayAt;
    /** Lifetime counters. */
    public long totalEarned;
    public long totalSpent;
    public long coinsLostToDeath;

    public void appendLedger(Entry entry, int max) {
        ledger.add(entry);
        while (ledger.size() > max) {
            ledger.remove(0);
        }
    }

    public void appendBank(BankEntry entry, int max) {
        bankHistory.add(entry);
        while (bankHistory.size() > max) {
            bankHistory.remove(0);
        }
    }
}
