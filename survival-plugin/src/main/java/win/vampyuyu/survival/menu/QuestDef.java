package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** One quest from the config (tutorial or daily pool). Immutable. */
public record QuestDef(String id, String name, String description, QuestType type, String target, int amount,
        Material icon, List<String> requires, Rewards rewards) {

    /** Skill experience / items / coins paid on completion. */
    public record Rewards(double coins, Map<String, Double> skillXp, Map<String, Integer> items) {
        public static final Rewards NONE = new Rewards(0, Map.of(), Map.of());

        public Rewards {
            skillXp = Map.copyOf(skillXp);
            items = Map.copyOf(items);
        }

        public boolean isEmpty() {
            return coins <= 0 && skillXp.isEmpty() && items.isEmpty();
        }
    }

    public QuestDef {
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Bad quest id '" + id + "' (use a-z, 0-9, _)");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("Quest " + id + ": amount must be at least 1");
        }
        target = target == null ? "" : target.trim();
        requires = List.copyOf(requires);
    }

    /** Parses one entry; throws IllegalArgumentException with the quest id on bad data. */
    public static QuestDef parse(String id, ConfigurationSection section, List<String> defaultRequires) {
        try {
            String typeName = section.getString("type", "").toUpperCase(Locale.ROOT);
            QuestType type;
            try {
                type = QuestType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown type '" + typeName + "'");
            }
            Material icon = Material.matchMaterial(section.getString("icon", "PAPER"));
            if (icon == null || icon.isAir()) {
                icon = Material.PAPER;
            }
            List<String> requires = section.contains("requires") ? section.getStringList("requires") : defaultRequires;
            Map<String, Double> xp = new LinkedHashMap<>();
            ConfigurationSection xpSection = section.getConfigurationSection("rewards.skill-xp");
            if (xpSection != null) {
                for (String key : xpSection.getKeys(false)) {
                    xp.put(key.toUpperCase(Locale.ROOT), xpSection.getDouble(key));
                }
            }
            Map<String, Integer> items = new LinkedHashMap<>();
            ConfigurationSection itemSection = section.getConfigurationSection("rewards.items");
            if (itemSection != null) {
                for (String key : itemSection.getKeys(false)) {
                    items.put(key, Math.max(1, itemSection.getInt(key, 1)));
                }
            }
            return new QuestDef(id, section.getString("name", id), section.getString("description", ""), type,
                section.getString("target", ""), section.getInt("amount", 1), icon, new ArrayList<>(requires),
                new Rewards(Math.max(0, section.getDouble("rewards.coins", 0)), xp, items));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("quest '" + id + "': " + e.getMessage(), e);
        }
    }
}
