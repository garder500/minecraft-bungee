package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import win.vampyuyu.survival.api.Realm;

/**
 * Which Sky Gates each player has lit. Pure data, no Bukkit: the document it saves ({@link Doc}) is what
 * {@code island/gates.json} holds. Every Gate starts dark. Not thread safe: use it from the main thread.
 */
public final class SkyGateState {
    public static final String FILE = "island/gates.json";

    /** The saved shape: player uuid to the ids of the Realms they lit. */
    public static final class Doc {
        public int version = 1;
        public Map<String, List<String>> players = new LinkedHashMap<>();
        /**
         * The lit Gates each player has already watched wake on the Quai. Null in a file written before the reveal
         * existed: every Gate lit then counts as seen, so nobody replays old Gates.
         */
        public Map<String, List<String>> seen;
    }

    private final Map<UUID, EnumSet<Realm>> lit = new HashMap<>();
    private final Map<UUID, EnumSet<Realm>> seen = new HashMap<>();

    public boolean isLit(UUID player, Realm realm) {
        EnumSet<Realm> set = lit.get(player);
        return set != null && set.contains(realm);
    }

    public Set<Realm> litRealms(UUID player) {
        EnumSet<Realm> set = lit.get(player);
        return set == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(set));
    }

    /** True when the state changed. */
    public boolean light(UUID player, Realm realm) {
        return lit.computeIfAbsent(player, k -> EnumSet.noneOf(Realm.class)).add(realm);
    }

    /**
     * The Gates this player has lit but not yet watched wake on the Quai, in Realm order (the order they light in the
     * reveal). A Gate put out is forgotten, so lighting it again replays it.
     */
    public List<Realm> unseen(UUID player) {
        EnumSet<Realm> set = lit.get(player);
        if (set == null) {
            return List.of();
        }
        EnumSet<Realm> done = seen.get(player);
        List<Realm> out = new ArrayList<>();
        for (Realm realm : set) {
            if (done == null || !done.contains(realm)) {
                out.add(realm);
            }
        }
        return out;
    }

    /** Records that the reveal of this Gate played. True when it was not marked yet. Ignored for a Gate that is not lit. */
    public boolean markSeen(UUID player, Realm realm) {
        return isLit(player, realm) && seen.computeIfAbsent(player, k -> EnumSet.noneOf(Realm.class)).add(realm);
    }

    /** The Gates shown lit to this player right now: lit and already revealed, in Realm order. */
    public List<Realm> revealed(UUID player) {
        EnumSet<Realm> set = lit.get(player);
        EnumSet<Realm> done = seen.get(player);
        List<Realm> out = new ArrayList<>();
        if (set != null && done != null) {
            for (Realm realm : set) {
                if (done.contains(realm)) {
                    out.add(realm);
                }
            }
        }
        return out;
    }

    /** True when the state changed. */
    public boolean extinguish(UUID player, Realm realm) {
        EnumSet<Realm> done = seen.get(player);
        if (done != null) {
            done.remove(realm);
        }
        EnumSet<Realm> set = lit.get(player);
        if (set == null || !set.remove(realm)) {
            return false;
        }
        if (set.isEmpty()) {
            lit.remove(player);
        }
        return true;
    }

    public Doc toDoc() {
        Doc doc = new Doc();
        for (Map.Entry<UUID, EnumSet<Realm>> entry : lit.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (Realm realm : entry.getValue()) {
                ids.add(realm.id());
            }
            doc.players.put(entry.getKey().toString(), ids);
        }
        doc.seen = new LinkedHashMap<>();
        for (Map.Entry<UUID, EnumSet<Realm>> entry : seen.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (Realm realm : entry.getValue()) {
                ids.add(realm.id());
            }
            if (!ids.isEmpty()) {
                doc.seen.put(entry.getKey().toString(), ids);
            }
        }
        return doc;
    }

    /** Reads a saved document; unknown players or Realm ids (a file from a newer version) are skipped. */
    public static SkyGateState fromDoc(Doc doc) {
        SkyGateState state = new SkyGateState();
        if (doc == null || doc.players == null) {
            return state;
        }
        for (Map.Entry<String, List<String>> entry : doc.players.entrySet()) {
            UUID player;
            try {
                player = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            for (String id : entry.getValue()) {
                Realm realm = Realm.byId(id);
                if (realm != null) {
                    state.light(player, realm);
                    if (doc.seen == null) {
                        state.markSeen(player, realm); // saved before the reveal existed: nothing is new
                    }
                }
            }
        }
        if (doc.seen != null) {
            for (Map.Entry<String, List<String>> entry : doc.seen.entrySet()) {
                try {
                    UUID player = UUID.fromString(entry.getKey());
                    for (String id : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                        Realm realm = Realm.byId(id);
                        if (realm != null) {
                            state.markSeen(player, realm);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // a bad uuid: skipped
                }
            }
        }
        return state;
    }
}
