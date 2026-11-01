package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The quests of the content database ({@link QuestRepo}): the ordered quest plan with its categories, and the pool of daily quests. */
public final class QuestCatalog {
    private final Map<String, QuestDef> tutorial;
    private final Map<String, QuestDef> daily;
    private final List<String> problems;
    private final Map<String, Category> categories;

    /** A group of quests in the quest menu: the main path is played in order, the others in parallel. */
    public record Category(String id, String name, String description, boolean main, org.bukkit.Material icon,
            String unlock) {}

    private QuestCatalog(Map<String, QuestDef> tutorial, Map<String, QuestDef> daily, List<String> problems) {
        this(tutorial, daily, problems, Map.of());
    }

    private QuestCatalog(Map<String, QuestDef> tutorial, Map<String, QuestDef> daily, List<String> problems,
            Map<String, Category> categories) {
        this.tutorial = tutorial;
        this.daily = daily;
        this.problems = List.copyOf(problems);
        this.categories = categories;
    }

    /** An empty catalog (quests switched off). */
    public static QuestCatalog empty() {
        return new QuestCatalog(Map.of(), Map.of(), List.of());
    }

    /**
     * The catalog as stored in the content database: the plan quests (in order, with their categories) and the daily
     * pool. A quest whose requirement is missing is dropped and reported, like the YAML parser did.
     */
    public static QuestCatalog of(Map<String, Category> categories, Map<String, QuestDef> plan, Map<String, QuestDef> daily,
            List<String> loadProblems) {
        List<String> problems = new ArrayList<>(loadProblems);
        Map<String, QuestDef> tutorial = new LinkedHashMap<>(plan);
        boolean dropped = true;
        while (dropped) { // a quest that needs a dropped one goes too
            dropped = false;
            for (QuestDef def : List.copyOf(tutorial.values())) {
                for (String req : def.requires()) {
                    if (!tutorial.containsKey(req)) {
                        problems.add("quest '" + def.id() + "': requires unknown quest '" + req + "'; quest skipped");
                        tutorial.remove(def.id());
                        dropped = true;
                        break;
                    }
                }
            }
        }
        for (QuestDef def : tutorial.values()) {
            if (!def.category().isEmpty() && !categories.containsKey(def.category())) {
                problems.add("quest '" + def.id() + "': unknown category '" + def.category() + "'");
            }
        }
        return new QuestCatalog(tutorial, new LinkedHashMap<>(daily), problems, new LinkedHashMap<>(categories));
    }

    /** The categories in config order; empty for the older tutorial. */
    public List<Category> categories() {
        return List.copyOf(categories.values());
    }

    public Optional<Category> category(String id) {
        return Optional.ofNullable(categories.get(id));
    }

    /** The tutorial quests of one category, in order. */
    public List<QuestDef> questsOf(String category) {
        return tutorial.values().stream().filter(d -> d.category().equals(category)).toList();
    }

    /** Tutorial quests in config order. */
    public List<QuestDef> tutorial() {
        return List.copyOf(tutorial.values());
    }

    public Map<String, QuestDef> dailyPool() {
        return Map.copyOf(daily);
    }

    public Optional<QuestDef> tutorialQuest(String id) {
        return Optional.ofNullable(tutorial.get(id));
    }

    public Optional<QuestDef> dailyQuest(String id) {
        return Optional.ofNullable(daily.get(id));
    }

    /** Human readable load problems, empty when everything parsed. */
    public List<String> problems() {
        return problems;
    }
}
