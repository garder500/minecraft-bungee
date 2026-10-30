package win.vampyuyu.survival.items.gear;

import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;

/** Pure computation of the per-item stat bonuses (written to {@code bonus_<stat>}) from an item's upgrades. */
public final class BonusCalc {
    private BonusCalc() {}

    /** Bonuses split by origin so the lore can colour them. */
    public record Breakdown(Map<Stat, Double> reforge, Map<Stat, Double> potato, Map<Stat, Double> enchants,
            Map<Stat, Double> singularity) {
        /** Everything summed per stat. */
        public Map<Stat, Double> total() {
            Map<Stat, Double> out = new EnumMap<>(Stat.class);
            for (Map<Stat, Double> part : java.util.List.of(reforge, potato, enchants, singularity)) {
                part.forEach((stat, value) -> out.merge(stat, value, Double::sum));
            }
            out.values().removeIf(v -> Math.abs(v) < 1e-9);
            return out;
        }

        /** Reforge + enchants + singularity (blue in the lore). */
        public Map<Stat, Double> blue() {
            Map<Stat, Double> out = new EnumMap<>(Stat.class);
            for (Map<Stat, Double> part : java.util.List.of(reforge, enchants, singularity)) {
                part.forEach((stat, value) -> out.merge(stat, value, Double::sum));
            }
            out.values().removeIf(v -> Math.abs(v) < 1e-9);
            return out;
        }
    }

    /** Stat granted by one level of {@code enchant} on an item of {@code kind} (null when it grants none). */
    public static @Nullable Stat statOf(Enchant enchant, ItemKind kind) {
        if (enchant.id().equals("fortune")) {
            return switch (kind) {
                case PICKAXE, DRILL, SHOVEL -> Stat.MINING_FORTUNE;
                case AXE -> Stat.FORAGING_FORTUNE;
                case HOE -> Stat.FARMING_FORTUNE;
                default -> null;
            };
        }
        return enchant.stat();
    }

    public static Breakdown compute(ItemKind kind, Rarity rarity, @Nullable String reforgeId, int potatoBooks,
            int singularity, Map<String, Integer> enchants, EnchantRegistry enchantRegistry,
            ReforgeRegistry reforges, ItemsSettings settings) {
        Map<Stat, Double> reforge = new EnumMap<>(Stat.class);
        reforge.putAll(reforges.bonuses(reforgeId, rarity));

        Map<Stat, Double> potato = new EnumMap<>(Stat.class);
        if (potatoBooks > 0) {
            if (kind.weapon()) {
                potato.put(Stat.STRENGTH, potatoBooks * settings.d("hot-potato.weapon.strength", 2));
            } else if (kind.armor()) {
                potato.put(Stat.HEALTH, potatoBooks * settings.d("hot-potato.armor.health", 4));
                potato.put(Stat.DEFENSE, potatoBooks * settings.d("hot-potato.armor.defense", 2));
            }
        }

        Map<Stat, Double> ench = new EnumMap<>(Stat.class);
        boolean oneForAll = enchants.containsKey("one_for_all");
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            if (oneForAll && !entry.getKey().equals("one_for_all")) {
                continue;
            }
            enchantRegistry.get(entry.getKey()).ifPresent(enchant -> {
                Stat stat = statOf(enchant, kind);
                if (stat != null) {
                    ench.merge(stat, enchant.perLevel() * entry.getValue(), Double::sum);
                }
            });
        }

        Map<Stat, Double> sing = new EnumMap<>(Stat.class);
        if (singularity > 0) {
            double amount = settings.d("singularity.fortune", 25);
            switch (kind) {
                case PICKAXE, DRILL, SHOVEL -> sing.put(Stat.MINING_FORTUNE, amount);
                case AXE -> sing.put(Stat.FORAGING_FORTUNE, amount);
                case HOE -> sing.put(Stat.FARMING_FORTUNE, amount);
                default -> { }
            }
        }
        return new Breakdown(reforge, potato, ench, sing);
    }
}
