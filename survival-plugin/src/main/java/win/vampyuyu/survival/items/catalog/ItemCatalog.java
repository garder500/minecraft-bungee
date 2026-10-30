package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.CustomItemDef.Builder;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Keys;

/**
 * Registers every custom item of the items feature. Numbers are defaults: {@code items.yml} may override
 * {@code items.<id>.damage} and {@code items.<id>.stats.<stat>} for any item below.
 */
public final class ItemCatalog {
    /** Item model namespace folder, see {@code resource-pack/pack/assets/vampyuyu/items/items/}. */
    public static final String MODEL = "vampyuyu:items/";

    /** Accessory families: id, display noun, material, stats of the four rarities. */
    public record Family(String id, String name, Material material, Stat stat, double[] values, Stat extra,
            double[] extraValues) {}

    public static final List<Family> FAMILIES = List.of(
        new Family("speed", "Speed", Material.SUGAR, Stat.SPEED, new double[] {2, 4, 7, 11}, null, null),
        new Family("strength", "Strength", Material.BLAZE_POWDER, Stat.STRENGTH, new double[] {3, 6, 10, 16},
            Stat.CRIT_DAMAGE, new double[] {0, 0, 4, 8}),
        new Family("crit", "Critical", Material.GHAST_TEAR, Stat.CRIT_CHANCE, new double[] {1, 2, 3, 4},
            Stat.CRIT_DAMAGE, new double[] {2, 5, 8, 12}),
        new Family("vitality", "Vitality", Material.GLISTERING_MELON_SLICE, Stat.HEALTH, new double[] {8, 16, 28, 45},
            Stat.DEFENSE, new double[] {0, 2, 5, 9}),
        new Family("guard", "Guardian", Material.IRON_NUGGET, Stat.DEFENSE, new double[] {4, 8, 14, 22}, Stat.TRUE_DEFENSE,
            new double[] {0, 0, 1, 2}),
        new Family("mind", "Mind", Material.LAPIS_LAZULI, Stat.INTELLIGENCE, new double[] {10, 20, 35, 55}, Stat.ABILITY_DAMAGE,
            new double[] {0, 0, 2, 4}),
        new Family("harvest", "Harvest", Material.WHEAT, Stat.FARMING_FORTUNE, new double[] {5, 10, 18, 28},
            Stat.FORAGING_FORTUNE, new double[] {0, 0, 5, 10}),
        new Family("prospect", "Prospector", Material.RAW_COPPER, Stat.MINING_FORTUNE, new double[] {5, 10, 18, 28},
            Stat.MINING_SPEED, new double[] {0, 5, 12, 20}),
        new Family("fortune", "Fortune", Material.FEATHER, Stat.MAGIC_FIND, new double[] {1, 2, 4, 6}, Stat.PET_LUCK,
            new double[] {0, 0, 1, 2}));

    private static final String[] TIER_NOUN = {"Talisman", "Charm", "Ring", "Artifact"};
    private static final Rarity[] TIER_RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

    private final Items items;
    private final ItemsSettings settings;
    private final List<CustomItemDef> defs = new ArrayList<>();

    public ItemCatalog(Items items, ItemsSettings settings) {
        this.items = items;
        this.settings = settings;
    }

    /** Definitions built (and registered) by {@link #registerAll}. */
    public List<CustomItemDef> defs() { return defs; }

    private double num(String id, String key, double fallback) {
        return settings.tuned("items." + id + "." + key, fallback);
    }

    private Builder base(String id, String name, Rarity rarity, Material material, String type) {
        return CustomItemDef.builder(id, name, rarity, material).itemModel(MODEL + id).type(type).unbreakable(true)
            .maxStackSize(1);
    }

    private Builder stat(Builder builder, String id, Stat stat, double value) {
        double v = num(id, "stats." + stat.key(), value);
        return v == 0 ? builder : builder.stat(stat, v);
    }

    private void add(Builder builder) {
        CustomItemDef def = builder.build();
        items.register(def);
        defs.add(def);
    }

    private Builder abilities(Builder builder, String... ids) {
        return builder.pdc(Keys.ABILITIES, String.join(",", ids));
    }

    /** Registers the whole catalog. */
    public void registerAll() {
        weapons();
        tools();
        armor();
        accessories();
        utility();
    }

    // ---------------------------------------------------------------- weapons

    private void weapons() {
        sword("rookie_sword", "Rookie Sword", Rarity.COMMON, Material.IRON_SWORD, 35, 10, 0, 0, "A simple blade for a new adventurer.");
        sword("warriors_blade", "Warrior's Blade", Rarity.UNCOMMON, Material.IRON_SWORD, 60, 25, 5, 15, "Balanced and dependable.");
        add(abilities(stat(stat(stat(stat(sword0("leaping_sword", "Leaping Sword", Rarity.RARE, Material.GOLDEN_SWORD, 85,
            "Light enough to jump with."), "leaping_sword", Stat.STRENGTH, 40), "leaping_sword", Stat.SPEED, 8),
            "leaping_sword", Stat.CRIT_CHANCE, 5), "leaping_sword", Stat.CRIT_DAMAGE, 25), "leap"));
        add(abilities(stat(stat(stat(sword0("frozen_scythe", "Frozen Scythe", Rarity.EPIC, Material.DIAMOND_SWORD, 110,
            "Forged in a glacier that never melts."), "frozen_scythe", Stat.STRENGTH, 60), "frozen_scythe",
            Stat.CRIT_DAMAGE, 40), "frozen_scythe", Stat.INTELLIGENCE, 60), "frost_nova"));
        add(abilities(stat(stat(stat(sword0("void_blade", "Void Blade", Rarity.EPIC, Material.IRON_SWORD, 120,
            "A blade that bends the space around it."), "void_blade", Stat.STRENGTH, 80), "void_blade",
            Stat.CRIT_DAMAGE, 50), "void_blade", Stat.SPEED, 10), "instant_transmission"));
        add(abilities(stat(stat(stat(stat(stat(sword0("wither_blade", "Wither Blade", Rarity.LEGENDARY,
            Material.NETHERITE_SWORD, 260, "Its edge hums with a withering power."), "wither_blade", Stat.STRENGTH, 150),
            "wither_blade", Stat.CRIT_DAMAGE, 70), "wither_blade", Stat.INTELLIGENCE, 100), "wither_blade",
            Stat.FEROCITY, 30), "wither_blade", Stat.ABILITY_DAMAGE, 15), "wither_impact"));
        add(abilities(stat(stat(stat(base("ember_rod", "Ember Rod", Rarity.EPIC, Material.BLAZE_ROD, "WAND")
            .pdc(Keys.DAMAGE, num("ember_rod", "damage", 70.0)).lore("<dark_gray>Still warm to the touch."),
            "ember_rod", Stat.INTELLIGENCE, 150), "ember_rod", Stat.ABILITY_DAMAGE, 20), "ember_rod", Stat.STRENGTH, 30),
            "fire_beam"));
        add(abilities(stat(stat(base("healing_wand", "Healing Wand", Rarity.RARE, Material.STICK, "WAND")
            .pdc(Keys.DAMAGE, num("healing_wand", "damage", 25.0)).lore("<dark_gray>Mends friends and allies."),
            "healing_wand", Stat.INTELLIGENCE, 120), "healing_wand", Stat.HEALTH, 40), "heal"));
        add(abilities(stat(stat(base("overflux_capacitor", "Overflux Capacitor", Rarity.EPIC, Material.PRISMARINE_CRYSTALS, "WAND")
            .pdc(Keys.DAMAGE, num("overflux_capacitor", "damage", 10.0)).lore("<dark_gray>Stores more mana than it should."),
            "overflux_capacitor", Stat.INTELLIGENCE, 200), "overflux_capacitor", Stat.ABILITY_DAMAGE, 10), "overflux"));
        add(abilities(stat(stat(stat(base("staff_of_the_volcano", "Staff of the Volcano", Rarity.LEGENDARY,
            Material.LIGHTNING_ROD, "STAFF").pdc(Keys.DAMAGE, num("staff_of_the_volcano", "damage", 150.0))
            .lore("<dark_gray>The mountain answers when it is called."), "staff_of_the_volcano", Stat.INTELLIGENCE, 300),
            "staff_of_the_volcano", Stat.ABILITY_DAMAGE, 35), "staff_of_the_volcano", Stat.STRENGTH, 50),
            "volcano_pillar"));
        add(stat(stat(base("hunters_shortbow", "Hunter's Shortbow", Rarity.UNCOMMON, Material.BOW, "BOW")
            .pdc(Keys.DAMAGE, num("hunters_shortbow", "damage", 50.0)).lore("<dark_gray>Quick to draw, quiet to loose."),
            "hunters_shortbow", Stat.STRENGTH, 20), "hunters_shortbow", Stat.CRIT_CHANCE, 5));
        add(abilities(stat(stat(stat(stat(base("voidpiercer_bow", "Voidpiercer Bow", Rarity.LEGENDARY, Material.BOW, "BOW")
            .pdc(Keys.DAMAGE, num("voidpiercer_bow", "damage", 180.0)).lore("<dark_gray>Three arrows, one purpose."),
            "voidpiercer_bow", Stat.STRENGTH, 90), "voidpiercer_bow", Stat.CRIT_CHANCE, 15), "voidpiercer_bow",
            Stat.CRIT_DAMAGE, 100), "voidpiercer_bow", Stat.FEROCITY, 10), "triple_shot"));
    }

    private Builder sword0(String id, String name, Rarity rarity, Material material, double damage, String flavor) {
        return base(id, name, rarity, material, "SWORD").pdc(Keys.DAMAGE, num(id, "damage", damage))
            .lore("<dark_gray>" + flavor);
    }

    private void sword(String id, String name, Rarity rarity, Material material, double damage, double strength,
            double critChance, double critDamage, String flavor) {
        Builder builder = sword0(id, name, rarity, material, damage, flavor);
        builder = stat(builder, id, Stat.STRENGTH, strength);
        builder = stat(builder, id, Stat.CRIT_CHANCE, critChance);
        builder = stat(builder, id, Stat.CRIT_DAMAGE, critDamage);
        add(builder);
    }

    // ---------------------------------------------------------------- tools

    private void tools() {
        add(abilities(stat(stat(stat(stat(base("treecapitator", "Treecapitator", Rarity.EPIC, Material.GOLDEN_AXE, "AXE")
            .pdc(Keys.DAMAGE, num("treecapitator", "damage", 80.0)).lore("<dark_gray>One swing, one tree."),
            "treecapitator", Stat.FORAGING_FORTUNE, 50), "treecapitator", Stat.STRENGTH, 40), "treecapitator",
            Stat.MINING_SPEED, 90), "treecapitator", Stat.CRIT_DAMAGE, 10), "treecapitator"));
        add(abilities(stat(stat(base("jungle_axe", "Jungle Axe", Rarity.RARE, Material.IRON_AXE, "AXE")
            .pdc(Keys.DAMAGE, num("jungle_axe", "damage", 60.0)).lore("<dark_gray>Cuts through the thickest canopy."),
            "jungle_axe", Stat.FORAGING_FORTUNE, 30), "jungle_axe", Stat.MINING_SPEED, 60), "jungle_chop"));
        add(stat(stat(base("diggers_shovel", "Digger's Shovel", Rarity.COMMON, Material.IRON_SHOVEL, "SHOVEL")
            .pdc(Keys.DAMAGE, num("diggers_shovel", "damage", 20.0)).lore("<dark_gray>Moves a lot of dirt."),
            "diggers_shovel", Stat.MINING_SPEED, 50), "diggers_shovel", Stat.MINING_FORTUNE, 5));
        add(abilities(stat(stat(base("miners_pickaxe", "Miner's Pickaxe", Rarity.RARE, Material.DIAMOND_PICKAXE, "PICKAXE")
            .pdc(Keys.DAMAGE, num("miners_pickaxe", "damage", 30.0)).lore("<dark_gray>Chips ore like it owes you money."),
            "miners_pickaxe", Stat.MINING_SPEED, 180), "miners_pickaxe", Stat.MINING_FORTUNE, 40),
            "mining_speed_boost"));
        add(abilities(stat(stat(base("miners_drill", "Miner's Drill", Rarity.EPIC, Material.DIAMOND_PICKAXE, "DRILL")
            .pdc(Keys.DAMAGE, num("miners_drill", "damage", 45.0)).pdc(Keys.FUEL_MAX, 3000L).pdc(Keys.FUEL, 3000L)
            .lore("<dark_gray>Loud, hungry and very fast."), "miners_drill", Stat.MINING_SPEED, 320), "miners_drill",
            Stat.MINING_FORTUNE, 70), "mining_speed_boost", "drill_fuel"));
        add(abilities(stat(stat(base("titan_drill", "Titan Drill", Rarity.LEGENDARY, Material.NETHERITE_PICKAXE, "DRILL")
            .pdc(Keys.DAMAGE, num("titan_drill", "damage", 70.0)).pdc(Keys.FUEL_MAX, 10000L).pdc(Keys.FUEL, 10000L)
            .lore("<dark_gray>Built to reach what nobody could."), "titan_drill", Stat.MINING_SPEED, 600),
            "titan_drill", Stat.MINING_FORTUNE, 140), "mining_speed_boost", "drill_fuel"));
        add(abilities(stat(base("farmers_hoe", "Farmer's Hoe", Rarity.UNCOMMON, Material.IRON_HOE, "HOE")
            .pdc(Keys.DAMAGE, num("farmers_hoe", "damage", 15.0)).lore("<dark_gray>Tilled soil never forgets."),
            "farmers_hoe", Stat.FARMING_FORTUNE, 40), "replant"));
        add(abilities(stat(stat(base("harvest_hoe", "Harvest Hoe", Rarity.EPIC, Material.DIAMOND_HOE, "HOE")
            .pdc(Keys.DAMAGE, num("harvest_hoe", "damage", 25.0)).lore("<dark_gray>The fields bow when you pass."),
            "harvest_hoe", Stat.FARMING_FORTUNE, 120), "harvest_hoe", Stat.SPEED, 10), "replant"));
        add(abilities(stat(stat(stat(base("rod_of_legends", "Rod of Legends", Rarity.LEGENDARY, Material.FISHING_ROD, "ROD")
            .pdc(Keys.DAMAGE, num("rod_of_legends", "damage", 60.0)).lore("<dark_gray>Some fish are stories."),
            "rod_of_legends", Stat.FISHING_SPEED, 120), "rod_of_legends", Stat.SEA_CREATURE_CHANCE, 8),
            "rod_of_legends", Stat.INTELLIGENCE, 100), "rod_pull"));
        add(abilities(base("grappling_hook", "Grappling Hook", Rarity.UNCOMMON, Material.FISHING_ROD, "HOOK")
            .pdc(Keys.DAMAGE, num("grappling_hook", "damage", 5.0)).lore("<dark_gray>Reach for the horizon."),
            "grappling_hook"));
    }

    // ---------------------------------------------------------------- armor

    private void armor() {
        Rarity[] rarities = {Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY};
        for (ArmorSet set : ArmorSets.all()) {
            for (int tier = 1; tier <= 3; tier++) {
                for (String slot : ArmorSets.SLOTS) {
                    String id = set.id() + "_" + slot + "_t" + tier;
                    String name = set.name() + " " + Character.toUpperCase(slot.charAt(0)) + slot.substring(1)
                        + (tier == 1 ? "" : tier == 2 ? " II" : " III");
                    Builder builder = base(id, name, rarities[tier - 1], set.pieceMaterial(slot), slot.toUpperCase(Locale.ROOT))
                        .pdc(Keys.SET, set.id()).pdc(Keys.TIER, (long) tier)
                        .lore("<dark_gray>Part of the " + set.name() + " armor.");
                    double factor = ArmorSet.tierFactor(tier) * ArmorSet.slotWeight(slot);
                    for (var entry : set.pieceStats().entrySet()) {
                        double value = Math.round(entry.getValue() * factor * (entry.getKey().percent() ? 0.5 : 1));
                        builder = stat(builder, id, entry.getKey(), Math.max(1, value));
                    }
                    add(builder);
                }
            }
        }
    }

    // ---------------------------------------------------------------- accessories

    private void accessories() {
        for (Family family : FAMILIES) {
            for (int tier = 0; tier < 4; tier++) {
                String id = "acc_" + family.id() + "_" + (tier + 1);
                String name = family.name() + " " + TIER_NOUN[tier];
                Builder builder = base(id, name, TIER_RARITY[tier], family.material(), "ACCESSORY")
                    .pdc(Keys.FAMILY, family.id()).pdc(Keys.TIER, (long) (tier + 1))
                    .lore("<dark_gray>Keep it in your accessory bag.", "<dark_gray>Only the best of a family counts.");
                builder = stat(builder, id, family.stat(), family.values()[tier]);
                if (family.extra() != null && family.extraValues()[tier] > 0) {
                    builder = stat(builder, id, family.extra(), family.extraValues()[tier]);
                }
                add(builder);
            }
        }
    }

    // ---------------------------------------------------------------- utility

    private void utility() {
        consumable("recombobulator", "Recombobulator 3000", Rarity.LEGENDARY, Material.NETHER_STAR, "recombobulate",
            "<gray>Upgrades the rarity of an item by <green>one tier</green>.", "<gray>Works up to <light_purple>MYTHIC</light_purple>.",
            "<gray>Click it onto an item in your inventory.");
        consumable("hot_potato_book", "Hot Potato Book", Rarity.UNCOMMON, Material.BOOK, "hot_potato",
            "<gray>Adds <green>+2 Strength</green> to a weapon or",
            "<gray><green>+4 Health</green> and <green>+2 Defense</green> to armor.", "<gray>Up to 10 per item.",
            "<gray>Click it onto an item in your inventory.");
        consumable("wood_singularity", "Wood Singularity", Rarity.EPIC, Material.OAK_LOG, "singularity",
            "<gray>Permanently adds <gold>+25 Foraging Fortune</gold> to an axe.", "<gray>One per item.");
        consumable("stone_singularity", "Stone Singularity", Rarity.EPIC, Material.COBBLESTONE, "singularity",
            "<gray>Permanently adds <gold>+25 Mining Fortune</gold> to a pickaxe, drill or shovel.", "<gray>One per item.");
        consumable("hay_singularity", "Hay Singularity", Rarity.EPIC, Material.HAY_BLOCK, "singularity",
            "<gray>Permanently adds <gold>+25 Farming Fortune</gold> to a hoe.", "<gray>One per item.");
        consumable("drill_fuel_cell", "Fuel Cell", Rarity.UNCOMMON, Material.COAL, "fuel:1000",
            "<gray>Refuels a drill with <green>1,000</green> fuel.", "<gray>Click it onto a drill in your inventory.");
        consumable("drill_fuel_volta", "Volta", Rarity.RARE, Material.REDSTONE_BLOCK, "fuel:5000",
            "<gray>Refuels a drill with <green>5,000</green> fuel.", "<gray>Click it onto a drill in your inventory.");
        consumable("drill_fuel_oil", "Oil Barrel", Rarity.EPIC, Material.BLACK_CONCRETE_POWDER, "fuel:10000",
            "<gray>Refuels a drill with <green>10,000</green> fuel.", "<gray>Click it onto a drill in your inventory.");
        add(base("warp_pearl", "Warp Pearl", Rarity.UNCOMMON, Material.ENDER_PEARL, "CONSUMABLE").maxStackSize(16)
            .lore("<gray>Throw it to teleport without taking damage."));
        for (String stone : new String[] {"fabled", "withered", "ancient", "refined"}) {
            String id = "reforge_stone_" + stone;
            add(base(id, Character.toUpperCase(stone.charAt(0)) + stone.substring(1) + " Reforge Stone", Rarity.LEGENDARY,
                Material.PRISMARINE_SHARD, "REFORGE STONE").maxStackSize(16).pdc(Keys.APPLY, "reforge").pdc(Keys.STONE, id)
                .lore("<gray>Click it onto a compatible item in your", "<gray>inventory to apply the <gold>"
                    + Character.toUpperCase(stone.charAt(0)) + stone.substring(1) + "</gold> reforge.").glint(true));
        }
        add(base("enchanted_book", "Enchanted Book", Rarity.UNCOMMON, Material.ENCHANTED_BOOK, "BOOK").maxStackSize(1)
            .lore("<gray>Combine it with an item at an anvil."));
        add(base("pet_egg", "Pet", Rarity.COMMON, Material.EGG, "PET").maxStackSize(1)
            .lore("<gray>Right click to add this pet to your collection."));
        add(base("pet_candy", "Pet Candy", Rarity.RARE, Material.SWEET_BERRIES, "CONSUMABLE").maxStackSize(64)
            .pdc(Keys.APPLY, "pet_candy").lore("<gray>Gives <green>5,000</green> XP to your active pet.",
                "<gray>Right click to eat."));
        heldItem("pet_tier_boost", "Tier Boost", Rarity.LEGENDARY, Material.AMETHYST_SHARD, "tier_boost",
            "<gray>The pet acts as if it were <green>one rarity higher</green>.");
        heldItem("pet_xp_share", "Exp Share", Rarity.EPIC, Material.EXPERIENCE_BOTTLE, "xp_share",
            "<gray>The pet gains <green>+25%</green> experience.");
        heldItem("pet_big_teeth", "Big Teeth", Rarity.UNCOMMON, Material.BONE, "big_teeth",
            "<gray>Grants <blue>+5 Crit Chance</blue> while the pet is out.");
        heldItem("pet_hardened_scales", "Hardened Scales", Rarity.UNCOMMON, Material.TURTLE_SCUTE, "hardened_scales",
            "<gray>Grants <green>+25 Defense</green> while the pet is out.");
        heldItem("pet_lucky_clover", "Lucky Clover", Rarity.EPIC, Material.FERN, "lucky_clover",
            "<gray>Grants <light_purple>+5 Pet Luck</light_purple> and <aqua>+5 Magic Find</aqua>",
            "<gray>while the pet is out.");
    }

    private void consumable(String id, String name, Rarity rarity, Material material, String apply, String... lore) {
        add(base(id, name, rarity, material, "CONSUMABLE").maxStackSize(64).pdc(Keys.APPLY, apply).lore(lore)
            .glint(rarity.ordinal() >= Rarity.EPIC.ordinal()));
    }

    private void heldItem(String id, String name, Rarity rarity, Material material, String effect, String... lore) {
        add(base(id, name, rarity, material, "PET ITEM").maxStackSize(1).pdc(Keys.HELD_EFFECT, effect).lore(lore)
            .lore("", "<gray>Hold it with a pet: drop it onto the pet in <yellow>/pets</yellow>."));
    }
}
