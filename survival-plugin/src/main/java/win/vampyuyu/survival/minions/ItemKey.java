package win.vampyuyu.survival.minions;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Items;

/**
 * The string identity of an item kind, used for minion storage and for every cost: {@code m:COBBLESTONE} for a plain
 * vanilla item, {@code c:minion_enchanted_cobblestone} for a registered custom item.
 */
public final class ItemKey {
    private static final String VANILLA = "m:";
    private static final String CUSTOM = "c:";

    private ItemKey() {}

    public static String vanilla(Material material) {
        return VANILLA + material.name();
    }

    public static String custom(String id) {
        return CUSTOM + id;
    }

    public static boolean isCustom(String key) {
        return key.startsWith(CUSTOM);
    }

    /** Custom item id of a {@code c:} key. */
    public static String id(String key) {
        return key.substring(CUSTOM.length());
    }

    /** Material of an {@code m:} key, null when the key is custom or unknown. */
    public static @Nullable Material material(String key) {
        return key.startsWith(VANILLA) ? Material.matchMaterial(key.substring(VANILLA.length())) : null;
    }

    /** Largest stack the key holds (custom items are assumed to stack to 64). */
    public static int stackSize(String key) {
        Material material = material(key);
        return material == null ? 64 : Math.max(1, material.getMaxStackSize());
    }

    /**
     * Key of a real stack: custom id when it is one of ours, else the vanilla material when the stack is plain (no
     * name, enchant or other data, so a renamed or enchanted item can never pay for something). Null otherwise.
     */
    public static @Nullable String of(Items items, @Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        var id = items.idOf(stack);
        if (id.isPresent()) {
            return items.def(id.get()).isPresent() ? custom(id.get()) : null;
        }
        return stack.isSimilar(new ItemStack(stack.getType())) ? vanilla(stack.getType()) : null;
    }

    /** A new stack of {@code amount} items of this key (amount may exceed the max stack: use addItem). */
    public static ItemStack create(Items items, String key, int amount) {
        if (isCustom(key)) {
            return items.create(id(key), amount);
        }
        Material material = material(key);
        if (material == null) {
            throw new IllegalArgumentException("Unknown item key " + key);
        }
        return new ItemStack(material, amount);
    }

    /** True when the key can be turned into a stack. */
    public static boolean valid(Items items, String key) {
        if (isCustom(key)) {
            return items.def(id(key)).isPresent();
        }
        return material(key) != null;
    }

    /** Lower case suffix used in generated custom item ids. */
    public static String slug(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }
}
