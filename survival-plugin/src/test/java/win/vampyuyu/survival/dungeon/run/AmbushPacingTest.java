package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import win.vampyuyu.survival.dungeon.gen.Pos;

/** The numbers of a fair ambush room: cap by party size, waves, release rule, warning, entry spots. */
public class AmbushPacingTest {
    @Test
    public void theCapGrowsWithThePartyAndNeverPassesTheRoomCap() {
        assertEquals(5, AmbushPacing.cap(5, 2, 1, 14));
        assertEquals(7, AmbushPacing.cap(5, 2, 2, 14));
        assertEquals(11, AmbushPacing.cap(5, 2, 4, 14));
        assertEquals(13, AmbushPacing.cap(5, 2, 5, 14));
        assertEquals("never above the room cap", 12, AmbushPacing.cap(5, 2, 5, 12));
        assertEquals("a room cap below the base wins", 3, AmbushPacing.cap(5, 2, 4, 3));
        assertEquals(1, AmbushPacing.cap(1, 0, 0, 14));
    }

    @Test
    public void splittingKeepsEveryEnemyInOrder() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(i);
        }
        var two = AmbushPacing.split(all, 2);
        assertEquals(2, two.size());
        assertEquals(6, two.get(0).size());
        assertEquals(6, two.get(1).size());
        var three = AmbushPacing.split(all.subList(0, 11), 3);
        assertEquals(List.of(4, 4, 3), three.stream().map(List::size).toList());
        var flat = new ArrayList<Integer>();
        two.forEach(flat::addAll);
        assertEquals(all, flat);
        assertEquals("one enemy cannot make two waves", 1, AmbushPacing.split(List.of(1), 2).size());
        assertEquals(1, AmbushPacing.split(all, 1).size());
    }

    @Test
    public void theWarningEndsOnItsTick() {
        assertFalse(AmbushPacing.telegraphOver(39, 40));
        assertTrue(AmbushPacing.telegraphOver(40, 40));
        assertTrue("no warning at all", AmbushPacing.telegraphOver(0, 0));
    }

    @Test
    public void theNextWaveComesWhenFewAreLeftOrWhenTimeIsUp() {
        long after = 12 * 20;
        assertFalse("five alive, one waiting, early", AmbushPacing.nextWaveDue(5, 1, 100, 0, 2, after));
        assertFalse(AmbushPacing.nextWaveDue(2, 1, 100, 0, 2, after));
        assertTrue("down to two", AmbushPacing.nextWaveDue(1, 1, 100, 0, 2, after));
        assertTrue("a passive player: the time runs out anyway", AmbushPacing.nextWaveDue(5, 1, after, 0, 2, after));
        assertFalse(AmbushPacing.nextWaveDue(5, 1, after - 5, 0, 2, after));
    }

    @Test
    public void enemiesComeInAlongTheWallsAwayFromThePlayers() {
        Pos center = new Pos(16, 64, 16);
        List<Pos> grid = new ArrayList<>();
        for (int x = 4; x <= 27; x += 2) {
            for (int z = 4; z <= 27; z += 2) {
                grid.add(new Pos(x, 64, z));
            }
        }
        List<double[]> players = List.of(new double[] {16.5, 16.5});
        for (int seed = 0; seed < 20; seed++) {
            var spots = AmbushPacing.edgeSpots(grid, center, players, 8, 5, new Random(seed));
            assertEquals(5, spots.size());
            for (Pos p : spots) {
                assertTrue(AmbushPacing.nearest(p, players) >= 64);
                assertTrue("on the outer ring: " + p, Math.max(Math.abs(p.x() - 16), Math.abs(p.z() - 16)) >= 8);
            }
        }
    }

    @Test
    public void whenEverySpotIsCloseItUsesTheFarthest() {
        Pos center = new Pos(16, 64, 16);
        List<Pos> few = List.of(new Pos(10, 64, 10), new Pos(12, 64, 12), new Pos(26, 64, 26), new Pos(14, 64, 14));
        List<double[]> players = List.of(new double[] {20, 20});
        var spots = AmbushPacing.edgeSpots(few, center, players, 50, 2, new Random(1));
        assertEquals(2, spots.size());
        assertTrue(spots.stream().allMatch(p -> p.x() <= 12 || p.x() == 26));
        assertEquals(List.of(), AmbushPacing.edgeSpots(List.of(), center, players, 8, 3, new Random(1)));
    }
}
