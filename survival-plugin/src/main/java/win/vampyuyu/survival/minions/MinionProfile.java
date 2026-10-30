package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Per player data, stored in the profile section {@code minions}. */
public final class MinionProfile {
    /** Different minions (type and tier, as "cobblestone:3") the player crafted or upgraded to: unlocks slots. */
    public Set<String> crafted = new TreeSet<>();

    /** Items owed to the player (minions removed while the owner was away, island resets): delivered when they fit. */
    public List<Owed> owed = new ArrayList<>();

    /** One owed stack. */
    public static final class Owed {
        /** Item key, see {@link ItemKey}. */
        public String key = "";
        public long amount;
        public String reason = "";

        public Owed() {}

        public Owed(String key, long amount, String reason) {
            this.key = key;
            this.amount = amount;
            this.reason = reason;
        }
    }
}
