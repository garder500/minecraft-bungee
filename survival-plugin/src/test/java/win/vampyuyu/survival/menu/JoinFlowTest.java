package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;
import java.util.Optional;
import org.bukkit.Location;
import org.junit.Before;
import org.junit.Test;
import win.vampyuyu.survival.api.IslandApi;

/** The surviving network routing; the old hub prompt/welcome/respawn flow is retired. */
public class JoinFlowTest extends MenuTestBase {
    FakeIslands islands;
    JoinFlow flow;
    @Before public void setup() {
        islands = new FakeIslands(); core.replace(IslandApi.class, islands);
        world("world");
        flow = new JoinFlow(rt, null, player -> {});
    }
    @Test public void normalJoinAndStaleSelectionStayInCurrentMode() {
        var player = playerIn("world");
        flow.route(player, Optional.empty());
        flow.route(player, HandoffPayload.parse("{\"dest\":\"skyblock\",\"ts\":1}"));
        assertTrue(islands.teleports.isEmpty());
    }
    @Test public void skyblockAndLegacyHubSelectionsUsePersonalIsland() {
        var player = playerIn("world");
        islands.give(player.getUniqueId(),new Location(world("sb_world"),0,65,0));
        for(String dest: java.util.List.of("skyblock","hub","island")) flow.route(player,HandoffPayload.parse(dest));
        assertEquals(java.util.List.of("home:"+player.getName(),"home:"+player.getName(),"home:"+player.getName()),islands.teleports);
    }
    @Test public void explicitSurvivalReturnsToOverworldAndCancelsPendingIslandEntry() {
        var player = playerIn("sb_world");
        core.modes().awaitIsland(player);
        flow.route(player,HandoffPayload.parse("survival"));
        assertEquals("world",player.getWorld().getName());
        assertFalse(core.modes().awaitingIsland(player));
    }
    @Test public void missingIslandNeverFallsBackToHub() {
        var player = playerIn("world");
        flow.route(player,HandoffPayload.parse("skyblock"));
        server.getScheduler().performTicks(21);
        assertTrue(islands.teleports.stream().allMatch(s -> s.startsWith("home:")));
    }
}
