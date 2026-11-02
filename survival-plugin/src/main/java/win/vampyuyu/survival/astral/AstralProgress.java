package win.vampyuyu.survival.astral;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each player did in the Astral Interiority (GDD 13.3: exploration, progression, world and narrative quests, and the
 * way to the Paragon). Pure data saved in {@code realms/astral.json}; every reward is paid once and the methods return the
 * coins to pay (0 when nothing new happened). Not thread safe: main thread only.
 */
public final class AstralProgress {
    public static final String FILE = "realms/astral.json";
    public static final int DISCOVER_COINS = 120;
    public static final int EXPLORE_BONUS = 1500;
    public static final int SYNAPSE_COINS = 60;
    public static final int CLIMB_BONUS = 900;
    public static final int ANCHOR_COINS = 200;
    public static final int ANCHOR_BONUS = 1500;
    public static final int SURVIVE_COINS = 4000;

    /** One player. */
    public static final class Entry {
        public Set<String> discovered = new LinkedHashSet<>();
        public Set<String> synapses = new LinkedHashSet<>();
        public Set<String> anchors = new LinkedHashSet<>();
        public Set<String> claimed = new LinkedHashSet<>();
        /** The player sat on the Throne: the Paragon is open to them. */
        public boolean survived;
    }

    /** The saved shape. */
    public static final class Doc {
        public int version = 1;
        public Map<String, Entry> players = new LinkedHashMap<>();
    }

    private final Map<UUID, Entry> players = new LinkedHashMap<>();

    public Entry of(UUID player) {
        return players.computeIfAbsent(player, k -> new Entry());
    }

    private Entry peek(UUID player) {
        Entry entry = players.get(player);
        return entry == null ? new Entry() : entry;
    }

    /** Coins for discovering a landmark (with the bonus for the last one); -1 when it was already found. */
    public int discover(UUID player, String landmark) {
        Entry entry = of(player);
        if (!entry.discovered.add(landmark)) {
            return -1;
        }
        int coins = DISCOVER_COINS;
        if (entry.discovered.size() >= AstralLayout.LANDMARKS.size() && entry.claimed.add("explore")) {
            coins += EXPLORE_BONUS;
        }
        return coins;
    }

    public boolean discovered(UUID player, String landmark) {
        return peek(player).discovered.contains(landmark);
    }

    public boolean exploreDone(UUID player) {
        return peek(player).discovered.size() >= AstralLayout.LANDMARKS.size();
    }

    /** Coins for firing a synapse (with the climb bonus for the last one); -1 when it already was fired. */
    public int fire(UUID player, String synapse) {
        Entry entry = of(player);
        if (!entry.synapses.add(synapse)) {
            return -1;
        }
        int coins = SYNAPSE_COINS;
        if (entry.synapses.size() >= AstralLayout.SYNAPSES.size() && entry.claimed.add("climb")) {
            coins += CLIMB_BONUS;
        }
        return coins;
    }

    public boolean climbDone(UUID player) {
        return peek(player).synapses.size() >= AstralLayout.SYNAPSES.size();
    }

    /** Coins for lighting an anchor (with the bonus for the last one); -1 when it was already lit. */
    public int light(UUID player, String anchor) {
        Entry entry = of(player);
        if (!entry.anchors.add(anchor)) {
            return -1;
        }
        int coins = ANCHOR_COINS;
        if (entry.anchors.size() >= AstralLayout.ANCHORS.size() && entry.claimed.add("anchors")) {
            coins += ANCHOR_BONUS;
        }
        return coins;
    }

    public boolean lit(UUID player, String anchor) {
        return peek(player).anchors.contains(anchor);
    }

    public boolean anchorsDone(UUID player) {
        return peek(player).anchors.size() >= AstralLayout.ANCHORS.size();
    }

    /** True when the Throne can be taken: the Crown reached and every anchor lit. */
    public boolean readyForThrone(UUID player) {
        return discovered(player, "crown") && anchorsDone(player);
    }

    /** Takes the Throne; returns the coins, or -1 when the player is not ready or already survived. */
    public int survive(UUID player) {
        if (!readyForThrone(player)) {
            return -1;
        }
        Entry entry = of(player);
        if (entry.survived) {
            return -1;
        }
        entry.survived = true;
        return entry.claimed.add("survive") ? SURVIVE_COINS : 0;
    }

    /** True when the player survived the Interiority: the Paragon trials are open to them. */
    public boolean survived(UUID player) {
        return peek(player).survived;
    }

    public void reset(UUID player) {
        players.remove(player);
    }

    public Doc toDoc() {
        Doc doc = new Doc();
        players.forEach((id, entry) -> doc.players.put(id.toString(), entry));
        return doc;
    }

    public static AstralProgress fromDoc(Doc doc) {
        AstralProgress progress = new AstralProgress();
        if (doc == null || doc.players == null) {
            return progress;
        }
        doc.players.forEach((key, entry) -> {
            try {
                if (entry != null) {
                    if (entry.discovered == null) {
                        entry.discovered = new LinkedHashSet<>();
                    }
                    if (entry.synapses == null) {
                        entry.synapses = new LinkedHashSet<>();
                    }
                    if (entry.anchors == null) {
                        entry.anchors = new LinkedHashSet<>();
                    }
                    if (entry.claimed == null) {
                        entry.claimed = new LinkedHashSet<>();
                    }
                    progress.players.put(UUID.fromString(key), entry);
                }
            } catch (IllegalArgumentException ignored) {
                // a key that is not a uuid: skip
            }
        });
        return progress;
    }
}
