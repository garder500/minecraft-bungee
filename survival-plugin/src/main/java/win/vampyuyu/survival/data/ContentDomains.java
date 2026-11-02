package win.vampyuyu.survival.data;

import java.util.List;
import win.vampyuyu.survival.data.Seeds.Child;
import win.vampyuyu.survival.data.Seeds.Domain;
import win.vampyuyu.survival.data.Seeds.Root;

/** Every seed file shipped in the jar and the tables it fills. A new domain is one entry here plus its seed and migration. */
public final class ContentDomains {
    public static final Domain QUESTS = new Domain("quests", List.of(
        new Root("quest_category", "id", null, List.of()),
        new Root("quest", "id", "pool = 'plan'", List.of(new Child("quest_requirement", "quest_id"),
            new Child("quest_reward_xp", "quest_id"), new Child("quest_reward_item", "quest_id")))));

    public static final Domain QUESTS_DAILY = new Domain("quests_daily", List.of(
        new Root("quest", "id", "pool = 'daily'", List.of(new Child("quest_requirement", "quest_id"),
            new Child("quest_reward_xp", "quest_id"), new Child("quest_reward_item", "quest_id")))));

    public static final Domain QUARRY = new Domain("quarry", List.of(
        new Root("ore_roll", "id", null, List.of(new Child("ore_roll_chance", "ore_roll_id"), new Child("ore_roll_block", "ore_roll_id"))),
        new Root("forge_recipe", "id", null, List.of(new Child("forge_recipe_ingredient", "forge_recipe_id"))),
        new Root("forge_block_ingredient", "item", null, List.of()),
        settings("quarry")));

    public static final Domain REALM = new Domain("realm", List.of(
        new Root("realm_world", "world", null, List.of()),
        new Root("realm_resource", "id", null, List.of(new Child("realm_resource_source", "resource_id")))));

    public static final Domain GADGETS = new Domain("gadgets", List.of(settings("gadgets")));

    public static final Domain ECONOMY = new Domain("economy", List.of(
        new Root("merchant", "id", null, List.of()),
        new Root("merchant_offer", "id", null, List.of()),
        new Root("bank_tier", "id", null, List.of()),
        new Root("bazaar_product", "id", null, List.of()),
        new Root("price_pressure_prefix", "prefix", null, List.of()),
        new Root("market_never_traded", "pattern", null, List.of()),
        new Root("auction_blocked_item", "id", null, List.of()),
        settings("economy")));

    public static final Domain SKILLS = new Domain("skills", List.of(
        settings("skills"),
        new Root("skill", "id", null, List.of(new Child("skill_stat_range", "skill_id"), new Child("skill_unlock", "skill_id"))),
        new Root("skill_block_xp", "material", null, List.of()),
        new Root("skill_mob_xp", "entity", null, List.of()),
        new Root("skill_catch_xp", "material", null, List.of()),
        new Root("skill_sea_xp", "entity", null, List.of()),
        new Root("skill_brew_xp", "material", null, List.of()),
        new Root("skill_craft_xp", "material", null, List.of()),
        new Root("sea_creature", "id", null, List.of()),
        new Root("collection_preset", "id", null, List.of(new Child("collection_preset_tier", "preset_id"))),
        new Root("collection", "id", null, List.of(new Child("collection_material", "collection_id"))),
        new Root("enchanted_item", "id", null, List.of(new Child("enchanted_item_lore", "item_id"))),
        new Root("skill_recipe", "id", null, List.of(new Child("skill_recipe_ingredient", "recipe_id"),
            new Child("skill_recipe_requirement", "recipe_id")))));

    public static final Domain BOSS = new Domain("boss", List.of(
        new Root("boss", "id", null, List.of(new Child("boss_equipment", "boss_id"), new Child("boss_phase", "boss_id"),
            new Child("boss_ability", "boss_id"), new Child("boss_ability_param", "boss_id"),
            new Child("boss_rank_bonus", "boss_id"), new Child("boss_drop", "boss_id"), new Child("boss_summon", "boss_id"),
            new Child("boss_summon_world", "boss_id"))),
        new Root("slayer", "id", null, List.of(new Child("slayer_target", "slayer_id"), new Child("slayer_world", "slayer_id"),
            new Child("slayer_level", "slayer_id"), new Child("slayer_perk", "slayer_id"), new Child("slayer_tier", "slayer_id"),
            new Child("slayer_equipment", "slayer_id"), new Child("slayer_ability", "slayer_id"),
            new Child("slayer_ability_param", "slayer_id"), new Child("slayer_rank_bonus", "slayer_id"),
            new Child("slayer_drop", "slayer_id"))),
        new Root("boss_recipe", "item", null, List.of(new Child("boss_recipe_row", "recipe"),
            new Child("boss_recipe_ingredient", "recipe"))),
        new Root("boss_mob_drop", "id", null, List.of(new Child("boss_mob_drop_entity", "drop_id"),
            new Child("boss_mob_drop_world", "drop_id"))),
        new Root("boss_item_text", "item", null, List.of(new Child("boss_item_text_lore", "item"))),
        new Root("boss_world_spot", "id", null, List.of()),
        new Root("boss_world_pool", "boss_id", null, List.of()),
        settings("boss")));

    public static final Domain RESONANCE = new Domain("resonance", List.of(settings("resonance")));

    public static final Domain TRADES = new Domain("trades", List.of(settings("trades")));

    public static final Domain DUNGEON = new Domain("dungeon", List.of(
        new Root("dungeon_grade", "id", null, List.of()),
        new Root("dungeon_reward", "id", null, List.of()),
        new Root("dungeon_trade", "id", null, List.of(new Child("dungeon_trade_cost", "trade_id"))),
        new Root("dungeon_floor", "id", null, List.of()),
        new Root("dungeon_boss", "id", null, List.of(new Child("dungeon_boss_attack", "boss_id"))),
        settings("dungeon")));

    public static final Domain BASTION = new Domain("bastion", List.of(
        new Root("bastion", "id", null, List.of()),
        settings("bastion")));

    /** Plugin level settings: the debug flag and the feature toggles (read before any feature enables). */
    public static final Domain CORE = new Domain("core", List.of(settings("core")));

    public static final Domain ISLAND = new Domain("island", List.of(
        new Root("island_world", "key", null, List.of(new Child("island_world_gamerule", "world"))),
        new Root("island_upgrade", "id", null, List.of()),
        new Root("island_template", "id", null, List.of(new Child("island_template_line", "template_id"),
            new Child("island_template_item", "template_id"))),
        new Root("island_chest", "id", null, List.of(new Child("island_chest_item", "chest_id"))),
        new Root("island_level_weight", "material", null, List.of()),
        new Root("island_flag_default", "flag", null, List.of()),
        settings("island")));

    public static final Domain UTILITY = new Domain("utility", List.of(
        new Root("utility_portable", "name", null, List.of()),
        settings("utility")));

    public static final Domain WINGS = new Domain("wings", List.of(
        new Root("wings_level", "level", null, List.of(new Child("wings_level_stat", "level"))),
        new Root("wings_upgrade", "level", null, List.of()),
        new Root("wings_core", "tier", null, List.of()),
        new Root("wings_region", "id", null, List.of()),
        settings("wings")));

    public static final Domain MENU = new Domain("menu", List.of(
        new Root("menu_command", "name", null, List.of(new Child("menu_command_alias", "command"))),
        new Root("menu_warp", "world", null, List.of()),
        new Root("menu_default_zone", "world", null, List.of()),
        new Root("menu_zone", "id", null, List.of()),
        new Root("menu_sound", "event", null, List.of()),
        new Root("menu_fairy_stat", "id", null, List.of()),
        new Root("menu_fairy_soul", "id", null, List.of()),
        settings("menu")));

    public static final Domain ITEMS = new Domain("items", List.of(
        settings("items"),
        new Root("item_mob", "entity", null, List.of()),
        new Root("item_recipe", "id", null, List.of(new Child("item_recipe_row", "recipe_id"),
            new Child("item_recipe_ingredient", "recipe_id"))),
        new Root("item_loot", "id", null, List.of(new Child("item_loot_source", "loot_id"))),
        new Root("armor_set", "id", null, List.of(new Child("armor_set_stat", "set_id"), new Child("armor_set_line", "set_id"))),
        new Root("item_def", "id", null, List.of(new Child("item_stat", "item_id"), new Child("item_ability", "item_id"),
            new Child("item_lore", "item_id"), new Child("item_pdc", "item_id")))));

    /** The {@code setting} rows of one domain ({@code id} starts with {@code <name>.}). */
    public static Root settings(String name) {
        return new Root("setting", "id", "id GLOB '" + name + ".*'", List.of(new Child("setting_item", "setting_id")));
    }

    /** In the order they are synced. */
    public static final List<Domain> ALL = List.of(QUESTS, QUESTS_DAILY, CORE, ISLAND, UTILITY, WINGS, MENU, ECONOMY, SKILLS, QUARRY, GADGETS, BOSS,
        RESONANCE, TRADES, DUNGEON, BASTION, ITEMS, REALM);

    private ContentDomains() {}

    public static Domain named(String name) {
        return ALL.stream().filter(d -> d.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown data domain " + name));
    }
}
