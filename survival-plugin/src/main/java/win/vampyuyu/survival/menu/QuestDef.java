package win.vampyuyu.survival.menu;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/** One quest of the plan or the daily pool. Immutable. */
public record QuestDef(String id, String name, String description, QuestType type, String target, int amount,
        Material icon, List<String> requires, Rewards rewards, String category) {

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
        QuestMatcher.validate(target); // IllegalArgumentException for a bad regex: the quest is skipped and reported
        requires = List.copyOf(requires);
        category = category == null ? "" : category;
    }

    /** A quest outside any category (the old tutorial and the daily pool). */
    public QuestDef(String id, String name, String description, QuestType type, String target, int amount,
            Material icon, List<String> requires, Rewards rewards) {
        this(id, name, description, type, target, amount, icon, requires, rewards, "");
    }
}
