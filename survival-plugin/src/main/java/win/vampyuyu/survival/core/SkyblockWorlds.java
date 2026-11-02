package win.vampyuyu.survival.core;

/** The only place world names are written down. */
public final class SkyblockWorlds {
    /** Shared spawn / hub world. */
    public static final String HUB = "sb_hub";
    /** Overworld holding the island grid. */
    public static final String WORLD = "sb_world";
    public static final String NETHER = "sb_nether";
    public static final String END = "sb_end";
    /** The Whispering Grove, second Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String GROVE = "sb_grove";
    /** The Ethereal Necropolis, eighth Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String NECROPOLIS = "sb_necropolis";
    /** The Frozen Heights, third Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String HEIGHTS = "sb_heights";
    /** The Cracked Ashes, first Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String ASHES = "sb_ashes";
    /** The Putrid Marshes, fifth Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String MARSH = "sb_marsh";

    /** The Citadel of Golden Sands, fourth Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String CITADEL = "sb_citadel";
    /** The Abyssal Depths, seventh Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String DEPTHS = "sb_depths";
    /** The Tempestuous Archipelago, sixth Realm (GDD 3.1), reached by its Sky Gate. */
    public static final String STORM = "sb_storm";
    /** Dungeon runs use {@code sb_dungeon_<runId>}. */
    public static final String DUNGEON_PREFIX = "sb_dungeon_";

    /** Bastion instances use {@code sb_bastion_<n>}. */
    public static final String BASTION_PREFIX = "sb_bastion_";

    private SkyblockWorlds() {}

    public static boolean isBastion(String worldName) {
        return worldName != null && worldName.startsWith(BASTION_PREFIX);
    }

    public static boolean isDungeon(String worldName) {
        return worldName != null && worldName.startsWith(DUNGEON_PREFIX);
    }

    /** True for every world that belongs to the Skyblock. */
    public static boolean isSkyblock(String worldName) {
        return HUB.equals(worldName) || WORLD.equals(worldName) || NETHER.equals(worldName)
            || END.equals(worldName) || GROVE.equals(worldName) || HEIGHTS.equals(worldName) || ASHES.equals(worldName) || STORM.equals(worldName)
            || NECROPOLIS.equals(worldName) || MARSH.equals(worldName) || CITADEL.equals(worldName) || DEPTHS.equals(worldName) || isDungeon(worldName)
            || isBastion(worldName);
    }

    /** World name of a dungeon run. */
    public static String dungeon(String runId) {
        return DUNGEON_PREFIX + runId;
    }
}
