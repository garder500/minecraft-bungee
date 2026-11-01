package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.data.Db;

/**
 * Registers every custom item of the items feature. The definitions (weapons, tools, armor, accessories, consumables)
 * are rows of the {@code item_def} tables shipped in {@code db/seed/items.sql}; this class only loads and registers them.
 */
public final class ItemCatalog {
    /** Item model namespace folder, see {@code resource-pack/pack/assets/vampyuyu/items/items/}. */
    public static final String MODEL = "vampyuyu:items/";

    private final Items items;
    private final Db db;
    private final List<CustomItemDef> defs = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    public ItemCatalog(Items items, Db db) {
        this.items = items;
        this.db = db;
    }

    /** Definitions built (and registered) by {@link #registerAll}. */
    public List<CustomItemDef> defs() { return defs; }

    /** Rows that could not be turned into an item (after {@link #registerAll}). */
    public List<String> problems() { return problems; }

    /** Registers the whole catalog. */
    public void registerAll() {
        for (CustomItemDef def : ItemDefs.load(db, "items", problems)) {
            try {
                items.register(def);
                defs.add(def);
            } catch (IllegalStateException e) {
                problems.add(def.id() + ": " + e.getMessage());
            }
        }
    }
}
