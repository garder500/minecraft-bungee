package win.vampyuyu.survival.menu;

/** What a quest counts. The meaning of {@code target} depends on the type (see {@link QuestMatcher}). */
public enum QuestType {
    /** The player owns an island. Absolute: counts 1 when they have one. */
    ISLAND_CREATE(true, "Create your island"),
    /** The player walks into an island area (target ignored). */
    ISLAND_ENTER(false, "Visit an island"),
    /** Break blocks. Target: material, group (LOGS, STONE, ORES, CROPS, ANY) or empty for any. Placed blocks do not count. */
    BREAK_BLOCK(false, "Break blocks"),
    /** Place blocks. Target: material, group or empty. */
    PLACE_BLOCK(false, "Place blocks"),
    /** Craft items. Target: material, group, or {@code custom:<id prefix>} for a custom item. */
    CRAFT_ITEM(false, "Craft items"),
    /** Kill mobs. Target: entity type or empty for any. */
    KILL_MOB(false, "Defeat mobs"),
    /** Catch fish (target ignored). */
    FISH(false, "Catch fish"),
    /** Reach a skill level. Target: skill key or ANY. Amount = the level. Absolute. */
    SKILL_LEVEL(true, "Reach a skill level"),
    /** Kill a boss. Target: boss id or empty for any. */
    BOSS_KILL(false, "Defeat a boss"),
    /** Earn coins (gains only, minus ignored reasons). */
    COINS_EARN(false, "Earn coins"),
    /** Run a command. Target: command name; its aliases match too. */
    COMMAND(false, "Use a command"),
    /** Enter a Skyblock world. Target: hub, world, nether or end. */
    VISIT_WORLD(false, "Visit an area"),
    /** Collect fairy souls (target ignored). */
    COLLECT_SOUL(false, "Collect Fairy Souls"),
    /** Enchant an item (target ignored). */
    ENCHANT_ITEM(false, "Enchant an item"),
    /** Find an ore in a cobble generator. Target: ore id (bronzite, diamond...) or ANY. */
    ORE_FOUND(false, "Find ores in your generator"),
    /** Collect an item from the Forge. Target: custom item id or ANY. */
    FORGE_ITEM(false, "Forge items"),
    /** Upgrade the cobble generator. Target: the tier reached ("2", "3") or ANY. */
    GENERATOR_UPGRADE(false, "Upgrade your generator");

    private final boolean absolute;
    private final String label;

    QuestType(boolean absolute, String label) {
        this.absolute = absolute;
        this.label = label;
    }

    /** Absolute types report the current value (a level, an island count) instead of an increment. */
    public boolean absolute() {
        return absolute;
    }

    public String label() {
        return label;
    }
}
