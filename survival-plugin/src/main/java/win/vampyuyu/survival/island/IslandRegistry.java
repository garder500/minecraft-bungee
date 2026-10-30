package win.vampyuyu.survival.island;

import com.google.gson.JsonParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import win.vampyuyu.survival.core.JsonStore;

/**
 * All islands, their indexes and the grid allocator. Main thread only. Every mutation must be followed by
 * {@link #changed()}; the owner of the registry persists {@link #toJson()} (see {@link IslandStorage}).
 */
public final class IslandRegistry {
    /** On-disk document. */
    static final class Data {
        int version = 1;
        long nextIndex = 1;
        List<Island> islands = new ArrayList<>();
        /** Cells that were wiped or never used and may be handed out again. */
        List<int[]> freeCells = new ArrayList<>();
        /** Cells of deleted islands whose blocks may still be there (wipe pending). */
        List<int[]> dirtyCells = new ArrayList<>();
        Map<UUID, String> names = new LinkedHashMap<>();
    }

    private final Logger log;
    private final Runnable onChange;
    private final Map<UUID, Island> byId = new LinkedHashMap<>();
    private final Map<UUID, UUID> memberIndex = new HashMap<>();
    private final Map<Long, UUID> byCell = new HashMap<>();
    private final ArrayDeque<int[]> freeCells = new ArrayDeque<>();
    private final List<int[]> dirtyCells = new ArrayList<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private long nextIndex = 1;

    public IslandRegistry(Logger log, Runnable onChange) {
        this.log = log;
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    // ---------------------------------------------------------------- persistence

    /** Loads the registry from its document; duplicates are logged and skipped. */
    public static IslandRegistry load(JsonStore store, String file, Logger log, Runnable onChange) {
        Data data = store.read(file, Data.class, Data::new);
        IslandRegistry registry = new IslandRegistry(log, onChange);
        registry.apply(data);
        return registry;
    }

    /** Parses a document (tests). */
    public static IslandRegistry fromJson(String json, Logger log, Runnable onChange) {
        Data data;
        try {
            data = JsonStore.GSON.fromJson(json, Data.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("bad island document", e);
        }
        IslandRegistry registry = new IslandRegistry(log, onChange);
        registry.apply(data == null ? new Data() : data);
        return registry;
    }

    private void apply(Data data) {
        nextIndex = Math.max(1, data.nextIndex);
        for (Island island : data.islands) {
            if (island == null || island.id == null || island.owner == null) {
                log.warning("Skipping a broken island record");
                continue;
            }
            if (byId.containsKey(island.id) || byCell.containsKey(IslandGrid.key(island.cellX, island.cellZ))
                    || memberIndex.containsKey(island.owner)) {
                log.warning("Skipping duplicate island " + island.id + " (cell " + island.cellX + "," + island.cellZ + ")");
                continue;
            }
            if (IslandGrid.reserved(island.cellX, island.cellZ)) {
                log.warning("Skipping island " + island.id + " on the reserved cell 0,0");
                continue;
            }
            if (island.members == null) island.members = new LinkedHashMap<>();
            if (island.trusted == null) island.trusted = new java.util.LinkedHashSet<>();
            if (island.banned == null) island.banned = new java.util.LinkedHashSet<>();
            if (island.flags == null) island.flags = new LinkedHashMap<>();
            index(island);
        }
        for (int[] cell : data.freeCells) {
            if (cell != null && cell.length == 2 && !byCell.containsKey(IslandGrid.key(cell[0], cell[1]))
                    && !IslandGrid.reserved(cell[0], cell[1])) {
                freeCells.add(cell);
            }
        }
        for (int[] cell : data.dirtyCells) {
            if (cell != null && cell.length == 2) {
                dirtyCells.add(cell);
            }
        }
        if (data.names != null) {
            names.putAll(data.names);
        }
    }

    private void index(Island island) {
        byId.put(island.id, island);
        byCell.put(IslandGrid.key(island.cellX, island.cellZ), island.id);
        memberIndex.put(island.owner, island.id);
        for (UUID member : island.members.keySet()) {
            memberIndex.putIfAbsent(member, island.id);
        }
    }

    /** Snapshot of the whole registry as JSON. */
    public String toJson() {
        Data data = new Data();
        data.nextIndex = nextIndex;
        data.islands = new ArrayList<>(byId.values());
        data.freeCells = new ArrayList<>(freeCells);
        data.dirtyCells = new ArrayList<>(dirtyCells);
        data.names = names;
        return JsonStore.GSON.toJson(data);
    }

    /** Tells the owner that something changed and must be saved. */
    public void changed() {
        onChange.run();
    }

    // ---------------------------------------------------------------- lookups

    public Optional<Island> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The island a player owns or belongs to. */
    public Optional<Island> ofPlayer(UUID player) {
        UUID id = memberIndex.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Optional<Island> byCell(int cellX, int cellZ) {
        UUID id = byCell.get(IslandGrid.key(cellX, cellZ));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Collection<Island> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int count() {
        return byId.size();
    }

    public boolean hasIsland(UUID player) {
        return memberIndex.containsKey(player);
    }

    // ---------------------------------------------------------------- names

    public void rememberName(UUID player, String name) {
        if (name != null && !name.isEmpty() && !name.equals(names.put(player, name))) {
            changed();
        }
    }

    public String nameOf(UUID player) {
        String name = names.get(player);
        return name != null ? name : player.toString().substring(0, 8);
    }

    public boolean knowsName(UUID player) {
        return names.containsKey(player);
    }

    /** Case-insensitive reverse lookup among every name seen so far. */
    public Optional<UUID> uuidOf(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Map<UUID, String> names() {
        return Collections.unmodifiableMap(names);
    }

    // ---------------------------------------------------------------- allocation and mutation

    /** Next free cell: a released one first, otherwise the next spiral cell that is not taken or reserved. */
    private int[] allocateCell() {
        int[] reused = freeCells.pollFirst();
        if (reused != null) {
            return reused;
        }
        while (true) {
            int[] cell = IslandGrid.spiral(nextIndex++);
            if (!IslandGrid.reserved(cell[0], cell[1]) && !byCell.containsKey(IslandGrid.key(cell[0], cell[1]))
                    && !isDirty(cell[0], cell[1])) {
                return cell;
            }
        }
    }

    /** True while the cell is parked waiting for its wipe. */
    public boolean isDirty(int cx, int cz) {
        for (int[] cell : dirtyCells) {
            if (cell[0] == cx && cell[1] == cz) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates an island for a player who has none.
     *
     * @throws IllegalStateException when the player already belongs to an island
     */
    public Island create(UUID owner, String ownerName, String template, long now) {
        if (memberIndex.containsKey(owner)) {
            throw new IllegalStateException("player already has an island");
        }
        int[] cell = allocateCell();
        Island island = new Island();
        island.id = UUID.randomUUID();
        island.owner = owner;
        island.cellX = cell[0];
        island.cellZ = cell[1];
        island.template = template;
        island.createdAt = now;
        island.name = ownerName + "'s island";
        island.state(Island.State.BUILDING);
        index(island);
        rememberName(owner, ownerName);
        changed();
        return island;
    }

    /** Adds a member. Fails (false) when the player already belongs to an island. */
    public boolean addMember(Island island, UUID player, IslandRole role) {
        if (memberIndex.containsKey(player) || role == IslandRole.OWNER || !role.isCoop()) {
            return false;
        }
        island.members.put(player, role.name());
        island.trusted.remove(player);
        memberIndex.put(player, island.id);
        changed();
        return true;
    }

    public boolean removeMember(Island island, UUID player) {
        if (island.members.remove(player) == null) {
            return false;
        }
        memberIndex.remove(player, island.id);
        changed();
        return true;
    }

    /** Gives the island to a co-op member; the old owner becomes a co-owner. */
    public boolean transfer(Island island, UUID newOwner) {
        if (!island.members.containsKey(newOwner)) {
            return false;
        }
        UUID old = island.owner;
        island.members.remove(newOwner);
        island.members.put(old, IslandRole.CO_OWNER.name());
        island.owner = newOwner;
        island.name = nameOf(newOwner) + "'s island";
        changed();
        return true;
    }

    /**
     * Removes the island and every membership. Its cell is parked as "dirty" until {@link #releaseCell} says the
     * blocks are gone, so a half-wiped cell is never handed to someone else.
     */
    public void delete(Island island) {
        byId.remove(island.id);
        byCell.remove(IslandGrid.key(island.cellX, island.cellZ));
        memberIndex.remove(island.owner, island.id);
        for (UUID member : island.members.keySet()) {
            memberIndex.remove(member, island.id);
        }
        dirtyCells.add(new int[] {island.cellX, island.cellZ});
        changed();
    }

    /** The cell was wiped: it can be allocated again. */
    public void releaseCell(int cellX, int cellZ) {
        dirtyCells.removeIf(cell -> cell[0] == cellX && cell[1] == cellZ);
        if (!IslandGrid.reserved(cellX, cellZ) && !byCell.containsKey(IslandGrid.key(cellX, cellZ))) {
            freeCells.addLast(new int[] {cellX, cellZ});
        }
        changed();
    }

    /** Cells still waiting for a wipe (startup recovery). */
    public List<int[]> dirtyCells() {
        return List.copyOf(dirtyCells);
    }

    public long nextIndex() {
        return nextIndex;
    }
}
