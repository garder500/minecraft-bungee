package win.vampyuyu.survival.items;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ability.BreakListener;
import win.vampyuyu.survival.items.accessory.AccessoryMath;
import win.vampyuyu.survival.items.combat.DamageFormula;
import win.vampyuyu.survival.items.combat.DamageIndicators;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.hud.HudFormat;
import win.vampyuyu.survival.items.menu.EnchantTableMenu;
import win.vampyuyu.survival.items.menu.ReforgeMenu;
import win.vampyuyu.survival.items.pet.PetLeveling;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;
import win.vampyuyu.survival.items.stats.AttributeApplier;
import win.vampyuyu.survival.items.stats.ManaPool;

/** Formulas and rules that need no server. */
public class PureLogicTest {
    @Test
    public void damageFormulaFollowsTheSpec() {
        // (100 + 5) * (1 + 50/100) = 157.5, crit with 100 crit damage doubles it
        assertEquals(157.5, DamageFormula.base(100, 5, 50), 1e-9);
        assertEquals(315.0, DamageFormula.hit(100, 5, 50, 0, true, 100), 1e-9);
        assertEquals(157.5, DamageFormula.hit(100, 5, 50, 0, false, 100), 1e-9);
        assertEquals(189.0, DamageFormula.hit(100, 5, 50, 0.2, false, 100), 1e-9);
        assertEquals(50.0, DamageFormula.mitigate(100, 100), 1e-9);
        assertEquals(100.0, DamageFormula.mitigate(100, -50), 1e-9);
    }

    @Test
    public void critRollUsesTheChance() {
        assertTrue(DamageFormula.rollCrit(30, () -> 0.29));
        assertFalse(DamageFormula.rollCrit(30, () -> 0.31));
        assertFalse(DamageFormula.rollCrit(0, () -> 0.0));
    }

    @Test
    public void enchantMultipliers() {
        assertEquals(1.0, DamageFormula.execute(5, 0.2, 0, 100), 1e-9);
        assertEquals(0.5, DamageFormula.execute(5, 0.2, 50, 100), 1e-9);
        assertEquals(0, DamageFormula.execute(5, 0.2, 100, 100), 1e-9);
        assertEquals(0, DamageFormula.giantKiller(3, 5, 100, 100), 1e-9);
        assertEquals(0.15, DamageFormula.giantKiller(3, 5, 400, 100), 1e-9);
        assertEquals(0, DamageFormula.snipe(2, 1.5, 4), 1e-9);
        assertEquals(0.6, DamageFormula.snipe(4, 1.5, 100), 1e-9);
        assertEquals(1, DamageFormula.ferocityStrikes(100, () -> 0.99));
        assertEquals(2, DamageFormula.ferocityStrikes(150, () -> 0.1));
    }

    @Test
    public void healthMapsToTheAttribute() {
        assertEquals(100, AttributeApplier.maxHealth(100, 1024), 1e-9);
        assertEquals(1024, AttributeApplier.maxHealth(5000, 1024), 1e-9);
        assertEquals(1, AttributeApplier.maxHealth(-4, 1024), 1e-9);
        assertEquals(0, AttributeApplier.speedAmount(100, 400), 1e-9);
        assertEquals(1.0, AttributeApplier.speedAmount(200, 400), 1e-9);
        assertEquals(3.0, AttributeApplier.speedAmount(9999, 400), 1e-9);
    }

    @Test
    public void manaPoolRegeneratesSpendsAndOverflows() {
        ManaPool pool = new ManaPool();
        pool.tick(100, 1, 2, 100, 2);
        assertEquals(100, pool.mana(), 1e-9);
        assertTrue(pool.spend(40));
        assertEquals(60, pool.mana(), 1e-9);
        pool.tick(100, 5, 2, 100, 2);
        assertEquals(70, pool.mana(), 1e-9);
        assertFalse(pool.spend(500));
        assertEquals(70, pool.mana(), 1e-9);
        pool.addOverflow(30, 100, 100);
        assertEquals(30, pool.overflow(), 1e-9);
        assertTrue(pool.spend(90));
        // regular mana is spent first, overflow covers the rest
        assertEquals(0, pool.mana(), 1e-9);
        assertEquals(10, pool.overflow(), 1e-9);
    }

    @Test
    public void petLevelsGoFromOneToOneHundred() {
        PetLeveling leveling = new PetLeveling(100, 1.075, Map.of("RARE", 1.0, "EPIC", 1.6));
        assertEquals(1, leveling.levelFor(0, Rarity.RARE));
        assertEquals(100, leveling.xpToNext(1, Rarity.RARE), 1e-9);
        assertEquals(2, leveling.levelFor(100, Rarity.RARE));
        assertEquals(1, leveling.levelFor(99, Rarity.RARE));
        double total = leveling.totalForLevel(100, Rarity.RARE);
        assertEquals(100, leveling.levelFor(total, Rarity.RARE));
        assertEquals(100, leveling.levelFor(total * 10, Rarity.RARE));
        assertEquals(99, leveling.levelFor(total - 1, Rarity.RARE));
        assertTrue(leveling.totalForLevel(50, Rarity.EPIC) > leveling.totalForLevel(50, Rarity.RARE));
        assertEquals("█████░░░░░", PetLeveling.bar(0.5));
    }

    @Test
    public void accessoriesCountOncePerFamily() {
        Map<String, Integer> mp = Map.of("COMMON", 3, "RARE", 8, "EPIC", 12);
        var low = new AccessoryMath.Entry("acc_speed_1", "speed", Rarity.COMMON, Map.of(Stat.SPEED, 2.0), 0);
        var high = new AccessoryMath.Entry("acc_speed_3", "speed", Rarity.RARE, Map.of(Stat.SPEED, 7.0), 1);
        var other = new AccessoryMath.Entry("acc_crit_4", "crit", Rarity.EPIC, Map.of(Stat.CRIT_CHANCE, 4.0), 2);
        var result = AccessoryMath.compute(List.of(low, high, other), mp, Map.of(Stat.STRENGTH, 0.5));
        assertEquals(2, result.counted().size());
        assertEquals(1, result.ignored().size());
        assertEquals("acc_speed_1", result.ignored().get(0).itemId());
        assertEquals(20, result.magicalPower());
        assertEquals(7.0, result.stats().get(Stat.SPEED), 1e-9);
        assertEquals(10.0, result.stats().get(Stat.STRENGTH), 1e-9);
        // order in the bag does not matter
        var reversed = AccessoryMath.compute(List.of(other, high, low), mp, Map.of());
        assertEquals(20, reversed.magicalPower());
        assertEquals(9, AccessoryMath.slots(9, 0, 3, 45));
        assertEquals(45, AccessoryMath.slots(9, 100, 3, 45));
        assertTrue(AccessoryMath.upgradeCost(2, 25000, 1.8) > AccessoryMath.upgradeCost(1, 25000, 1.8));
    }

    @Test
    public void enchantRulesAndRegistry() {
        EnchantRegistry registry = new EnchantRegistry(null);
        assertTrue("at least 25 enchants", registry.all().size() >= 25);
        assertTrue(registry.all().stream().filter(e -> e.ultimate()).count() >= 5);
        for (String id : List.of("sharpness", "critical", "execute", "life_steal", "giant_killer", "first_strike", "looting",
                "protection", "growth", "rejuvenate", "thorns", "efficiency", "fortune", "smelting_touch", "telekinesis",
                "replenish", "harvesting", "cubism", "cleave", "power", "snipe", "infinite_quiver", "feather_falling",
                "respiration")) {
            assertTrue(id, registry.get(id).isPresent());
        }
        assertTrue(registry.all().stream().anyMatch(e -> e.maxLevel() == 10));
        var sharp = registry.get("sharpness").orElseThrow();
        var applied = EnchantRules.combine(new java.util.HashMap<>(), registry, sharp, 3, ItemKind.SWORD);
        assertTrue(applied.ok());
        assertEquals(3, applied.level());
        assertEquals(4, EnchantRules.combine(Map.of("sharpness", 3), registry, sharp, 3, ItemKind.SWORD).level());
        assertFalse(EnchantRules.combine(Map.of("sharpness", 5), registry, sharp, 3, ItemKind.SWORD).ok());
        assertFalse(EnchantRules.combine(Map.of(), registry, sharp, 1, ItemKind.BOOTS).ok());
        var ultimate = registry.get("soul_eater").orElseThrow();
        assertFalse(EnchantRules.combine(Map.of("one_for_all", 1), registry, ultimate, 1, ItemKind.SWORD).ok());
        assertEquals(Map.of("sharpness", 5, "critical", 2), EnchantRules.parse("sharpness:5,critical:2"));
        assertTrue(EnchantRules.parse("garbage").isEmpty());
        assertTrue(EnchantRules.tableCost(5, 150, 1.9) > EnchantRules.tableCost(4, 150, 1.9));
        assertEquals(0, EnchantTableMenu.requiredSkill(1, 5));
        assertEquals(20, EnchantTableMenu.requiredSkill(5, 5));
    }

    @Test
    public void reforgesScaleWithRarity() {
        ReforgeRegistry reforges = new ReforgeRegistry(null);
        assertTrue("about twenty reforges", reforges.all().size() >= 20);
        assertTrue(reforges.scale(Rarity.LEGENDARY) > reforges.scale(Rarity.COMMON));
        assertTrue(reforges.rollable(ItemKind.SWORD).stream().noneMatch(r -> r.stoneOnly()));
        assertTrue(reforges.byStone("reforge_stone_fabled").isPresent());
        assertTrue(ReforgeMenu.price(Rarity.LEGENDARY, 400) > ReforgeMenu.price(Rarity.COMMON, 400));
        var sharp = reforges.get("sharp").orElseThrow();
        assertTrue(sharp.at(Rarity.EPIC, reforges.scale(Rarity.EPIC)).get(Stat.CRIT_CHANCE)
            > sharp.at(Rarity.COMMON, reforges.scale(Rarity.COMMON)).get(Stat.CRIT_CHANCE));
        assertTrue(sharp.appliesTo(ItemKind.SWORD));
        assertFalse(sharp.appliesTo(ItemKind.HELMET));
    }

    @Test
    public void fortuneAndSmelting() {
        assertEquals(0, BreakListener.fortuneCopies(0, 0.5));
        assertEquals(1, BreakListener.fortuneCopies(100, 0.99));
        assertEquals(2, BreakListener.fortuneCopies(150, 0.1));
        assertEquals(1, BreakListener.fortuneCopies(150, 0.9));
        assertEquals(Material.IRON_INGOT, BreakListener.smelted(Material.RAW_IRON));
        assertEquals(Material.DIAMOND, BreakListener.smelted(Material.DIAMOND));
    }

    @Test
    public void hudTextIsStable() {
        assertEquals("+3.2", HudFormat.gain(3.24));
        assertEquals("+12", HudFormat.gain(12));
        assertTrue(HudFormat.xpPopup("Mining", 3.2, 7, 1234).startsWith("+3.2 Mining"));
        String line = HudFormat.plain(HudFormat.line(80, 100, 0, 45, null, 30, 100, 0));
        assertTrue(line.contains("80/100"));
        assertTrue(line.contains("45"));
        assertTrue(line.contains("30/100"));
        assertEquals("1,234", DamageIndicators.number(1234));
    }

    @Test
    public void shippedConfigMatchesTheCodeDefaults() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/items.yml")) {
            assertNotNull(in);
            yaml.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        ItemsSettings shipped = new ItemsSettings(yaml);
        ItemsSettings code = new ItemsSettings(new YamlConfiguration());
        assertEquals(code.maxHealth, shipped.maxHealth, 0);
        assertEquals(code.damageBonus, shipped.damageBonus, 0);
        assertEquals(code.incomingScale, shipped.incomingScale, 0);
        assertEquals(code.incomingScaleByCause, shipped.incomingScaleByCause);
        assertEquals(code.magicalPower, shipped.magicalPower);
        assertEquals(code.perMagicalPower, shipped.perMagicalPower);
        assertEquals(code.petRarityXp, shipped.petRarityXp);
        assertEquals(code.fortuneOres, shipped.fortuneOres);
        assertEquals(code.heldTypes, shipped.heldTypes);
        assertEquals(code.hudPeriodTicks, shipped.hudPeriodTicks);
        assertEquals(code.enchantCostBase, shipped.enchantCostBase, 0);
        assertEquals(code.bagUpgradeCost, shipped.bagUpgradeCost, 0);
        assertEquals(code.regenPercentPerSecond, shipped.regenPercentPerSecond, 0);
    }
}
