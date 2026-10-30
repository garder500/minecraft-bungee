package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class CommandsTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private Permissions permissions;
    private Commands commands;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        permissions = new Permissions();
        commands = new Commands(plugin, permissions);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void registeredCommandRunsAndTabCompletes() {
        AtomicReference<String> seen = new AtomicReference<>();
        commands.command("sbtest").aliases("sbt").description("Test").usage("/sbtest <x>")
            .permission("vampyuyu.sb.test.use", PermissionDefault.TRUE)
            .executor((sender, command, label, args) -> {
                seen.set(label + ":" + String.join(",", args));
                return true;
            })
            .tab((sender, command, alias, args) -> List.of("alpha", "beta"))
            .register();
        PlayerMock player = server.addPlayer();
        assertNotNull(Bukkit.getCommandMap().getCommand("sbtest"));
        assertTrue(player.performCommand("sbt one two"));
        assertEquals("sbt:one,two", seen.get());
        assertEquals(List.of("alpha", "beta"), Bukkit.getCommandMap().getCommand("sbtest")
            .tabComplete(player, "sbtest", new String[] {""}));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.test.use"));
    }

    @Test
    public void permissionIsEnforced() {
        AtomicReference<String> seen = new AtomicReference<>();
        commands.command("sbsecret").permission("vampyuyu.sb.test.secret", PermissionDefault.FALSE)
            .executor((s, c, l, a) -> { seen.set("ran"); return true; }).register();
        PlayerMock player = server.addPlayer();
        player.performCommand("sbsecret");
        assertNull(seen.get());
        player.addAttachment(plugin, "vampyuyu.sb.test.secret", true);
        player.performCommand("sbsecret");
        assertEquals("ran", seen.get());
    }

    @Test
    public void unregisterAllRemovesNamesAliasesAndPrefixedNames() {
        commands.command("sbgone").aliases("sbg").executor((s, c, l, a) -> true)
            .permission("vampyuyu.sb.test.gone", PermissionDefault.OP).register();
        var known = Bukkit.getCommandMap().getKnownCommands();
        assertTrue(known.containsKey("sbgone"));
        assertTrue(known.containsKey("sbg"));
        commands.unregisterAll();
        permissions.unregisterAll();
        known = Bukkit.getCommandMap().getKnownCommands();
        assertFalse(known.containsKey("sbgone"));
        assertFalse(known.containsKey("sbg"));
        assertTrue(known.keySet().stream().noneMatch(k -> k.endsWith(":sbgone")));
        assertEquals(0, commands.count());
        assertNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.test.gone"));
    }

    @Test(expected = IllegalStateException.class)
    public void executorIsRequired() {
        commands.command("noexec").register();
    }
}
