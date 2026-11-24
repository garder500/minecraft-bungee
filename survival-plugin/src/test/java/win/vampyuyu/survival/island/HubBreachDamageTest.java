package win.vampyuyu.survival.island;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;

/** The hub is a safe zone everywhere but in the Breach. */
public class HubBreachDamageTest {
    private ServerMock server;

    @Before
    public void up() {
        server = MockBukkit.mock();
        MockBukkit.load(SurvivalPlugin.class);
    }

    @After
    public void down() {
        MockBukkit.unmock();
    }

    @SuppressWarnings({"deprecation", "removal"})
    private boolean cancelled(PlayerMock p) {
        EntityDamageEvent event = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    public void damageCountsInTheBreachOnly() {
        World hub = server.getWorld("sb_hub") != null ? server.getWorld("sb_hub") : server.addSimpleWorld("sb_hub");
        PlayerMock p = server.addPlayer();
        p.setLocation(new Location(hub, HubPlan.SPAWN_X + 0.5, 101, HubPlan.SPAWN_Z + 0.5));
        assertTrue("safe at the spawn", cancelled(p));
        p.setLocation(new Location(hub, HubBreach.X + 3.5, 101, HubBreach.Z + 0.5));
        assertFalse("hurt in the Breach", cancelled(p));
        p.setLocation(new Location(hub, HubBreach.BRIDGE_FROM[0] - 4.5, 101, HubBreach.BRIDGE_FROM[1] - 2));
        assertTrue("safe on the bridge", cancelled(p));
    }
}
