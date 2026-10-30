package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The deletion of dungeon world folders must never touch anything else. */
public class WorldFilesTest {
    private Path root;
    private Path outside;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("worlds");
        outside = Files.createTempDirectory("outside");
        Files.writeString(outside.resolve("precious.txt"), "keep");
    }

    @After
    public void tearDown() throws IOException {
        deleteTree(root);
        deleteTree(outside);
    }

    private static void deleteTree(Path p) throws IOException {
        if (p == null || !Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (var s = Files.list(p)) {
                for (Path c : s.toList()) {
                    deleteTree(c);
                }
            }
        }
        Files.delete(p);
    }

    private Path world(String name) throws IOException {
        Path w = root.resolve(name);
        Files.createDirectories(w.resolve("region"));
        Files.writeString(w.resolve("level.dat"), "x");
        Files.writeString(w.resolve("region").resolve("r.0.0.mca"), "x");
        return w;
    }

    @Test
    public void onlyExactDungeonNamesCount() {
        assertTrue(WorldFiles.isDungeonWorldName("sb_dungeon_1"));
        assertTrue(WorldFiles.isDungeonWorldName("sb_dungeon_4021"));
        for (String bad : new String[] {"sb_dungeon_", "sb_dungeon_1x", "sb_dungeon_-1", "world", "sb_hub", "sb_dungeon_1/..",
                "../sb_dungeon_1", " sb_dungeon_1", "SB_DUNGEON_1", "sb_dungeon_1\n", null, ""}) {
            assertFalse("'" + bad + "'", WorldFiles.isDungeonWorldName(bad));
        }
    }

    @Test
    public void deletesADungeonWorldWithItsContent() throws IOException {
        Path w = world("sb_dungeon_12");
        assertTrue(WorldFiles.deleteWorldFolder(root, w));
        assertFalse(Files.exists(w));
        assertTrue("deleting a missing folder is a success", WorldFiles.deleteWorldFolder(root, w));
    }

    @Test
    public void refusesEverythingThatIsNotADungeonFolderBelowTheContainer() throws IOException {
        Path normal = world("world");
        assertFalse(WorldFiles.deleteWorldFolder(root, normal));
        assertTrue(Files.exists(normal));
        assertFalse("the container itself", WorldFiles.deleteWorldFolder(root, root));
        Path elsewhere = Files.createDirectories(outside.resolve("sb_dungeon_9"));
        assertFalse("outside the container", WorldFiles.deleteWorldFolder(root, elsewhere));
        assertTrue(Files.exists(elsewhere));
        Path sneaky = root.resolve("sb_dungeon_1").resolve("..").resolve("world");
        assertFalse(WorldFiles.deleteWorldFolder(root, sneaky));
        assertTrue(Files.exists(normal));
        assertFalse(WorldFiles.deleteWorldFolder(null, normal));
        assertFalse(WorldFiles.deleteWorldFolder(root, null));
    }

    @Test
    public void aFileNamedLikeAWorldIsNotDeleted() throws IOException {
        Path file = root.resolve("sb_dungeon_3");
        Files.writeString(file, "not a folder");
        assertFalse(WorldFiles.deleteWorldFolder(root, file));
        assertTrue(Files.exists(file));
    }

    @Test
    public void symbolicLinksAreNeverFollowed() throws IOException {
        Path link = root.resolve("sb_dungeon_5");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // the file system cannot make links: nothing to test
        }
        assertFalse("a link named like a world is refused", WorldFiles.deleteWorldFolder(root, link));
        assertTrue(Files.exists(outside.resolve("precious.txt")));
        Files.delete(link);

        Path w = world("sb_dungeon_6");
        Files.createSymbolicLink(w.resolve("evil"), outside);
        assertTrue(WorldFiles.deleteWorldFolder(root, w));
        assertFalse(Files.exists(w));
        assertTrue("the link was removed, its target was not", Files.exists(outside.resolve("precious.txt")));
    }

    @Test
    public void findsDungeonFoldersAndIgnoresLinks() throws IOException {
        world("sb_dungeon_1");
        world("sb_dungeon_2");
        world("world");
        Path nested = Files.createDirectories(root.resolve("worlds"));
        Files.createDirectories(nested.resolve("sb_dungeon_3"));
        Files.createDirectories(root.resolve("a").resolve("b").resolve("c").resolve("d").resolve("e").resolve("sb_dungeon_4"));
        try {
            Files.createSymbolicLink(root.resolve("sb_dungeon_9"), outside);
        } catch (UnsupportedOperationException | IOException ignored) {
            // no links on this file system
        }
        List<String> names = WorldFiles.findDungeonFolders(root, 4).stream().map(p -> p.getFileName().toString()).sorted().toList();
        assertEquals(List.of("sb_dungeon_1", "sb_dungeon_2", "sb_dungeon_3"), names);
        assertTrue(WorldFiles.findDungeonFolders(root.resolve("missing"), 4).isEmpty());
        assertTrue(WorldFiles.findDungeonFolders(null, 4).isEmpty());
    }
}
