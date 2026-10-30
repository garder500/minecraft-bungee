package win.vampyuyu.survival.minions;

/** What a minion is doing right now (not saved: recomputed from the surroundings). */
public enum Status {
    WORKING("Working", "<green>", false),
    STORAGE_FULL("Storage full", "<red>", true),
    NO_SPACE("No space", "<red>", true),
    INVALID_TERRAIN("Invalid terrain", "<red>", true),
    NO_WATER("No water", "<red>", true),
    ISLAND_MISSING("Island missing", "<red>", true),
    OUTSIDE_ISLAND("Area outside island", "<red>", true);

    private final String label;
    private final String color;
    private final boolean blocked;

    Status(String label, String color, boolean blocked) {
        this.label = label;
        this.color = color;
        this.blocked = blocked;
    }

    public String label() {
        return label;
    }

    /** MiniMessage colour tag. */
    public String color() {
        return color;
    }

    public boolean blocked() {
        return blocked;
    }

    /** True when production may run (the terrain is fine; a full storage is decided by the production itself). */
    public boolean workable() {
        return this == WORKING || this == STORAGE_FULL;
    }

    /** One line telling the player how to fix it for the given kind. */
    public String hint(MinionConfig.Kind kind) {
        return switch (this) {
            case WORKING -> "The minion is working.";
            case STORAGE_FULL -> "Collect the items from the minion, or upgrade it for more storage.";
            case NO_SPACE -> switch (kind) {
                case MINING -> "Free some blocks around it: it needs air to place blocks, or blocks it can mine.";
                case ANIMAL, COMBAT -> "It needs a free 1x2 space on solid ground around it.";
                default -> "Free some space around it.";
            };
            case INVALID_TERRAIN -> switch (kind) {
                case MINING -> "It needs a solid block under the air it works on: no pillar, no void.";
                case FARMING -> "It needs farmland around it, at the layer below its feet.";
                case FORAGING -> "It needs grass or dirt around it for its trees to grow on.";
                default -> "The ground around it is not suitable.";
            };
            case NO_WATER -> "It needs water in the layer below its feet.";
            case ISLAND_MISSING -> "Its island is gone. Ask an admin or pick the minion up.";
            case OUTSIDE_ISLAND -> "Its working area leaves the island. Move it inward or remove the Minion Expander.";
        };
    }
}
