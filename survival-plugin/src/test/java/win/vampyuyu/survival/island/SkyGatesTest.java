package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;

/** The Quai des Huit: Realm list, per player gate state, layout and the Sky Gate service. */
public class SkyGatesTest {
    private ServerMock server;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- realms and state

    @Test
    public void eightRealmsWithStableIds() {
        assertEquals(8, Realm.values().length);
        Set<String> ids = new HashSet<>();
        for (Realm realm : Realm.values()) {
            assertTrue(ids.add(realm.id()));
            assertSame(realm, Realm.byId(realm.id().toUpperCase()));
        }
        assertEquals(1, Realm.ASHES.number());
        assertEquals(8, Realm.NECROPOLIS.number());
        assertNull(Realm.byId("nope"));
        assertNull(Realm.byId(null));
    }

    @Test
    public void everyGateStartsDark() {
        SkyGateState state = new SkyGateState();
        UUID p = UUID.randomUUID();
        for (Realm realm : Realm.values()) {
            assertFalse(state.isLit(p, realm));
        }
        assertTrue(state.litRealms(p).isEmpty());
    }

    @Test
    public void lightingIsPerPlayerAndIdempotent() {
        SkyGateState state = new SkyGateState();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(state.light(a, Realm.GROVE));
        assertFalse(state.light(a, Realm.GROVE));
        assertTrue(state.isLit(a, Realm.GROVE));
        assertFalse(state.isLit(b, Realm.GROVE));
        assertFalse(state.isLit(a, Realm.ASHES));
        assertTrue(state.extinguish(a, Realm.GROVE));
        assertFalse(state.extinguish(a, Realm.GROVE));
        assertTrue(state.litRealms(a).isEmpty());
    }

    @Test
    public void stateSurvivesSaveAndLoad() {
        SkyGateState state = new SkyGateState();
        UUID a = UUID.randomUUID();
        state.light(a, Realm.ASHES);
        state.light(a, Realm.DEPTHS);
        String json = win.vampyuyu.survival.core.JsonStore.GSON.toJson(state.toDoc());
        SkyGateState back = SkyGateState.fromDoc(
            win.vampyuyu.survival.core.JsonStore.GSON.fromJson(json, SkyGateState.Doc.class));
        assertEquals(Set.of(Realm.ASHES, Realm.DEPTHS), back.litRealms(a));
    }

    @Test
    public void unknownEntriesInTheSavedFileAreSkipped() {
        SkyGateState.Doc doc = new SkyGateState.Doc();
        UUID a = UUID.randomUUID();
        doc.players.put(a.toString(), java.util.List.of("ashes", "future-realm"));
        doc.players.put("not-a-uuid", java.util.List.of("grove"));
        SkyGateState state = SkyGateState.fromDoc(doc);
        assertEquals(Set.of(Realm.ASHES), state.litRealms(a));
        assertTrue(SkyGateState.fromDoc(null).litRealms(a).isEmpty());
    }

    // ---------------------------------------------------------------- layout

    @Test
    public void eightGatesSitInsideTheHubBorderAndApartFromEachOther() {
        assertEquals(8, QuaiPlan.GATES.size());
        Set<Long> cells = new HashSet<>();
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            assertTrue(Math.abs(gate.x()) + QuaiPlan.RADIUS < 100);
            assertTrue(QuaiPlan.CENTER_Z + QuaiPlan.RADIUS < 100);
            assertFalse(gate.membrane().isEmpty());
            for (int[] cell : gate.membrane()) {
                assertTrue("membrane cells are never shared by two gates", cells.add(BlockPlan.key(cell[0], cell[1], cell[2])));
            }
            // the opening is 5 high and no wider than the gate
            assertEquals(0, gate.membrane().size() % 5);
        }
        // the bridge arrives between gate 1 and gate 8, so no gate stands at the entrance
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            assertTrue(Math.hypot(gate.x(), gate.z() - QuaiPlan.SPAWN_Z) > 6);
        }
    }

    @Test
    public void planBuildsGatesDarkAndHasSpawnAndSigns() {
        BlockPlan plan = QuaiPlan.build(30000);
        assertTrue(plan.size() > 2000 && plan.size() < 30000);
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            for (int[] cell : gate.membrane()) {
                assertEquals(QuaiPlan.DARK_MEMBRANE, plan.get(cell[0], cell[1], cell[2]));
            }
            int[] crown = gate.crown();
            assertEquals(QuaiPlan.DARK_CROWN, plan.get(crown[0], crown[1], crown[2]));
            assertNotNull(plan.anchor("gate:" + gate.realm().id()));
            // standing room in front of the opening
            assertTrue(plan.get(gate.x(), 0, gate.z()) != null);
        }
        assertEquals(8, plan.signs().size());
        assertNotNull(plan.anchor("spawn"));
        assertEquals(Material.QUARTZ_BRICKS, plan.get(0, 0, QuaiPlan.SPAWN_Z));
        // the bridge reaches the hub ring road
        assertNotNull(plan.get(0, 0, QuaiPlan.BRIDGE_FROM));
        assertNull(plan.get(0, 0, QuaiPlan.BRIDGE_FROM - 3));
    }

    @Test
    public void signTextFitsAndNamesTheRealm() {
        for (Realm realm : Realm.values()) {
            String[] lines = QuaiPlan.signLines(realm);
            assertEquals("Gate " + realm.number(), lines[0]);
            assertEquals(realm.title(), lines[1] + " " + lines[2]);
            assertTrue(lines[1].length() <= 15 && lines[2].length() <= 15);
        }
    }

    @Test
    public void everyRealmHasALandingAndAColour() {
        Set<Long> centres = new HashSet<>();
        for (Realm realm : Realm.values()) {
            int[] c = QuaiLanding.center(realm);
            assertTrue(Math.abs(c[0]) + QuaiLanding.RADIUS < 100 && Math.abs(c[1]) + QuaiLanding.RADIUS < 100);
            assertTrue(centres.add(BlockPlan.key(c[0], 0, c[1])));
            assertNotNull(QuaiLanding.concrete(realm));
            assertTrue(QuaiLanding.build(realm).signs().size() == 1);
            assertTrue(QuaiPlan.litMembrane(realm).name().endsWith("_STAINED_GLASS"));
        }
    }

    @Test
    public void walkingNextToAGateTouchesThatGateOnly() {
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            double x = gate.x() + 0.5 - gate.outX() * 1.2;
            double z = QuaiPlan.CENTER_Z + gate.z() + 0.5 - gate.outZ() * 1.2;
            assertSame(gate.realm(), SkyGates.gateAt(x, 1, z));
        }
        // the dais, the arrival point and the bridge touch nothing
        assertNull(SkyGates.gateAt(0.5, 1, QuaiPlan.CENTER_Z + 0.5));
        assertNull(SkyGates.gateAt(0.5, 1, QuaiPlan.CENTER_Z + QuaiPlan.SPAWN_Z + 0.5));
        assertNull(SkyGates.gateAt(0.5, 1, QuaiPlan.CENTER_Z + QuaiPlan.BRIDGE_FROM));
        // far above or outside the platform
        QuaiPlan.Gate g = QuaiPlan.GATES.get(2);
        assertNull(SkyGates.gateAt(g.x() + 0.5, 30, QuaiPlan.CENTER_Z + g.z() + 0.5));
        assertNull(SkyGates.gateAt(200, 1, 200));
    }

    // ---------------------------------------------------------------- service

    @Test
    public void featurePublishesTheApiAndLightingWorks() {
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        SkyGateApi api = plugin.core().service(SkyGateApi.class).orElseThrow();
        var player = server.addPlayer();
        UUID id = player.getUniqueId();
        assertFalse(api.isLit(id, Realm.ASHES));
        assertTrue(api.light(id, Realm.ASHES));
        assertFalse(api.light(id, Realm.ASHES));
        assertEquals(Set.of(Realm.ASHES), api.litRealms(id));
        assertFalse(api.isLit(id, Realm.GROVE));
        // a dark gate refuses to travel
        assertFalse(api.travel(player, Realm.GROVE));
        assertTrue(api.extinguish(id, Realm.ASHES));
        assertTrue(api.litRealms(id).isEmpty());
        // there is no hub world in the mock server: no arrival point
        assertNull(api.quaiSpawn());
    }

    @Test
    public void adminCommandLightsAGateByName() {
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        SkyGateApi api = plugin.core().service(SkyGateApi.class).orElseThrow();
        var admin = server.addPlayer();
        admin.setOp(true);
        admin.performCommand("gateadmin light " + admin.getName() + " heights");
        assertTrue(api.isLit(admin.getUniqueId(), Realm.HEIGHTS));
        admin.performCommand("gateadmin light " + admin.getName() + " all");
        assertEquals(8, api.litRealms(admin.getUniqueId()).size());
        admin.performCommand("gateadmin dark " + admin.getName() + " all");
        assertTrue(api.litRealms(admin.getUniqueId()).isEmpty());
        var plain = server.addPlayer();
        plain.performCommand("gateadmin light " + plain.getName() + " ashes");
        assertFalse(api.isLit(plain.getUniqueId(), Realm.ASHES));
    }
}
