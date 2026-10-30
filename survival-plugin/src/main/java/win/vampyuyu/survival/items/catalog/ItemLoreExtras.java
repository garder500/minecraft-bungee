package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.ability.Ability;
import win.vampyuyu.survival.items.ability.AbilityEnv;
import win.vampyuyu.survival.items.ability.AbilityRegistry;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.enchant.EnchantRules;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.gear.ItemRender;
import win.vampyuyu.survival.items.gear.Keys;

/** Lore between the enchants and the rarity line: abilities, set bonus, fuel, accessory power, stored book enchant. */
public final class ItemLoreExtras implements ItemRender.ExtraLore {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final AbilityRegistry abilities;
    private final SetBonuses sets;
    private final Gear gear;
    private final EnchantRegistry enchants;
    private volatile ItemsSettings settings;
    private volatile AbilityEnv env;
    private volatile BiFunction<GearInfo, ItemStack, List<Component>> pets = (info, stack) -> List.of();

    public ItemLoreExtras(AbilityRegistry abilities, SetBonuses sets, Gear gear, EnchantRegistry enchants,
            ItemsSettings settings) {
        this.abilities = abilities;
        this.sets = sets;
        this.gear = gear;
        this.enchants = enchants;
        this.settings = settings;
    }

    public void env(AbilityEnv env) { this.env = env; }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    /** Lets the pet feature add the lines of pet eggs. */
    public void petLore(BiFunction<GearInfo, ItemStack, List<Component>> provider) { this.pets = provider; }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 10) / 10.0);
    }

    @Override
    public List<Component> lines(GearInfo info, ItemStack stack) {
        List<Component> out = new ArrayList<>();
        // stored enchant of a book
        Optional<String> stored = gear.items().getString(stack, Keys.BOOK);
        if (stored.isPresent()) {
            EnchantRules.parseBook(stored.get()).ifPresent(entry -> enchants.get(entry.getKey()).ifPresent(enchant -> {
                out.add(line(Component.text(enchant.label(entry.getValue()),
                    enchant.ultimate() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.BLUE)
                    .decoration(TextDecoration.BOLD, enchant.ultimate())));
                for (String text : describe(enchant, entry.getValue())) {
                    out.add(line(MINI.deserialize("<gray>" + text)));
                }
                out.add(Component.empty());
                out.add(line(Component.text("Applies to: " + appliesTo(enchant), NamedTextColor.DARK_GRAY)));
            }));
        }
        // accessory power
        if (info.kind() == ItemKind.ACCESSORY) {
            int mp = settings.magicalPower.getOrDefault(info.rarity().name(), 0);
            out.add(line(Component.text("Magical Power: ", NamedTextColor.GRAY)
                .append(Component.text("+" + mp + " ★", NamedTextColor.GOLD))));
            out.add(line(Component.text("Only the best accessory of a family counts.", NamedTextColor.DARK_GRAY)));
        }
        // drill fuel
        long fuelMax = info.def().pdcLongs().getOrDefault(Keys.FUEL_MAX, 0L);
        if (fuelMax > 0) {
            long fuel = gear.items().getLong(stack, Keys.FUEL).orElse(fuelMax);
            out.add(line(Component.text("Fuel: ", NamedTextColor.GRAY).append(Component.text(
                Msg.grouped(fuel) + "/" + Msg.grouped(fuelMax), fuel > 0 ? NamedTextColor.GREEN : NamedTextColor.RED))));
        }
        // upgrades
        if (info.potatoBooks() > 0) {
            out.add(line(Component.text(info.potatoBooks() + "/" + settings.i("hot-potato.max", 10)
                + " Hot Potato Books", NamedTextColor.YELLOW)));
        }
        if (info.singularity() > 0) {
            out.add(line(Component.text("Singularity applied", NamedTextColor.GOLD)));
        }
        // abilities
        for (Ability ability : abilities.forItem(info)) {
            if (!out.isEmpty()) {
                out.add(Component.empty());
            }
            out.add(line(Component.text("Ability: " + ability.name() + "  ", NamedTextColor.GOLD)
                .append(Component.text(ability.trigger().label(), NamedTextColor.YELLOW, TextDecoration.BOLD))));
            for (String text : ability.description()) {
                out.add(line(MINI.deserialize(resolve(ability, text))));
            }
            double mana = env == null ? ability.mana() : env.mana(ability);
            double cooldown = env == null ? ability.cooldownSeconds() : env.cooldown(ability);
            if (mana > 0) {
                out.add(line(Component.text("Mana Cost: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(number(mana), NamedTextColor.DARK_AQUA))));
            }
            if (cooldown > 0) {
                out.add(line(Component.text("Cooldown: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(number(cooldown) + "s", NamedTextColor.DARK_GREEN))));
            }
        }
        // set
        String set = info.def().pdcStrings().get(Keys.SET);
        if (set != null) {
            if (!out.isEmpty()) {
                out.add(Component.empty());
            }
            out.addAll(sets.loreFor(set));
        }
        // consumable action
        String apply = info.def().pdcStrings().get(Keys.APPLY);
        if (apply != null && apply.equals("reforge")) {
            // text is part of the definition lore
        }
        out.addAll(pets.apply(info, stack));
        return out;
    }

    private String resolve(Ability ability, String text) {
        String out = text;
        for (Map.Entry<String, Double> param : ability.params().entrySet()) {
            double value = env == null ? param.getValue() : env.param(ability, param.getKey());
            out = out.replace("{" + param.getKey() + "}", number(value));
        }
        return out;
    }

    /** The enchant's description for one level, MiniMessage. */
    public List<String> describe(Enchant enchant, int level) {
        double value = enchant.perLevel() * level;
        String text = enchant.description().replace("{v}", number(value));
        return List.of(text.split("\n"));
    }

    private static String appliesTo(Enchant enchant) {
        List<String> names = new ArrayList<>();
        for (ItemKind kind : ItemKind.values()) {
            if (enchant.appliesTo(kind)) {
                String name = kind.name().charAt(0) + kind.name().substring(1).toLowerCase(java.util.Locale.ROOT);
                names.add(name);
            }
        }
        return String.join(", ", names);
    }
}
