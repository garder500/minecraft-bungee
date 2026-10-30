package win.vampyuyu.survival.economy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Text form of an ItemStack for the auction file. Production uses {@code ItemStack.serializeAsBytes()} (full
 * fidelity: components, enchantments, nested items) as Base64 with the prefix {@code b:}. A server that does not
 * implement it (the test mock) falls back to the YAML form ({@code y:}); both decode on any server that can read them.
 */
final class ItemCodec {
    private ItemCodec() {}

    static String encode(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() < 1) {
            throw new IllegalArgumentException("Nothing to encode");
        }
        try {
            byte[] bytes = stack.serializeAsBytes();
            if (bytes != null && bytes.length > 0) {
                return "b:" + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // fall through to the YAML form
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", stack);
        return "y:" + Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /** The stack, or null when the text cannot be read (corrupt file). */
    static ItemStack decode(String text) {
        if (text == null || text.length() < 3) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(text.substring(2));
            if (text.startsWith("b:")) {
                return ItemStack.deserializeBytes(raw);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(raw, StandardCharsets.UTF_8));
            return yaml.getItemStack("item");
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}
