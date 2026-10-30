package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;

/** The shipped skills.yml must parse cleanly and be internally consistent. */
public class ConfigTest {
    private SkillsConfig load() {
        MockBukkit.mock();
        try (var in = getClass().getClassLoader().getResourceAsStream("skills.yml")) {
            assertNotNull("skills.yml must ship in the jar", in);
            return new SkillsConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    public void shippedConfigHasNoWarnings() {
        SkillsConfig cfg = load();
        assertEquals(java.util.List.of(), cfg.warnings);
    }

    @Test
    public void everySkillIsConfiguredWithStats() {
        SkillsConfig cfg = load();
        for (Skill skill : Skill.values()) {
            SkillsConfig.SkillDef def = cfg.skill(skill);
            assertNotNull(skill.name(), def);
            assertFalse(skill + " needs stat rewards", def.stats().isEmpty());
            assertEquals(skill == Skill.CARPENTRY ? 50 : 60, def.maxLevel());
        }
        assertTrue(cfg.skill(Skill.COMBAT).stats().stream().anyMatch(r -> r.stat() == Stat.CRIT_CHANCE));
        assertTrue(cfg.skill(Skill.MINING).stats().stream().anyMatch(r -> r.stat() == Stat.MINING_FORTUNE));
        assertTrue(cfg.skill(Skill.FORAGING).stats().stream().anyMatch(r -> r.stat() == Stat.FORAGING_FORTUNE));
        assertTrue(cfg.skill(Skill.FARMING).stats().stream().anyMatch(r -> r.stat() == Stat.FARMING_FORTUNE));
        assertTrue(cfg.skill(Skill.FISHING).stats().stream().anyMatch(r -> r.stat() == Stat.HEALTH));
        assertTrue(cfg.skill(Skill.ENCHANTING).stats().stream().anyMatch(r -> r.stat() == Stat.INTELLIGENCE));
        assertTrue(cfg.skill(Skill.ALCHEMY).stats().stream().anyMatch(r -> r.stat() == Stat.INTELLIGENCE));
    }

    @Test
    public void levelCurveIsSmooth() {
        SkillsConfig cfg = load();
        assertEquals(60, cfg.levels.maxLevel());
        for (int level = 2; level <= 60; level++) {
            assertTrue(cfg.levels.cost(level) >= cfg.levels.cost(level - 1));
        }
        assertEquals(Set.of(Skill.COMBAT, Skill.MINING, Skill.FARMING, Skill.FORAGING, Skill.FISHING, Skill.ENCHANTING,
            Skill.ALCHEMY), cfg.averageSkills);
    }

    @Test
    public void atLeastTwentyFiveRecipesAndTheyAreConsistent() {
        SkillsConfig cfg = load();
        assertTrue("recipes: " + cfg.recipes.size(), cfg.recipes.size() >= 25);
        Set<String> shells = new HashSet<>();
        for (SkillsConfig.RecipeDef recipe : cfg.recipes.values()) {
            assertTrue(recipe.id() + " result", recipe.resultCustom() == null
                || cfg.enchantedItems.containsKey(recipe.resultCustom()));
            assertTrue(recipe.id() + " slots", recipe.totalSlots() >= 1 && recipe.totalSlots() <= 9);
            for (var need : recipe.requires().entrySet()) {
                SkillsConfig.CollectionDef def = cfg.collections.get(need.getKey());
                assertNotNull(recipe.id() + " needs unknown collection " + need.getKey(), def);
                assertTrue(recipe.id() + " tier", need.getValue() <= def.maxTier());
            }
            for (SkillsConfig.Ingredient ingredient : recipe.ingredients()) {
                if (ingredient.custom()) {
                    assertTrue(recipe.id() + " ingredient " + ingredient.customId(),
                        cfg.enchantedItems.containsKey(ingredient.customId()));
                }
            }
            // two recipes with the same ingredient shell would shadow each other
            String shell = recipe.ingredients().toString();
            assertTrue("duplicate shell for " + recipe.id(), shells.add(shell));
        }
        for (SkillsConfig.EnchantedDef item : cfg.enchantedItems.values()) {
            assertTrue(item.id(), item.id().startsWith("enchanted_"));
        }
        assertTrue(cfg.recipes.containsKey("enchanted_cobblestone"));
        assertTrue(cfg.recipes.containsKey("enchanted_diamond"));
        assertTrue(cfg.recipes.containsKey("enchanted_diamond_block"));
        assertEquals(cfg.recipes.size(), cfg.enchantedItems.size());
    }

    @Test
    public void collectionsHaveIncreasingTiersAndCoins() {
        SkillsConfig cfg = load();
        assertTrue(cfg.collections.size() >= 40);
        for (SkillsConfig.CollectionDef def : cfg.collections.values()) {
            for (int i = 1; i < def.tiers().size(); i++) {
                assertTrue(def.id(), def.tiers().get(i) > def.tiers().get(i - 1));
            }
            assertEquals(def.id() + " coins", def.tiers().size(), def.coins().size());
        }
        assertEquals(def("COBBLESTONE", cfg).tiers().get(3), (Long) 1000L);
        assertSame(cfg.collections.get("COBBLESTONE"), cfg.collectionByMaterial.get(Material.COBBLESTONE));
        assertSame(cfg.collections.get("COBBLESTONE"), cfg.collectionByMaterial.get(Material.COBBLED_DEEPSLATE));
        assertSame(cfg.collections.get("MELON"), cfg.collectionByMaterial.get(Material.MELON_SLICE));
    }

    private static SkillsConfig.CollectionDef def(String id, SkillsConfig cfg) {
        return cfg.collections.get(id);
    }

    @Test
    public void experienceTablesCoverTheNaturalSources() {
        SkillsConfig cfg = load();
        assertEquals(Skill.MINING, cfg.blockSkill.get(Material.DIAMOND_ORE));
        assertEquals(Skill.MINING, cfg.blockSkill.get(Material.DEEPSLATE_DIAMOND_ORE));
        assertEquals(Skill.FORAGING, cfg.blockSkill.get(Material.OAK_LOG));
        assertEquals(Skill.FARMING, cfg.blockSkill.get(Material.WHEAT));
        assertTrue(cfg.matureOnly.contains(Material.WHEAT));
        assertFalse(cfg.matureOnly.contains(Material.SUGAR_CANE));
        assertTrue(cfg.trackedPlaced.contains(Material.STONE));
        assertTrue(cfg.trackedPlaced.contains(Material.MELON));
        assertTrue(cfg.trackedPlaced.contains(Material.LILY_PAD));
        assertFalse(cfg.trackedPlaced.contains(Material.DIRT));
        assertTrue(cfg.blockedSpawnReasons.contains(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER));
        assertTrue(cfg.blockedSpawnReasons.contains(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertTrue(cfg.worldAllowed("sb_world"));
        assertTrue(cfg.worldAllowed("sb_dungeon_abc"));
        assertFalse(cfg.worldAllowed("world"));
        assertFalse(cfg.worldAllowed(null));
    }

    @Test
    public void levelCoinsHaveMilestones() {
        SkillsConfig cfg = load();
        assertEquals(240, cfg.levelCoins.at(1), 0);
        assertEquals(3000, cfg.levelCoins.at(10), 0);
        assertTrue(cfg.levelCoins.at(60) > cfg.levelCoins.at(59));
    }
}
