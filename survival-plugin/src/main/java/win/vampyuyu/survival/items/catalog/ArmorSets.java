package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import win.vampyuyu.survival.api.Stat;

/** The six armor sets. */
public final class ArmorSets {
    private ArmorSets() {}

    public static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

    private static Map<Stat, Double> stats(Object... pairs) {
        Map<Stat, Double> map = new EnumMap<>(Stat.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Stat) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    private static final List<ArmorSet> ALL = build();

    private static List<ArmorSet> build() {
        List<ArmorSet> sets = new ArrayList<>();
        sets.add(new ArmorSet("miner", "Miner", "IRON",
            stats(Stat.HEALTH, 40, Stat.DEFENSE, 30, Stat.MINING_SPEED, 25, Stat.MINING_FORTUNE, 12),
            "Deep Delver", List.of("<gray>Gain <aqua>Night Vision</aqua> while you are",
                "<gray>below <green>Y=40</green>."),
            stats(Stat.MINING_FORTUNE, 25, Stat.MINING_SPEED, 40)));
        sets.add(new ArmorSet("lumberjack", "Lumberjack", "LEATHER",
            stats(Stat.HEALTH, 45, Stat.STRENGTH, 12, Stat.FORAGING_FORTUNE, 14, Stat.DEFENSE, 15),
            "Timber!", List.of("<gray>Every log you chop gives <red>+{timber} Strength</red> for",
                "<gray><green>{seconds}s</green>, stacking up to <green>5 times</green>."),
            stats(Stat.FORAGING_FORTUNE, 40, Stat.STRENGTH, 10)));
        sets.add(new ArmorSet("farmer", "Farmer", "GOLDEN",
            stats(Stat.HEALTH, 35, Stat.SPEED, 8, Stat.FARMING_FORTUNE, 16, Stat.DEFENSE, 12),
            "Green Thumb", List.of("<gray>Harvesting a crop has a <green>{chance}%</green> chance to",
                "<gray>grow the crops around it by a stage."),
            stats(Stat.FARMING_FORTUNE, 50, Stat.SPEED, 20)));
        sets.add(new ArmorSet("hunter", "Hunter", "CHAINMAIL",
            stats(Stat.STRENGTH, 14, Stat.CRIT_CHANCE, 3, Stat.CRIT_DAMAGE, 12, Stat.FEROCITY, 4, Stat.HEALTH, 30),
            "Predator", List.of("<gray>Heal <green>{lifesteal}%</green> of the damage you deal",
                "<gray>to enemies."),
            stats(Stat.CRIT_DAMAGE, 30, Stat.CRIT_CHANCE, 5)));
        sets.add(new ArmorSet("void_walker", "Void Walker", "NETHERITE",
            stats(Stat.HEALTH, 60, Stat.DEFENSE, 45, Stat.INTELLIGENCE, 40, Stat.SPEED, 5),
            "Void Anchor", List.of("<gray>Falling into the void teleports you back to your",
                "<gray>last safe spot instead (every <green>{cooldown}s</green>)."),
            stats(Stat.INTELLIGENCE, 50, Stat.ABILITY_DAMAGE, 8)));
        sets.add(new ArmorSet("frostborne", "Frostborne", "DIAMOND",
            stats(Stat.HEALTH, 55, Stat.DEFENSE, 55, Stat.TRUE_DEFENSE, 3, Stat.INTELLIGENCE, 10),
            "Permafrost", List.of("<gray>Enemies that hit you are <aqua>slowed</aqua> for <green>{slow}s</green>",
                "<gray>and you take <green>{reduction}%</green> less damage."),
            stats(Stat.DEFENSE, 40, Stat.TRUE_DEFENSE, 5)));
        return List.copyOf(sets);
    }

    public static List<ArmorSet> all() { return ALL; }

    public static Optional<ArmorSet> get(String id) {
        return ALL.stream().filter(set -> set.id().equals(id)).findFirst();
    }
}
