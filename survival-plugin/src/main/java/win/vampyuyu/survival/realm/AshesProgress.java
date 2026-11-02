package win.vampyuyu.survival.realm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each player did in the Cracked Ashes (GDD 13.3: exploration, progression, world and narrative quests). Pure
 * data saved in {@code realms/ashes.json}; every reward is paid once and the methods return the coins to pay (0 when
 * nothing new happened). Not thread safe: main thread only.
 */
public final class AshesProgress {
    public static final String FILE = "realms/ashes.json";
    /** Progression quest: Cinderheart to gather. */
    public static final int GATHER_GOAL = 12;
    /** World quest: Cinderheart a vent takes to be stabilised. */
    public static final int VENT_COST = 3;
    public static final int DISCOVER_COINS = 60;
    public static final int EXPLORE_BONUS = 400;
    public static final int GATHER_COINS = 300;
    public static final int VENT_COINS = 150;
    public static final int FINISH_COINS = 600;

    /** One player. */
    public static final class Entry {
        public Set<String> discovered = new LinkedHashSet<>();
        public int harvested;
        /** Cinderheart taken from the player's inventory by vents (so the quest cannot be paid twice). */
        public Set<String> vents = new LinkedHashSet<>();
        public Set<String> claimed = new LinkedHashSet<>();
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
        return players.getOrDefault(player, new Entry());
    }

    /** Coins for discovering a landmark (including the bonus for the last one); -1 when it was already found. */
    public int discover(UUID player, String landmark) {
        Entry entry = of(player);
        if (!entry.discovered.add(landmark)) {
            return -1;
        }
        int coins = DISCOVER_COINS;
        if (entry.discovered.size() >= AshesPlan.LANDMARKS.size() && entry.claimed.add("explore")) {
            coins += EXPLORE_BONUS;
        }
        return coins;
    }

    public boolean discovered(UUID player, String landmark) {
        return peek(player).discovered.contains(landmark);
    }

    /** Counts a harvested Cinderheart; returns the coins of the progression quest when this one completes it. */
    public int harvest(UUID player) {
        Entry entry = of(player);
        entry.harvested++;
        if (entry.harvested >= GATHER_GOAL && entry.claimed.add("gather")) {
            return GATHER_COINS;
        }
        return 0;
    }

    public int harvested(UUID player) {
        return peek(player).harvested;
    }

    /** Marks a vent stabilised; returns the coins, or -1 when it already was. */
    public int stabilise(UUID player, String vent) {
        Entry entry = of(player);
        if (!entry.vents.add(vent)) {
            return -1;
        }
        int coins = VENT_COINS;
        if (entry.vents.size() >= AshesPlan.VENTS.size() && entry.claimed.add("vents")) {
            coins += FINISH_COINS;
        }
        return coins;
    }

    public boolean stabilised(UUID player, String vent) {
        return peek(player).vents.contains(vent);
    }

    /** Ashen Attunement: all vents stabilised. Adds {@link HeatMath#ATTUNEMENT} resistance. */
    public boolean attuned(UUID player) {
        return peek(player).vents.size() >= AshesPlan.VENTS.size();
    }

    public boolean exploreDone(UUID player) {
        return peek(player).discovered.size() >= AshesPlan.LANDMARKS.size();
    }

    /** Lore fragments (narrative quest) the player unlocked, in landmark order. */
    public List<AshesPlan.Landmark> fragments(UUID player) {
        List<AshesPlan.Landmark> list = new ArrayList<>();
        for (AshesPlan.Landmark mark : AshesPlan.LANDMARKS) {
            if (discovered(player, mark.id())) {
                list.add(mark);
            }
        }
        return list;
    }

    public void reset(UUID player) {
        players.remove(player);
    }

    public Doc toDoc() {
        Doc doc = new Doc();
        players.forEach((id, entry) -> doc.players.put(id.toString(), entry));
        return doc;
    }

    public static AshesProgress fromDoc(Doc doc) {
        AshesProgress progress = new AshesProgress();
        if (doc == null || doc.players == null) {
            return progress;
        }
        doc.players.forEach((key, entry) -> {
            try {
                if (entry != null) {
                    if (entry.discovered == null) {
                        entry.discovered = new LinkedHashSet<>();
                    }
                    if (entry.vents == null) {
                        entry.vents = new LinkedHashSet<>();
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
