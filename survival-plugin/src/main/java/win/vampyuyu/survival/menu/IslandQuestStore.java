package win.vampyuyu.survival.menu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import win.vampyuyu.survival.core.JsonStore;

/** Cache and persistence of the {@link IslandQuests} records. Main thread only; dirty records are written by {@link #flush}. */
final class IslandQuestStore {
    private final JsonStore store;
    private final java.util.logging.Logger log;
    private final Map<UUID, IslandQuests> cache = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    IslandQuestStore(JsonStore store, java.util.logging.Logger log) {
        this.store = store;
        this.log = log;
    }

    private static String path(UUID island) {
        return "islandquests/" + island + ".json";
    }

    /** The island's record and whether it was created just now (nothing was on disk). */
    IslandQuests get(UUID island) {
        return cache.computeIfAbsent(island, id -> store.read(path(id), IslandQuests.class, IslandQuests::new));
    }

    boolean exists(UUID island) {
        return cache.containsKey(island) || store.exists(path(island));
    }

    void markDirty(UUID island) {
        dirty.add(island);
    }

    void flush() {
        for (UUID id : Set.copyOf(dirty)) {
            IslandQuests record = cache.get(id);
            if (record == null) {
                dirty.remove(id);
                continue;
            }
            try {
                store.write(path(id), record);
                dirty.remove(id);
            } catch (java.io.IOException | RuntimeException e) {
                log.log(Level.WARNING, "Could not save the quests of island " + id, e);
            }
        }
    }
}
