package win.vampyuyu.survival.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

class NpcMemoryTest {
    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final AtomicLong clock = new AtomicLong(1_000_000);

    private NpcMemory memory() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.NPC), null);
        return new NpcMemory(db, NpcConfig.load(db), clock::get);
    }

    @Test
    void theShippedSeedLoadsCleanly() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.NPC), null);
        NpcConfig cfg = NpcConfig.load(db);
        assertEquals(List.of(), cfg.problems());
        assertEquals(9, cfg.reactions().size());
    }

    @Test
    void aStrangerGetsTheDefaultGreeting() {
        NpcMemory m = memory();
        String line = m.greet(alice, "Alice", "bazaar").orElseThrow();
        assertTrue(line.contains("Welcome, Alice"), line);
    }

    @Test
    void aGuardianSlayerIsRecognisedAndOtherNpcsAreNot() {
        NpcMemory m = memory();
        m.recordFact(alice, "defeated.guardian");
        assertTrue(m.greet(alice, "Alice", "bazaar").orElseThrow().contains("bane of a Guardian"));
        UUID bob = UUID.randomUUID();
        assertTrue(m.greet(bob, "Bob", "bazaar").orElseThrow().contains("Welcome, Bob"));
    }

    @Test
    void theHighestPriorityReactionWins() {
        NpcMemory m = memory();
        m.recordFact(alice, "rescued");
        m.recordFact(alice, "breath.survived");
        assertTrue(m.greet(alice, "Alice", "x").orElseThrow().contains("breathed the sky"));
    }

    @Test
    void factsKeepTheirHighestValue() {
        NpcMemory m = memory();
        m.recordFact(alice, "k", 3);
        m.recordFact(alice, "k", 1);
        assertEquals(3, m.fact(alice, "k"));
        assertEquals(0, m.fact(alice, "unknown"));
    }

    @Test
    void aVisitRaisesTheRelationOncePerCooldown() {
        NpcMemory m = memory();
        m.greet(alice, "Alice", "bazaar");
        m.greet(alice, "Alice", "bazaar");
        assertEquals(1, m.relation(alice, "bazaar"));
        clock.addAndGet(601_000);
        m.greet(alice, "Alice", "bazaar");
        assertEquals(2, m.relation(alice, "bazaar"));
        assertEquals(0, m.relation(alice, "other"));
    }

    @Test
    void relationIsClampedBySettings() {
        NpcMemory m = memory();
        m.addRelation(alice, "n", 500);
        assertEquals(100, m.relation(alice, "n"));
        m.addRelation(alice, "n", -1000);
        assertEquals(-100, m.relation(alice, "n"));
    }

    @Test
    void pricesFollowReputationAndStayInTheWindow() {
        NpcMemory m = memory();
        assertEquals(0, m.priceDeltaPct(alice, "n"));
        m.addRelation(alice, "n", 30);
        assertEquals(-5, m.priceDeltaPct(alice, "n"));
        m.addRelation(alice, "n", 40);
        m.recordFact(alice, "rescued");
        assertEquals(-10, m.priceDeltaPct(alice, "n"), "friend + ally + rescued = -15, clamped to -10");
        m.addRelation(alice, "m", -50);
        assertEquals(10, m.priceDeltaPct(alice, "m"));
    }

    @Test
    void localStateIsKept() {
        NpcMemory m = memory();
        assertEquals("", m.state("guard", "spot"));
        m.setState("guard", "spot", "gate");
        m.setState("guard", "spot", "market");
        assertEquals("market", m.state("guard", "spot"));
    }

    @Test
    void anNpcSpecificReactionBeatsAGenericOneOfTheSamePriority() {
        NpcRules rules = new NpcRules(List.of(
            new NpcReaction("a_generic", "*", NpcReaction.Kind.GREETING, "", 1, -1000, 1000, 5, 0, "generic"),
            new NpcReaction("z_special", "smith", NpcReaction.Kind.GREETING, "", 1, -1000, 1000, 5, 0, "special")), -10, 10);
        assertEquals("special", rules.greeting("smith", "A", Map.of(), 0).orElseThrow());
        assertEquals("generic", rules.greeting("cook", "A", Map.of(), 0).orElseThrow());
    }

    @Test
    void applyRoundsDownAndNeverReachesZero() {
        assertEquals(95, NpcRules.apply(100, -5));
        assertEquals(1, NpcRules.apply(1, -10));
        assertEquals(0, NpcRules.apply(0, 10));
    }
}
