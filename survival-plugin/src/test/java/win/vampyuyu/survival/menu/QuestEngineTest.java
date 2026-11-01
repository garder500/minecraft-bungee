package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.TestDb;
import win.vampyuyu.survival.menu.QuestEngine.Completion;
import win.vampyuyu.survival.menu.QuestEngine.Event;

public class QuestEngineTest {
    /** One quest row: {@code requires} are quest ids, {@code coins} the coin reward. */
    private static void quest(Db db, String id, String pool, int position, String name, String type, String target, int amount,
            double coins, String... requires) {
        TestDb.exec(db, "INSERT INTO quest (id, pool, position, name, type, target, amount, reward_coins) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, pool, position, name, type, target, amount, coins);
        for (int i = 0; i < requires.length; i++) {
            TestDb.exec(db, "INSERT INTO quest_requirement (quest_id, position, requires) VALUES (?, ?, ?)", id, i, requires[i]);
        }
    }

    /** The old test line, as rows: a tutorial chain (with a broken type and a missing prerequisite) and four daily quests. */
    private static QuestCatalog load() {
        try (Db db = Db.memory()) {
            quest(db, "island", "plan", 0, "Island", "ISLAND_CREATE", "", 1, 0);
            quest(db, "wood", "plan", 1, "Wood", "BREAK_BLOCK", "LOGS", 3, 100, "island");
            quest(db, "bench", "plan", 2, "Bench", "CRAFT_ITEM", "CRAFTING_TABLE", 1, 0, "wood");
            quest(db, "mining3", "plan", 3, "Mine", "SKILL_LEVEL", "MINING", 3, 0, "island");
            quest(db, "broken", "plan", 4, "Broken", "NOPE", "", 1, 0);
            quest(db, "orphan", "plan", 5, "Orphan", "FISH", "", 1, 0, "ghost");
            quest(db, "d1", "daily", 0, "D1", "KILL_MOB", "ZOMBIE", 2, 0);
            quest(db, "d2", "daily", 1, "D2", "FISH", "", 1, 0);
            quest(db, "d3", "daily", 2, "D3", "COINS_EARN", "", 100, 0);
            quest(db, "d4", "daily", 3, "D4", "BREAK_BLOCK", "ORES", 1, 0);
            return QuestRepo.load(db);
        }
    }

    private QuestCatalog catalog;
    private QuestEngine engine;

    @Before
    public void setUp() throws Exception {
        MockBukkit.mock();
        catalog = load();
        engine = new QuestEngine(catalog);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private static Event ev(QuestType type, String subject, int value) {
        return new Event(type, subject, value);
    }

    @Test
    public void brokenEntriesAreSkippedAndReported() {
        assertEquals(4, catalog.tutorial().size());
        assertEquals(2, catalog.problems().size());
        assertTrue(catalog.problems().stream().anyMatch(p -> p.contains("broken")));
        assertTrue(catalog.problems().stream().anyMatch(p -> p.contains("ghost")));
    }

    @Test
    public void lockedQuestsDoNotProgressUntilTheirPrerequisiteIsDone() {
        QuestData d = new QuestData();
        assertEquals(List.of("island"), engine.activeTutorial(d).stream().map(QuestDef::id).toList());
        assertTrue(engine.apply(d, ev(QuestType.BREAK_BLOCK, "OAK_LOG", 5)).isEmpty());
        assertFalse(d.progress.containsKey("wood"));
        List<Completion> done = engine.apply(d, ev(QuestType.ISLAND_CREATE, "ANY", 1));
        assertEquals(List.of("island"), done.stream().map(c -> c.def().id()).toList());
        assertEquals(List.of("wood", "mining3"), engine.activeTutorial(d).stream().map(QuestDef::id).toList());
    }

    @Test
    public void incrementsAreClampedAndCompleteExactlyOnce() {
        QuestData d = new QuestData();
        engine.apply(d, ev(QuestType.ISLAND_CREATE, "ANY", 1));
        assertTrue(engine.apply(d, ev(QuestType.BREAK_BLOCK, "OAK_LOG", 1)).isEmpty());
        assertTrue(engine.apply(d, ev(QuestType.BREAK_BLOCK, "STONE", 1)).isEmpty()); // not a log
        assertEquals(1, d.progress.get("wood").intValue());
        List<Completion> done = engine.apply(d, ev(QuestType.BREAK_BLOCK, "BIRCH_LOG", 50));
        assertEquals(1, done.size());
        assertTrue(d.completed.contains("wood"));
        assertFalse(d.progress.containsKey("wood"));
        // already completed: nothing more happens, no second reward
        assertTrue(engine.apply(d, ev(QuestType.BREAK_BLOCK, "OAK_LOG", 50)).isEmpty());
        assertEquals(1, d.completed.stream().filter("wood"::equals).count());
    }

    @Test
    public void negativeIncrementsAreIgnored() {
        QuestData d = new QuestData();
        engine.apply(d, ev(QuestType.ISLAND_CREATE, "ANY", 1));
        engine.apply(d, ev(QuestType.BREAK_BLOCK, "OAK_LOG", 2));
        engine.apply(d, ev(QuestType.BREAK_BLOCK, "OAK_LOG", -5));
        assertEquals(2, d.progress.get("wood").intValue());
    }

    @Test
    public void absoluteQuestsTrackTheHighestValueAndSyncCompletesChains() {
        QuestData d = new QuestData();
        // the player already has an island and mining 4 before the quests exist
        List<Completion> done = engine.sync(d, def -> switch (def.type()) {
            case ISLAND_CREATE -> OptionalInt.of(1);
            case SKILL_LEVEL -> OptionalInt.of(4);
            default -> OptionalInt.empty();
        });
        assertEquals(List.of("island", "mining3"), done.stream().map(c -> c.def().id()).toList());
        assertTrue(d.completed.containsAll(List.of("island", "mining3")));
        assertEquals(List.of("wood"), engine.activeTutorial(d).stream().map(QuestDef::id).toList());
    }

    @Test
    public void skillLevelOnlyCountsForTheMatchingSkill() {
        QuestData d = new QuestData();
        engine.apply(d, ev(QuestType.ISLAND_CREATE, "ANY", 1));
        assertTrue(engine.apply(d, ev(QuestType.SKILL_LEVEL, "FARMING", 9)).isEmpty());
        assertTrue(engine.apply(d, ev(QuestType.SKILL_LEVEL, "MINING", 2)).isEmpty());
        assertEquals(2, d.progress.get("mining3").intValue());
        assertEquals(1, engine.apply(d, ev(QuestType.SKILL_LEVEL, "MINING", 3)).size());
    }

    @Test
    public void trackedFallsBackToFirstActive() {
        QuestData d = new QuestData();
        assertEquals("island", engine.tracked(d).orElseThrow().def().id());
        engine.apply(d, ev(QuestType.ISLAND_CREATE, "ANY", 1));
        d.tracked = "mining3";
        assertEquals("mining3", engine.tracked(d).orElseThrow().def().id());
        d.tracked = "bench"; // locked: ignored
        assertEquals("wood", engine.tracked(d).orElseThrow().def().id());
    }

    // ------------------------------------------------------------ dailies

    @Test
    public void ensureDayRotatesOnceAndResetsProgress() {
        QuestData d = new QuestData();
        LocalDate day = LocalDate.of(2026, 10, 29);
        assertTrue(engine.ensureDay(d, day, 2));
        assertEquals(2, d.dailyIds.size());
        assertFalse(engine.ensureDay(d, day, 2));
        List<String> ids = List.copyOf(d.dailyIds);
        d.dailyProgress.put(ids.get(0), 1);
        assertTrue(engine.ensureDay(d, day.plusDays(1), 2));
        assertTrue(d.dailyProgress.isEmpty());
        assertNotEquals(ids, d.dailyIds);
    }

    @Test
    public void completingAllDailiesPaysTheBonusOnceAndBuildsAStreak() {
        QuestData d = new QuestData();
        LocalDate day = LocalDate.of(2026, 10, 29);
        for (int i = 0; i < 3; i++) {
            LocalDate today = day.plusDays(i);
            engine.ensureDay(d, today, 2);
            assertTrue(engine.claimDailyBonus(d, today).isEmpty());
            completeAllDailies(d);
            var bonus = engine.claimDailyBonus(d, today);
            assertEquals(i + 1, bonus.orElseThrow().streak());
            assertTrue("bonus must not be claimable twice", engine.claimDailyBonus(d, today).isEmpty());
        }
        assertEquals(3, d.dailyTotal);
        assertEquals(3, engine.liveStreak(d, day.plusDays(2)));
    }

    @Test
    public void missingADayBreaksTheStreak() {
        QuestData d = new QuestData();
        LocalDate day = LocalDate.of(2026, 10, 29);
        engine.ensureDay(d, day, 2);
        completeAllDailies(d);
        engine.claimDailyBonus(d, day);
        assertEquals(1, d.streak);
        // skip a day entirely
        LocalDate later = day.plusDays(2);
        assertEquals(0, engine.liveStreak(d, later));
        engine.ensureDay(d, later, 2);
        assertEquals(0, d.streak);
        completeAllDailies(d);
        assertEquals(1, engine.claimDailyBonus(d, later).orElseThrow().streak());
    }

    @Test
    public void dailyProgressMatchesTargetsAndCompletesOnce() {
        QuestData d = new QuestData();
        d.dailyDay = LocalDate.of(2026, 10, 29).toString();
        d.dailyIds = new java.util.ArrayList<>(List.of("d1"));
        assertTrue(engine.apply(d, ev(QuestType.KILL_MOB, "SKELETON", 1)).isEmpty());
        assertTrue(engine.apply(d, ev(QuestType.KILL_MOB, "ZOMBIE", 1)).isEmpty());
        List<Completion> done = engine.apply(d, ev(QuestType.KILL_MOB, "ZOMBIE", 1));
        assertEquals(1, done.size());
        assertTrue(done.get(0).daily());
        assertTrue(engine.apply(d, ev(QuestType.KILL_MOB, "ZOMBIE", 1)).isEmpty());
        assertEquals(2, engine.progress(d, catalog.dailyQuest("d1").orElseThrow(), true));
    }

    private void completeAllDailies(QuestData d) {
        for (QuestDef def : engine.dailyQuests(d)) {
            d.dailyDone.add(def.id());
        }
    }
}
