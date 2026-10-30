package win.vampyuyu.survival.items.gear;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/**
 * Everything the items feature knows about one stack, read in one pass.
 *
 * @param rarity effective rarity (definition rarity plus one tier when recombobulated)
 */
public record GearInfo(CustomItemDef def, ItemKind kind, Rarity rarity, double damage, Map<String, Integer> enchants,
        @Nullable String reforge, int potatoBooks, int singularity, boolean recombobulated) {

    public String id() { return def.id(); }
}
