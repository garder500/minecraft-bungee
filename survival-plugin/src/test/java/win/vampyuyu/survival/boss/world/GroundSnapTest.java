package win.vampyuyu.survival.boss.world;

import static org.junit.Assert.*;

import org.junit.Test;

public class GroundSnapTest {
    @Test
    public void standsOnTheHighestGroundWithAClearColumn() {
        // ground at y 64 under a far ceiling at 80, scanning from below the ceiling
        assertEquals(65, GroundSnap.find(79, 0, 10, b -> b == 64 || b == 80));
        // a low ceiling (70) leaves no room: no place
        assertEquals(Integer.MIN_VALUE, GroundSnap.find(69, 0, 10, b -> b == 64 || b == 70));
    }

    @Test
    public void highestOpenGroundWins() {
        assertEquals(81, GroundSnap.find(100, 0, 10, b -> b == 64 || b == 80));
    }

    @Test
    public void voidHasNoGround() {
        assertEquals(Integer.MIN_VALUE, GroundSnap.find(100, 0, 10, b -> false));
    }

    @Test
    public void buriedColumnHasNoGround() {
        assertEquals(Integer.MIN_VALUE, GroundSnap.find(95, 0, 10, b -> b < 100));
    }

    @Test
    public void floorLimitsTheSearch() {
        assertEquals(Integer.MIN_VALUE, GroundSnap.find(100, 70, 10, b -> b == 64));
    }
}
