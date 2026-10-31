package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;

/** {@code /skins}: the six sword skins, locked or unlocked, applied to the sword in the main hand. */
final class SkinGui {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final Core core;
    private final SkinService skins;

    SkinGui(Core core, SkinService skins) {
        this.core = core;
        this.skins = skins;
    }

    void open(Player player) {
        Gui gui = core.guis().create(3, "<dark_gray>Sword Skins");
        gui.fill(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int slot = 10;
        for (Skin skin : Skin.values()) {
            boolean owned = skins.has(player.getUniqueId(), skin);
            ItemStack icon = core.items().create(skin.tokenId());
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(line("<gray>" + skin.rarity + " skin"));
            lore.add(Component.empty());
            lore.add(line(owned ? "<green>Unlocked. Click to put it on the sword you hold."
                : "<red>Locked: <gray>" + skin.source));
            meta.lore(lore);
            icon.setItemMeta(meta);
            gui.set(slot++, icon, click -> {
                if (!owned) {
                    core.msg().warn(player, "You have not unlocked " + skin.title + " yet: " + skin.source + ".");
                    return;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!skins.isSword(held)) {
                    core.msg().warn(player, "Hold the sword you want to skin first.");
                    return;
                }
                skins.apply(held, skin);
                core.msg().ok(player, skin.title + " is on your sword. It is bound to you now.");
            });
        }
        gui.set(22, Guis.icon(Material.BARRIER, "<white>Default look", "<gray>Click to remove the skin from",
            "<gray>the sword you hold."), click -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!skins.isSword(held)) {
                core.msg().warn(player, "Hold the sword you want to change first.");
                return;
            }
            skins.apply(held, null);
            core.msg().ok(player, "Your sword has its default look again.");
        });
        gui.open(player);
    }

    private static Component line(String mini) {
        return MINI.deserialize(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
