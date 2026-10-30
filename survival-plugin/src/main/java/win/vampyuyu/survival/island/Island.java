package win.vampyuyu.survival.island;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted island record (Gson, public fields). Mutate on the main thread only and call
 * {@code IslandRegistry.changed()} afterwards.
 */
public final class Island {
    /** Island lifecycle: only READY islands accept building and visits. */
    public enum State { BUILDING, READY, RESETTING }

    public UUID id;
    public UUID owner;
    public int cellX;
    public int cellZ;
    public String name = "";
    public String template = "classic";
    public long createdAt;
    public String state = State.READY.name();
    /** Overworld spawn set with /is sethome; null means the template spawn. */
    public Pos home;
    /** Members besides the owner: uuid to {@code CO_OWNER} or {@code MEMBER}. */
    public Map<UUID, String> members = new LinkedHashMap<>();
    public Set<UUID> trusted = new LinkedHashSet<>();
    public Set<UUID> banned = new LinkedHashSet<>();
    public Map<String, Boolean> flags = new LinkedHashMap<>();
    public String timeLock = TimeLock.NONE.name();
    public boolean locked;
    public int radiusTier;
    public int memberTier;
    public int minionTier;
    /** Weighted block value from the last scan, and the level derived from it. */
    public double value;
    public long level;
    public long levelAt;
    public long lastResetAt;
    /** True once the Nether and End platforms exist. */
    public boolean platformsReady;
    public long visits;
    public Warp warp;
    /** Where the island's own Nether / End platform spawn is, relative offsets are fixed by the generator. */
    public int generation = 1;

    /** A public warp (listed in the warp browser). */
    public static final class Warp {
        public String name = "";
        public Pos pos;
        public long since;
        public long visits;
    }

    public State state() {
        try {
            return State.valueOf(state);
        } catch (IllegalArgumentException | NullPointerException e) {
            return State.READY;
        }
    }

    public void state(State value) {
        this.state = value.name();
    }

    public boolean flag(IslandFlag flag) {
        Boolean value = flags.get(flag.id());
        return value == null ? flag.defaultValue() : value;
    }

    public void flag(IslandFlag flag, boolean value) {
        flags.put(flag.id(), value);
    }

    public TimeLock timeLock() {
        return TimeLock.parse(timeLock);
    }

    /** Role of a player on this island (never null). */
    public IslandRole roleOf(UUID player) {
        if (player == null) {
            return IslandRole.VISITOR;
        }
        if (banned.contains(player)) {
            return IslandRole.BANNED;
        }
        if (player.equals(owner)) {
            return IslandRole.OWNER;
        }
        String role = members.get(player);
        if (role != null) {
            return "CO_OWNER".equals(role) ? IslandRole.CO_OWNER : IslandRole.MEMBER;
        }
        return trusted.contains(player) ? IslandRole.TRUSTED : IslandRole.VISITOR;
    }

    /** Owner plus members. */
    public Set<UUID> coop() {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(owner);
        all.addAll(members.keySet());
        return all;
    }

    public int size() {
        return 1 + members.size();
    }
}
