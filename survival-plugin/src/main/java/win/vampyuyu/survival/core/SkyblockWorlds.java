package win.vampyuyu.survival.core;

/** The only place world names are written down. */
public final class SkyblockWorlds {
    /** Shared spawn / hub world. */
    public static final String HUB = "sb_hub";
    /** Overworld holding the island grid. */
    public static final String WORLD = "sb_world";
    public static final String NETHER = "sb_nether";
    public static final String END = "sb_end";
    /** Dungeon runs use {@code sb_dungeon_<runId>}. */
    public static final String DUNGEON_PREFIX = "sb_dungeon_";

    private SkyblockWorlds() {}

    public static boolean isDungeon(String worldName) {
        return worldName != null && worldName.startsWith(DUNGEON_PREFIX);
    }

    /** True for every world that belongs to the Skyblock. */
    public static boolean isSkyblock(String worldName) {
        return HUB.equals(worldName) || WORLD.equals(worldName) || NETHER.equals(worldName)
            || END.equals(worldName) || isDungeon(worldName);
    }

    /** World name of a dungeon run. */
    public static String dungeon(String runId) {
        return DUNGEON_PREFIX + runId;
    }
}
