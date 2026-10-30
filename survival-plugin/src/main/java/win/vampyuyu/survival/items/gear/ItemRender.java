package win.vampyuyu.survival.items.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;

/**
 * Name and lore of every item the items feature manages. The core renderer cannot show enchants, reforges or a
 * recombobulated rarity, so this one writes the whole lore:
 * <pre>
 * Damage: +100 ✦                 weapon damage (red)
 * Strength: +50 ❁ (+5) (+2)      stat lines in Stat order; blue = reforge/enchant, yellow = hot potato books
 *
 * Sharpness VII, Critical V       enchants (blue, ultimate in bold light purple)
 *
 * Ability: Name  RIGHT CLICK      extra lines from {@link ExtraLore} (abilities, set bonus, pet data...)
 *
 * LEGENDARY SWORD                 bold, effective rarity colour
 * </pre>
 */
public final class ItemRender {
    /** Supplies the ability / set / pet lines of an item, between the enchants and the rarity line. */
    @FunctionalInterface
    public interface ExtraLore {
        List<Component> lines(GearInfo info, ItemStack stack);
    }

    /** Bump to redraw every item the next time a player holds it. */
    public static final long RENDER_VERSION = 1L;

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final TextColor DAMAGE_COLOR = NamedTextColor.RED;

    /** Overrides the display name of special stacks (pet eggs, enchanted books); null keeps the default. */
    @FunctionalInterface
    public interface NameHook {
        String name(GearInfo info, ItemStack stack);
    }

    private volatile ExtraLore extra = (info, stack) -> List.of();
    private volatile NameHook nameHook = (info, stack) -> null;

    public void extra(ExtraLore extra) { this.extra = extra; }

    public void nameHook(NameHook hook) { this.nameHook = hook; }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** "+50" / "+2.5" / "-3", with "%" for percent stats. */
    public static String signed(double value, Stat stat) {
        String number = value == Math.rint(value) ? Long.toString((long) value)
            : Double.toString(Math.round(value * 10) / 10.0);
        return (value >= 0 ? "+" : "") + number + (stat != null && stat.percent() ? "%" : "");
    }

    /** One stat line, e.g. {@code Strength: +50 ❁ (+5) (+2)}. */
    public static Component statLine(Stat stat, double base, double blue, double yellow) {
        Component text = Component.text(stat.displayName() + ": ", NamedTextColor.GRAY)
            .append(Component.text(signed(base + 0.0, stat) + " " + stat.symbol(), stat.color()));
        if (blue != 0) {
            text = text.append(Component.text(" (" + signed(blue, stat) + ")", NamedTextColor.BLUE));
        }
        if (yellow != 0) {
            text = text.append(Component.text(" (" + signed(yellow, stat) + ")", NamedTextColor.YELLOW));
        }
        return line(text);
    }

    /** Builds the lore of a stack (pure apart from reading registries). */
    public List<Component> lore(GearInfo info, ItemStack stack, Gear gear) {
        List<Component> out = new ArrayList<>();
        BonusCalc.Breakdown breakdown = gear.breakdown(info);
        Map<Stat, Double> blue = breakdown.blue();
        Map<Stat, Double> yellow = breakdown.potato();
        if (info.damage() > 0) {
            out.add(line(Component.text("Damage: ", NamedTextColor.GRAY)
                .append(Component.text("+" + trim(info.damage()) + " ✦", DAMAGE_COLOR))));
        }
        for (Stat stat : Stat.values()) {
            double base = info.def().stats().getOrDefault(stat, 0.0);
            double b = blue.getOrDefault(stat, 0.0);
            double y = yellow.getOrDefault(stat, 0.0);
            if (base == 0 && b == 0 && y == 0) {
                continue;
            }
            out.add(statLine(stat, base, b, y));
        }
        if (!info.enchants().isEmpty()) {
            if (!out.isEmpty()) {
                out.add(Component.empty());
            }
            out.addAll(enchantLines(info.enchants(), gear.enchants()));
        }
        List<Component> more = new ArrayList<>(extra.lines(info, stack));
        if (!info.def().lore().isEmpty() || !more.isEmpty()) {
            if (!out.isEmpty()) {
                out.add(Component.empty());
            }
            for (String text : info.def().lore()) {
                out.add(line(MINI.deserialize(text)));
            }
            if (!info.def().lore().isEmpty() && !more.isEmpty()) {
                out.add(Component.empty());
            }
            out.addAll(more);
        }
        if (!out.isEmpty()) {
            out.add(Component.empty());
        }
        String label = info.rarity().label() + (info.def().typeLabel() == null ? "" : " " + info.def().typeLabel());
        out.add(line(Component.text((info.recombobulated() ? "a " : "") + label, info.rarity().color(),
            TextDecoration.BOLD)));
        return out;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 10) / 10.0);
    }

    /** Enchant lines: ultimate first (bold), then the others, a few per line like the original game. */
    public static List<Component> enchantLines(Map<String, Integer> enchants, EnchantRegistry registry) {
        List<Component> out = new ArrayList<>();
        List<Component> names = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            Optional<Enchant> enchant = registry.get(entry.getKey());
            if (enchant.isEmpty()) {
                continue;
            }
            Component name = Component.text(enchant.get().label(entry.getValue()),
                enchant.get().ultimate() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.BLUE);
            if (enchant.get().ultimate()) {
                out.add(line(name.decoration(TextDecoration.BOLD, true)));
            } else {
                names.add(name);
            }
        }
        for (int i = 0; i < names.size(); i += 3) {
            Component row = Component.empty();
            for (int j = i; j < Math.min(i + 3, names.size()); j++) {
                row = row.append(names.get(j));
                if (j < Math.min(i + 3, names.size()) - 1) {
                    row = row.append(Component.text(", ", NamedTextColor.BLUE));
                }
            }
            out.add(line(row));
        }
        return out;
    }

    /** Applies name and lore to the stack in place. */
    public void apply(ItemStack stack, Gear gear) {
        Optional<GearInfo> read = gear.read(stack);
        if (read.isEmpty()) {
            return;
        }
        GearInfo info = read.get();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE,
            Keys.RENDERED), org.bukkit.persistence.PersistentDataType.LONG, RENDER_VERSION);
        meta.lore(lore(info, stack, gear));
        String override = nameHook.name(info, stack);
        Component name = Component.text(override != null ? override : displayName(info, gear.reforges()),
            info.rarity().color());
        if (info.reforge() != null) {
            meta.customName(line(name));
        } else {
            meta.customName(null);
            meta.itemName(line(name));
        }
        stack.setItemMeta(meta);
    }

    /** "Sharp Void Blade" or "Void Blade". */
    public static String displayName(GearInfo info, ReforgeRegistry reforges) {
        return reforges.get(info.reforge()).map(r -> r.name() + " " + info.def().name()).orElse(info.def().name());
    }
}
