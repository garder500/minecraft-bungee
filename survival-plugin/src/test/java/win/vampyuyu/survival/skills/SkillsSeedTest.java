package win.vampyuyu.survival.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bukkit.Material;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.Db;

/** The converted skills seed keeps what the former skills.yml held (counts measured on that file). */
public class SkillsSeedTest {
    private static int count(Db db, String table) {
        return db.read(c -> Db.query(c, "SELECT COUNT(*) FROM " + table, rs -> rs.getInt(1)).get(0));
    }

    @Test
    public void everyRowOfTheOldYamlIsInTheSeed() {
        try (Db db = SkillsTestData.seeded()) {
            assertEquals(51, count(db, "setting WHERE id GLOB 'skills.*'"));
            assertEquals(8, count(db, "skill"));
            assertEquals(26, count(db, "skill_stat_range"));
            assertEquals(55, count(db, "skill_unlock"));
            assertEquals(67, count(db, "skill_block_xp"));
            assertEquals(38, count(db, "skill_mob_xp"));
            assertEquals(20, count(db, "skill_catch_xp"));
            assertEquals(5, count(db, "skill_sea_xp"));
            assertEquals(17, count(db, "skill_brew_xp"));
            assertEquals(88, count(db, "skill_craft_xp"));
            assertEquals(5, count(db, "sea_creature"));
            assertEquals(4, count(db, "collection_preset"));
            assertEquals(37, count(db, "collection_preset_tier"));
            assertEquals(56, count(db, "collection"));
            assertEquals(59, count(db, "collection_material"));
            assertEquals(53, count(db, "enchanted_item"));
            assertEquals(53, count(db, "skill_recipe"));
            assertEquals(53, count(db, "skill_recipe_ingredient"));
            assertEquals(53, count(db, "skill_recipe_requirement"));
        }
    }

    @Test
    public void theLoadedConfigurationHasTheOldValues() {
        MockBukkit.mock();
        try {
            SkillsConfig cfg = SkillsTestData.load();
            assertEquals(java.util.List.of(), cfg.warnings);
            assertEquals(56, cfg.collections.size());
            assertEquals(53, cfg.recipes.size());
            assertEquals(53, cfg.enchantedItems.size());
            assertEquals(5, cfg.seaCreatures.creatures().size());
            assertEquals(67, cfg.blockXp.size());
            assertEquals(38, cfg.mobXp.size());
            assertEquals(88, cfg.craftXp.size());
            assertEquals(60, cfg.levels.maxLevel());
            assertEquals(50, cfg.maxLevel(Skill.CARPENTRY));
            SkillsConfig.StatReward strength = cfg.skill(Skill.COMBAT).stats().stream()
                .filter(r -> r.stat() == Stat.MIGHT).findFirst().orElseThrow();
            assertEquals(2, strength.ranges().size());
            assertEquals(0.75, strength.at(21), 0);
            assertEquals(250, cfg.maxPerKill, 0);
            assertEquals(120_000, cfg.mobAntiFarm.windowMillis());
            assertEquals(0.35, cfg.seaCreatures.maxChance(), 0);
            assertEquals(java.util.List.of(100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L, 25000L, 50000L, 100000L),
                cfg.collections.get("COBBLESTONE").tiers());
            assertEquals(Material.IRON_INGOT, cfg.collections.get("IRON").icon());
            assertEquals("Cobblestone", cfg.collections.get("COBBLESTONE").name());
            assertEquals("vampyuyu:skills/enchanted_coal", cfg.enchantedItems.get("enchanted_coal").model());
            assertTrue(cfg.recipes.get("enchanted_coal").requires().containsKey("COAL"));
            assertEquals(32, cfg.recipes.get("enchanted_coal").ingredients().get(0).perSlot());
        } finally {
            MockBukkit.unmock();
        }
    }
}
