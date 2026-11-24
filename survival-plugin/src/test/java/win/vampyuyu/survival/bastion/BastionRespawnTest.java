package win.vampyuyu.survival.bastion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.core.Core;

/**
 * Found with a real client in the Raid of the Devourer: a death inside a Bastion respawned the player on their island
 * (the mode travel runs at HIGHEST, after the Bastion's own respawn point), so the lives of the Bastion were worth nothing.
 */
public class BastionRespawnTest {
    private ServerMock server;
    private Core core;

    @Before
    public void up() {
        server = MockBukkit.mock();
        core = MockBukkit.load(SurvivalPlugin.class).core();
    }

    @After
    public void down() {
        MockBukkit.unmock();
    }

    private void arenaMember(boolean inArena) {
        core.replace(BastionApi.class, new BastionApi() {
            @Override
            public boolean isKey(String itemId) {
                return false;
            }

            @Override
            public Optional<String> bastionOfKey(String itemId) {
                return Optional.empty();
            }

            @Override
            public String enter(Player leader, String bastionId) {
                return null;
            }

            @Override
            public boolean inArena(UUID player) {
                return inArena;
            }
        });
    }

    @Test
    public void aBastionMemberKeepsTheArenaRespawnPoint() {
        World arena = server.addSimpleWorld("sb_bastion_1");
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(arena, 0, 70, 0));
        arenaMember(true);
        Location arrival = new Location(arena, 0.5, 70, 30.5);
        PlayerRespawnEvent event = new PlayerRespawnEvent(player, arrival, false);
        core.modes().onRespawn(event);
        assertEquals("the Bastion's respawn point is not overwritten", arrival, event.getRespawnLocation());
    }

    @Test
    public void anyoneElseInASkyblockWorldStillRespawnsOnTheirIsland() {
        World arena = server.addSimpleWorld("sb_bastion_1");
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(arena, 0, 70, 0));
        arenaMember(false);
        Location elsewhere = new Location(arena, 0.5, 70, 30.5);
        PlayerRespawnEvent event = new PlayerRespawnEvent(player, elsewhere, false);
        core.modes().onRespawn(event);
        assertNotEquals("not a member: the usual Skyblock respawn", elsewhere, event.getRespawnLocation());
    }
}
