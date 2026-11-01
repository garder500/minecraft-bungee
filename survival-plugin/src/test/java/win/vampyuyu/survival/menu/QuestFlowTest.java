package win.vampyuyu.survival.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/**
 * The shipped plan, played through the engine: the glass step a player reported stuck (smelted glass, coloured glass,
 * the Frost ring) and the catch-up for players who built the ring while the glass quest could not end.
 */
public class QuestFlowTest {
    private Db db;
    private QuestEngine engine;

    @Before
    public void setUp() {
        MockBukkit.mock();
        db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        engine = new QuestEngine(QuestRepo.load(db));
    }

    @After
    public void tearDown() {
        db.close();
        MockBukkit.unmock();
    }

    private QuestData upTo(String lastDone) {
        QuestData data = new QuestData();
        for (QuestDef def : engine.catalog().tutorial()) {
            data.completed.add(def.id());
            if (def.id().equals(lastDone)) break;
        }
        return data;
    }

    private List<String> apply(QuestData data, QuestType type, String subject, int amount) {
        return engine.apply(data, new QuestEngine.Event(type, subject, amount)).stream().map(c -> c.def().id()).toList();
    }

    @Test
    public void aForgeRecipeOpensWithItsMainPathQuestNotWithASideQuest() {
        QuestData data = new QuestData();
        QuestDef lens = engine.catalog().tutorialQuest("q068").orElseThrow();
        assertEquals("Void Lens", engine.forgeLock(data, "void_lens").orElseThrow().name());
        data.completed.addAll(engine.catalog().tutorialQuest("q137").orElseThrow().requires());
        assertTrue("the side quest asking for 2 lenses is open", engine.isActive(data, engine.catalog().tutorialQuest("q137").orElseThrow()));
        assertTrue("the lens still waits for the main path", engine.forgeLock(data, "void_lens").isPresent());
        assertTrue("a side quest item opens with its side quest", engine.forgeLock(data, "solar_panel").isEmpty());
        data.completed.addAll(lens.requires());
        assertTrue(engine.forgeLock(data, "void_lens").isEmpty());
        data.completed.add(lens.id());
        assertTrue("stays open afterwards", engine.forgeLock(data, "void_lens").isEmpty());
        assertTrue("no quest asks for it", engine.forgeLock(new QuestData(), "no_such_item").isEmpty());
    }

    @Test
    public void aLensForgedBeforeItsQuestUnlockedIsNotAskedForAgain() {
        QuestData data = new QuestData();
        engine.apply(data, new QuestEngine.Event(QuestType.FORGE_ITEM, "void_lens", 1));
        QuestDef lens = engine.catalog().tutorial().stream()
            .filter(d -> d.type() == QuestType.FORGE_ITEM && d.target().equals("void_lens") && d.amount() == 1)
            .findFirst().orElseThrow();
        assertTrue("still locked", engine.sync(data, d -> java.util.OptionalInt.empty()).isEmpty());
        assertFalse(data.completed.contains(lens.id()));

        data.completed.addAll(lens.requires());
        assertTrue("active now", engine.isActive(data, lens));
        engine.sync(data, d -> java.util.OptionalInt.empty());
        assertTrue(data.completed.contains(lens.id()));
    }

    @Test
    public void aRetiredQuestStillInARecordDoesNotCountAsDone() {
        QuestData data = new QuestData();
        data.completed.add("q001");
        data.completed.add("q134");
        assertEquals(1, engine.completedCount(data));
    }

    @Test
    public void smeltedGlassAndColouredGlassFinishTheGlassQuests() {
        QuestData data = upTo("q045");
        assertEquals(List.of(), apply(data, QuestType.CRAFT_ITEM, "WHITE_STAINED_GLASS", 8));
        assertEquals(List.of("q046"), apply(data, QuestType.OBTAIN_ITEM, "GLASS", 8));
        assertEquals(List.of("q047"), apply(data, QuestType.CRAFT_ITEM, "WHITE_CONCRETE_POWDER", 8));
        apply(data, QuestType.OBTAIN_ITEM, "GLASS", 8);
        assertEquals(List.of("q048"), apply(data, QuestType.OBTAIN_ITEM, "LIME_STAINED_GLASS", 8));
        assertEquals("the Frost ring has 7 glass slots", List.of(), apply(data, QuestType.PLACE_BLOCK, "GLASS", 6));
        assertEquals(List.of("q049"), apply(data, QuestType.PLACE_BLOCK, "GLASS", 1));
        assertEquals(List.of("q050"), apply(data, QuestType.EVENT, "generator_built:frost", 1));
    }

    @Test
    public void aBuiltFrostRingCatchesUpAStuckChain() {
        QuestData data = upTo("q045");
        var done = engine.catchUp(data, new QuestEngine.Event(QuestType.EVENT, "generator_built:frost", 1));
        assertEquals(List.of("q046", "q047", "q048", "q049", "q050"), done.stream().map(c -> c.def().id()).toList());
        assertTrue(engine.isActive(data, engine.catalog().tutorialQuest("q051").orElseThrow()));
    }

    @Test
    public void catchUpNeverCrossesACategory() {
        QuestData data = new QuestData(); // nothing done: the variety category is not even open
        assertTrue(engine.catchUp(data, new QuestEngine.Event(QuestType.EVENT, "generator_built:frost", 1)).isEmpty());
        assertFalse(data.completed.contains("q050"));
        assertTrue("ordinary progress never skips", engine.catchUp(upTo("q045"),
            new QuestEngine.Event(QuestType.OBTAIN_ITEM, "GLASS", 64)).isEmpty());
    }
}
