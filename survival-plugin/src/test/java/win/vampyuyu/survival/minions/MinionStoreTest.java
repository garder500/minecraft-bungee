package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import win.vampyuyu.survival.core.JsonStore;

public class MinionStoreTest {
    private Path root;
    private final Logger log = Logger.getAnonymousLogger();
    private JsonStore json;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("minions-store");
        json = new JsonStore(root, log);
        log.setUseParentHandlers(false);
    }

    @After
    public void tearDown() throws Exception {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private MinionStore store() {
        return new MinionStore(json, root.toFile(), log);
    }

    private static MinionData minion(String owner, int x) {
        MinionData d = new MinionData();
        d.id = UUID.randomUUID().toString();
        d.uid = UUID.randomUUID().toString();
        d.owner = owner;
        d.island = "island-1";
        d.world = "sb_world";
        d.x = x;
        d.y = 64;
        d.z = 3;
        d.type = "cobblestone";
        d.tier = 4;
        d.progress = 123.5;
        d.fuel = "coal";
        d.fuelMs = 987.25;
        d.upgrades.add("compactor");
        d.skin = "pumpkin_hat";
        d.storage.put("m:COBBLESTONE", 99L);
        d.produced = 4242;
        d.spread = 3;
        d.lastTick = 1234567890123L;
        return d;
    }

    @Test
    public void everythingRoundTripsThroughTheFiles() {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        MinionData two = minion("owner-a", 2);
        MinionData three = minion("owner-b", 3);
        a.add(one);
        a.add(two);
        a.add(three);
        a.spendUid("uid-spent-1");
        a.saveNow("owner-a");
        a.saveNow("owner-b");
        a.close();

        MinionStore b = store();
        b.loadAll();
        assertEquals(3, b.size());
        assertEquals(2, b.countOwned("owner-a"));
        assertEquals(3, b.countOnIsland("island-1"));
        MinionData back = b.get(one.id).orElseThrow();
        assertEquals(one.uid, back.uid);
        assertEquals(4, back.tier);
        assertEquals(123.5, back.progress, 0);
        assertEquals("coal", back.fuel);
        assertEquals(987.25, back.fuelMs, 0);
        assertEquals(java.util.List.of("compactor"), back.upgrades);
        assertEquals("pumpkin_hat", back.skin);
        assertEquals(99L, back.storage.get("m:COBBLESTONE").longValue());
        assertEquals(4242, back.produced);
        assertEquals(3, back.spread);
        assertEquals(1234567890123L, back.lastTick);
        assertTrue(b.ledger().isSpent("uid-spent-1"));
        assertEquals(one.id, b.atBlock("sb_world", 1, 64, 3).orElseThrow().id);
        assertEquals(3, b.inChunk("sb_world", 0, 0).size());
    }

    @Test
    public void removingTheLastMinionDeletesTheOwnersFile() {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        a.saveNow("owner-a");
        assertTrue(new File(root.toFile(), "minions/owner-a.json").exists());
        a.remove(one.id);
        a.saveNow("owner-a");
        assertFalse(new File(root.toFile(), "minions/owner-a.json").exists());
        a.close();
    }

    @Test
    public void aHandEditedFileWithDuplicatesLoadsEachMinionOnce() throws Exception {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        a.saveNow("owner-a");
        a.close();
        Files.copy(new File(root.toFile(), "minions/owner-a.json").toPath(), new File(root.toFile(), "minions/owner-copy.json").toPath());
        MinionStore b = store();
        b.loadAll();
        assertEquals(1, b.size());
    }

    @Test
    public void addingTwoMinionsOnTheSameBlockIsRefused() {
        MinionStore a = store();
        a.add(minion("owner-a", 1));
        assertThrows(IllegalStateException.class, () -> a.add(minion("owner-b", 1)));
        a.close();
    }

    @Test
    public void manyQuickSavesLeaveTheLastStateOnDisk() {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        for (int i = 1; i <= 200; i++) {
            one.produced = i;
            a.markDirty(one);
            if (i % 3 == 0) {
                a.saveNow("owner-a");
            } else {
                a.flushDirty();
            }
        }
        a.close();
        MinionStore b = store();
        b.loadAll();
        assertEquals(200, b.get(one.id).orElseThrow().produced);
    }

    @Test
    public void aSnapshotIsTakenBeforeTheWriteSoLaterChangesDoNotLeakIn() throws Exception {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        a.saveNow("owner-a");
        one.storage.put("m:COBBLESTONE", 1L);
        // not saved: the file still has the old value
        MinionStore b = store();
        b.loadAll();
        assertEquals(99L, b.get(one.id).orElseThrow().storage.get("m:COBBLESTONE").longValue());
        a.close();
    }

    @Test
    public void aSaveAfterCloseStillReachesTheDisk() {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        a.close();
        one.produced = 7;
        a.saveNow("owner-a");
        MinionStore b = store();
        b.loadAll();
        assertEquals(7, b.get(one.id).orElseThrow().produced);
    }

    @Test
    public void ledgerIsBoundedAndRejectsRepeats() {
        UidLedger ledger = new UidLedger(16);
        assertTrue(ledger.spend("a"));
        assertFalse(ledger.spend("a"));
        for (int i = 0; i < 100; i++) {
            ledger.spend("u" + i);
        }
        assertEquals(16, ledger.size());
        assertTrue(ledger.isSpent("u99"));
        assertFalse(ledger.isSpent("a"));
    }

    @Test
    public void incompleteEntriesAreIgnored() throws Exception {
        Files.createDirectories(root.resolve("minions"));
        Files.writeString(root.resolve("minions/broken.json"), "{\"version\":1,\"minions\":[{\"id\":\"\"},{\"id\":\"x\"}]}");
        MinionStore a = store();
        a.loadAll();
        assertEquals(0, a.size());
    }

    @Test
    public void aPlacedMinionsUidIsSpentAgainOnLoadEvenWithoutTheLedgerFile() throws Exception {
        MinionStore a = store();
        MinionData one = minion("owner-a", 1);
        a.add(one);
        a.saveNow("owner-a");
        a.close();
        Files.deleteIfExists(root.resolve("minions/ledger.json"));

        MinionStore b = store();
        b.loadAll();
        assertTrue(b.ledger().isSpent(one.uid));
        assertFalse(b.spendUid(one.uid));
    }
}
