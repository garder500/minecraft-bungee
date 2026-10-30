package win.vampyuyu.survival.items.gear;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;

/** Reads and writes the items feature's data on stacks (enchants, reforge, upgrades) and keeps bonuses and lore in step. */
public final class Gear {
    private final Items items;
    private final EnchantRegistry enchantRegistry;
    private final ReforgeRegistry reforges;
    private volatile ItemsSettings settings;
    private final ItemRender render;

    public Gear(Items items, EnchantRegistry enchantRegistry, ReforgeRegistry reforges, ItemsSettings settings,
            ItemRender render) {
        this.items = items;
        this.enchantRegistry = enchantRegistry;
        this.reforges = reforges;
        this.settings = settings;
        this.render = render;
    }

    public Items items() { return items; }

    public EnchantRegistry enchants() { return enchantRegistry; }

    public ReforgeRegistry reforges() { return reforges; }

    public ItemRender renderer() { return render; }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(Items.NAMESPACE, name);
    }

    /** Effective rarity of a definition, with the recombobulator applied. */
    public static Rarity effective(Rarity base, boolean recombobulated) {
        if (!recombobulated) {
            return base;
        }
        Rarity[] all = Rarity.values();
        return base.ordinal() < Rarity.MYTHIC.ordinal() ? all[base.ordinal() + 1] : base;
    }

    /** True when the recombobulator may still be used on an item of this rarity. */
    public static boolean canRecombobulate(Rarity base) {
        return base.ordinal() < Rarity.MYTHIC.ordinal();
    }

    /** Reads a stack, empty for non custom stacks and unregistered ids. */
    public Optional<GearInfo> read(@Nullable ItemStack stack) {
        Optional<String> id = items.idOf(stack);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Optional<CustomItemDef> def = items.def(id.get());
        if (def.isEmpty()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        CustomItemDef d = def.get();
        Long recomb = pdc.get(key(Keys.RECOMB), PersistentDataType.LONG);
        Double damage = pdc.get(key(Keys.DAMAGE), PersistentDataType.DOUBLE);
        Long hpb = pdc.get(key(Keys.HOT_POTATO), PersistentDataType.LONG);
        Long sing = pdc.get(key(Keys.SINGULARITY), PersistentDataType.LONG);
        boolean recombobulated = recomb != null && recomb > 0;
        Rarity rarity = effective(d.rarity(), recombobulated);
        String petRarity = pdc.get(key(Keys.PET_RARITY), PersistentDataType.STRING);
        if (petRarity != null) {
            try {
                rarity = Rarity.valueOf(petRarity);
            } catch (IllegalArgumentException ignored) {
                // keep the definition rarity
            }
        }
        return Optional.of(new GearInfo(d, ItemKind.of(d.typeLabel()), rarity,
            damage == null ? 0 : damage,
            EnchantRules.parse(pdc.get(key(Keys.ENCHANTS), PersistentDataType.STRING)),
            pdc.get(key(Keys.REFORGE), PersistentDataType.STRING), hpb == null ? 0 : hpb.intValue(),
            sing == null ? 0 : sing.intValue(), recombobulated));
    }

    /** Rewrites the item's PDC in one meta round trip. */
    public void edit(ItemStack stack, Consumer<PersistentDataContainer> change) {
        ItemMeta meta = stack.getItemMeta();
        change.accept(meta.getPersistentDataContainer());
        stack.setItemMeta(meta);
    }

    public void setEnchants(ItemStack stack, Map<String, Integer> enchants) {
        String text = EnchantRules.format(enchants);
        edit(stack, pdc -> {
            if (text.isEmpty()) {
                pdc.remove(key(Keys.ENCHANTS));
            } else {
                pdc.set(key(Keys.ENCHANTS), PersistentDataType.STRING, text);
            }
        });
    }

    public void setReforge(ItemStack stack, @Nullable String reforgeId) {
        edit(stack, pdc -> {
            if (reforgeId == null) {
                pdc.remove(key(Keys.REFORGE));
            } else {
                pdc.set(key(Keys.REFORGE), PersistentDataType.STRING, reforgeId);
            }
        });
    }

    public void setLong(ItemStack stack, String name, long value) {
        edit(stack, pdc -> pdc.set(key(name), PersistentDataType.LONG, value));
    }

    /** Pure breakdown of the item's bonuses from its current upgrades. */
    public BonusCalc.Breakdown breakdown(GearInfo info) {
        return BonusCalc.compute(info.kind(), info.rarity(), info.reforge(), info.potatoBooks(), info.singularity(),
            info.enchants(), enchantRegistry, reforges, settings);
    }

    /**
     * Brings an item up to date after any change: recomputes the {@code bonus_<stat>} values from the reforge, hot
     * potato books, enchants and singularity, then redraws name and lore. The one way every code path should finish.
     */
    public void rebuild(ItemStack stack) {
        Optional<GearInfo> read = read(stack);
        if (read.isEmpty()) {
            return;
        }
        GearInfo info = read.get();
        Map<Stat, Double> wanted = breakdown(info).total();
        Map<Stat, Double> current = items.bonuses(stack);
        for (Stat stat : Stat.values()) {
            double want = wanted.getOrDefault(stat, 0.0);
            double have = current.getOrDefault(stat, 0.0);
            if (Math.abs(want - have) > 1e-9) {
                items.setBonus(stack, stat, want);
            }
        }
        render.apply(stack, this);
    }

    /** An enchanted book holding {@code enchantId} at {@code level} (the one way books are made: command, loot, shops). */
    public ItemStack createBook(String enchantId, int level) {
        ItemStack book = items.create("enchanted_book");
        items.set(book, Keys.BOOK, enchantId + ":" + level);
        rebuild(book);
        return book;
    }
}
