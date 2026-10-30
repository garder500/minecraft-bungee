package win.vampyuyu.survival.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;

/**
 * Immutable definition of a custom item. Build with {@link #builder}, register with {@link Items#register}.
 *
 * @param id unique id, {@code [a-z0-9_]+} (e.g. "wings", "minion_cobblestone_1"); stored in the item's PDC
 * @param name display name WITHOUT colour codes (the rarity colours it)
 * @param rarity rarity, drives the name colour and the last lore line
 * @param material base material
 * @param itemModel optional {@code item_model} key such as "vampyuyu:wings" (resource pack model)
 * @param typeLabel optional word after the rarity on the last line, e.g. "CHESTPLATE" gives "LEGENDARY CHESTPLATE"
 * @param lore description lines, MiniMessage allowed, shown under the stats
 * @param stats base stat bonuses shown as "Strength: +50"
 * @param pdcStrings extra PDC string values written on creation (keys {@code [a-z0-9_./-]+})
 * @param pdcLongs extra PDC long values
 * @param pdcDoubles extra PDC double values
 * @param unbreakable true makes the item unbreakable
 * @param hideFlags true hides vanilla attribute / enchant / unbreakable tooltips
 * @param glint true forces the enchant glint
 * @param maxStackSize optional max stack size override (1-99)
 * @param equippable optional equippable component data, makes the item wearable like armor
 */
public record CustomItemDef(String id, String name, Rarity rarity, Material material, @Nullable String itemModel,
        @Nullable String typeLabel, List<String> lore, Map<Stat, Double> stats, Map<String, String> pdcStrings,
        Map<String, Long> pdcLongs, Map<String, Double> pdcDoubles, boolean unbreakable, boolean hideFlags,
        boolean glint, @Nullable Integer maxStackSize, @Nullable Equip equippable) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_./-]+");

    /**
     * Wearable data.
     *
     * @param slot equipment slot (HEAD, CHEST, LEGS, FEET, BODY...)
     * @param assetId equipment asset key such as "vampyuyu:wings_default" (resource-pack {@code equipment/wings_default.json}), may be null
     */
    public record Equip(EquipmentSlot slot, @Nullable String assetId) {}

    public CustomItemDef {
        Objects.requireNonNull(id);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Item id must match [a-z0-9_]+: " + id);
        }
        Objects.requireNonNull(name);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(material);
        lore = List.copyOf(lore);
        EnumMap<Stat, Double> copy = new EnumMap<>(Stat.class);
        copy.putAll(stats);
        stats = Collections.unmodifiableMap(copy);
        pdcStrings = Collections.unmodifiableMap(new LinkedHashMap<>(pdcStrings));
        pdcLongs = Collections.unmodifiableMap(new LinkedHashMap<>(pdcLongs));
        pdcDoubles = Collections.unmodifiableMap(new LinkedHashMap<>(pdcDoubles));
        for (String key : pdcStrings.keySet()) {
            checkKey(key);
        }
        for (String key : pdcLongs.keySet()) {
            checkKey(key);
        }
        for (String key : pdcDoubles.keySet()) {
            checkKey(key);
        }
        if (maxStackSize != null && (maxStackSize < 1 || maxStackSize > 99)) {
            throw new IllegalArgumentException("maxStackSize must be 1-99");
        }
    }

    private static void checkKey(String key) {
        if (!KEY.matcher(key).matches() || key.equals(Items.ID_KEY) || key.startsWith(Items.BONUS_PREFIX)) {
            throw new IllegalArgumentException("Invalid or reserved PDC key: " + key);
        }
    }

    public static Builder builder(String id, String name, Rarity rarity, Material material) {
        return new Builder(id, name, rarity, material);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final String id;
        private final String name;
        private final Rarity rarity;
        private final Material material;
        private String itemModel;
        private String typeLabel;
        private final java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        private final Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        private final Map<String, String> strings = new LinkedHashMap<>();
        private final Map<String, Long> longs = new LinkedHashMap<>();
        private final Map<String, Double> doubles = new LinkedHashMap<>();
        private boolean unbreakable;
        private boolean hideFlags = true;
        private boolean glint;
        private Integer maxStack;
        private Equip equip;

        private Builder(String id, String name, Rarity rarity, Material material) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.material = material;
        }

        /** Resource-pack item model key, e.g. "vampyuyu:wings". */
        public Builder itemModel(String key) { this.itemModel = key; return this; }

        /** Word after the rarity on the last line, e.g. "SWORD". */
        public Builder type(String label) { this.typeLabel = label; return this; }

        /** Adds description lines (MiniMessage allowed). */
        public Builder lore(String... lines) { lore.addAll(List.of(lines)); return this; }

        public Builder stat(Stat stat, double amount) { stats.put(stat, amount); return this; }

        public Builder pdc(String key, String value) { strings.put(key, value); return this; }

        public Builder pdc(String key, long value) { longs.put(key, value); return this; }

        public Builder pdc(String key, double value) { doubles.put(key, value); return this; }

        public Builder unbreakable(boolean value) { this.unbreakable = value; return this; }

        /** Hide vanilla tooltips (default true). */
        public Builder hideFlags(boolean value) { this.hideFlags = value; return this; }

        public Builder glint(boolean value) { this.glint = value; return this; }

        public Builder maxStackSize(int size) { this.maxStack = size; return this; }

        /** Makes the item wearable in {@code slot}, with an optional equipment asset (worn model/texture). */
        public Builder equippable(EquipmentSlot slot, @Nullable String assetId) {
            this.equip = new Equip(slot, assetId);
            return this;
        }

        public CustomItemDef build() {
            return new CustomItemDef(id, name, rarity, material, itemModel, typeLabel, lore, stats, strings, longs,
                doubles, unbreakable, hideFlags, glint, maxStack, equip);
        }
    }
}
