package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.SplittableRandom;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.Test;
import win.vampyuyu.survival.minions.MinionConfig.Type;

public class MinionLogicTest {
    private MinionConfig cfg;
    private MinionLogic logic;
    private Type cobble;

    @Before
    public void setUp() {
        MockBukkit.mock();
        cfg = TestConfig.load();
        logic = new MinionLogic(cfg, new SplittableRandom(42));
        cobble = cfg.types.get("cobblestone");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private MinionData minion(String type, int tier) {
        MinionData d = new MinionData();
        d.id = "m1";
        d.type = type;
        d.tier = tier;
        d.lastTick = 1_000_000;
        return d;
    }

    private static final long HOUR = 3_600_000L;

    @Test
    public void tierOneCobbleMakesOneItemEvery28Seconds() {
        MinionData d = minion("cobblestone", 1);
        logic.advance(d, cobble, d.lastTick + 28_000L * 10, true);
        assertEquals(10, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(10, d.produced);
    }

    @Test
    public void storageCapStopsProductionAtOneStackOnTierOne() {
        MinionData d = minion("cobblestone", 1);
        Production.Outcome o = logic.advance(d, cobble, d.lastTick + 24 * HOUR, true);
        assertTrue(o.stalled());
        assertEquals(64, d.storage.get("m:COBBLESTONE").longValue());
        // collecting makes room, the held cycle finishes at once
        d.storage.put("m:COBBLESTONE", 60L);
        Production.Outcome again = logic.advance(d, cobble, d.lastTick + 1, true);
        assertEquals(1, again.cycles());
        assertEquals(61, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void tierElevenFillsThirtyStacksInTheExpectedTime() {
        MinionData d = minion("cobblestone", 11);
        // 14s per cobble: 30 * 64 = 1920 items = 26880 s = 7h28m; give it 8 hours
        Production.Outcome o = logic.advance(d, cobble, d.lastTick + 8 * HOUR, true);
        assertTrue(o.stalled());
        assertEquals(1920, d.storage.get("m:COBBLESTONE").longValue());
        MinionData d2 = minion("cobblestone", 11);
        logic.advance(d2, cobble, d2.lastTick + 7 * HOUR, true);
        assertEquals(1800, d2.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void catchUpIsCappedByTheMaxWindow() {
        cfg.maxCatchupHours = 1;
        MinionData d = minion("cobblestone", 11);
        logic.advance(d, cobble, d.lastTick + 100 * HOUR, true);
        assertEquals(257, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void aClockThatGoesBackProducesNothingAndDoesNotBreakTheMinion() {
        MinionData d = minion("cobblestone", 1);
        long before = d.lastTick;
        Production.Outcome o = logic.advance(d, cobble, before - 5000, true);
        assertEquals(0, o.cycles());
        assertEquals(before - 5000, d.lastTick);
    }

    @Test
    public void blockedTerrainProducesNothingAndKeepsFuel() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "coal";
        d.fuelMs = 1000;
        logic.advance(d, cobble, d.lastTick + 100 * HOUR, false);
        assertTrue(d.storage.isEmpty());
        assertEquals(1000, d.fuelMs, 1e-9);
        assertEquals("coal", d.fuel);
    }

    @Test
    public void speedsAddUpFuelAndUpgrades() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "enchanted_coal";
        d.fuelMs = HOUR;
        d.upgrades = List.of("flycatcher", "mithril_infusion");
        MinionLogic.Speeds s = logic.speeds(d, cobble);
        assertEquals(1.30, s.plain(), 1e-9);
        assertEquals(1.40, s.fuel(), 1e-9);
        assertEquals(0.8, s.burn(), 1e-9);
        assertEquals(28 / 1.40, logic.secondsPerItem(d, cobble), 1e-9);
    }

    @Test
    public void catalystStacksWithEverythingButOnlyOneFuelAtATime() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "hyper_catalyst";
        d.fuelMs = HOUR;
        d.upgrades = List.of("flycatcher");
        assertEquals(1 + 0.2 + 3.0, logic.speeds(d, cobble).fuel(), 1e-9);
    }

    @Test
    public void fuelThatDoesNotSuitTheKindAddsNothing() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "hamster_wheel";
        d.fuelMs = HOUR;
        assertEquals(1.0, logic.speeds(d, cobble).fuel(), 1e-9);
        MinionData wheat = minion("wheat", 1);
        wheat.fuel = "hamster_wheel";
        wheat.fuelMs = HOUR;
        assertEquals(1.5, logic.speeds(wheat, cfg.types.get("wheat")).fuel(), 1e-9);
    }

    @Test
    public void fuelBurnsOutDuringCatchUpAndSpeedDropsAtThatMoment() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "catalyst"; // +200 percent for the fuel
        d.fuelMs = 1000L * 280; // 280 s of fuel: 840 s of work = 30 cobble
        logic.advance(d, cobble, d.lastTick + 280_000L + 280_000L, true);
        // 30 cobble from the fuel window + 10 more in the plain 280 s
        assertEquals(40, d.storage.get("m:COBBLESTONE").longValue());
        assertNull(d.fuel);
        assertEquals(0, d.fuelMs, 1e-9);
    }

    @Test
    public void unknownFuelIsDroppedSafely() {
        MinionData d = minion("cobblestone", 1);
        d.fuel = "removed_from_config";
        d.fuelMs = 99999;
        logic.advance(d, cobble, d.lastTick + 28_000, true);
        assertNull(d.fuel);
        assertEquals(1, d.storage.get("m:COBBLESTONE").longValue());
    }

    @Test
    public void autoSmelterChangesTheProduct() {
        MinionData d = minion("cobblestone", 3);
        d.upgrades = List.of("auto_smelter");
        logic.advance(d, cobble, d.lastTick + 10 * 24_000L, true);
        assertNull(d.storage.get("m:COBBLESTONE"));
        assertEquals(10, d.storage.get("m:STONE").longValue());
    }

    @Test
    public void compactorTurnsAFullRatioIntoOneEnchantedItem() {
        MinionData d = minion("cobblestone", 11);
        d.upgrades = List.of("compactor");
        logic.advance(d, cobble, d.lastTick + 160 * 14_000L + 3 * 14_000L, true);
        assertEquals(3, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(1, d.storage.get("c:minion_enchanted_cobblestone").longValue());
    }

    @Test
    public void compactorKeepsTheMinionFromFillingUp() {
        MinionData d = minion("cobblestone", 1);
        d.upgrades = List.of("compactor");
        // tier I has one slot; raw cobble needs it, the enchanted item needs a second: the conversion is skipped
        logic.advance(d, cobble, d.lastTick + 24 * HOUR, true);
        assertEquals(64, d.storage.get("m:COBBLESTONE").longValue());
        assertNull(d.storage.get("c:minion_enchanted_cobblestone"));
        // tier III has 6 slots
        MinionData big = minion("cobblestone", 3);
        big.upgrades = List.of("compactor");
        logic.advance(big, cobble, big.lastTick + 20 * 24_000L * 160 / 20 * 1, true);
        assertTrue(big.storage.get("c:minion_enchanted_cobblestone") >= 1);
    }

    @Test
    public void superCompactorGoesTwoLevelsAndCountsInTotalItems() {
        cfg.compactRatio = 4;
        cfg.superRatio = 3;
        logic = new MinionLogic(cfg, new SplittableRandom(1));
        MinionData d = minion("cobblestone", 11);
        d.upgrades = List.of("super_compactor");
        logic.advance(d, cobble, d.lastTick + 30 * 28_000L / 2, true);
        // 30 cobble at tier XI (14 s each): 7 enchanted + 2 raw; 7 enchanted: 2 super + 1 enchanted
        assertEquals(2, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(1, d.storage.get("c:minion_enchanted_cobblestone").longValue());
        assertEquals(2, d.storage.get("c:minion_super_cobblestone").longValue());
    }

    @Test
    public void superCompactorReplacesTheCompactorWhenBothAreListed() {
        MinionData d = minion("cobblestone", 1);
        d.upgrades = List.of("compactor", "super_compactor");
        assertEquals(MinionConfig.Effect.SUPER_COMPACT, logic.compactMode(d));
        d.upgrades = List.of("compactor");
        assertEquals(MinionConfig.Effect.COMPACT, logic.compactMode(d));
        d.upgrades = List.of();
        assertNull(logic.compactMode(d));
    }

    @Test
    public void diamondSpreadingAddsOneDiamondPerTenItems() {
        MinionData d = minion("cobblestone", 11);
        d.upgrades = List.of("diamond_spreading");
        logic.advance(d, cobble, d.lastTick + 25 * 14_000L, true);
        assertEquals(25, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(2, d.storage.get("m:DIAMOND").longValue());
        assertEquals(5, d.spread);
        assertEquals(27, d.produced);
    }

    @Test
    public void diamondSpreadingCounterSurvivesSaves() {
        MinionData d = minion("cobblestone", 11);
        d.upgrades = List.of("diamond_spreading");
        for (int i = 0; i < 25; i++) {
            logic.advance(d, cobble, d.lastTick + 14_000, true);
        }
        assertEquals(2, d.storage.get("m:DIAMOND").longValue());
    }

    @Test
    public void expanderWidensTheArea() {
        MinionData d = minion("cobblestone", 1);
        assertEquals(2, logic.radius(d));
        d.upgrades = List.of("minion_expander");
        assertEquals(3, logic.radius(d));
        d.tier = 11;
        assertEquals(4, logic.radius(d));
    }

    @Test
    public void multiDropTypesRollEachDropAndRespectAmountsAndChance() {
        Type wheat = cfg.types.get("wheat");
        MinionData d = minion("wheat", 11);
        logic.advance(d, wheat, d.lastTick + 200 * 15_000L * 2 / 2, true);
        long wheats = d.storage.get("m:WHEAT");
        long seeds = d.storage.getOrDefault("m:WHEAT_SEEDS", 0L);
        assertTrue("wheat " + wheats, wheats >= 200 && wheats <= 400);
        assertTrue("seeds " + seeds, seeds > 0 && seeds < 400);
    }

    @Test
    public void weightedFishingPicksExactlyOneDropPerCycle() {
        Type fishing = cfg.types.get("fishing");
        MinionData d = minion("fishing", 11);
        long cycles = 500;
        logic.advance(d, fishing, d.lastTick + (long) (cycles * cfg.cycleMillis(fishing, 11)), true);
        long cod = d.storage.getOrDefault("m:COD", 0L);
        long salmon = d.storage.getOrDefault("m:SALMON", 0L);
        assertTrue(cod > salmon);
        long fish = cod + salmon + d.storage.getOrDefault("m:TROPICAL_FISH", 0L) + d.storage.getOrDefault("m:PUFFERFISH", 0L)
            + d.storage.getOrDefault("m:SPONGE", 0L);
        assertTrue(fish >= 450 && fish <= 500);
    }

    @Test
    public void rollIsDeterministicWithASeed() {
        MinionLogic a = new MinionLogic(cfg, new SplittableRandom(7));
        MinionLogic b = new MinionLogic(cfg, new SplittableRandom(7));
        Type zombie = cfg.types.get("zombie");
        for (int i = 0; i < 50; i++) {
            assertEquals(a.roll(zombie), b.roll(zombie));
        }
    }

    @Test
    public void nothingIsLostWhenTheStorageIsAlmostFull() {
        Type clay = cfg.types.get("clay"); // 2-4 per cycle
        MinionData d = minion("clay", 1);
        d.storage.put("m:CLAY_BALL", 62L);
        long before = d.storage.get("m:CLAY_BALL");
        logic.advance(d, clay, d.lastTick + 24 * HOUR, true);
        long after = d.storage.get("m:CLAY_BALL");
        assertTrue(after >= before && after <= 64);
        assertEquals(after - before, d.produced);
    }

    @Test
    public void compactionKeysExistOnlyForProducibleMaterials() {
        Compaction c = logic.compaction();
        assertEquals("c:minion_enchanted_cobblestone", c.compactedKey("m:COBBLESTONE"));
        assertNull(c.compactedKey("m:BEDROCK"));
        assertNull(c.compactedKey("c:minion_enchanted_cobblestone"));
        assertEquals("c:minion_super_cobblestone", c.superKey("c:minion_enchanted_cobblestone"));
        assertNull(c.superKey("m:COBBLESTONE"));
    }

    @Test
    public void tierUpCostsComeFromConfig() {
        assertEquals(new MinionConfig.TierUp(false, 8), cfg.tierUp(1));
        assertEquals(new MinionConfig.TierUp(false, 8), cfg.tierUp(7));
        assertEquals(new MinionConfig.TierUp(true, 2), cfg.tierUp(8));
        assertEquals(new MinionConfig.TierUp(true, 8), cfg.tierUp(10));
    }

    @Test
    public void randomnessIsInjectable() {
        RandomGenerator always = new SplittableRandom(3);
        assertNotNull(new MinionLogic(cfg, always).roll(cobble));
        assertEquals(Map.of(Material.COBBLESTONE, 1), logic.roll(cobble));
    }

    // ---------------------------------------------------------------- review fixes: partial drops

    @Test
    public void aTierOneCowKeepsFillingBeefOverEightHoursEvenWhenLeatherHasNoSlot() {
        Type cow = cfg.types.get("cow");
        MinionData d = minion("cow", 1);
        Production.Outcome out = logic.advance(d, cow, d.lastTick + 8 * HOUR, true);
        // 1 slot: beef fills it (leather is rolled but never fits), and only then does the minion stall.
        assertEquals(64, d.storage.get("m:BEEF").longValue());
        assertFalse(d.storage.containsKey("m:LEATHER"));
        assertTrue(out.stalled());
        assertTrue(logic.storage(d).isCompletelyFull());
    }

    @Test
    public void aTierOneWheatMinionDoesNotFreezeOnSeeds() {
        Type wheat = cfg.types.get("wheat");
        MinionData d = minion("wheat", 1);
        logic.advance(d, wheat, d.lastTick + 8 * HOUR, true);
        assertEquals(64, d.storage.get("m:WHEAT").longValue());
        assertFalse(d.storage.containsKey("m:WHEAT_SEEDS"));
    }

    @Test
    public void aTierOneFisherKeepsGoingWhenItRollsAFishWithNoSlot() {
        Type fishing = cfg.types.get("fishing");
        MinionData d = minion("fishing", 1);
        Production.Outcome out = logic.advance(d, fishing, d.lastTick + 48 * HOUR, true);
        // only one kind ever gets the slot, and it ends up as a full stack instead of freezing at the first fish
        assertEquals(1, d.storage.size());
        assertEquals(64L, d.storage.values().iterator().next().longValue());
        assertTrue(out.stalled());
    }

    @Test
    public void diamondSpreadingNeverBlocksATierOneCobblestoneMinion() {
        MinionData d = minion("cobblestone", 1);
        d.upgrades.add("diamond_spreading");
        Production.Outcome out = logic.advance(d, cobble, d.lastTick + 8 * HOUR, true);
        // The bonus diamond has no second slot: it is dropped, the counter keeps moving, the cobblestone fills up.
        assertEquals(64, d.storage.get("m:COBBLESTONE").longValue());
        assertFalse(d.storage.containsKey("m:DIAMOND"));
        assertTrue(out.stalled());
        assertTrue(d.spread >= 0 && d.spread < cfg.spreadEvery);
        assertTrue(d.produced >= 64);
    }

    @Test
    public void aMinionWithTwoSlotsKeepsTheBonusDiamond() {
        MinionData d = minion("cobblestone", 2);
        d.upgrades.add("diamond_spreading");
        logic.advance(d, cobble, d.lastTick + 28_000L * 20, true);
        assertEquals(20, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(2, d.storage.get("m:DIAMOND").longValue());
    }

    @Test
    public void aCompletelyFullStorageStallsAndLosesNothing() {
        MinionData d = minion("cobblestone", 1);
        d.storage.put("m:COBBLESTONE", 64L);
        assertFalse(logic.produce(d, cobble));
        assertEquals(64, d.storage.get("m:COBBLESTONE").longValue());
        assertEquals(0, d.produced);
    }

    @Test
    public void itemsPerHourFollowsTheSpeed() {
        MinionData d = minion("cobblestone", 1);
        double plain = logic.itemsPerHour(d, cobble);
        assertEquals(3600d / 28d, plain, 1e-6);
        d.upgrades.add("flycatcher");
        assertEquals(plain * 1.2, logic.itemsPerHour(d, cobble), 1e-6);
    }
}
