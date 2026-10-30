package win.vampyuyu.survival.skills;

import org.mockbukkit.mockbukkit.ServerMock;

final class PlayerMockHelper {
    private PlayerMockHelper() {}

    static void join(ServerMock server, org.bukkit.plugin.Plugin plugin, String name) {
        server.addPlayer(name);
    }
}
