package win.vampyuyu.survival.menu;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/** The "Skyblock Menu" item definition. */
final class SkyblockMenuItem {
    static final String ID = "skyblock_menu";
    static final String MODEL = "vampyuyu:menu/skyblock_menu";

    private final Core core;

    SkyblockMenuItem(Core core) {
        this.core = core;
    }

    void register() {
        if (core.items().def(ID).isPresent()) {
            core.items().unregister(ID);
        }
        core.items().register(CustomItemDef.builder(ID, "Skyblock Menu", Rarity.SPECIAL, Material.NETHER_STAR)
            .itemModel(MODEL)
            .type("MENU")
            .lore("<gray>View all of your Skyblock progress,", "<gray>skills, collections, quests and more.", "",
                "<yellow>Right-click to open!")
            .maxStackSize(1)
            .build());
    }

    void unregister() {
        core.items().unregister(ID);
    }

    ItemStack create() {
        return core.items().create(ID);
    }

    boolean is(ItemStack stack) {
        return stack != null && stack.getType() == Material.NETHER_STAR && core.items().is(stack, ID);
    }

    Optional<String> idOf(ItemStack stack) {
        return core.items().idOf(stack);
    }
}
