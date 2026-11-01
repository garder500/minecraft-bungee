package win.vampyuyu.survival.gadgets;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Rarity;

/** The six utility items. Pack models live under {@code vampyuyu:utility/*}. */
public final class GadgetItems {
    public static final String SKYHOOK = "skyhook";
    public static final String CLOUD_ANCHOR = "cloud_anchor";
    public static final String ORE_COMPASS = "ore_compass";
    public static final String VOID_NET = "void_net";
    public static final String RAIN_TOTEM = "rain_totem";
    public static final String SATCHEL = "satchel";
    /** PDC key (long) of the Void Net: charges left. */
    public static final String CHARGES = "charges";

    private GadgetItems() {}

    private static String line(int charges, int max) {
        return "<gray>Charges: <white>" + charges + "/" + max;
    }

    static List<String> register(Items items, int maxCharges) {
        List<CustomItemDef> defs = List.of(
            CustomItemDef.builder(SKYHOOK, "Skyhook", Rarity.UNCOMMON, Material.LEAD)
                .itemModel("vampyuyu:utility/skyhook").type("TOOL").maxStackSize(1)
                .lore("<gray>Pulls you to the block you look at.", "<gray>Right-click to fire the hook.").build(),
            CustomItemDef.builder(CLOUD_ANCHOR, "Cloud Anchor", Rarity.RARE, Material.RECOVERY_COMPASS)
                .itemModel("vampyuyu:utility/cloud_anchor").type("TOOL").maxStackSize(1)
                .lore("<gray>Remembers a spot on your island.", "<gray>Sneak + right-click to set it.", "<gray>Right-click to return to it.").build(),
            CustomItemDef.builder(ORE_COMPASS, "Ore Compass", Rarity.UNCOMMON, Material.COMPASS)
                .itemModel("vampyuyu:utility/ore_compass").type("TOOL").maxStackSize(1)
                .lore("<gray>Counts the blocks left until each", "<gray>guaranteed generator ore.", "<gray>Right-click to read the counters.").build(),
            CustomItemDef.builder(VOID_NET, "Void Net", Rarity.EPIC, Material.PHANTOM_MEMBRANE)
                .itemModel("vampyuyu:utility/void_net").type("CHARM").maxStackSize(1).pdc(CHARGES, (long) maxCharges)
                .lore("<gray>Keep it in your inventory: it catches you", "<gray>and your items under your island.", line(maxCharges, maxCharges)).build(),
            CustomItemDef.builder(RAIN_TOTEM, "Rain Totem", Rarity.RARE, Material.HEART_OF_THE_SEA)
                .itemModel("vampyuyu:utility/rain_totem").type("CONSUMABLE").glint(true)
                .lore("<gray>Crops around you grow faster for a minute.", "<gray>Right-click on your island to use it.").build(),
            CustomItemDef.builder(SATCHEL, "Satchel", Rarity.EPIC, Material.RABBIT_HIDE)
                .itemModel("vampyuyu:utility/satchel").type("BAG").maxStackSize(1)
                .lore("<gray>A bag that takes your ores and the", "<gray>resources that do not fit.", "<gray>Right-click to open it.").build());
        defs.forEach(items::register);
        return defs.stream().map(CustomItemDef::id).toList();
    }

    /** Charges left on a Void Net stack (0 when it is not one). */
    static int charges(Items items, ItemStack stack) {
        return items.getLong(stack, CHARGES).map(Long::intValue).orElse(0);
    }

    /** Writes the charges and the "Charges: n/max" lore line. */
    static void setCharges(Items items, ItemStack stack, int charges, int max) {
        items.set(stack, CHARGES, (long) charges);
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        Component next = Msg.component(line(charges, max));
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            if (PlainTextComponentSerializer.plainText().serialize(lore.get(i)).startsWith("Charges:")) {
                lore.set(i, next);
                replaced = true;
            }
        }
        if (!replaced) {
            lore.add(next);
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }
}
