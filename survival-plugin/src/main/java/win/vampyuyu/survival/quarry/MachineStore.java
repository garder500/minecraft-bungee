package win.vampyuyu.survival.quarry;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import win.vampyuyu.survival.core.JsonStore;
import win.vampyuyu.survival.quarry.EnergyNetwork.Pos;

/** Every placed machine, per world, saved in {@code quarry/machines.json}. Main thread only. */
final class MachineStore {
    static final String FILE = "quarry/machines.json";

    /** On-disk document: world name to ("x,y,z" to machine). */
    static final class Doc {
        Map<String, Map<String, MachineData>> worlds = new LinkedHashMap<>();
    }

    private final JsonStore store;
    private final Logger log;
    private final Doc doc;
    private boolean dirty;

    MachineStore(JsonStore store, Logger log) {
        this.store = store;
        this.log = log;
        Doc read = store.read(FILE, Doc.class, Doc::new);
        this.doc = read == null || read.worlds == null ? new Doc() : read;
    }

    private static String key(Pos pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static Pos pos(String key) {
        String[] p = key.split(",");
        return new Pos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    static Pos pos(Block block) {
        return new Pos(block.getX(), block.getY(), block.getZ());
    }

    MachineData get(String world, Pos pos) {
        Map<String, MachineData> m = doc.worlds.get(world);
        return m == null ? null : m.get(key(pos));
    }

    void put(String world, Pos pos, MachineData data) {
        doc.worlds.computeIfAbsent(world, w -> new LinkedHashMap<>()).put(key(pos), data);
        dirty = true;
    }

    MachineData remove(String world, Pos pos) {
        Map<String, MachineData> m = doc.worlds.get(world);
        MachineData gone = m == null ? null : m.remove(key(pos));
        if (gone != null) {
            dirty = true;
        }
        return gone;
    }

    /** Positions and machines of one world (a copy). */
    Map<Pos, MachineData> all(String world) {
        Map<Pos, MachineData> out = new HashMap<>();
        Map<String, MachineData> m = doc.worlds.get(world);
        if (m != null) {
            m.forEach((k, v) -> {
                try {
                    out.put(pos(k), v);
                } catch (RuntimeException e) {
                    log.warning("quarry: bad machine key " + k + " in " + world);
                }
            });
        }
        return out;
    }

    java.util.Set<String> worlds() {
        return new java.util.LinkedHashSet<>(doc.worlds.keySet());
    }

    int countOnIsland(String islandId) {
        int n = 0;
        for (Map<String, MachineData> m : doc.worlds.values()) {
            for (MachineData d : m.values()) {
                if (islandId.equals(d.island)) {
                    n++;
                }
            }
        }
        return n;
    }

    void markDirty() {
        dirty = true;
    }

    void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            store.write(FILE, doc);
        } catch (IOException e) {
            log.warning("quarry: could not save machines: " + e);
            dirty = true;
        }
    }
}
