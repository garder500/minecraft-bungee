package win.vampyuyu.survival.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.Skill;

/**
 * Cached skill leaderboards. The cache is rebuilt off the main thread by reading the profile documents directly
 * ({@code players/<uuid>.json}, section {@code skills}), so offline players are ranked without loading their
 * profiles; online players are overlaid with their live values at query time. Boards: each skill by experience and
 * {@code average} by average level.
 */
final class Leaderboard {
    static final String AVERAGE = "average";

    /** One ranked player. {@code score} is experience, or the average level for the average board. */
    record Entry(UUID id, String name, double score, double level) {}

    private final Logger log;
    private volatile Map<String, List<Entry>> cache = Map.of();
    private volatile long builtAt;

    Leaderboard(Logger log) {
        this.log = log;
    }

    long builtAt() {
        return builtAt;
    }

    static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            keys.add(skill.key());
        }
        keys.add(AVERAGE);
        return keys;
    }

    /** Reads every profile document under {@code playersDir}. Blocking: call it from an async task. */
    void rebuild(Path playersDir, SkillsConfig cfg) {
        Map<String, List<Entry>> boards = new HashMap<>();
        for (String key : keys()) {
            boards.put(key, new ArrayList<>());
        }
        int files = 0;
        if (Files.isDirectory(playersDir)) {
            try (Stream<Path> stream = Files.list(playersDir)) {
                for (Path file : (Iterable<Path>) stream::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".json") || files >= cfg.leaderboardMaxFiles) {
                        continue;
                    }
                    files++;
                    readOne(file, name.substring(0, name.length() - 5), cfg, boards);
                }
            } catch (IOException e) {
                log.warning("skills: leaderboard scan failed: " + e.getMessage());
            }
        }
        Map<String, List<Entry>> trimmed = new LinkedHashMap<>();
        for (var board : boards.entrySet()) {
            List<Entry> list = board.getValue();
            list.sort(Comparator.comparingDouble(Entry::score).reversed().thenComparing(Entry::name));
            trimmed.put(board.getKey(), List.copyOf(list.subList(0, Math.min(list.size(), cfg.leaderboardSize))));
        }
        this.cache = trimmed;
        this.builtAt = System.currentTimeMillis();
    }

    private void readOne(Path file, String stem, SkillsConfig cfg, Map<String, List<Entry>> boards) {
        UUID id;
        try {
            id = UUID.fromString(stem);
        } catch (IllegalArgumentException e) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            String name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : "";
            if (name.isBlank()) {
                name = id.toString().substring(0, 8);
            }
            JsonObject sections = root.has("sections") && root.get("sections").isJsonObject()
                ? root.getAsJsonObject("sections") : null;
            JsonObject skills = sections != null && sections.has("skills") && sections.get("skills").isJsonObject()
                ? sections.getAsJsonObject("skills") : null;
            JsonObject xp = skills != null && skills.has("xp") && skills.get("xp").isJsonObject()
                ? skills.getAsJsonObject("xp") : null;
            if (xp == null) {
                return;
            }
            Map<String, Double> values = new HashMap<>();
            for (Skill skill : Skill.values()) {
                JsonElement e = xp.get(skill.key());
                values.put(skill.key(), e != null && e.isJsonPrimitive() ? Math.max(0, e.getAsDouble()) : 0);
            }
            addEntries(boards, id, name, values, cfg);
        } catch (IOException | RuntimeException e) {
            log.fine("skills: leaderboard skipped " + file.getFileName() + ": " + e);
        }
    }

    private static void addEntries(Map<String, List<Entry>> boards, UUID id, String name, Map<String, Double> xp,
            SkillsConfig cfg) {
        double averageSum = 0;
        boolean any = false;
        for (Skill skill : Skill.values()) {
            double value = xp.getOrDefault(skill.key(), 0.0);
            if (value > 0) {
                any = true;
                boards.get(skill.key()).add(new Entry(id, name, value,
                    cfg.levels.levelOf(value, cfg.maxLevel(skill))));
            }
            if (cfg.averageSkills.contains(skill)) {
                averageSum += cfg.levels.fractionalLevel(value, cfg.maxLevel(skill));
            }
        }
        if (any && !cfg.averageSkills.isEmpty()) {
            double avg = averageSum / cfg.averageSkills.size();
            boards.get(AVERAGE).add(new Entry(id, name, avg, avg));
        }
    }

    /** Top {@code limit} of a board with online players overlaid. Main thread. */
    List<Entry> top(String key, int limit, SkillsService service) {
        List<Entry> base = cache.getOrDefault(key, List.of());
        Map<UUID, Entry> merged = new LinkedHashMap<>();
        for (Entry entry : base) {
            merged.put(entry.id(), entry);
        }
        Skill skill = SkillsService.parseSkill(key);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (skill != null) {
                double xp = service.xp(id, skill);
                if (xp > 0) {
                    merged.put(id, new Entry(id, player.getName(), xp, service.level(id, skill)));
                }
            } else if (AVERAGE.equals(key)) {
                double avg = service.average(id);
                if (avg > 0) {
                    merged.put(id, new Entry(id, player.getName(), avg, avg));
                }
            }
        }
        List<Entry> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparingDouble(Entry::score).reversed().thenComparing(Entry::name));
        return list.subList(0, Math.min(limit, list.size()));
    }
}
