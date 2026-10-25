package win.vampyuyu.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;

public class StaffChannelTest {
    @Test
    public void staffTextIsStrippedOfControlCharactersAndCut() throws Exception {
        var clean = StaffChannel.class.getDeclaredMethod("clean", String.class, int.class);
        clean.setAccessible(true);
        assertEquals("a b", clean.invoke(null, "a\u0007b\n".replace('\u0007', ' ').replace('\n', ' '), 10));
        assertEquals("abc", clean.invoke(null, "abcdef", 3));
        assertEquals("§cred stays literal", clean.invoke(null, "§cred stays literal", 50));
    }

    @Test
    public void staffNamesAreRestricted() throws Exception {
        var name = StaffChannel.class.getDeclaredMethod("staffName", String.class);
        name.setAccessible(true);
        assertEquals("Jeremy_27", name.invoke(null, "Jeremy_27"));
        for (String bad : new String[] {"", "x".repeat(33), "<red>x", "a\nb", "§ax"})
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> name.invoke(null, bad));
    }

    @Test
    public void anOfflinePlayerCannotBeWrittenTo() {
        // No server is needed: the request is refused before anything touches the world.
        StaffChannel channel = new StaffChannel(null);
        JsonObject open = new JsonObject();
        open.addProperty("action", "open");
        open.addProperty("staff", "admin");
        assertThrows(IllegalArgumentException.class, () -> channel.handle(open, null, UUID.randomUUID()));
        JsonObject junk = new JsonObject();
        assertThrows(IllegalArgumentException.class, () -> channel.handle(junk, null, UUID.randomUUID()));
    }
}
