package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;

/**
 * Registers the compacted resources of {@code enchanted_items} in {@code skills.yml} as custom items. An id the
 * items feature already registered is left alone (its definition wins); every other id is owned by the skills
 * feature (ids start with {@code enchanted_}) and is unregistered again on disable.
 */
final class EnchantedItems {
    private final Core core;
    private final List<String> owned = new ArrayList<>();

    EnchantedItems(Core core) {
        this.core = core;
    }

    /** Registers what is missing and returns the ids registered by this call. */
    List<String> registerAll(SkillsConfig config) {
        List<String> added = new ArrayList<>();
        for (SkillsConfig.EnchantedDef def : config.enchantedItems.values()) {
            if (core.items().def(def.id()).isPresent()) {
                continue;
            }
            String tierLine = def.tier() >= 2 ? "<gray>A heavily compacted resource." : "<gray>A concentrated resource.";
            List<String> lore = new ArrayList<>();
            CustomItemDef.Builder builder = CustomItemDef.builder(def.id(), def.name(), def.rarity(), def.material())
                .itemModel(def.model()).type("RESOURCE").glint(true);
            if (def.lore().isEmpty()) {
                lore.add(tierLine);
                lore.add("<dark_gray>A crafting material, not meant to be used as the plain item.");
            } else {
                lore.addAll(def.lore());
            }
            SkillsConfig.RecipeDef recipe = RecipeText.recipeFor(config, def.id());
            if (recipe != null) {
                // The vanilla recipe book only shows one item per slot: the real cost is written on the result.
                lore.add("");
                lore.add("<gray>Recipe (any arrangement):");
                RecipeText.lines(config, core, recipe).forEach(line -> lore.add("<dark_gray> " + line));
            }
            builder.lore(lore.toArray(new String[0]));
            core.items().register(builder.build());
            owned.add(def.id());
            added.add(def.id());
        }
        return added;
    }

    void unregisterAll() {
        owned.forEach(core.items()::unregister);
        owned.clear();
    }

    List<String> owned() {
        return List.copyOf(owned);
    }
}
