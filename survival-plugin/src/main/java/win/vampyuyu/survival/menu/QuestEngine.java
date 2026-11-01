package win.vampyuyu.survival.menu;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

/**
 * The quest state machine, free of Bukkit so it can be tested. It only edits a {@link QuestData}; paying rewards,
 * messages and persistence are the caller's job ({@link QuestService}).
 *
 * <p>Tutorial quest states: LOCKED (a prerequisite is not completed) -> ACTIVE (progress counts) -> COMPLETED. Progress
 * of a locked or completed quest never changes, and a quest completes exactly once: it is recorded as completed
 * before the caller pays, so a reward cannot be paid twice. Daily quests: ACTIVE while listed for the current day
 * and not done; the all-dailies bonus and the streak are paid once per day.
 */
public final class QuestEngine {
    /** Something that happened. For absolute types {@code value} is the current level/count, otherwise an increment. */
    public record Event(QuestType type, String subject, int value) {}

    /** A quest that just completed. */
    public record Completion(QuestDef def, boolean daily) {}

    /** The all-dailies bonus of a day. */
    public record StreakBonus(int streak) {}

    /** What the sidebar tracks. */
    public record Tracked(QuestDef def, int progress, boolean daily) {}

    private final QuestCatalog catalog;

    public QuestEngine(QuestCatalog catalog) {
        this.catalog = catalog;
    }

    public QuestCatalog catalog() {
        return catalog;
    }

    // ---------------------------------------------------------------- state queries

    public boolean isActive(QuestData data, QuestDef def) {
        return !data.completed.contains(def.id()) && data.completed.containsAll(def.requires());
    }

    /** Completed quests still in the plan: a player's record keeps the ids of retired quests. */
    public int completedCount(QuestData data) {
        return (int) catalog.tutorial().stream().filter(def -> data.completed.contains(def.id())).count();
    }

    public List<QuestDef> activeTutorial(QuestData data) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef def : catalog.tutorial()) {
            if (isActive(data, def)) {
                out.add(def);
            }
        }
        return out;
    }

    /** Today's daily quests that still exist in the catalog. */
    public List<QuestDef> dailyQuests(QuestData data) {
        List<QuestDef> out = new ArrayList<>();
        for (String id : data.dailyIds) {
            catalog.dailyQuest(id).ifPresent(out::add);
        }
        return out;
    }

    public int progress(QuestData data, QuestDef def, boolean daily) {
        if (daily) {
            return data.dailyDone.contains(def.id()) ? def.amount() : data.dailyProgress.getOrDefault(def.id(), 0);
        }
        return data.completed.contains(def.id()) ? def.amount() : data.progress.getOrDefault(def.id(), 0);
    }

    public Optional<Tracked> tracked(QuestData data) {
        String tracked = data.tracked == null ? "" : data.tracked;
        if (tracked.startsWith("daily:")) {
            String id = tracked.substring(6);
            for (QuestDef def : dailyQuests(data)) {
                if (def.id().equals(id) && !data.dailyDone.contains(id)) {
                    return Optional.of(new Tracked(def, progress(data, def, true), true));
                }
            }
        } else if (!tracked.isEmpty()) {
            Optional<QuestDef> def = catalog.tutorialQuest(tracked);
            if (def.isPresent() && isActive(data, def.get())) {
                return Optional.of(new Tracked(def.get(), progress(data, def.get(), false), false));
            }
        }
        List<QuestDef> active = activeTutorial(data);
        if (!active.isEmpty()) {
            return Optional.of(new Tracked(active.get(0), progress(data, active.get(0), false), false));
        }
        for (QuestDef def : dailyQuests(data)) {
            if (!data.dailyDone.contains(def.id())) {
                return Optional.of(new Tracked(def, progress(data, def, true), true));
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- progress

    /**
     * Proof events: a state the world re-announces (a built ring) proves the steps before it. When such an event
     * matches a locked quest, the quest and the unfinished quests it waits for in the same category are completed, in
     * chain order. This lets a player who built the ring before a broken earlier step was fixed move on. Only
     * {@code generator_built:<kind>} events are proofs; ordinary progress never skips a quest.
     */
    public List<Completion> catchUp(QuestData data, Event event) {
        if (event.type() != QuestType.EVENT || !event.subject().startsWith("generator_built:")) {
            return List.of();
        }
        List<Completion> done = new ArrayList<>();
        for (QuestDef def : catalog.tutorial()) {
            if (def.type() != QuestType.EVENT || data.completed.contains(def.id()) || isActive(data, def)
                    || !QuestMatcher.matches(def.target(), event.subject())) {
                continue;
            }
            List<QuestDef> chain = new ArrayList<>();
            if (!pending(data, def, def.category(), chain, new java.util.HashSet<>())) {
                continue; // something outside the category is missing: the quest stays locked
            }
            for (QuestDef step : chain) {
                data.progress.remove(step.id());
                data.completed.add(step.id());
                done.add(new Completion(step, false));
            }
        }
        return done;
    }

    /** Collects def and its unfinished requirements (first ones first); false when one is outside the category. */
    private boolean pending(QuestData data, QuestDef def, String category, List<QuestDef> into, java.util.Set<String> seen) {
        if (data.completed.contains(def.id()) || !seen.add(def.id())) {
            return true;
        }
        if (!def.category().equals(category)) {
            return false;
        }
        for (String req : def.requires()) {
            Optional<QuestDef> before = catalog.tutorialQuest(req);
            if (before.isEmpty() || !pending(data, before.get(), category, into, seen)) {
                return false;
            }
        }
        into.add(def);
        return true;
    }

    /** Applies an event to every active quest it matches. Returns the quests that completed because of it. */
    public List<Completion> apply(QuestData data, Event event) {
        List<Completion> done = new ArrayList<>();
        if (event.type() == QuestType.FORGE_ITEM) {
            data.forged.merge(event.subject(), Math.max(0, event.value()), Integer::sum);
        }
        for (QuestDef def : catalog.tutorial()) {
            if (def.type() == event.type() && isActive(data, def) && QuestMatcher.matches(def.target(), event.subject())) {
                if (bump(data.progress, def, event)) {
                    data.progress.remove(def.id());
                    data.completed.add(def.id());
                    done.add(new Completion(def, false));
                }
            }
        }
        for (QuestDef def : dailyQuests(data)) {
            if (def.type() == event.type() && !data.dailyDone.contains(def.id())
                    && QuestMatcher.matches(def.target(), event.subject())) {
                if (bump(data.dailyProgress, def, event)) {
                    data.dailyProgress.remove(def.id());
                    data.dailyDone.add(def.id());
                    done.add(new Completion(def, true));
                }
            }
        }
        return done;
    }

    private static boolean bump(java.util.Map<String, Integer> map, QuestDef def, Event event) {
        int current = map.getOrDefault(def.id(), 0);
        int next = def.type().absolute() ? Math.max(current, event.value())
            : (int) Math.min(Integer.MAX_VALUE, (long) current + Math.max(0, event.value()));
        next = Math.min(next, def.amount());
        if (next != current) {
            map.put(def.id(), next);
        }
        return next >= def.amount();
    }

    /**
     * Re-reads the current value of every active absolute quest (levels, island ownership) and completes the ones
     * that are already satisfied, repeating while completions unlock further quests. {@code probe} returns the
     * current value for a definition, empty when unknown (a feature is off). Forge quests also take what was forged
     * before they unlocked: a forged item costs hours and rare materials, so it is not asked for a second time.
     */
    public List<Completion> sync(QuestData data, Function<QuestDef, OptionalInt> probe) {
        List<Completion> all = new ArrayList<>();
        for (int round = 0; round < 64; round++) { // a chain can unlock a chain; bounded against cycles
            List<Completion> found = creditForged(data, probe);
            List<QuestDef> candidates = new ArrayList<>(activeTutorial(data));
            candidates.addAll(dailyQuests(data).stream().filter(d -> !data.dailyDone.contains(d.id())).toList());
            for (QuestDef def : candidates) {
                if (!def.type().absolute()) {
                    continue;
                }
                OptionalInt value = probe.apply(def);
                if (value.isPresent() && value.getAsInt() > 0) {
                    found.addAll(apply(data, new Event(def.type(), def.target().isEmpty() ? "ANY" : def.target(),
                        value.getAsInt())));
                }
            }
            if (found.isEmpty()) {
                break;
            }
            all.addAll(found);
        }
        return all;
    }

    /**
     * The quest a Forge recipe waits for, empty when the recipe is open: it opens with its first quest on the main path,
     * or, for an item only side quests ask for, with any of them. An item no quest asks for is always open.
     */
    public Optional<QuestDef> forgeLock(QuestData data, String item) {
        List<QuestDef> asks = catalog.tutorial().stream()
            .filter(def -> def.type() == QuestType.FORGE_ITEM && def.target().equals(item)).toList();
        List<QuestDef> main = asks.stream()
            .filter(def -> catalog.category(def.category()).map(QuestCatalog.Category::main).orElse(false)).toList();
        List<QuestDef> gate = main.isEmpty() ? asks : main;
        boolean open = gate.stream().anyMatch(def -> data.completed.contains(def.id()) || isActive(data, def));
        return open ? Optional.empty() : gate.stream().findFirst();
    }

    private List<Completion> creditForged(QuestData data, Function<QuestDef, OptionalInt> probe) {
        List<Completion> done = new ArrayList<>();
        for (QuestDef def : activeTutorial(data)) {
            if (def.type() != QuestType.FORGE_ITEM) {
                continue;
            }
            int made = probe.apply(def).orElse(0);
            for (java.util.Map.Entry<String, Integer> entry : data.forged.entrySet()) {
                if (QuestMatcher.matches(def.target(), entry.getKey())) {
                    made = Math.max(made, entry.getValue());
                }
            }
            if (made >= def.amount()) {
                data.progress.remove(def.id());
                data.completed.add(def.id());
                done.add(new Completion(def, false));
            } else if (made > data.progress.getOrDefault(def.id(), 0)) {
                data.progress.put(def.id(), made);
            }
        }
        return done;
    }

    // ---------------------------------------------------------------- daily rotation and streak

    /**
     * Starts a new quest day when {@code today} differs from the stored one: picks the rotation, clears progress,
     * and drops the streak if yesterday was not completed. Returns true when something changed.
     */
    public boolean ensureDay(QuestData data, LocalDate today, int count) {
        String key = today.toString();
        if (key.equals(data.dailyDay)) {
            return false;
        }
        if (!data.streakDay.equals(today.minusDays(1).toString()) && !data.streakDay.equals(key)) {
            data.streak = 0;
        }
        data.dailyDay = key;
        data.dailyIds = new ArrayList<>(DailyClock.rotation(new ArrayList<>(catalog.dailyPool().keySet()), today, count));
        data.dailyProgress.clear();
        data.dailyDone.clear();
        data.dailyBonusPaid = false;
        return true;
    }

    /** Streak that is still alive on {@code today} (0 when the last full day was before yesterday). */
    public int liveStreak(QuestData data, LocalDate today) {
        if (data.streakDay.equals(today.toString()) || data.streakDay.equals(today.minusDays(1).toString())) {
            return data.streak;
        }
        return 0;
    }

    /**
     * When every daily of today is done and the bonus was not paid yet, marks it paid, advances the streak and
     * returns it. Empty otherwise. Call after daily completions.
     */
    public Optional<StreakBonus> claimDailyBonus(QuestData data, LocalDate today) {
        List<QuestDef> dailies = dailyQuests(data);
        if (dailies.isEmpty() || data.dailyBonusPaid || !data.dailyDay.equals(today.toString())) {
            return Optional.empty();
        }
        Set<String> done = new HashSet<>(data.dailyDone);
        for (QuestDef def : dailies) {
            if (!done.contains(def.id())) {
                return Optional.empty();
            }
        }
        data.dailyBonusPaid = true;
        data.streak = data.streakDay.equals(today.minusDays(1).toString()) ? data.streak + 1 : 1;
        data.streakDay = today.toString();
        data.dailyTotal++;
        return Optional.of(new StreakBonus(data.streak));
    }
}
