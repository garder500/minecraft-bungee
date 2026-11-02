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
    }

    private final Map<UUID, EnumSet<Realm>> lit = new HashMap<>();

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

    /** True when the state changed. */
    public boolean extinguish(UUID player, Realm realm) {
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
                }
            }
        }
        return state;
    }
}
