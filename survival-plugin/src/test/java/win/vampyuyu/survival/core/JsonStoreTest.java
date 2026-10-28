package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    public static final class Doc {
        public String name = "a";
        public int count;
        public List<String> tags = new ArrayList<>();
    }

    private JsonStore store() {
        return new JsonStore(tmp.getRoot().toPath(), Logger.getAnonymousLogger());
    }

    @Test
    public void roundTrip() throws Exception {
        Doc doc = new Doc();
        doc.name = "wings";
        doc.count = 7;
        doc.tags.add("x");
        store().write("sub/dir/doc.json", doc);
        Doc back = store().read("sub/dir/doc.json", Doc.class, Doc::new);
        assertEquals("wings", back.name);
        assertEquals(7, back.count);
        assertEquals(List.of("x"), back.tags);
        assertFalse("no tmp file left", Files.exists(tmp.getRoot().toPath().resolve("sub/dir/doc.json.tmp")));
    }

    @Test
    public void missingFileGivesDefaults() {
        Doc doc = store().read("none.json", Doc.class, () -> {
            Doc d = new Doc();
            d.count = 42;
            return d;
        });
        assertEquals(42, doc.count);
    }

    @Test
    public void corruptFileIsKeptAndDefaultsReturned() throws Exception {
        Path file = tmp.getRoot().toPath().resolve("bad.json");
        Files.writeString(file, "{ not json");
        Doc doc = store().read("bad.json", Doc.class, Doc::new);
        assertEquals("a", doc.name);
        assertEquals("{ not json", Files.readString(tmp.getRoot().toPath().resolve("bad.json.corrupt")));
    }

    @Test
    public void overwriteReplacesAtomically() throws Exception {
        Doc doc = new Doc();
        doc.count = 1;
        store().write("d.json", doc);
        doc.count = 2;
        store().write("d.json", doc);
        assertEquals(2, store().read("d.json", Doc.class, Doc::new).count);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pathTraversalIsRejected() throws Exception {
        store().write("../escape.json", new Doc());
    }
}
