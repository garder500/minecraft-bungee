package win.vampyuyu.survival.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The house stats and the map from the names used before the rework. */
public class StatTest {
    private static double factor(String name, Stat stat) {
        return Stat.resolve(name).stream().filter(c -> c.stat() == stat).mapToDouble(Stat.Converted::factor).sum();
    }

    @Test
    public void theHouseStatsAreTheOnlyOnes() {
        assertEquals(List.of("VITALITY", "WARD", "MIGHT", "ATTUNEMENT", "AETHER", "SWIFTNESS", "MINING_YIELD", "FARMING_YIELD",
            "FORAGING_YIELD", "HASTE", "LURE", "DEEP_LURE", "FORTUNE", "FLIGHT_ENERGY"),
            java.util.Arrays.stream(Stat.values()).map(Enum::name).toList());
        assertEquals(100, Stat.VITALITY.base(), 0);
        assertEquals(100, Stat.AETHER.base(), 0);
        assertEquals(100, Stat.SWIFTNESS.base(), 0);
        assertTrue(Stat.ATTUNEMENT.percent());
    }

    @Test
    public void currentNamesMapToThemselvesWhateverTheSpelling() {
        assertEquals(1.0, factor("ward", Stat.WARD), 0);
        assertEquals(1.0, factor("WARD", Stat.WARD), 0);
        assertEquals(1.0, factor("Deep Lure", Stat.DEEP_LURE), 0);
        assertEquals(1.0, factor("deep-lure", Stat.DEEP_LURE), 0);
        assertEquals(Stat.MINING_YIELD, Stat.parse("mining_yield"));
        assertNull(Stat.parse("strength"));
    }

    @Test
    public void oldNamesConvertLikeTheBrief() {
        assertEquals(1.0, factor("HEALTH", Stat.VITALITY), 0);
        assertEquals(1.0, factor("DEFENSE", Stat.WARD), 0);
        assertEquals(1.0, factor("true_defense", Stat.WARD), 0);
        assertEquals(1.0, factor("STRENGTH", Stat.MIGHT), 0);
        assertEquals(0.25, factor("CRIT_DAMAGE", Stat.MIGHT), 0);
        assertEquals(0.5, factor("FEROCITY", Stat.MIGHT), 0);
        assertEquals(1.0, factor("ABILITY_DAMAGE", Stat.MIGHT), 0);
        assertEquals(1.0, factor("INTELLIGENCE", Stat.AETHER), 0);
        assertEquals(1.0, factor("SPEED", Stat.SWIFTNESS), 0);
        assertEquals(1.0, factor("MINING_FORTUNE", Stat.MINING_YIELD), 0);
        assertEquals(1.0, factor("FARMING_FORTUNE", Stat.FARMING_YIELD), 0);
        assertEquals(1.0, factor("FORAGING_FORTUNE", Stat.FORAGING_YIELD), 0);
        assertEquals(1.0, factor("MINING_SPEED", Stat.HASTE), 0);
        assertEquals(1.0, factor("FISHING_SPEED", Stat.LURE), 0);
        assertEquals(1.0, factor("SEA_CREATURE_CHANCE", Stat.DEEP_LURE), 0);
        assertEquals(1.0, factor("MAGIC_FIND", Stat.FORTUNE), 0);
        assertEquals(1.0, factor("PET_LUCK", Stat.FORTUNE), 0);
    }

    @Test
    public void droppedAndUnknownNamesGiveNothing() {
        assertTrue(Stat.resolve("CRIT_CHANCE").isEmpty());
        assertTrue(Stat.resolve("ATTACK_SPEED").isEmpty());
        assertTrue(Stat.known("CRIT_CHANCE"));
        assertTrue(Stat.resolve("luck").isEmpty());
        assertFalse(Stat.known("luck"));
        assertTrue(Stat.legacyKeys().contains("strength"));
        assertFalse(Stat.legacyKeys().contains("might"));
    }

    @Test
    public void elementsParse() {
        assertEquals(Element.EARTH, Element.parse("earth"));
        assertEquals(Element.ASTRAL, Element.parse(" Astral "));
        assertNull(Element.parse(""));
        assertNull(Element.parse(null));
        assertNull(Element.parse("water"));
        assertEquals(5, Element.values().length);
    }
}
