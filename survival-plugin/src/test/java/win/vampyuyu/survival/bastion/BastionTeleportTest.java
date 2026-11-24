package win.vampyuyu.survival.bastion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** An eliminated member is a spectator: the spectator menu must not take them out of the Bastion. */
public class BastionTeleportTest {
    private ServerMock server;

    @Before
    public void up() {
        server = MockBukkit.mock();
    }

    @After
    public void down() {
        MockBukkit.unmock();
    }

    @Test
    public void aSpectatorMayFollowATeammateInsideButNotJumpToAnotherWorld() {
        World bastion = server.addSimpleWorld("sb_bastion_1");
        World hub = server.addSimpleWorld("sb_hub");
        PlayerMock ghost = server.addPlayer("Ghost");
        ghost.setLocation(new Location(bastion, 0, 70, 0));
        BastionListener listener = new BastionListener(null, null); // the teleport rule needs neither

        PlayerTeleportEvent out = new PlayerTeleportEvent(ghost, ghost.getLocation(), new Location(hub, 0, 100, 0), TeleportCause.SPECTATE);
        listener.onTeleport(out);
        assertTrue(out.isCancelled());

        PlayerTeleportEvent inside = new PlayerTeleportEvent(ghost, ghost.getLocation(), new Location(bastion, 5, 70, 5), TeleportCause.SPECTATE);
        listener.onTeleport(inside);
        assertFalse(inside.isCancelled());

        PlayerTeleportEvent exit = new PlayerTeleportEvent(ghost, ghost.getLocation(), new Location(hub, 0, 100, 0), TeleportCause.PLUGIN);
        listener.onTeleport(exit);
        assertFalse("the run's own exit is a plugin teleport", exit.isCancelled());
    }
}
