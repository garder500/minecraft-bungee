package win.vampyuyu.survival.minions;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Compactor and Super Compactor on a {@link Storage}: {@code ratio} items of a resource become one enchanted item,
 * {@code superRatio} enchanted items become one super enchanted item. A conversion that would not fit (the new
 * item needs a slot of its own) is simply skipped, nothing is ever lost.
 */
public final class Compaction {
    private final MinionConfig config;
    private final Set<Material> eligible = EnumSet.noneOf(Material.class);

    public Compaction(MinionConfig config) {
        this.config = config;
        eligible.addAll(config.producible());
    }

    /** Key of the enchanted form of a plain {@code m:} key, null when it has none. */
    public String compactedKey(String key) {
        Material material = ItemKey.material(key);
        return material != null && eligible.contains(material) ? ItemKey.custom(MinionItems.enchantedId(material)) : null;
    }

    /** Key of the super enchanted form of an enchanted {@code c:} key, null when it has none. */
    public String superKey(String key) {
        String prefix = ItemKey.isCustom(key) ? ItemKey.id(key) : "";
        if (!prefix.startsWith(MinionItems.ENCHANTED_PREFIX)) {
            return null;
        }
        return ItemKey.custom(MinionItems.SUPER_PREFIX + prefix.substring(MinionItems.ENCHANTED_PREFIX.length()));
    }

    /**
     * Compacts the touched keys.
     *
     * @param mode COMPACT, SUPER_COMPACT or null for none
     */
    public void apply(Storage storage, Map<String, Long> counts, MinionConfig.Effect mode, Collection<String> touched) {
        if (mode != MinionConfig.Effect.COMPACT && mode != MinionConfig.Effect.SUPER_COMPACT) {
            return;
        }
        for (String key : touched) {
            String compact = compactedKey(key);
            if (compact != null) {
                convert(storage, counts, key, compact, config.compactRatio);
                if (mode == MinionConfig.Effect.SUPER_COMPACT) {
                    String sup = superKey(compact);
                    if (sup != null) {
                        convert(storage, counts, compact, sup, config.superRatio);
                    }
                }
            }
        }
    }

    private static void convert(Storage storage, Map<String, Long> counts, String from, String to, int ratio) {
        long have = storage.count(from);
        long batches = have / ratio;
        while (batches > 0) {
            Map<String, Long> trial = new LinkedHashMap<>(counts);
            Storage probe = storage.over(trial);
            probe.remove(from, batches * ratio);
            if (probe.add(to, batches) == batches) {
                counts.clear();
                counts.putAll(trial);
                return;
            }
            batches = batches > 1 ? 1 : 0;
        }
    }
}
