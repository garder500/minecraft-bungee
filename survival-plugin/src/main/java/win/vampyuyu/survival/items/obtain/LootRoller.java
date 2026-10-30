package win.vampyuyu.survival.items.obtain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.catalog.ArmorSet;
import win.vampyuyu.survival.items.catalog.ArmorSets;
import win.vampyuyu.survival.items.enchant.Enchant;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.pet.PetService;

/** Rolls the loot tables ({@link LootEntry}) and builds the item stacks. The odds are pure and unit tested. */
public final class LootRoller {
    private final Gear gear;
    private final PetService pets;

    public LootRoller(Gear gear, PetService pets) {
        this.gear = gear;
        this.pets = pets;
    }

    /**
     * Pure: the percent chance of one roll: {@code base * (1 + luck/100) * multiplier}, never above {@code cap}.
     *
     * @param luck Magic Find (Pet Luck for pet rows), negative values count as 0
     */
    public static double chance(double base, double luck, double multiplier, double cap) {
        double value = base * (1 + Math.max(0, luck) / 100.0) * Math.max(0, multiplier);
        return Math.max(0, Math.min(cap, value));
    }

    /** Pure: the rows that hit for a source. {@code roll} returns numbers in [0, 1). */
    public static List<LootEntry> pick(List<LootEntry> entries, String source, String detail, double magicFind,
            double petLuck, double multiplier, double cap, DoubleSupplier roll) {
        List<LootEntry> hits = new ArrayList<>();
        for (LootEntry entry : entries) {
            if (!entry.matches(source, detail)) {
                continue;
            }
            double p = chance(entry.chance(), entry.petLuck() ? petLuck : magicFind, multiplier, cap);
            if (p > 0 && roll.getAsDouble() * 100.0 < p) {
                hits.add(entry);
            }
        }
        return hits;
    }

    /** Rolls a source for a player and builds the stacks. */
    public List<ItemStack> roll(ItemsSettings settings, String source, String detail, double magicFind, double petLuck,
            double multiplier) {
        if (!settings.lootEnabled) {
            return List.of();
        }
        DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();
        List<ItemStack> out = new ArrayList<>();
        for (LootEntry entry : pick(settings.lootEntries, source, detail, magicFind, petLuck, multiplier,
                settings.lootChanceCap, random)) {
            ItemStack stack = build(entry, random);
            if (stack != null) {
                out.add(stack);
            }
        }
        return out;
    }

    /** Builds the stack of a row, or null when the row points at something that does not exist. */
    public @Nullable ItemStack build(LootEntry entry, DoubleSupplier random) {
        try {
            return switch (entry.kind()) {
                case "item" -> item(entry, random);
                case "book" -> book(entry, random);
                case "pet" -> pet(entry);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private @Nullable ItemStack item(LootEntry entry, DoubleSupplier random) {
        String id = entry.id();
        if (id.startsWith("@armor:")) {
            int tier = Integer.parseInt(id.substring("@armor:".length()));
            List<ArmorSet> sets = ArmorSets.all();
            ArmorSet set = sets.get((int) (random.getAsDouble() * sets.size()) % sets.size());
            String slot = ArmorSets.SLOTS.get((int) (random.getAsDouble() * ArmorSets.SLOTS.size())
                % ArmorSets.SLOTS.size());
            id = set.id() + "_" + slot + "_t" + Math.max(1, Math.min(3, tier));
        }
        if (gear.items().def(id).isEmpty()) {
            return null;
        }
        ItemStack stack = gear.items().create(id);
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), entry.amount())));
        gear.rebuild(stack);
        return stack;
    }

    private @Nullable ItemStack book(LootEntry entry, DoubleSupplier random) {
        Optional<Enchant> enchant;
        if (entry.id().equals("@ultimate")) {
            List<Enchant> ultimates = gear.enchants().all().stream().filter(Enchant::ultimate).toList();
            enchant = ultimates.isEmpty() ? Optional.empty()
                : Optional.of(ultimates.get((int) (random.getAsDouble() * ultimates.size()) % ultimates.size()));
        } else {
            enchant = gear.enchants().get(entry.id());
        }
        if (enchant.isEmpty()) {
            return null;
        }
        int high = Math.max(entry.level(), entry.maxLevel());
        int level = entry.level() + (int) (random.getAsDouble() * (high - entry.level() + 1));
        level = Math.max(1, Math.min(enchant.get().maxLevel(), level));
        return gear.createBook(enchant.get().id(), level);
    }

    private @Nullable ItemStack pet(LootEntry entry) {
        Rarity rarity = Rarity.valueOf(entry.rarity().isEmpty() ? "COMMON" : entry.rarity().toUpperCase(java.util.Locale.ROOT));
        return pets.newEgg(entry.id(), rarity, 1);
    }

    /** Names of rows that point at nothing (unknown item, enchant or species), for the startup log and tests. */
    public List<String> problems(List<LootEntry> entries) {
        List<String> out = new ArrayList<>();
        DoubleSupplier first = () -> 0;
        for (LootEntry entry : entries) {
            if (build(entry, first) == null) {
                out.add(entry.kind() + ":" + entry.id());
            }
        }
        return out;
    }
}
