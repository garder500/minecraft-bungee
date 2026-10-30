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

    /** Applies an event to every active quest it matches. Returns the quests that completed because of it. */
    public List<Completion> apply(QuestData data, Event event) {
        List<Completion> done = new ArrayList<>();
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
     * current value for a definition, empty when unknown (a feature is off).
     */
    public List<Completion> sync(QuestData data, Function<QuestDef, OptionalInt> probe) {
        List<Completion> all = new ArrayList<>();
        for (int round = 0; round < 64; round++) { // a chain can unlock a chain; bounded against cycles
            List<Completion> found = new ArrayList<>();
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
