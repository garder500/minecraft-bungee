package win.vampyuyu.survival.dungeon.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A group of 1 to 5 players. In memory only; ends with the server (documented). */
public final class Party {
    public final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private String runId;

    Party(UUID leader, String leaderName) {
        this.leader = leader;
        members.put(leader, leaderName);
    }

    public UUID leader() { return leader; }

    public boolean isLeader(UUID player) { return leader.equals(player); }

    public boolean has(UUID player) { return members.containsKey(player); }

    /** Members in join order (leader first unless promoted). */
    public List<UUID> members() { return Collections.unmodifiableList(new ArrayList<>(members.keySet())); }

    public int size() { return members.size(); }

    public String nameOf(UUID player) { return members.getOrDefault(player, "?"); }

    /** Id of the dungeon run the party is in, null when idle. */
    public String runId() { return runId; }

    public boolean inRun() { return runId != null; }

    void add(UUID player, String name) { members.put(player, name); }

    void remove(UUID player) { members.remove(player); }

    void setLeader(UUID player) { this.leader = player; }

    void setRun(String runId) { this.runId = runId; }

    void rename(UUID player, String name) {
        if (members.containsKey(player)) {
            members.put(player, name);
        }
    }
}
