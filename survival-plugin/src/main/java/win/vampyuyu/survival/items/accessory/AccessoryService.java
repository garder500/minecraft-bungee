package win.vampyuyu.survival.items.accessory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.stats.StatProvider;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Accessory bag storage (profile section {@code accessories}) and the "accessory" stat source. */
public final class AccessoryService implements StatProvider {
    public static final String SECTION = "accessories";

    private final Core core;
    private final Gear gear;
    private final StatsEngine engine;
    private volatile ItemsSettings settings;
    private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();
    private final Map<UUID, AccessoryMath.Result> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cacheVersion = new ConcurrentHashMap<>();

    public AccessoryService(Core core, Gear gear, StatsEngine engine, ItemsSettings settings) {
        this.core = core;
        this.gear = gear;
        this.engine = engine;
        this.settings = settings;
    }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    public ItemsSettings settings() { return settings; }

    public AccessoryData data(UUID id) {
        return core.profiles().section(id, SECTION, AccessoryData.class, AccessoryData::new);
    }

    public int slots(UUID id) {
        return AccessoryMath.slots(settings.bagBaseSlots, data(id).upgrades, settings.bagSlotsPerUpgrade, settings.bagMaxSlots);
    }

    // ---------------------------------------------------------------- encoding

    /** Base64 of an item (keeps every component including our PDC), empty string for no item. */
    public static String encode(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    /** Inverse of {@link #encode}; null for an empty or unreadable entry. */
    public static @Nullable ItemStack decode(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(text));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The bag contents, one entry per slot (null = free). */
    public List<ItemStack> items(UUID id) {
        AccessoryData data = data(id);
        int size = slots(id);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(i < data.slots.size() ? decode(data.slots.get(i)) : null);
        }
        return out;
    }

    /** Writes the whole bag (called after every menu change so a crash loses nothing). */
    public void store(UUID id, List<ItemStack> stacks) {
        AccessoryData data = data(id);
        List<String> encoded = new ArrayList<>();
        for (ItemStack stack : stacks) {
            encoded.add(encode(stack));
        }
        data.slots = encoded;
        core.profiles().markDirty(id);
        versions.merge(id, 1, Integer::sum);
        engine.markDirty(id, false);
    }

    /** True when the stack may live in the bag. */
    public boolean accepts(@Nullable ItemStack stack) {
        return gear.read(stack).map(info -> info.kind() == ItemKind.ACCESSORY).orElse(false);
    }

    /** Buys the next slot upgrade bookkeeping (payment is the menu's job). */
    public boolean addUpgrade(UUID id) {
        AccessoryData data = data(id);
        if (slots(id) >= settings.bagMaxSlots) {
            return false;
        }
        data.upgrades++;
        core.profiles().markDirty(id);
        versions.merge(id, 1, Integer::sum);
        return true;
    }

    public double nextUpgradeCost(UUID id) {
        return AccessoryMath.upgradeCost(data(id).upgrades, settings.bagUpgradeCost, settings.bagUpgradeGrowth);
    }

    // ---------------------------------------------------------------- computation

    /** Counted accessories, Magical Power and the stat bonus (cached until the bag changes). */
    public AccessoryMath.Result result(UUID id) {
        int version = versions.getOrDefault(id, 0);
        AccessoryMath.Result cached = cache.get(id);
        if (cached != null && cacheVersion.getOrDefault(id, -1) == version) {
            return cached;
        }
        List<AccessoryMath.Entry> entries = new ArrayList<>();
        List<ItemStack> stacks = items(id);
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            Optional<GearInfo> info = gear.read(stack);
            if (info.isEmpty() || info.get().kind() != ItemKind.ACCESSORY) {
                continue;
            }
            String family = info.get().def().pdcStrings().getOrDefault(Keys.FAMILY, info.get().id());
            entries.add(new AccessoryMath.Entry(info.get().id(), family, info.get().rarity(),
                gear.items().statsOf(stack), slot));
        }
        AccessoryMath.Result result = AccessoryMath.compute(entries, settings.magicalPower, settings.perMagicalPower);
        cache.put(id, result);
        cacheVersion.put(id, version);
        return result;
    }

    public int magicalPower(UUID id) {
        return result(id).magicalPower();
    }

    public void forget(UUID id) {
        versions.remove(id);
        cache.remove(id);
        cacheVersion.remove(id);
    }

    @Override
    public String source() { return "accessory"; }

    @Override
    public Map<Stat, Double> stats(Player player) {
        EnumMap<Stat, Double> copy = new EnumMap<>(Stat.class);
        copy.putAll(result(player.getUniqueId()).stats());
        return copy;
    }

    @Override
    public int signature(Player player) {
        return versions.getOrDefault(player.getUniqueId(), 0) + 1;
    }
}
