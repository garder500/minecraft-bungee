package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;
import win.vampyuyu.survival.core.Msg;

/** The Forge menu: recipes on the first row, the player's jobs on the last one. */
final class ForgeGui {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final Core core;
    private final QuarryService service;

    ForgeGui(Core core, QuarryService service) {
        this.core = core;
        this.service = service;
    }

    void open(Player player) {
        Gui gui = core.guis().create(6, "<dark_gray>Forge");
        render(gui, player);
        gui.open(player);
    }

    private void render(Gui gui, Player player) {
        long now = System.currentTimeMillis();
        QuarryConfig cfg = service.config();
        gui.fill(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        int slot = 9;
        for (ForgeRecipe recipe : cfg.recipes.values()) {
            if (slot > 35) {
                break;
            }
            var def = core.items().def(recipe.result());
            if (def.isEmpty()) {
                continue;
            }
            boolean ok = service.canAfford(player, recipe);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Takes " + Msg.duration(recipe.seconds() * 1000L) + (recipe.amount() > 1 ? " (makes " + recipe.amount() + ")" : ""));
            lore.add("");
            for (Map.Entry<String, Integer> need : recipe.cost().entrySet()) {
                lore.add("<gray>- " + need.getValue() + " x " + label(need.getKey()));
            }
            lore.add("");
            lore.add(ok ? "<green>Click to start" : "<red>Missing materials");
            ItemStack icon = icon(def.get().id(), lore);
            gui.set(slot++, icon, click -> {
                String problem = service.start(player, recipe.id(), System.currentTimeMillis());
                if (problem != null) {
                    core.msg().warn(player, problem);
                } else {
                    core.msg().ok(player, "Forging " + def.get().name() + ".");
                }
                render(gui, player);
            });
        }
        var jobs = service.data(player.getUniqueId()).jobs;
        for (int i = 0; i < cfg.forgeSlots && i < 7; i++) {
            int at = 46 + i;
            if (i >= jobs.size()) {
                gui.set(at, Guis.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Free slot"));
                continue;
            }
            QuarryData.Job job = jobs.get(i);
            ForgeRecipe recipe = cfg.recipes.get(job.recipe);
            String name = recipe == null ? job.recipe : core.items().def(recipe.result()).map(d -> d.name()).orElse(job.recipe);
            boolean done = job.finishAt <= now;
            gui.set(at, Guis.icon(done ? Material.LIME_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE,
                    "<white>" + name,
                    done ? "<green>Ready: click to collect" : "<gray>Ready in " + Msg.duration(job.finishAt - now)),
                click -> {
                    for (String got : service.collect(player, System.currentTimeMillis(), id -> org.bukkit.Bukkit.getPluginManager()
                            .callEvent(new win.vampyuyu.survival.api.event.QuarryProgressEvent(player, "FORGE_ITEM", id, 1)))) {
                        core.msg().ok(player, "You collected " + got + ".");
                    }
                    render(gui, player);
                });
        }
    }

    /** The real custom item (so the menu shows its pack texture), with the recipe lines under its own description. */
    private ItemStack icon(String itemId, List<String> lines) {
        ItemStack stack = core.items().create(itemId);
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        for (String line : lines) {
            lore.add(MINI.deserialize(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String label(String key) {
        if (key.startsWith("custom:")) {
            return core.items().def(key.substring(7)).map(d -> d.name()).orElse(key.substring(7));
        }
        String name = key.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
