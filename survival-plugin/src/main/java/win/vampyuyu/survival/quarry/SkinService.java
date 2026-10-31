package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.core.Core;

/** Sword skins: account unlocks and the look of one sword. A skinned sword is bound to its owner (never sold). */
final class SkinService {
    /** PDC key (string): the skin applied to a sword. {@link MarketRules} treats such an item as bound. */
    static final NamespacedKey APPLIED = new NamespacedKey("vampyuyu", "skin");
    private static final String LORE_PREFIX = "Skin: ";

    private final Core core;
    private final QuarryService quarry;

    SkinService(Core core, QuarryService quarry) {
        this.core = core;
        this.quarry = quarry;
    }

    boolean has(UUID player, Skin skin) {
        return quarry.data(player).skins.contains(skin.id);
    }

    /** Unlocks a skin for the account. False when it already was. */
    boolean unlock(UUID player, Skin skin) {
        QuarryData data = quarry.data(player);
        if (data.skins.contains(skin.id)) {
            return false;
        }
        data.skins.add(skin.id);
        core.profiles().markDirty(player);
        return true;
    }

    List<Skin> unlocked(UUID player) {
        List<Skin> out = new ArrayList<>();
        for (Skin skin : Skin.values()) {
            if (has(player, skin)) {
                out.add(skin);
            }
        }
        return out;
    }

    boolean isSword(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && Tag.ITEMS_SWORDS.isTagged(stack.getType());
    }

    /** Puts the skin on the sword (null removes it). The sword keeps its stats and abilities. */
    void apply(ItemStack sword, Skin skin) {
        ItemMeta meta = sword.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(line).startsWith(LORE_PREFIX));
        if (skin == null) {
            meta.getPersistentDataContainer().remove(APPLIED);
            String base = core.items().idOf(sword).flatMap(id -> core.items().def(id)).map(d -> d.itemModel()).orElse(null);
            meta.setItemModel(base == null ? null : NamespacedKey.fromString(base));
        } else {
            meta.getPersistentDataContainer().set(APPLIED, PersistentDataType.STRING, skin.id);
            meta.setItemModel(NamespacedKey.fromString(skin.model()));
            lore.add(Component.text(LORE_PREFIX + skin.title, skin.color).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Bound to you: it cannot be sold.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (skin == null) {
            lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line).startsWith("Bound to you"));
        }
        meta.lore(lore);
        sword.setItemMeta(meta);
    }
}
