package win.vampyuyu.survival.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.depths.DepthsLayout;
import win.vampyuyu.survival.depths.DepthsPlan;
import win.vampyuyu.survival.marsh.MarshPlan;
import win.vampyuyu.survival.realm.AshesPlan;
import win.vampyuyu.survival.realm.StormPlan;

/** The return arch at every Realm landing: same shape, built with the real caps, never on the arrival spot, trigger on the veil only. */
public class ReturnArchTest {
    private static final int HUB_CAP = 45000;

    private record Landing(BlockPlan plan, int[] spawn, int version) {}

    private static Map<Realm, Landing> landings() {
        Map<Realm, Landing> all = new EnumMap<>(Realm.class);
        all.put(Realm.ASHES, new Landing(AshesPlan.build(), AshesPlan.SPAWN, AshesPlan.VERSION));
        all.put(Realm.GROVE, new Landing(GrovePlan.build(HUB_CAP), GrovePlan.spawn(), GrovePlan.VERSION));
        all.put(Realm.HEIGHTS, new Landing(FrostPlan.build(HUB_CAP), FrostPlan.spawn(), FrostPlan.VERSION));
        all.put(Realm.CITADEL, new Landing(CitadelPlan.build(HUB_CAP), CitadelPlan.spawn(), CitadelPlan.VERSION));
        all.put(Realm.MARSHES, new Landing(MarshPlan.build(HUB_CAP), MarshPlan.SPAWN, MarshPlan.VERSION));
        all.put(Realm.ARCHIPELAGO, new Landing(StormPlan.build(), StormPlan.SPAWN, StormPlan.VERSION));
        all.put(Realm.DEPTHS, new Landing(DepthsPlan.build(), DepthsLayout.SPAWN, DepthsLayout.VERSION));
        all.put(Realm.NECROPOLIS, new Landing(NecropolisPlan.build(HUB_CAP), NecropolisPlan.spawn(), NecropolisPlan.VERSION));
        return all;
    }

    /** World x / z of the arch-local cell (u along the veil, v from the spot toward it). */
    private static int ax(Realm r, int sx, int u, int v) { return ReturnArch.side(r).x(sx, u, v); }
    private static int az(Realm r, int sz, int u, int v) { return ReturnArch.side(r).z(sz, u, v); }

    private static boolean passable(BlockPlan plan, int x, int y, int z) {
        Material m = plan.get(x, y, z);
        return m == null || m.isAir();
    }

    @Test
    public void everyRealmHasItsArchBuiltWithTheRealCapAndTheVersionIsBumped() {
        Map<Realm, Landing> all = landings();
        assertEquals(Realm.values().length, all.size());
        for (var e : all.entrySet()) {
            Landing l = e.getValue();
            ReturnArch.Palette p = ReturnArch.palette(e.getKey());
            int sx = l.spawn()[0];
            int sy = l.spawn()[1];
            int sz = l.spawn()[2];
            assertTrue(e.getKey() + " plan version", l.version() >= 2);
            for (int[] cell : ReturnArch.veilCells(e.getKey(), sx, sy, sz)) {
                assertEquals(e.getKey() + " veil at " + cell[0] + "," + cell[1] + "," + cell[2], p.veil(),
                    l.plan().get(cell[0], cell[1], cell[2]));
            }
            assertEquals(e.getKey() + " left pillar", p.pillar(), l.plan().get(ax(e.getKey(), sx, -2, ReturnArch.DZ), sy + 1, az(e.getKey(), sz, -2, ReturnArch.DZ)));
            assertEquals(e.getKey() + " right pillar", p.pillar(), l.plan().get(ax(e.getKey(), sx, 2, ReturnArch.DZ), sy, az(e.getKey(), sz, 2, ReturnArch.DZ)));
            assertEquals(e.getKey() + " lintel", p.cap(), l.plan().get(ax(e.getKey(), sx, 0, ReturnArch.DZ), sy + 3, az(e.getKey(), sz, 0, ReturnArch.DZ)));
        }
    }

    @Test
    public void theArrivalSpotAndTheWayToTheVeilAreFreeAndStandOnAFloor() {
        for (var e : landings().entrySet()) {
            BlockPlan plan = e.getValue().plan();
            int sx = e.getValue().spawn()[0];
            int sy = e.getValue().spawn()[1];
            int sz = e.getValue().spawn()[2];
            for (int y = sy; y <= sy + 2; y++) {
                assertTrue(e.getKey() + " spawn headroom at y+" + (y - sy), passable(plan, sx, y, sz));
                for (int u = -1; u <= 1; u++) {
                    int x = ax(e.getKey(), sx, u, ReturnArch.DZ - 1);
                    int z = az(e.getKey(), sz, u, ReturnArch.DZ - 1);
                    assertTrue(e.getKey() + " approach " + x + "," + y + "," + z, passable(plan, x, y, z));
                }
            }
            assertTrue(e.getKey() + " floor under the spawn", plan.solidAt(sx, sy - 1, sz));
            for (int u = -1; u <= 1; u++) {
                assertTrue(e.getKey() + " floor under the approach at " + u,
                    plan.solidAt(ax(e.getKey(), sx, u, ReturnArch.DZ - 1), sy - 1, az(e.getKey(), sz, u, ReturnArch.DZ - 1)));
            }
            for (int u = -2; u <= 2; u++) {
                assertTrue(e.getKey() + " threshold at " + u,
                    plan.solidAt(ax(e.getKey(), sx, u, ReturnArch.DZ), sy - 1, az(e.getKey(), sz, u, ReturnArch.DZ)));
            }
        }
    }

    @Test
    public void theArchHasTheSameShapeInEveryRealmAndOnlyThePaletteChanges() {
        Set<String> reference = null;
        Set<Material> veils = new HashSet<>();
        for (Realm realm : Realm.values()) {
            BlockPlan plan = new BlockPlan();
            ReturnArch.build(plan, realm, 100, 50, -20);
            Set<String> shape = new HashSet<>();
            ReturnArch.Side d = ReturnArch.side(realm);
            plan.blocks().forEach((k, blk) -> {
                int dx = BlockPlan.keyX(k) - 100;
                int dz = BlockPlan.keyZ(k) + 20;
                int u = d.ax * dx + d.az * dz; // back to arch-local coordinates
                int v = d.ox * dx + d.oz * dz;
                shape.add(u + "," + (BlockPlan.keyY(k) - 50) + "," + v + (blk.material().isAir() ? "a" : "s"));
            });
            if (reference == null) {
                reference = shape;
            } else {
                assertEquals(realm + " has another shape", reference, shape);
            }
            veils.add(ReturnArch.palette(realm).veil());
            assertNotNull(ReturnArch.palette(realm).wallSign());
        }
        assertTrue("a palette per Realm", veils.size() >= 7);
    }

    @Test
    public void theSignIsTheSameFourLinesEverywhereAndFitsASign() {
        Set<String> tails = new HashSet<>();
        for (var e : landings().entrySet()) {
            int sx = e.getValue().spawn()[0];
            int sy = e.getValue().spawn()[1];
            int sz = e.getValue().spawn()[2];
            BlockPlan.SignText sign = e.getValue().plan().signs().stream()
                .filter(s -> s.x() == ax(e.getKey(), sx, ReturnArch.REACH, ReturnArch.DZ - 1) && s.y() == sy + 1
                    && s.z() == az(e.getKey(), sz, ReturnArch.REACH, ReturnArch.DZ - 1))
                .findFirst().orElse(null);
            assertNotNull(e.getKey() + " has its arch sign", sign);
            assertEquals(4, sign.lines().size());
            assertEquals(ReturnArch.signName(e.getKey()), sign.lines().get(0));
            tails.add(String.join("|", sign.lines().subList(1, 4)));
            for (String line : sign.lines()) {
                assertTrue(e.getKey() + " line too long: " + line, line.length() <= 17);
            }
            assertEquals("facing=" + ReturnArch.side(e.getKey()).signFacing, e.getValue().plan().blk(sign.x(), sign.y(), sign.z()).props());
            for (BlockPlan.SignText other : e.getValue().plan().signs()) {
                for (String line : other.lines()) {
                    assertFalse("old wording left: " + line, line.contains("to return") || line.contains("to leave") || line.contains("go home"));
                }
            }
        }
        assertEquals("one wording below the name", 1, tails.size());
    }

    @Test
    public void theTriggerIsTheVeilFaceAndNeverTheArrivalSpot() {
        int sx = 0;
        int sy = 101;
        int sz = 18;
        assertFalse("arrival spot", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy, sz + 0.5, sx, sy, sz));
        assertFalse("one block in front of the arrival spot", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy, sz + 1.0, sx, sy, sz));
        assertTrue("at the glass", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
        assertTrue("left of the opening", ReturnArch.inVeil(Realm.ASHES, sx - 0.8, sy, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
        assertTrue("jumping at the glass", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy + 2.5, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
        assertFalse("beside the pillar", ReturnArch.inVeil(Realm.ASHES, sx + 3.5, sy, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
        assertFalse("on top of the lintel", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy + 4.2, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
        assertFalse("well behind the arch", ReturnArch.inVeil(Realm.ASHES, sx + 0.5, sy, sz + ReturnArch.DZ + 3, sx, sy, sz));
        assertFalse("far from it", ReturnArch.inVeil(Realm.ASHES, 40, sy, 40, sx, sy, sz));
    }

    @Test
    public void everyRealmPlanStaysUnderItsCap() {
        landings().forEach((realm, l) -> assertTrue(realm + " plan size " + l.plan().size(), l.plan().size() > 100));
        assertTrue(AshesPlan.build().size() < AshesPlan.CAP);
        assertTrue(StormPlan.build().size() < StormPlan.CAP);
        assertTrue(DepthsPlan.build().size() < DepthsLayout.CAP);
    }

    @Test
    public void theGroveArchLeavesTheBridgeToTheFirstIsletOpen() {
        Landing grove = landings().get(Realm.GROVE);
        int sx = grove.spawn()[0];
        int sy = grove.spawn()[1];
        int sz = grove.spawn()[2];
        for (int dz = 1; dz <= 4; dz++) { // the bridge to islet 1 leaves the landing due south
            for (int y = sy; y <= sy + 1; y++) {
                assertTrue("south of the Grove landing, z+" + dz, passable(grove.plan(), sx, y, sz + dz));
            }
        }
        // the east-facing veil triggers from the spot's east, not from the south path
        assertTrue(ReturnArch.inVeil(Realm.GROVE, sx + ReturnArch.DZ - 0.3, sy, sz + 0.5, sx, sy, sz));
        assertFalse(ReturnArch.inVeil(Realm.GROVE, sx + 0.5, sy, sz + ReturnArch.DZ - 0.3, sx, sy, sz));
    }
}
