package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.core.Core;

/** Plain descriptions of recipes ("5 x 32 Diamond"), shared by item lore, menus and chat. */
final class RecipeText {
    private RecipeText() {}

    /** Name of an ingredient: the enchanted item's display name, a custom item's name, or the prettified material. */
    static String name(SkillsConfig cfg, Core core, SkillsConfig.Ingredient ingredient) {
        if (ingredient.custom()) {
            SkillsConfig.EnchantedDef ench = cfg.enchantedItems.get(ingredient.customId());
            if (ench != null) {
                return ench.name();
            }
            return core == null ? ingredient.customId()
                : core.items().def(ingredient.customId()).map(d -> d.name()).orElse(ingredient.customId());
        }
        return SkillsConfig.prettify(ingredient.material().name());
    }

    /** One line per ingredient: "5 slots x 32 Diamond". */
    static List<String> lines(SkillsConfig cfg, Core core, SkillsConfig.RecipeDef recipe) {
        List<String> out = new ArrayList<>();
        for (SkillsConfig.Ingredient ingredient : recipe.ingredients()) {
            out.add(ingredient.slots() + (ingredient.slots() == 1 ? " slot" : " slots") + " x " + ingredient.perSlot() + " "
                + name(cfg, core, ingredient));
        }
        return out;
    }

    /** Total cost per ingredient: "160 Diamond". */
    static List<String> totals(SkillsConfig cfg, Core core, SkillsConfig.RecipeDef recipe) {
        List<String> out = new ArrayList<>();
        for (SkillsConfig.Ingredient ingredient : recipe.ingredients()) {
            out.add((ingredient.slots() * ingredient.perSlot()) + " " + name(cfg, core, ingredient));
        }
        return out;
    }

    /** The recipe that makes this custom item, or null. */
    static SkillsConfig.RecipeDef recipeFor(SkillsConfig cfg, String customId) {
        for (SkillsConfig.RecipeDef recipe : cfg.recipes.values()) {
            if (customId.equals(recipe.resultCustom())) {
                return recipe;
            }
        }
        return null;
    }
}
