package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import win.vampyuyu.survival.minions.MinionConfig.Kind;

public class MinionConfigTest {
    @Test
    public void shippedConfigHasAtLeastEighteenTypesAndNoWarnings() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        MinionConfig c = MinionConfig.load(TestConfig.yaml(), log);
        assertEquals(java.util.List.of(), warnings);
        assertTrue("types: " + c.types.size(), c.types.size() >= 18);
        for (Kind kind : Kind.values()) {
            assertTrue(kind + " has a type", c.types.values().stream().anyMatch(t -> t.kind() == kind));
        }
        assertEquals(9, c.fuels.size());
        assertEquals(7, c.upgrades.size());
        assertTrue(c.skins.size() >= 8);
        assertTrue("recipes: " + c.itemRecipes.size(), c.itemRecipes.size() >= 10);
    }

    @Test
    public void everyUpgradeIsCraftableAndSoAreTheCommonFuelsAndHats() {
        MinionConfig c = TestConfig.load();
        for (String up : c.upgrades.keySet()) {
            assertTrue(up + " has a recipe", c.itemRecipes.values().stream().anyMatch(r -> r.result().equals("upgrade:" + up)));
        }
        assertTrue(c.itemRecipes.values().stream().anyMatch(r -> r.result().equals("skin:pumpkin_hat")));
        assertTrue(c.itemRecipes.values().stream().anyMatch(r -> r.result().equals("fuel:hamster_wheel")));
        // the settings added by the review
        assertFalse(c.memberCollect);
        assertEquals(6 * 3_600_000L, c.islandMissingMillis());
        assertEquals(150, c.guiClickMillis);
    }

    @Test
    public void aBadRecipeIsSkippedWithAWarning() {
        YamlConfiguration yaml = TestConfig.yaml();
        yaml.set("recipes.broken.result", "upgrade:nope");
        yaml.set("recipes.broken.shape", java.util.List.of("EE"));
        yaml.set("recipes.broken.ingredients.E", "COAL");
        yaml.set("recipes.no_ingredient.result", "upgrade:compactor");
        yaml.set("recipes.no_ingredient.shape", java.util.List.of("EX"));
        yaml.set("recipes.no_ingredient.ingredients.E", "COAL");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { warnings.add(r.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        MinionConfig c = MinionConfig.load(yaml, log);
        assertFalse(c.itemRecipes.containsKey("broken"));
        assertFalse(c.itemRecipes.containsKey("no_ingredient"));
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("broken"));
    }

    @Test
    public void aCompressedFuelMustBeAResourceAMinionMakes() {
        YamlConfiguration yaml = TestConfig.yaml();
        yaml.set("fuels.bad.material", "COAL");
        yaml.set("fuels.bad.compressed", "BEDROCK");
        MinionConfig c = MinionConfig.load(yaml, Logger.getAnonymousLogger());
        assertFalse(c.fuels.containsKey("bad"));
        assertEquals(Material.COAL, c.fuels.get("enchanted_coal").compressed());
        assertFalse(c.fuels.get("enchanted_coal").custom());
        assertEquals("c:minion_enchanted_charcoal", c.fuels.get("enchanted_charcoal").key());
    }

    @Test
    public void tierTablesAreCompleteAndMonotonic() {
        MinionConfig c = TestConfig.load();
        assertEquals(11, c.actionFactor.length);
        for (int i = 1; i < 11; i++) {
            assertTrue(c.actionFactor[i] <= c.actionFactor[i - 1]);
            assertTrue(c.storageSlots[i] > c.storageSlots[i - 1]);
            assertTrue(c.radius[i] >= c.radius[i - 1]);
        }
        assertEquals(1, c.storageSlots(1));
        assertEquals(30, c.storageSlots(11));
        assertEquals(30, c.storageSlots(99));
        assertEquals(1, c.storageSlots(-4));
    }

    @Test
    public void cycleMillisFollowsTheHypixelPacing() {
        MinionConfig c = TestConfig.load();
        MinionConfig.Type cobble = c.types.get("cobblestone");
        // 14s per action, two actions per cycle at tier I, 7s per action at tier XI
        assertEquals(28_000, c.cycleMillis(cobble, 1), 1e-6);
        assertEquals(14_000, c.cycleMillis(cobble, 11), 1e-6);
        assertEquals(78_000, c.cycleMillis(c.types.get("fishing"), 1), 1e-6);
    }

    @Test
    public void smeltTargetsAndCompactableMaterialsAreConsistent() {
        MinionConfig c = TestConfig.load();
        assertEquals(Material.STONE, c.smelted(Material.COBBLESTONE));
        assertEquals(Material.DIAMOND, c.smelted(Material.DIAMOND));
        assertTrue(c.producible().contains(Material.STONE));
        assertTrue(c.producible().contains(Material.COOKED_BEEF));
        assertTrue(c.producible().contains(Material.DIAMOND));
    }

    @Test
    public void everyTypeHasADropAndATierOneRecipe() {
        MinionConfig c = TestConfig.load();
        for (MinionConfig.Type t : c.types.values()) {
            assertFalse(t.id(), t.drops().isEmpty());
            assertNotNull(t.recipe().ingredient());
            assertTrue(t.id(), t.actionSeconds() > 0);
        }
    }

    @Test
    public void aBadEntryIsSkippedNotFatal() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("types.good.kind", "MINING");
        y.set("types.good.block", "STONE");
        y.set("types.good.recipe.ingredient", "STONE");
        y.set("types.good.recipe.center", "STICK");
        y.set("types.good.drops", java.util.List.of(java.util.Map.of("item", "STONE")));
        y.set("types.bad.kind", "FLYING");
        y.set("types.worse.kind", "MINING");
        y.set("types.worse.block", "NOT_A_BLOCK");
        y.set("fuels.f.material", "NOPE");
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MinionConfig c = MinionConfig.load(y, log);
        assertEquals(java.util.Set.of("good"), c.types.keySet());
        assertTrue(c.fuels.isEmpty());
    }

    @Test
    public void wrongTierTableLengthFallsBackToDefaults() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("tiers.storage-slots", java.util.List.of(1, 2, 3));
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MinionConfig c = MinionConfig.load(y, log);
        assertEquals(30, c.storageSlots(11));
    }

    @Test
    public void slotRules() {
        MinionConfig c = TestConfig.load();
        assertEquals(5, SlotRules.slots(c, 0));
        assertEquals(5, SlotRules.slots(c, 4));
        assertEquals(6, SlotRules.slots(c, 5));
        assertEquals(7, SlotRules.slots(c, 15));
        assertEquals(17, SlotRules.slots(c, 250));
        assertEquals(17, SlotRules.slots(c, 9999));
        assertEquals(5, SlotRules.untilNext(c, 0));
        assertEquals(1, SlotRules.untilNext(c, 4));
        assertEquals(10, SlotRules.untilNext(c, 5));
        assertEquals(-1, SlotRules.untilNext(c, 250));
    }

    @Test
    public void roman() {
        assertEquals("I", MinionConfig.roman(1));
        assertEquals("XI", MinionConfig.roman(11));
        assertEquals("XI", MinionConfig.roman(50));
        assertEquals("Raw Beef", MinionConfig.prettify(Material.BEEF).equals("Beef") ? "Raw Beef" : "x");
    }
}
