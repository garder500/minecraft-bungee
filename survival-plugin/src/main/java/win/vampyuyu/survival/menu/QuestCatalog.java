package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

/** The quests defined in {@code menu.yml}: an ordered tutorial line and the pool of daily quests. */
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
     * Builds the catalog from the {@code quests} section. A broken entry is skipped and reported by
     * {@link #problems()}; the other quests still load.
     */
    public static QuestCatalog parse(ConfigurationSection quests) {
        return parse(quests, null);
    }

    /** Same, with more tutorial quests (from another feature's config) appended after the main ones. */
    public static QuestCatalog parse(ConfigurationSection quests, ConfigurationSection extraTutorial) {
        return parse(quests, extraTutorial, def -> true);
    }

    /**
     * Same, keeping only the tutorial quests {@code available} accepts: a quest nobody can complete (its system is not
     * loaded) is dropped, and so is every quest that needs it.
     */
    public static QuestCatalog parse(ConfigurationSection quests, ConfigurationSection extraTutorial,
            java.util.function.Predicate<QuestDef> available) {
        List<String> problems = new ArrayList<>();
        Map<String, QuestDef> tutorial = new LinkedHashMap<>();
        Map<String, QuestDef> daily = new LinkedHashMap<>();
        if (quests != null) {
            read(quests.getConfigurationSection("tutorial"), tutorial, problems);
            read(quests.getConfigurationSection("daily.pool"), daily, problems);
        }
        read(extraTutorial, tutorial, problems);
        java.util.Set<String> gone = new java.util.HashSet<>();
        tutorial.values().removeIf(def -> !available.test(def) && gone.add(def.id()));
        boolean more = !gone.isEmpty();
        while (more) { // a quest that needs a dropped one is dropped too, without a warning
            more = tutorial.values().removeIf(def -> def.requires().stream().anyMatch(gone::contains) && gone.add(def.id()));
        }
        for (QuestDef def : List.copyOf(tutorial.values())) {
            for (String req : def.requires()) {
                if (!tutorial.containsKey(req)) {
                    problems.add("quest '" + def.id() + "': requires unknown quest '" + req + "'; quest skipped");
                    tutorial.remove(def.id());
                    break;
                }
            }
        }
        return new QuestCatalog(tutorial, daily, problems);
    }

    /**
     * The quest plan: the tutorial line, the categories and their quests come from {@code plan}
     * ({@code quests.yml}); the daily pool still comes from {@code quests}. Nothing of the older tutorial is loaded.
     */
    public static QuestCatalog parsePlan(ConfigurationSection quests, ConfigurationSection plan) {
        List<String> problems = new ArrayList<>();
        Map<String, QuestDef> tutorial = new LinkedHashMap<>();
        Map<String, QuestDef> daily = new LinkedHashMap<>();
        Map<String, Category> categories = new LinkedHashMap<>();
        if (quests != null) {
            read(quests.getConfigurationSection("daily.pool"), daily, problems);
        }
        if (plan != null) {
            read(plan.getConfigurationSection("quests.tutorial"), tutorial, problems);
            ConfigurationSection cats = plan.getConfigurationSection("categories");
            if (cats != null) {
                for (String id : cats.getKeys(false)) {
                    ConfigurationSection c = cats.getConfigurationSection(id);
                    if (c == null) {
                        continue;
                    }
                    org.bukkit.Material icon = org.bukkit.Material.matchMaterial(c.getString("icon", "PAPER"));
                    categories.put(id, new Category(id, c.getString("name", id), c.getString("description", ""),
                        c.getBoolean("main", false), icon == null || icon.isAir() ? org.bukkit.Material.PAPER : icon,
                        c.getString("unlock", "")));
                }
            }
        }
        for (QuestDef def : List.copyOf(tutorial.values())) {
            for (String req : def.requires()) {
                if (!tutorial.containsKey(req)) {
                    problems.add("quest '" + def.id() + "': requires unknown quest '" + req + "'; quest skipped");
                    tutorial.remove(def.id());
                    break;
                }
            }
            if (!def.category().isEmpty() && !categories.containsKey(def.category())) {
                problems.add("quest '" + def.id() + "': unknown category '" + def.category() + "'");
            }
        }
        return new QuestCatalog(tutorial, daily, problems, categories);
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

    private static void read(ConfigurationSection section, Map<String, QuestDef> into, List<String> problems) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                problems.add("quest '" + id + "': not a section");
                continue;
            }
            try {
                into.put(id, QuestDef.parse(id, entry, List.of()));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
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
