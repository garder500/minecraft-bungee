package win.vampyuyu.survival.core;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;

/**
 * Custom item registry and factory. Every custom item carries its id in the PDC key {@code vampyuyu:item_id};
 * extra values declared by the definition are stored under {@code vampyuyu:<key>}; per-item stat bonuses (reforges,
 * enchants) under {@code vampyuyu:bonus_<stat>}. The definition (name, lore, base stats) is NOT copied onto the
 * stack: {@link #refresh} re-renders it, so changing a definition updates old items the next time they are refreshed.
 */
public final class Items {
    /** PDC namespace of everything this plugin writes on items. */
    public static final String NAMESPACE = "vampyuyu";
    /** PDC key (inside the namespace) holding the item id. */
    public static final String ID_KEY = "item_id";
    /** Prefix of the per-item stat bonus keys. */
    public static final String BONUS_PREFIX = "bonus_";

    private static final NamespacedKey ID = new NamespacedKey(NAMESPACE, ID_KEY);

    private final ConcurrentMap<String, CustomItemDef> defs = new ConcurrentHashMap<>();

    /** Registers a definition. Throws if the id is already taken. */
    public void register(CustomItemDef def) {
        if (defs.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("Duplicate custom item id: " + def.id());
        }
    }

    /** Removes a definition (items already in the world keep their id but stop rendering). */
    public void unregister(String id) {
        defs.remove(id);
    }

    public Optional<CustomItemDef> def(String id) {
        return Optional.ofNullable(defs.get(id));
    }

    /** Every registered id. */
    public Collection<String> ids() {
        return Collections.unmodifiableCollection(defs.keySet());
    }

    /** One item. @throws IllegalArgumentException for an unknown id */
    public ItemStack create(String id) {
        return create(id, 1);
    }

    /** A stack of {@code amount} items. @throws IllegalArgumentException for an unknown id */
    public ItemStack create(String id, int amount) {
        CustomItemDef def = defs.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown custom item: " + id);
        }
        ItemStack stack = new ItemStack(def.material(), amount);
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ID, PersistentDataType.STRING, def.id());
        def.pdcStrings().forEach((k, v) -> pdc.set(key(k), PersistentDataType.STRING, v));
        def.pdcLongs().forEach((k, v) -> pdc.set(key(k), PersistentDataType.LONG, v));
        def.pdcDoubles().forEach((k, v) -> pdc.set(key(k), PersistentDataType.DOUBLE, v));
        if (def.itemModel() != null) {
            NamespacedKey model = NamespacedKey.fromString(def.itemModel());
            if (model == null) {
                throw new IllegalArgumentException("Bad item model key: " + def.itemModel());
            }
            meta.setItemModel(model);
        }
        if (def.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (def.hideFlags()) {
            meta.addItemFlags(ItemFlag.values());
        }
        if (def.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (def.maxStackSize() != null) {
            meta.setMaxStackSize(def.maxStackSize());
        }
        if (def.equippable() != null) {
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(def.equippable().slot());
            if (def.equippable().assetId() != null) {
                NamespacedKey asset = NamespacedKey.fromString(def.equippable().assetId());
                if (asset == null) {
                    throw new IllegalArgumentException("Bad equipment asset key: " + def.equippable().assetId());
                }
                equippable.setModel(asset);
            }
            meta.setEquippable(equippable);
        }
        meta.itemName(ItemRenderer.name(def));
        meta.lore(ItemRenderer.lore(def, Map.of()));
        stack.setItemMeta(meta);
        return stack;
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(NAMESPACE, name);
    }

    /** The custom id of a stack, empty for vanilla or unmarked items. */
    public Optional<String> idOf(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    }

    public boolean isCustom(@Nullable ItemStack stack) {
        return idOf(stack).isPresent();
    }

    /** True when the stack is the custom item {@code id}. */
    public boolean is(@Nullable ItemStack stack, String id) {
        return idOf(stack).map(id::equals).orElse(false);
    }

    /**
     * Total stat bonuses of an item: definition stats plus per-item bonuses. Empty map for non-custom items
     * and for ids that are no longer registered.
     */
    public Map<Stat, Double> statsOf(@Nullable ItemStack stack) {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        Optional<String> id = idOf(stack);
        if (id.isEmpty()) {
            return result;
        }
        CustomItemDef def = defs.get(id.get());
        if (def != null) {
            result.putAll(def.stats());
        }
        bonuses(stack).forEach((stat, value) -> result.merge(stat, value, Double::sum));
        return result;
    }

    /** Per-item bonuses only (reforges, enchants...). */
    public Map<Stat, Double> bonuses(@Nullable ItemStack stack) {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        if (stack == null || !stack.hasItemMeta()) {
            return result;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        for (Stat stat : Stat.values()) {
            Double value = pdc.get(key(BONUS_PREFIX + stat.key()), PersistentDataType.DOUBLE);
            if (value != null && value != 0) {
                result.put(stat, value);
            }
        }
        return result;
    }

    /** Sets (0 removes) a per-item stat bonus. Call {@link #refresh} afterwards to redraw the lore. */
    public void setBonus(ItemStack stack, Stat stat, double amount) {
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey key = key(BONUS_PREFIX + stat.key());
        if (amount == 0) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, amount);
        }
        stack.setItemMeta(meta);
    }

    /** Re-renders name and lore from the current definition and the item's bonuses. No-op for non-custom stacks. */
    public void refresh(ItemStack stack) {
        Optional<String> id = idOf(stack);
        CustomItemDef def = id.map(defs::get).orElse(null);
        if (def == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(ItemRenderer.name(def));
        meta.lore(ItemRenderer.lore(def, bonuses(stack)));
        stack.setItemMeta(meta);
    }

    // ---------------------------------------------------------------- extra PDC values

    public Optional<String> getString(@Nullable ItemStack stack, String key) {
        return get(stack, key, PersistentDataType.STRING);
    }

    public Optional<Long> getLong(@Nullable ItemStack stack, String key) {
        return get(stack, key, PersistentDataType.LONG);
    }

    public Optional<Double> getDouble(@Nullable ItemStack stack, String key) {
        return get(stack, key, PersistentDataType.DOUBLE);
    }

    private <T> Optional<T> get(@Nullable ItemStack stack, String key, PersistentDataType<?, T> type) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(key(key), type));
    }

    /** Writes an extra value (per-item data such as a pet's xp or a minion's tier). Keys: {@code [a-z0-9_./-]+}. */
    public void set(ItemStack stack, String key, String value) {
        put(stack, key, PersistentDataType.STRING, value);
    }

    public void set(ItemStack stack, String key, long value) {
        put(stack, key, PersistentDataType.LONG, value);
    }

    public void set(ItemStack stack, String key, double value) {
        put(stack, key, PersistentDataType.DOUBLE, value);
    }

    private <T> void put(ItemStack stack, String key, PersistentDataType<?, T> type, T value) {
        if (key.equals(ID_KEY) || key.startsWith(BONUS_PREFIX)) {
            throw new IllegalArgumentException("Reserved PDC key: " + key);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(key(key), type, value);
        stack.setItemMeta(meta);
    }
}
