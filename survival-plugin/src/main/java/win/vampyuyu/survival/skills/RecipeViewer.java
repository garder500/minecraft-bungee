package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/**
 * The recipe book of the skills feature ({@code /recipes}): every collection-gated recipe with its lock state, a
 * crafting-grid preview with the real per-slot amounts, and a quick-craft button that takes the ingredients straight
 * from the inventory (no 3x3 grid to hand-fill). Text lives in item names and tooltips only.
 */
final class RecipeViewer {
    private static final int[] GRID = {11, 12, 13, 20, 21, 22, 29, 30, 31};

    private final Core core;
    private final SkillsService service;
    private final SkillRecipes recipes;

    RecipeViewer(Core core, SkillsService service, SkillRecipes recipes) {
        this.core = core;
        this.service = service;
        this.recipes = recipes;
    }

    private static ItemStack pane() {
        return Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    // ---------------------------------------------------------------- list

    void openList(Player player) {
        List<SkillsConfig.RecipeDef> all = new ArrayList<>(service.config().recipes.values());
        UUID id = player.getUniqueId();
        Gui gui = core.guis().create(6, "<dark_aqua>Enchanted Recipes");
        gui.border(pane());
        gui.paginate(all, gui.innerSlots(), def -> listIcon(id, def), (click, def) -> open(click.player(), def), 48, 50);
        gui.set(49, Guis.icon(Material.BARRIER, "<red>Close"), Gui.Click::close);
        gui.open(player);
    }

    private ItemStack listIcon(UUID id, SkillsConfig.RecipeDef def) {
        boolean unlocked = service.meetsRequirements(id, def);
        ItemStack icon = recipes.resultOf(def);
        List<String> lore = new ArrayList<>();
        lore.add(unlocked ? "<green>Unlocked" : "<red>Locked");
        for (String line : RecipeText.lines(service.config(), core, def)) {
            lore.add("<gray>" + line);
        }
        lore.add("");
        lore.add("<yellow>Click to open");
        return appendLore(icon, lore);
    }

    /** Adds MiniMessage lines under the lore the item already has (a custom item keeps its own description). */
    private static ItemStack appendLore(ItemStack stack, List<String> lines) {
        var meta = stack.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.empty());
        for (String line : lines) {
            lore.add(Msg.component(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------------------------------------------------------------- detail

    void open(Player player, SkillsConfig.RecipeDef def) {
        SkillsConfig cfg = service.config();
        UUID id = player.getUniqueId();
        boolean unlocked = service.meetsRequirements(id, def);
        Gui gui = core.guis().create(5, "<dark_aqua>" + service.resultName(def));
        gui.border(pane());
        int slot = 0;
        for (SkillsConfig.Ingredient ingredient : def.ingredients()) {
            for (int i = 0; i < ingredient.slots() && slot < GRID.length; i++) {
                ItemStack shown = ingredient.custom() ? core.items().create(ingredient.customId())
                    : new ItemStack(ingredient.material());
                shown.setAmount(Math.min(shown.getMaxStackSize(), ingredient.perSlot()));
                gui.set(GRID[slot++], shown, null);
            }
        }
        List<String> need = new ArrayList<>();
        need.add("<gray>Put the ingredients in the crafting table,");
        need.add("<gray>in any arrangement:");
        RecipeText.lines(cfg, core, def).forEach(line -> need.add("<white> " + line));
        need.add("");
        need.add("<gray>Total: <white>" + String.join("<gray>, <white>", RecipeText.totals(cfg, core, def)));
        gui.set(23, Guis.icon(Material.CRAFTING_TABLE, "<yellow>Crafting grid", need.toArray(new String[0])), null);
        gui.set(24, recipes.resultOf(def), null);

        List<String> status = new ArrayList<>();
        if (unlocked) {
            status.add("<green>Unlocked");
        } else {
            status.add("<red>Locked, requires:");
            recipes.missingRequirements(id, def).forEach(line -> status.add("<yellow> " + line));
        }
        if (def.xpSkill() != null && def.xpAmount() > 0) {
            status.add("");
            status.add("<gray>Each craft gives <aqua>" + Msg.number(def.xpAmount()) + " " + def.xpSkill().displayName() + " XP");
        }
        gui.set(15, Guis.icon(unlocked ? Material.LIME_DYE : Material.RED_DYE, unlocked ? "<green>Unlocked" : "<red>Locked",
            status.toArray(new String[0])), null);

        List<String> quick = new ArrayList<>();
        quick.add("<gray>Takes <white>" + String.join("<gray>, <white>", RecipeText.totals(cfg, core, def))
            + "<gray> from your inventory");
        quick.add("<gray>and gives <white>" + def.amount() + " " + service.resultName(def) + "<gray>.");
        quick.add("");
        quick.add(unlocked ? "<yellow>Click to craft" : "<red>Locked");
        gui.set(33, Guis.icon(unlocked ? Material.EMERALD_BLOCK : Material.BARRIER, "<green>Quick craft",
            quick.toArray(new String[0])), click -> {
                Player who = click.player();
                SkillRecipes.QuickResult result = recipes.quickCraft(who, def);
                switch (result) {
                    case OK -> core.msg().ok(who, "Crafted " + def.amount() + " " + service.resultName(def) + ".");
                    case LOCKED -> core.msg().warn(who, "This recipe is locked. Requires "
                        + String.join(", ", recipes.missingRequirements(who.getUniqueId(), def)) + ".");
                    case MISSING -> core.msg().warn(who, "You do not have enough ingredients: "
                        + String.join(", ", RecipeText.totals(service.config(), core, def)) + ".");
                    case NO_ROOM -> core.msg().warn(who, "Your inventory has no room for the result.");
                    case UNAVAILABLE -> core.msg().warn(who, "You cannot craft right now.");
                }
                open(who, def);
            });
        gui.set(40, Guis.icon(Material.ARROW, "<gray>Back to recipes"), click -> openList(click.player()));
        gui.open(player);
    }
}
