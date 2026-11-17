package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * What the Skyblock showcase gallery needs from the custom block system: every item that is PLACED as a block with a
 * 3D item-display model (machines, generators, Fountain, Spring Stone, cables, the Forge). Read-only: nothing here
 * registers a machine, so a gallery block never runs.
 */
public final class ShowcaseBlocks {
    /** One placed block: {@code itemId} is the item whose model is drawn, {@code hitbox} the vanilla block under it. */
    public record Entry(String itemId, String title, Material hitbox, String modelKey) {}

    /** Cable shapes shown: not connected, straight, a full junction, and the powered junction. */
    private static final int[] CABLE_MASKS = {0, 5, 10, 15};

    private ShowcaseBlocks() {
    }

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (MachineType type : MachineType.values()) {
            if (type == MachineType.CABLE) {
                for (int mask : CABLE_MASKS) {
                    out.add(new Entry(type.itemId, type.title + " " + mask, type.block, "machine/cable_" + mask));
                }
                out.add(new Entry(type.itemId, type.title + " 15 on", type.block, "machine/cable_15_on"));
            } else {
                out.add(new Entry(type.itemId, type.title, type.block, null));
            }
        }
        out.add(new Entry(QuarryItems.FORGE, "Forge", Material.BARRIER, null));
        return out;
    }
}
