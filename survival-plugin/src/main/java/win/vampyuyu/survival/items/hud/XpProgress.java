package win.vampyuyu.survival.items.hud;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;

/**
 * Progress inside the current skill level for the XP popup ({@code 412/1,000}). {@code SkillsApi} has no per-level XP
 * method (see NEEDS in the notes), so this class:
 * <ol>
 * <li>calls {@code SkillsApi.xpForLevel(Skill, int)} (cumulative XP needed to REACH a level) by reflection when the
 *     implementation has it;</li>
 * <li>otherwise uses the optional curve of {@code hud.xp-curve} ({@code need(level) = round(base * growth^(level-1))}
 *     XP to go from level-1 to level), but only when the curve reproduces the level the skills feature reports for the
 *     player's total XP; a curve that disagrees is never shown;</li>
 * <li>otherwise reports nothing and the popup keeps the {@code (Lv 7, 1,234 XP)} text.</li>
 * </ol>
 */
public final class XpProgress {
    /** XP into the current level and XP needed for the next one. */
    public record Progress(double into, double needed) {}

    private final boolean curveEnabled;
    private final double base;
    private final double growth;

    public XpProgress(boolean curveEnabled, double base, double growth) {
        this.curveEnabled = curveEnabled;
        this.base = Math.max(1, base);
        this.growth = Math.max(1.0, growth);
    }

    public static XpProgress from(ConfigurationSection root) {
        return new XpProgress(root.getBoolean("hud.xp-curve.enabled", false), root.getDouble("hud.xp-curve.base", 100),
            root.getDouble("hud.xp-curve.growth", 1.15));
    }

    /** Pure: XP needed to go from {@code level - 1} to {@code level} (level 1 and up). */
    public double need(int level) {
        return Math.round(base * Math.pow(growth, Math.max(0, level - 1)));
    }

    /** Pure: cumulative XP needed to reach {@code level} (0 for level 0). */
    public double cumulative(int level) {
        double sum = 0;
        for (int l = 1; l <= level; l++) {
            sum += need(l);
        }
        return sum;
    }

    /** Pure: the level a total XP amount gives on the curve (capped at {@code maxLevel}). */
    public int levelOf(double totalXp, int maxLevel) {
        int level = 0;
        double sum = 0;
        while (level < maxLevel) {
            sum += need(level + 1);
            if (totalXp + 1e-9 < sum) {
                break;
            }
            level++;
        }
        return level;
    }

    /**
     * Pure: progress from a cumulative-XP function. Empty at max level or when the function is inconsistent
     * (the XP is not inside [reach(level), reach(level + 1)) ).
     */
    public static java.util.Optional<Progress> progress(java.util.function.IntToDoubleFunction reach, int level,
            int maxLevel, double totalXp) {
        if (level >= maxLevel || level < 0) {
            return java.util.Optional.empty();
        }
        double from = reach.applyAsDouble(level);
        double to = reach.applyAsDouble(level + 1);
        if (!(to > from) || totalXp + 1e-6 < from || totalXp - 1e-6 >= to) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Progress(totalXp - from, to - from));
    }

    private Class<?> lookedUp;
    private Method cached;

    /** Finds {@code xpForLevel(Skill, int)} on the interface first (works for any implementation), then the class. */
    private Method lookup(SkillsApi api) {
        if (lookedUp == api.getClass()) {
            return cached;
        }
        Method found = null;
        for (Class<?> type : new Class<?>[] {SkillsApi.class, api.getClass()}) {
            try {
                found = type.getMethod("xpForLevel", Skill.class, int.class);
                found.setAccessible(true);
                break;
            } catch (NoSuchMethodException | RuntimeException ignored) {
                // the skills feature does not expose the curve (yet)
            }
        }
        lookedUp = api.getClass();
        cached = found;
        return found;
    }

    /** Progress of a player in a skill, if it can be known reliably. */
    public java.util.Optional<Progress> of(SkillsApi api, UUID player, Skill skill) {
        int level = api.level(player, skill);
        int max = api.maxLevel(skill);
        double total = api.xp(player, skill);
        Method method = lookup(api);
        if (method != null) {
            java.util.function.IntToDoubleFunction reach = l -> {
                try {
                    Object value = method.invoke(api, skill, l);
                    return value instanceof Number n ? n.doubleValue() : Double.NaN;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return Double.NaN;
                }
            };
            var found = progress(reach, level, max, total);
            if (found.isPresent()) {
                return found;
            }
        }
        if (curveEnabled && levelOf(total, max) == level) {
            return progress(this::cumulative, level, max, total);
        }
        return java.util.Optional.empty();
    }
}
