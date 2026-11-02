package win.vampyuyu.survival.realm;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
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
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.island.BlockPlan;

/** The Realm of the Cracked Ashes: layout, heat rules, quest progress and the opening by the Echo. */
public class AshesTest {
    private ServerMock server;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- heat

    @Test
    public void tiersRiseBesideLavaButNeverOnTheLanding() {
        assertEquals(0, HeatMath.tier(0, true));
        assertEquals(1, HeatMath.tier(1, false));
        assertEquals(2, HeatMath.tier(1, true));
        assertEquals(3, HeatMath.tier(3, true));
        assertEquals(0, HeatMath.required(0));
        assertTrue(HeatMath.required(1) < HeatMath.required(2) && HeatMath.required(2) < HeatMath.required(3));
    }

    @Test
    public void damageGrowsWithTheMissingResistanceAndStopsWhenEnough() {
        assertEquals(0.0, HeatMath.damage(0, 0), 0.0);
        assertEquals(0.0, HeatMath.damage(2, HeatMath.required(2)), 0.0);
        assertTrue(HeatMath.damage(1, 0) > 0);
        assertTrue(HeatMath.damage(3, 0) > HeatMath.damage(1, 0));
        assertEquals(6.0, HeatMath.damage(3, -500), 0.0);
        assertFalse(HeatMath.hurts(3, Double.POSITIVE_INFINITY));
    }

    @Test
    public void heatNeverKills() {
        assertEquals(HeatMath.FLOOR, HeatMath.healthAfter(5.0, 6.0), 0.0);
        assertEquals(1.0, HeatMath.healthAfter(1.0, 6.0), 0.0);
        assertEquals(15.0, HeatMath.healthAfter(20.0, 5.0), 0.0);
    }

    @Test
    public void armorGivesResistanceAndTheCalderaAsksForMore() {
        assertEquals(12, HeatMath.armorPoints(Material.NETHERITE_CHESTPLATE));
        assertEquals(0, HeatMath.armorPoints(Material.TURTLE_HELMET));
        assertEquals("a tool is not armor", 0, HeatMath.armorPoints(Material.DIAMOND_PICKAXE));
        int netheriteSet = 4 * HeatMath.armorPoints(Material.NETHERITE_HELMET);
        assertTrue("netherite is enough for the tarn", netheriteSet >= HeatMath.required(2));
        assertTrue("but not for the caldera without the attunement", netheriteSet + HeatMath.ATTUNEMENT < HeatMath.required(3));
    }

    // ---------------------------------------------------------------- layout

    @Test
    public void planFitsTheCapAndHasEverythingTheRulesNeed() {
        BlockPlan plan = AshesPlan.build();
        assertTrue("plan size " + plan.size(), plan.size() > 5000 && plan.size() < AshesPlan.CAP);
        assertEquals(Material.LODESTONE, plan.get(0, 1, 0));
        assertNotNull(plan.anchor("spawn"));
        assertNotNull(plan.anchor("sarcophagus"));
        assertEquals(5, AshesPlan.LANDMARKS.size());
        assertEquals(3, AshesPlan.VENTS.size());
        assertEquals(10, AshesPlan.NODES.size());
        for (AshesPlan.Landmark mark : AshesPlan.LANDMARKS) {
            assertNotNull(mark.id(), plan.get(mark.x(), 0, mark.z()));
            assertFalse(mark.lore().isBlank());
        }
        for (AshesPlan.Spot vent : AshesPlan.VENTS) {
            assertEquals(Material.BLAST_FURNACE, plan.get(vent.x(), vent.y(), vent.z()));
            assertNotNull("ground under " + vent.id(), plan.get(vent.x(), vent.y() - 1, vent.z()));
        }
        Set<Long> seen = new HashSet<>();
        for (AshesPlan.Spot node : AshesPlan.NODES) {
            assertEquals(Material.GILDED_BLACKSTONE, plan.get(node.x(), node.y(), node.z()));
            assertTrue(seen.add(BlockPlan.key(node.x(), node.y(), node.z())));
        }
        int[] spawn = AshesPlan.SPAWN;
        assertNotNull(plan.get(spawn[0], spawn[1] - 1, spawn[2]));
        assertNull(plan.get(spawn[0], spawn[1], spawn[2]));
        assertNull(plan.get(spawn[0], spawn[1] + 1, spawn[2]));
    }

    @Test
    public void everyIsletIsJoinedToTheLandingAndInsideTheBorder() {
        BlockPlan plan = AshesPlan.build();
        for (AshesPlan.Islet islet : AshesPlan.ISLETS) {
            assertTrue(islet.id(), Math.hypot(islet.x(), islet.z()) + islet.radius() < 200);
            assertNotNull(plan.get(islet.x(), 0, islet.z()));
            // walk the straight line from the landing: every step has a floor block (the bridge) at y = 0
            double len = Math.hypot(islet.x(), islet.z());
            for (double s = 0; s <= len; s += 1) {
                int x = (int) Math.round(islet.x() * s / Math.max(len, 1));
                int z = (int) Math.round(islet.z() * s / Math.max(len, 1));
                boolean floor = plan.get(x, 0, z) != null || plan.get(x + 1, 0, z) != null || plan.get(x, 0, z + 1) != null
                    || plan.get(x - 1, 0, z) != null || plan.get(x, 0, z - 1) != null;
                assertTrue(islet.id() + " at " + x + "," + z, floor);
            }
        }
    }

    @Test
    public void lavaIsContainedSoItNeverFlowsIntoTheVoid() {
        BlockPlan plan = AshesPlan.build();
        int lava = 0;
        for (var entry : plan.blocks().entrySet()) {
            if (entry.getValue().material() != Material.LAVA) {
                continue;
            }
            lava++;
            int x = BlockPlan.keyX(entry.getKey());
            int y = BlockPlan.keyY(entry.getKey());
            int z = BlockPlan.keyZ(entry.getKey());
            assertNotNull("under lava " + x + "," + y + "," + z, plan.get(x, y - 1, z));
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                assertNotNull("beside lava " + x + "," + y + "," + z, plan.get(x + d[0], y, z + d[1]));
            }
        }
        assertTrue(lava > 500);
    }

    @Test
    public void heatTiersFollowTheIslets() {
        assertEquals(0, AshesPlan.baseTier(0, 0));
        assertEquals(0, AshesPlan.baseTier(5, -8));
        AshesPlan.Islet caldera = AshesPlan.at("caldera");
        assertEquals(3, AshesPlan.baseTier(caldera.x(), caldera.z()));
        assertEquals(2, AshesPlan.baseTier(AshesPlan.at("tarn").x(), AshesPlan.at("tarn").z()));
        assertEquals(1, AshesPlan.baseTier(AshesPlan.at("needle").x(), AshesPlan.at("needle").z()));
        // a bridge between the landing and an islet is tier 1
        assertEquals(1, AshesPlan.baseTier(0, AshesPlan.at("needle").z() / 2.0));
    }

    @Test
    public void protectedSpotsAreTheLandingAndTheSealedDoor() {
        assertTrue(AshesRealm.protectedSpot(3, 0, 3));
        assertFalse(AshesRealm.protectedSpot(40, 0, 40));
        AshesPlan.Islet caldera = AshesPlan.at("caldera");
        assertTrue(AshesRealm.protectedSpot(caldera.x(), 3, caldera.z() - 14));
        assertFalse(AshesRealm.protectedSpot(caldera.x(), 3, caldera.z() + 5));
    }

    // ---------------------------------------------------------------- quests

    @Test
    public void discoveriesPayOnceAndTheLastOneAddsTheBonus() {
        AshesProgress progress = new AshesProgress();
        UUID p = UUID.randomUUID();
        int total = 0;
        for (AshesPlan.Landmark mark : AshesPlan.LANDMARKS) {
            assertFalse(progress.exploreDone(p));
            int coins = progress.discover(p, mark.id());
            assertTrue(coins >= AshesProgress.DISCOVER_COINS);
            total += coins;
            assertEquals(-1, progress.discover(p, mark.id()));
        }
        assertTrue(progress.exploreDone(p));
        assertEquals(AshesProgress.DISCOVER_COINS * 5 + AshesProgress.EXPLORE_BONUS, total);
        assertEquals(5, progress.fragments(p).size());
    }

    @Test
    public void gatheringPaysAtTheGoalOnlyOnce() {
        AshesProgress progress = new AshesProgress();
        UUID p = UUID.randomUUID();
        int paid = 0;
        for (int i = 0; i < AshesProgress.GATHER_GOAL + 5; i++) {
            paid += progress.harvest(p);
        }
        assertEquals(AshesProgress.GATHER_COINS, paid);
        assertEquals(AshesProgress.GATHER_GOAL + 5, progress.harvested(p));
    }

    @Test
    public void stabilisingEveryVentGivesTheAttunement() {
        AshesProgress progress = new AshesProgress();
        UUID p = UUID.randomUUID();
        int total = 0;
        for (AshesPlan.Spot vent : AshesPlan.VENTS) {
            assertFalse(progress.attuned(p));
            total += progress.stabilise(p, vent.id());
            assertEquals(-1, progress.stabilise(p, vent.id()));
        }
        assertTrue(progress.attuned(p));
        assertEquals(AshesProgress.VENT_COINS * 3 + AshesProgress.FINISH_COINS, total);
        UUID other = UUID.randomUUID();
        assertFalse(progress.attuned(other));
    }

    @Test
    public void progressSurvivesASaveAndAForeignKeyIsSkipped() {
        AshesProgress progress = new AshesProgress();
        UUID p = UUID.randomUUID();
        progress.discover(p, "needle");
        progress.harvest(p);
        progress.stabilise(p, "vent:tarn");
        AshesProgress.Doc doc = progress.toDoc();
        doc.players.put("not-a-uuid", new AshesProgress.Entry());
        AshesProgress back = AshesProgress.fromDoc(doc);
        assertTrue(back.discovered(p, "needle"));
        assertEquals(1, back.harvested(p));
        assertTrue(back.stabilised(p, "vent:tarn"));
        assertEquals(0, AshesProgress.fromDoc(null).harvested(p));
        progress.reset(p);
        assertFalse(progress.discovered(p, "needle"));
    }

    // ---------------------------------------------------------------- the plugin

    @Test
    public void theEchoFallingLightsTheGateForItsFighters() {
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        SkyGateApi gates = plugin.core().service(SkyGateApi.class).orElseThrow();
        var killer = server.addPlayer();
        var helper = server.addPlayer();
        var bystander = server.addPlayer();
        server.getPluginManager().callEvent(new BossKillEvent("zombie_king", killer, List.of(), killer.getLocation()));
        assertFalse(gates.isLit(killer.getUniqueId(), Realm.ASHES));
        server.getPluginManager().callEvent(new BossKillEvent(AshesRealm.ECHO_BOSS, killer,
            List.of(new BossKillEvent.Damager(killer.getUniqueId(), 100), new BossKillEvent.Damager(helper.getUniqueId(), 50)),
            killer.getLocation()));
        assertTrue(gates.isLit(killer.getUniqueId(), Realm.ASHES));
        assertTrue(gates.isLit(helper.getUniqueId(), Realm.ASHES));
        assertFalse(gates.isLit(bystander.getUniqueId(), Realm.ASHES));
        assertFalse("the other gates stay dark", gates.isLit(killer.getUniqueId(), Realm.GROVE));
    }

    @Test
    public void cinderheartIsAnItemAndTheCommandsAnswer() {
        SurvivalPlugin plugin = MockBukkit.load(SurvivalPlugin.class);
        assertTrue(plugin.core().items().def(AshesRealm.CINDERHEART).isPresent());
        var player = server.addPlayer();
        player.performCommand("ashes");
        player.performCommand("ashes go");
        player.performCommand("ashes build");
        player.performCommand("ashes nonsense");
        assertNotNull(player.nextMessage());
    }
}
