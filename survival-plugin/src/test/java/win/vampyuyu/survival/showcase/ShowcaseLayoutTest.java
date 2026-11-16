package win.vampyuyu.survival.showcase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.showcase.ShowcaseLayout.BlockEntry;
import win.vampyuyu.survival.showcase.ShowcaseLayout.Item;

public class ShowcaseLayoutTest {
    private static List<Item> items() {
        List<Item> items = new ArrayList<>();
        String[] groups = {"quarry", "boss", "core", "runes", "wings", "big"};
        int[] sizes = {40, 7, 3, 25, 1, 400};
        for (int g = 0; g < groups.length; g++) {
            for (int i = 0; i < sizes[g]; i++) {
                items.add(new Item(groups[g] + "_item_" + i, "Name " + i, groups[g], i % 5 == 0));
            }
        }
        return items;
    }

    private static List<BlockEntry> blocks() {
        List<BlockEntry> blocks = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            blocks.add(new BlockEntry("machine_" + i, "Machine " + i, Material.BARRIER, null));
        }
        return blocks;
    }

    @Test
    public void everyItemHasExactlyOneFrame() {
        List<Item> items = items();
        ShowcaseLayout layout = ShowcaseLayout.build(items, blocks());
        assertEquals(items.size(), layout.frames().size());
        Set<String> ids = new HashSet<>();
        layout.frames().forEach(f -> assertTrue("duplicate frame for " + f.id(), ids.add(f.id())));
        items.forEach(i -> assertTrue(ids.contains(i.id())));
    }

    @Test
    public void nothingOverlaps() {
        ShowcaseLayout layout = ShowcaseLayout.build(items(), blocks());
        Set<String> cells = new HashSet<>();
        layout.frames().forEach(f -> assertTrue("frame cell used twice " + f, cells.add(f.x() + "," + f.y() + "," + f.z())));
        layout.signs().forEach(s -> assertTrue("sign cell used twice " + s, cells.add(s.x() + "," + s.y() + "," + s.z())));
        layout.blocks().forEach(b -> assertTrue("block cell used twice " + b, cells.add(b.x() + "," + b.y() + "," + b.z())));
        layout.blocks().forEach(b -> assertTrue("pedestal clash " + b, cells.add(b.x() + ",1," + b.z())));
        layout.spins().forEach(s -> assertTrue("pedestal clash " + s, cells.add(s.x() + ",1," + s.z())));
    }

    @Test
    public void sectionsOnAWallNeverTouchAndGroupsAreOrdered() {
        ShowcaseLayout layout = ShowcaseLayout.build(items(), blocks());
        for (boolean north : new boolean[] {true, false}) {
            int end = ShowcaseLayout.FIRST_X - ShowcaseLayout.GAP - 1;
            for (ShowcaseLayout.Section s : layout.sections()) {
                if (s.north() != north) {
                    continue;
                }
                assertTrue("sections overlap or touch: " + s, s.x0() >= end + ShowcaseLayout.GAP + 1);
                end = s.x1();
            }
        }
        // sections are produced in group order: core first, then alphabetical, parts in sequence
        List<String> order = new ArrayList<>();
        for (ShowcaseLayout.Section s : layout.sections()) {
            if (order.isEmpty() || !order.get(order.size() - 1).equals(s.group())) {
                assertFalse("group split in two places: " + s.group(), order.contains(s.group()));
                order.add(s.group());
            }
        }
        assertEquals(List.of("core", "big", "boss", "quarry", "runes", "wings"), order);
        assertEquals(4, layout.sections().stream().filter(s -> s.group().equals("big")).count());
    }

    @Test
    public void everythingIsInsideTheBounds() {
        ShowcaseLayout layout = ShowcaseLayout.build(items(), blocks());
        int[] b = layout.bounds();
        layout.frames().forEach(f -> assertTrue(f.x() >= b[0] && f.x() <= b[3] && f.y() >= b[1] && f.y() <= b[4] && f.z() >= b[2] && f.z() <= b[5]));
        layout.signs().forEach(s -> assertTrue(s.x() <= layout.length() && s.z() >= 0 && s.z() < ShowcaseLayout.WIDTH));
        layout.blocks().forEach(s -> assertTrue(s.x() <= layout.length()));
        layout.spins().forEach(s -> assertTrue(s.x() <= layout.length()));
        double[] e = ShowcaseLayout.entrance();
        assertTrue(e[0] < ShowcaseLayout.FIRST_X);
    }

    @Test
    public void spinDisplaysAreCapped() {
        ShowcaseLayout layout = ShowcaseLayout.build(items(), blocks());
        assertTrue(layout.spins().size() <= ShowcaseLayout.SPIN_CAP);
        assertFalse(layout.spins().isEmpty());
        for (String group : List.of("quarry", "boss", "big")) {
            assertTrue(layout.spins().stream().filter(s -> s.item().group().equals(group)).count() <= ShowcaseLayout.SPIN_PER_GROUP);
        }
    }

    @Test
    public void planHasFloorWallsPedestalsAndOneSignPerSlot() {
        ShowcaseLayout layout = ShowcaseLayout.build(items(), blocks());
        BlockPlan plan = layout.plan();
        assertEquals(Material.SEA_LANTERN, plan.get(1, 0, 1));
        assertEquals(Material.BLACK_CONCRETE, plan.get(5, 4, -1));
        assertEquals(Material.POLISHED_BLACKSTONE, plan.get(ShowcaseLayout.FIRST_X, 1, ShowcaseLayout.BLOCK_Z));
        assertEquals(layout.signs().size(), plan.signs().size());
        assertEquals(layout.sections().size() + layout.blocks().size() + layout.spins().size(), layout.signs().size());
    }

    @Test
    public void spinSelectionKnowsWeaponsToolsCoresAndHelmets() {
        assertTrue(ShowcaseFeature.wantsSpin(CustomItemDef.builder("blade", "Blade", Rarity.RARE, Material.DIAMOND_SWORD).build()));
        assertTrue(ShowcaseFeature.wantsSpin(CustomItemDef.builder("x_core", "Core", Rarity.RARE, Material.NETHER_STAR).build()));
        assertTrue(ShowcaseFeature.wantsSpin(CustomItemDef.builder("cap", "Cap", Rarity.RARE, Material.IRON_HELMET).build()));
        assertFalse(ShowcaseFeature.wantsSpin(CustomItemDef.builder("dust", "Dust", Rarity.COMMON, Material.GLOWSTONE_DUST).build()));
    }

    @Test
    public void signTextFitsTheSign() {
        assertTrue(ShowcaseLayout.wrap("A very long machine name that wraps over lines").stream().allMatch(l -> l.length() <= 15));
        assertTrue(ShowcaseLayout.wrap("A very long machine name that wraps over lines").size() <= 4);
    }
}
