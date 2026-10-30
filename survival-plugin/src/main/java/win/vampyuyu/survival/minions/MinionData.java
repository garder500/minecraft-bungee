package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One placed minion as it is saved on disk (a plain Gson class: give new fields a default so old files load).
 * Everything transient (status, entities, scan times) lives in {@code MinionRuntime}, never here.
 */
public final class MinionData {
    /** Unique id of this placed minion. */
    public String id = "";
    /** Lineage uuid carried by the minion item (anti duplication, see {@link UidLedger}); regenerated on pickup. */
    public String uid = "";
    public String owner = "";
    /** Island the minion was placed on. */
    public String island = "";
    public String world = "";
    /** Block position of the minion (its feet). */
    public int x;
    public int y;
    public int z;
    public float yaw;
    public String type = "";
    public int tier = 1;
    public long placedAt;
    /** Wall clock (millis) up to which production has been computed. */
    public long lastTick;
    /** Work done on the current cycle, in milliseconds of normal speed work. */
    public double progress;
    /** Fuel id (key of {@code fuels}) and the work time it still has, in burn milliseconds. */
    public @Nullable String fuel;
    public double fuelMs;
    /** Installed upgrade ids (key of {@code upgrades}). */
    public List<String> upgrades = new ArrayList<>();
    /** Hat id (key of {@code skins}), null for none. */
    public @Nullable String skin;
    /** Item key to amount. */
    public Map<String, Long> storage = new LinkedHashMap<>();
    /** Lifetime number of items produced. */
    public long produced;
    /** Items produced since the last Diamond Spreading bonus. */
    public int spread;
    /** Value of {@link #produced} the last time the owner was told about the production (join summary). */
    public long seenProduced;
    /**
     * The block the minion placed for the current cycle (ore, crop, log) and is going to remove at the end of it.
     * Saved, so a restart or a chunk reload can still finish the cycle: the minion only ever removes this exact
     * block, never anything else of the same kind.
     */
    public boolean placed;
    public int spotX;
    public int spotY;
    public int spotZ;
    /** Material name the placed block had; the block is only removed while it still is that material. */
    public @Nullable String spotMaterial;

    public MinionData copy() {
        MinionData c = new MinionData();
        c.id = id;
        c.uid = uid;
        c.owner = owner;
        c.island = island;
        c.world = world;
        c.x = x;
        c.y = y;
        c.z = z;
        c.yaw = yaw;
        c.type = type;
        c.tier = tier;
        c.placedAt = placedAt;
        c.lastTick = lastTick;
        c.progress = progress;
        c.fuel = fuel;
        c.fuelMs = fuelMs;
        c.upgrades = new ArrayList<>(upgrades);
        c.skin = skin;
        c.storage = new LinkedHashMap<>(storage);
        c.produced = produced;
        c.spread = spread;
        c.seenProduced = seenProduced;
        c.placed = placed;
        c.spotX = spotX;
        c.spotY = spotY;
        c.spotZ = spotZ;
        c.spotMaterial = spotMaterial;
        return c;
    }

    /** Packs the chunk coordinates into one long key. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }
}
