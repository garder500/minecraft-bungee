package win.vampyuyu.survival.economy;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Items;

/**
 * Item identity for trading. A key is the registry id: the UPPER CASE vanilla material name ({@code WHEAT}) or the
 * lower case custom item id ({@code boss_grave_key}). Custom items are looked up lazily because the feature that
 * registers them may enable after the economy.
 *
 * <p>Only "clean" stacks trade: an unmodified vanilla stack (no name, enchantment, damage, contents...) or a custom
 * item with no per-item bonuses and no vanilla enchantments. Traded goods are always RE-CREATED from the key, never
 * handed back as the stored ItemStack, so no tag can be smuggled through a market.
 */
final class ItemKeys {
    private final Core core;

    ItemKeys(Core core) {
        this.core = core;
    }

    private Items items() {
        return core.items();
    }

    /** True when {@code raw} is written as a vanilla key (upper case material name). */
    static boolean looksVanilla(String raw) {
        return raw != null && !raw.isEmpty() && raw.equals(raw.toUpperCase(Locale.ROOT));
    }

    /** The key of a stack, or null for air / null. */
    String keyOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return items().idOf(stack).orElse(stack.getType().name());
    }

    /** True when the stack can be traded: see the class comment. */
    boolean clean(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        var id = items().idOf(stack);
        if (id.isPresent()) {
            if (items().def(id.get()).isEmpty() || !items().bonuses(stack).isEmpty()) {
                return false;
            }
            ItemMeta meta = stack.getItemMeta();
            return meta == null || !meta.hasEnchants();
        }
        return stack.isSimilar(new ItemStack(stack.getType()));
    }

    /** True when the key names an existing item right now. */
    boolean known(String key) {
        return create(key, 1).isPresent();
    }

    /** A fresh stack of {@code amount} (1..max stack) items, empty for an unknown key. */
    Optional<ItemStack> create(String key, int amount) {
        if (key == null || key.isEmpty() || amount < 1) {
            return Optional.empty();
        }
        try {
            if (looksVanilla(key)) {
                Material m = Material.matchMaterial(key);
                if (m == null || !m.isItem() || m.isAir() || !m.name().equals(key)) {
                    return Optional.empty();
                }
                return Optional.of(new ItemStack(m, Math.min(amount, m.getMaxStackSize())));
            }
            if (items().def(key).isEmpty()) {
                return Optional.empty();
            }
            ItemStack stack = items().create(key, 1);
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return Optional.of(stack);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    int maxStack(String key) {
        return create(key, 1).map(ItemStack::getMaxStackSize).orElse(64);
    }

    /** Readable name for chat ("Enchanted Coal" / "Wheat"). */
    String name(String key) {
        if (key == null) {
            return "?";
        }
        if (!looksVanilla(key)) {
            return items().def(key).map(d -> d.name()).orElse(pretty(key));
        }
        return pretty(key);
    }

    static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : key.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** Material used for generic icons of this key. */
    Material material(String key) {
        return create(key, 1).map(ItemStack::getType).orElse(Material.BARRIER);
    }
}
