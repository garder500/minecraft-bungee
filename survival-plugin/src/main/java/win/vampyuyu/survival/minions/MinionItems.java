package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.minions.MinionConfig.Fuel;
import win.vampyuyu.survival.minions.MinionConfig.Skin;
import win.vampyuyu.survival.minions.MinionConfig.Type;
import win.vampyuyu.survival.minions.MinionConfig.Upgrade;

/**
 * Custom item ids, definitions and helpers of the feature. Every id starts with {@code minion_} so none can clash
 * with another feature. The minion item is a PAPER with the pack model {@code vampyuyu:minions/<type>}: PAPER cannot
 * be placed as a block, and placement is handled by this feature only.
 */
public final class MinionItems {
    public static final String ENCHANTED_PREFIX = "minion_enchanted_";
    public static final String SUPER_PREFIX = "minion_super_";
    /** PDC keys written on the minion item. */
    public static final String PDC_TYPE = "mtype";
    public static final String PDC_TIER = "tier";
    public static final String PDC_UID = "uid";

    /** A minion item read back. */
    public record Parsed(String type, int tier, @Nullable String uid) {}

    private MinionItems() {}

    public static String minionId(String type, int tier) {
        return "minion_" + type + "_" + tier;
    }

    public static String fuelId(String id) {
        return "minion_fuel_" + id;
    }

    public static String upgradeId(String id) {
        return "minion_upgrade_" + id;
    }

    public static String skinId(String id) {
        return "minion_skin_" + id;
    }

    public static String enchantedId(Material material) {
        return ENCHANTED_PREFIX + ItemKey.slug(material);
    }

    public static String superId(Material material) {
        return SUPER_PREFIX + ItemKey.slug(material);
    }

    /** Display name of the minion of a type and tier, e.g. "Cobblestone Minion XI". */
    public static String minionName(Type type, int tier) {
        return type.name() + " Minion " + MinionConfig.roman(tier);
    }

    // ---------------------------------------------------------------- registration

    /** Registers every custom item of the config. @throws IllegalStateException on a duplicate id */
    public static void register(Items items, MinionConfig config) {
        for (Type type : config.types.values()) {
            for (int tier = 1; tier <= MinionConfig.MAX_TIER; tier++) {
                items.register(minionDef(config, type, tier));
            }
        }
        for (Fuel fuel : config.fuels.values()) {
            if (fuel.custom()) {
                items.register(CustomItemDef.builder(fuelId(fuel.id()), fuel.name(), fuel.rarity(), fuel.material())
                    .itemModel("vampyuyu:minions/fuel_" + fuel.id()).type("MINION FUEL").glint(true)
                    .lore(fuelLore(fuel).toArray(String[]::new)).build());
            }
        }
        for (Upgrade up : config.upgrades.values()) {
            List<String> lore = new ArrayList<>();
            for (String line : up.lore()) {
                lore.add("<gray>" + line);
            }
            lore.add("");
            lore.add("<yellow>Add it from the upgrade slots of a minion.");
            items.register(CustomItemDef.builder(upgradeId(up.id()), up.name(), up.rarity(), up.material())
                .itemModel("vampyuyu:minions/upgrade_" + up.id()).type("MINION UPGRADE")
                .lore(lore.toArray(String[]::new)).build());
        }
        for (Skin skin : config.skins.values()) {
            items.register(CustomItemDef.builder(skinId(skin.id()), skin.name(), skin.rarity(), skin.head())
                .type("MINION HAT").lore("<gray>A hat for your minions.", "",
                    "<yellow>Put it in the hat slot of a minion.").build());
        }
        for (Material material : config.producible()) {
            String name = MinionConfig.prettify(material);
            items.register(CustomItemDef.builder(enchantedId(material), "Enchanted " + name, Rarity.UNCOMMON, material)
                .type("RESOURCE").glint(true)
                .lore("<gray>" + config.compactRatio + " " + name + " compacted into one.",
                    "<gray>Pays for minion tier upgrades.").build());
            items.register(CustomItemDef.builder(superId(material), "Super Enchanted " + name, Rarity.RARE, material)
                .type("RESOURCE").glint(true)
                .lore("<gray>" + config.superRatio + " Enchanted " + name + " compacted into one.",
                    "<gray>Pays for the highest minion tiers.").build());
        }
    }

    /** Removes everything {@link #register} added. */
    public static void unregister(Items items, MinionConfig config) {
        for (String id : new ArrayList<>(items.ids())) {
            if (id.startsWith("minion_")) {
                items.unregister(id);
            }
        }
    }

    private static CustomItemDef minionDef(MinionConfig config, Type type, int tier) {
        Rarity rarity = config.tierRarity[tier - 1];
        double seconds = type.actionSeconds() * config.actionFactor[tier - 1];
        int diameter = config.radius[tier - 1] * 2 + 1;
        return CustomItemDef.builder(minionId(type.id(), tier), minionName(type, tier), rarity, Material.PAPER)
            .itemModel("vampyuyu:minions/" + type.id()).type("MINION").maxStackSize(1)
            .pdc(PDC_TYPE, type.id()).pdc(PDC_TIER, (long) tier)
            .lore("<gray>Place it on your island and it works",
                "<gray>for you, even while you are away.", "",
                "<gray>Time between actions: <green>" + trim(seconds) + "s",
                "<gray>Max storage: <yellow>" + config.storageSlots[tier - 1] + " slots",
                "<gray>Working area: <yellow>" + diameter + "x" + diameter, "",
                "<yellow>Right-click a block to place it.").build();
    }

    private static List<String> fuelLore(Fuel fuel) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Minion speed: <green>+" + trim(fuel.speed() * 100) + "%");
        lore.add("<gray>Lasts: <green>" + trim(fuel.hours()) + " hours");
        lore.add("<dark_gray>Burns only while the minion works.");
        if (!fuel.kinds().isEmpty()) {
            List<String> kinds = new ArrayList<>();
            fuel.kinds().forEach(k -> kinds.add(k.name().charAt(0) + k.name().substring(1).toLowerCase(Locale.ROOT)));
            java.util.Collections.sort(kinds);
            lore.add("<gray>Works on: <yellow>" + String.join(", ", kinds));
        }
        lore.add("");
        lore.add("<yellow>Add it from the fuel slot of a minion.");
        return lore;
    }

    /** 14.0 becomes "14", 12.0 stays "12", 8.57 becomes "8.6". */
    public static String trim(double value) {
        double rounded = Math.round(value * 10d) / 10d;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    // ---------------------------------------------------------------- stacks

    /** A fresh minion item; {@code uid} null stamps nothing (a crafted item is stamped when placed). */
    public static ItemStack createMinion(Items items, String type, int tier, @Nullable String uid) {
        ItemStack stack = items.create(minionId(type, tier));
        if (uid != null) {
            items.set(stack, PDC_UID, uid);
        }
        return stack;
    }

    /** A new random uid. */
    public static String newUid() {
        return UUID.randomUUID().toString();
    }

    /** Reads a minion item, null when the stack is something else. */
    public static @Nullable Parsed parse(Items items, @Nullable ItemStack stack) {
        var id = items.idOf(stack);
        if (id.isEmpty() || !id.get().startsWith("minion_")) {
            return null;
        }
        var type = items.getString(stack, PDC_TYPE);
        var tier = items.getLong(stack, PDC_TIER);
        if (type.isEmpty() || tier.isEmpty() || !id.get().equals(minionId(type.get(), tier.get().intValue()))) {
            return null;
        }
        return new Parsed(type.get(), tier.get().intValue(), items.getString(stack, PDC_UID).orElse(null));
    }
}
