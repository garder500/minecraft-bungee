package win.vampyuyu.survival.wings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Rarity;

/**
 * Definitions and identity of the Wings and the Wing Cores.
 *
 * <p>Items: {@code wings_1} .. {@code wings_<max>} (one definition per level, so the lore shows the real numbers
 * and the tier decides the worn texture) and {@code wings_core_1} .. {@code wings_core_5}.
 *
 * <p>PDC (namespace {@code vampyuyu}): {@code wings_owner} (owner UUID string), {@code wings_uuid} (unique id of this
 * very item), {@code untradable} (long 1, "the economy feature must refuse this item": no auction, bazaar, trade, shop).
 */
public final class WingsItems {
    /** PDC key: owner UUID. */
    public static final String KEY_OWNER = "wings_owner";
    /** PDC key: unique id of the item. */
    public static final String KEY_UUID = "wings_uuid";
    /** PDC key: flag read by the economy feature, long 1 means the item can never be traded or sold. */
    public static final String KEY_UNTRADABLE = "untradable";

    private static final Pattern WINGS_ID = Pattern.compile("wings_(\\d+)");
    private static final Pattern CORE_ID = Pattern.compile("wings_core_([1-5])");
    private static final Rarity[] CORE_RARITY = {Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY, Rarity.MYTHIC, Rarity.DIVINE};

    /** What a Wings stack says about itself. {@code owner} is null while the item is unbound. */
    public record Tag(int level, @Nullable UUID owner, @Nullable String uuid) {
        public boolean unbound() {
            return owner == null || uuid == null;
        }
    }

    private final Items items;
    private final List<String> registered = new ArrayList<>();

    public WingsItems(Items items) {
        this.items = items;
    }

    public static String wingsId(int level) {
        return "wings_" + level;
    }

    public static String coreId(int tier) {
        return "wings_core_" + tier;
    }

    /** (Re)registers every definition from the settings. Safe to call again on reload. */
    public void register(WingsSettings s) {
        unregister();
        for (int level = 1; level <= s.maxLevel; level++) {
            items.register(wingsDef(s, level));
            registered.add(wingsId(level));
        }
        for (int tier = 1; tier <= 5; tier++) {
            items.register(coreDef(s, tier));
            registered.add(coreId(tier));
        }
    }

    /** Removes the definitions (feature disable or reload). */
    public void unregister() {
        for (String id : registered) {
            items.unregister(id);
        }
        registered.clear();
    }

    private static CustomItemDef wingsDef(WingsSettings s, int level) {
        int tier = WingsSettings.tierOf(level);
        double capacity = 100 + s.capacityBonus(level);
        double seconds = s.flightSeconds(capacity, level);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Wings of pure light. Wear them like a");
        lore.add("<gray>chestplate and <white>fly freely</white> over your islands.");
        lore.add("");
        lore.add("<gray>Level: <gold>" + level + "<dark_gray>/<gold>" + s.maxLevel);
        lore.add("<gray>Flight Energy: <light_purple>" + Math.round(capacity) + " <dark_gray>(~" + Math.round(seconds) + "s of flight)");
        lore.add("<gray>Recharge: <green>" + Msg.number(s.regenPerSecond(level, false)) + "/s <dark_gray>on the ground");
        lore.add("<gray>Flight speed: <aqua>" + Math.round(s.flySpeed(level) / 0.1f * 100) + "%");
        lore.add("<gray>Gliding: <aqua>" + Msg.number(s.drainGlide * s.drainMultiplier(level)) + "/s <dark_gray>(rocket boost "
            + Msg.number(s.fireworkCost) + ")");
        s.statBonuses(level).forEach((stat, amount) -> lore.add("<gray>" + stat.displayName() + ": <green>+"
            + Msg.number(amount)));
        lore.addAll(abilityLines(s, level));
        lore.add("");
        lore.add("<dark_gray>Soulbound: cannot be crafted, traded or sold.");
        lore.add("<dark_gray>Disabled in dungeons, boss arenas and in combat.");
        lore.add("<dark_gray>Gliding and rocket boosts cost Flight Energy.");
        lore.add("<dark_gray>Upgrade with Wing Cores: <gray>/wings");
        String model = tier == 1 ? "vampyuyu:wings/wings" : "vampyuyu:wings/wings_tier" + tier;
        return CustomItemDef.builder(wingsId(level), "The Wings", tier >= 5 ? Rarity.DIVINE : Rarity.MYTHIC, Material.ELYTRA)
            .itemModel(model)
            .equippable(EquipmentSlot.CHEST, "vampyuyu:wings_tier" + tier)
            .type("WINGS")
            .lore(lore.toArray(String[]::new))
            .pdc(KEY_UNTRADABLE, 1L)
            .unbreakable(true)
            .glint(tier >= 3)
            .maxStackSize(1)
            .build();
    }

    /** The abilities the level has unlocked (and the next one to come) for lore and menu. */
    static List<String> abilityLines(WingsSettings s, int level) {
        List<String> out = new ArrayList<>();
        if (s.dashEnabled) {
            out.add(level >= s.dashLevel ? "<gray>Dash: <green>press F in the air <dark_gray>(" + Msg.number(s.dashCost) + " energy)"
                : "<dark_gray>Dash: unlocks at level " + s.dashLevel);
        }
        out.add(level >= s.diveLevel ? "<gray>Dive recovery: <green>steep glides refill energy"
            : "<dark_gray>Dive recovery: unlocks at level " + s.diveLevel);
        out.add(level >= s.landingLevel ? "<gray>Landing burst: <green>wind cushions your falls"
            : "<dark_gray>Landing burst: unlocks at level " + s.landingLevel);
        return out;
    }

    private static CustomItemDef coreDef(WingsSettings s, int tier) {
        String name = s.coreNames.get(tier - 1);
        return CustomItemDef.builder(coreId(tier), name, CORE_RARITY[tier - 1], Material.AMETHYST_SHARD)
            .itemModel("vampyuyu:wings/core_" + tier)
            .type("WING CORE")
            .lore("<gray>Dropped by bosses. Open <white>/wings</white> and",
                "<gray>consume it to raise the level of your Wings.",
                "<dark_gray>Tier " + tier + " core.")
            .glint(true)
            .maxStackSize(64)
            .build();
    }

    // ---------------------------------------------------------------- creation

    /** An unbound Wings item of {@code level}: it binds to the first player who holds it and has no Wings yet. */
    public ItemStack createUnbound(int level) {
        return items.create(wingsId(level));
    }

    /** The Wings item of a player: owner and unique id written, "Bound to" line added to the lore. */
    public ItemStack create(int level, UUID owner, String ownerName, String uuid) {
        ItemStack stack = items.create(wingsId(level));
        items.set(stack, KEY_OWNER, owner.toString());
        items.set(stack, KEY_UUID, uuid);
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int at = Math.max(0, lore.size() - 1);  // before the rarity line
        lore.add(at, Component.text("Bound to " + ownerName, NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(at + 1, Component.empty());
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createCore(int tier, int amount) {
        return items.create(coreId(tier), amount);
    }

    // ---------------------------------------------------------------- reading

    public boolean isWings(@Nullable ItemStack stack) {
        return level(stack) > 0;
    }

    /** Level encoded in the item id, 0 when the stack is not a Wings item. */
    public int level(@Nullable ItemStack stack) {
        Optional<String> id = items.idOf(stack);
        if (id.isEmpty()) {
            return 0;
        }
        Matcher m = WINGS_ID.matcher(id.get());
        if (!m.matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Identity of a Wings stack, null for anything else. */
    public @Nullable Tag read(@Nullable ItemStack stack) {
        int level = level(stack);
        if (level <= 0) {
            return null;
        }
        UUID owner = null;
        Optional<String> raw = items.getString(stack, KEY_OWNER);
        if (raw.isPresent()) {
            try {
                owner = UUID.fromString(raw.get());
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        return new Tag(level, owner, items.getString(stack, KEY_UUID).orElse(null));
    }

    /** Tier (1-5) of a Wing Core stack, 0 when it is not one. */
    public int coreTier(@Nullable ItemStack stack) {
        Optional<String> id = items.idOf(stack);
        if (id.isEmpty()) {
            return 0;
        }
        Matcher m = CORE_ID.matcher(id.get());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }
}
