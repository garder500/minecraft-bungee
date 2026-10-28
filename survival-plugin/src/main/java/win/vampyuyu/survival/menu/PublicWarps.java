package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import win.vampyuyu.survival.core.Core;

/**
 * Islands their owners opened to visitors ("public island warps"). The island API has no such flag, so the menu
 * feature keeps its own opt-in registry in {@code menu/public_warps.json}. Memory is the source of truth; every change is
 * written (atomically) off the main thread, in order.
 */
final class PublicWarps {
    /** One published island. */
    static final class Entry {
        public String name = "";
        public String note = "";
        public long since;
    }

    /** File format. */
    static final class FileData {
        public Map<String, Entry> entries = new java.util.LinkedHashMap<>();
    }

    static final String FILE = "menu/public_warps.json";
    static final int MAX_NOTE = 60;

    private final Core core;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final OrderedJsonWriter writer;

    PublicWarps(Core core) {
        this.core = core;
        this.writer = new OrderedJsonWriter(core, FILE);
    }

    void load() {
        FileData data = core.store().read(FILE, FileData.class, FileData::new);
        entries.clear();
        if (data.entries == null) {
            return;
        }
        data.entries.forEach((key, entry) -> {
            try {
                if (entry != null) {
                    entries.put(UUID.fromString(key), entry);
                }
            } catch (IllegalArgumentException e) {
                core.debug("Ignoring bad public warp key " + key);
            }
        });
    }

    boolean isPublic(UUID owner) {
        return entries.containsKey(owner);
    }

    Optional<Entry> get(UUID owner) {
        return Optional.ofNullable(entries.get(owner));
    }

    /** Publishes (or updates the note of) an island. The note is cut to {@link #MAX_NOTE} characters. */
    void publish(UUID owner, String ownerName, String note) {
        Entry entry = entries.computeIfAbsent(owner, id -> new Entry());
        entry.name = ownerName;
        entry.note = note == null ? "" : note.strip().substring(0, Math.min(MAX_NOTE, note.strip().length()));
        if (entry.since == 0) {
            entry.since = System.currentTimeMillis();
        }
        persist();
    }

    void unpublish(UUID owner) {
        if (entries.remove(owner) != null) {
            persist();
        }
    }

    /** Published islands, sorted by owner name. */
    List<Map.Entry<UUID, Entry>> list() {
        List<Map.Entry<UUID, Entry>> out = new ArrayList<>(entries.entrySet());
        out.sort(Comparator.comparing(e -> e.getValue().name.toLowerCase(java.util.Locale.ROOT)));
        return out;
    }

    private void persist() {
        FileData data = new FileData();
        entries.forEach((id, entry) -> data.entries.put(id.toString(), entry));
        writer.write(data);
    }

    void flushNow() {
        FileData data = new FileData();
        entries.forEach((id, entry) -> data.entries.put(id.toString(), entry));
        writer.writeNow(data);
    }
}
