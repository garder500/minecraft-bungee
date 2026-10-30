package win.vampyuyu.survival.items.accessory;

import java.util.ArrayList;
import java.util.List;

/** Profile section {@code accessories}: purchased slot upgrades and the bag contents (base64 of {@code ItemStack.serializeAsBytes}). */
public final class AccessoryData {
    /** Slot upgrades bought so far. */
    public int upgrades = 0;
    /** One entry per bag slot, null or empty for a free slot. */
    public List<String> slots = new ArrayList<>();
}
