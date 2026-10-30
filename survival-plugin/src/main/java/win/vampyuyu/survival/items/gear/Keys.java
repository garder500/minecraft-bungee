package win.vampyuyu.survival.items.gear;

/** PDC keys (inside the {@code vampyuyu} namespace) the items feature adds on top of the reserved core keys. */
public final class Keys {
    private Keys() {}

    /** Definition: base weapon damage (double). */
    public static final String DAMAGE = "damage";
    /** Item: enchants as {@code "sharpness:5,critical:3"} (string). */
    public static final String ENCHANTS = "enchants";
    /** Item: reforge id (string). */
    public static final String REFORGE = "reforge";
    /** Item: 1 when recombobulated (long). */
    public static final String RECOMB = "recomb";
    /** Item: hot potato books applied (long). */
    public static final String HOT_POTATO = "hpb";
    /** Item: wood singularity style upgrade applied (long). */
    public static final String SINGULARITY = "singularity";
    /** Definition: armor set id (string). */
    public static final String SET = "set";
    /** Definition: armor set tier (long). */
    public static final String TIER = "tier";
    /** Definition: accessory family id (string). */
    public static final String FAMILY = "family";
    /** Definition: ability ids, comma separated (string). */
    public static final String ABILITIES = "abilities";
    /** Item: drill fuel left (long). */
    public static final String FUEL = "fuel";
    /** Definition: drill fuel capacity (long). */
    public static final String FUEL_MAX = "fuel_max";
    /** Enchanted book: the stored enchant {@code "id:level"} (string). */
    public static final String BOOK = "stored_enchant";
    /** Pet egg: pet type (string). */
    public static final String PET_TYPE = "pet_type";
    /** Pet egg: pet rarity (string). */
    public static final String PET_RARITY = "pet_rarity";
    /** Pet egg: pet experience (double). */
    public static final String PET_XP = "pet_xp";
    /** Pet egg: held item id (string). */
    public static final String PET_HELD = "pet_held";
    /** Pet egg: stable pet uuid (string). */
    public static final String PET_ID = "pet_id";
    /** Definition: consumable action id (string), e.g. "recombobulate". */
    public static final String APPLY = "apply";
    /** Definition: a pet held item effect id (string). */
    public static final String HELD_EFFECT = "held_effect";
    /** Item: version of the lore renderer that last drew it (long), see RenderUpgrader. */
    public static final String RENDERED = "rendered";
    /** Definition: reforge stone id (string). */
    public static final String STONE = "stone";
}
