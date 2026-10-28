package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlayerProfilesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private JsonStore store;

    public static final class Eco {
        public double coins;
    }

    public static final class Skills {
        public int mining = 1;
    }

    @Before
    public void setUp() {
        store = new JsonStore(tmp.getRoot().toPath(), Logger.getAnonymousLogger());
    }

    private PlayerProfiles profiles() {
        return new PlayerProfiles(store, Logger.getAnonymousLogger());
    }

    private Path file() {
        return tmp.getRoot().toPath().resolve("players/" + id + ".json");
    }

    @Test
    public void sectionsRoundTripThroughDisk() {
        PlayerProfiles first = profiles();
        first.playerJoined(id, "Steve");
        first.section(id, "economy", Eco.class, Eco::new).coins = 12.5;
        first.section(id, "skills", Skills.class, Skills::new).mining = 9;
        first.markDirty(id);
        first.playerQuit(id);
        first.shutdown();
        assertTrue(Files.exists(file()));

        PlayerProfiles second = profiles();
        assertEquals(12.5, second.section(id, "economy", Eco.class, Eco::new).coins, 0);
        assertEquals(9, second.section(id, "skills", Skills.class, Skills::new).mining);
        assertEquals("Steve", second.lastName(id));
        second.shutdown();
    }

    @Test
    public void cleanProfileIsNotRewrittenButDirtyOneIs() throws Exception {
        PlayerProfiles profiles = profiles();
        Eco eco = profiles.section(id, "economy", Eco.class, Eco::new);
        profiles.saveDirty();
        profiles.awaitWrites();
        assertFalse("never marked dirty, nothing written", Files.exists(file()));
        eco.coins = 3;
        profiles.markDirty(id);
        profiles.saveDirty();
        profiles.awaitWrites();
        assertTrue(Files.readString(file()).contains("\"coins\": 3.0"));
        profiles.shutdown();
    }

    @Test
    public void editWorksOfflineAndPersists() {
        PlayerProfiles profiles = profiles();
        profiles.edit(id, "economy", Eco.class, Eco::new, e -> e.coins += 4);
        profiles.edit(id, "economy", Eco.class, Eco::new, e -> e.coins += 4);
        profiles.shutdown();
        PlayerProfiles again = profiles();
        assertEquals(8, again.section(id, "economy", Eco.class, Eco::new).coins, 0);
        again.shutdown();
    }

    @Test
    public void unknownSectionsSurviveASave() {
        PlayerProfiles a = profiles();
        a.edit(id, "other_feature", Eco.class, Eco::new, e -> e.coins = 77);
        a.shutdown();
        PlayerProfiles b = profiles();
        b.edit(id, "economy", Eco.class, Eco::new, e -> e.coins = 1);
        b.shutdown();
        PlayerProfiles c = profiles();
        assertEquals(77, c.section(id, "other_feature", Eco.class, Eco::new).coins, 0);
        c.shutdown();
    }

    @Test
    public void quickRejoinSeesTheLatestData() {
        PlayerProfiles profiles = profiles();
        profiles.playerJoined(id, "Steve");
        profiles.edit(id, "economy", Eco.class, Eco::new, e -> e.coins = 99);
        profiles.playerQuit(id);
        profiles.playerJoined(id, "Steve");
        assertEquals(99, profiles.section(id, "economy", Eco.class, Eco::new).coins, 0);
        profiles.shutdown();
    }

    @Test
    public void corruptDocumentStartsEmptyAndKeepsACopy() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "garbage");
        PlayerProfiles profiles = profiles();
        assertEquals(0, profiles.section(id, "economy", Eco.class, Eco::new).coins, 0);
        assertTrue(Files.exists(file().resolveSibling(id + ".json.corrupt")));
        profiles.shutdown();
    }

    @Test(expected = IllegalStateException.class)
    public void sameKeyWithAnotherTypeIsRefused() {
        PlayerProfiles profiles = profiles();
        profiles.section(id, "economy", Eco.class, Eco::new);
        profiles.section(id, "economy", Skills.class, Skills::new);
    }
}
