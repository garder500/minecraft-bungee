package win.vampyuyu.survival.island;

/** What a player is on a given island. Ordered from most to least trusted. */
public enum IslandRole {
    OWNER("Owner"),
    CO_OWNER("Co-owner"),
    MEMBER("Member"),
    TRUSTED("Trusted guest"),
    VISITOR("Visitor"),
    BANNED("Banned");

    private final String label;

    IslandRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Owner, co-owner and member: part of the co-op. */
    public boolean isCoop() {
        return this == OWNER || this == CO_OWNER || this == MEMBER;
    }
}
