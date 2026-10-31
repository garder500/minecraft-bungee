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

    private QuestCatalog(Map<String, QuestDef> tutorial, Map<String, QuestDef> daily, List<String> problems) {
        this.tutorial = tutorial;
        this.daily = daily;
        this.problems = List.copyOf(problems);
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
        List<String> problems = new ArrayList<>();
        Map<String, QuestDef> tutorial = new LinkedHashMap<>();
        Map<String, QuestDef> daily = new LinkedHashMap<>();
        if (quests != null) {
            read(quests.getConfigurationSection("tutorial"), tutorial, problems);
            read(quests.getConfigurationSection("daily.pool"), daily, problems);
        }
        read(extraTutorial, tutorial, problems);
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
