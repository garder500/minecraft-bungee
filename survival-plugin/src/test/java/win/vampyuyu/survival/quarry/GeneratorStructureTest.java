package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.Test;

public class GeneratorStructureTest {
    private static Map<String, Material> ring(Material ring, Material south) {
        Map<String, Material> map = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                map.put(dx + "," + dz, dx == 0 && dz == 0 ? Material.SMOOTH_STONE : ring);
            }
        }
        map.put("0,1", south);
        return map;
    }

    private static GeneratorStructure.Result detect(Map<String, Material> world) {
        return GeneratorStructure.detect((dx, dz) -> world.getOrDefault(dx + "," + dz, Material.AIR));
    }

    @Test
    public void everyKindIsRecognisedFromItsRing() {
        assertEquals(GeneratorStructure.Kind.STONE, detect(ring(Material.STONE_BRICKS, GeneratorStructure.SPRING)).kind());
        assertEquals(GeneratorStructure.Kind.DUNES, detect(ring(Material.SANDSTONE, GeneratorStructure.SPRING)).kind());
        assertEquals(GeneratorStructure.Kind.FROST, detect(ring(Material.GLASS, GeneratorStructure.SPRING)).kind());
        assertEquals(GeneratorStructure.Kind.EMBER, detect(ring(Material.NETHER_BRICKS, Material.MAGMA_BLOCK)).kind());
    }

    @Test
    public void aMissingBlockIsNamedWithItsSide() {
        Map<String, Material> world = ring(Material.STONE_BRICKS, GeneratorStructure.SPRING);
        world.put("1,-1", Material.AIR);
        GeneratorStructure.Result result = detect(world);
        assertFalse(result.complete());
        assertEquals(GeneratorStructure.Kind.STONE, result.nearest());
        assertEquals(java.util.List.of("stone bricks on the north-east side"), result.missing());
    }

    @Test
    public void theWaterCellIsRequiredForEveryKindButEmber() {
        GeneratorStructure.Result noSpring = detect(ring(Material.STONE_BRICKS, Material.STONE_BRICKS));
        assertFalse(noSpring.complete());
        assertEquals(java.util.List.of("a Spring Stone on the south side"), noSpring.missing());
        assertTrue(detect(ring(Material.NETHER_BRICKS, Material.MAGMA_BLOCK)).complete());
        assertFalse(detect(ring(Material.NETHER_BRICKS, GeneratorStructure.SPRING)).complete());
    }

    @Test
    public void anEmptyFountainReportsTheNearestPlan() {
        GeneratorStructure.Result result = detect(new HashMap<>());
        assertFalse(result.complete());
        assertEquals(8, result.missing().size());
    }
}
