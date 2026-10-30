package win.vampyuyu.survival.boss;

import java.util.Map;

/**
 * One configured ability of a boss.
 *
 * @param type the ability
 * @param cooldownTicks ticks between two casts (the boss waits at least this long)
 * @param telegraphTicks warning time before the effect lands, 0 for the ability's own default
 * @param damage damage multiplier applied to the boss base damage
 * @param target who the ability aims at: nearest, random, top (best damager), far, all
 * @param fromTier first slayer tier that uses it (1 for everyone, ignored by regular bosses)
 * @param params remaining keys of the yaml entry (radius, count, duration...)
 */
public record AbilitySpec(AbilityType type, int cooldownTicks, int telegraphTicks, double damage, String target,
        int fromTier, Map<String, Object> params) {
    public AbilitySpec {
        params = Map.copyOf(params);
    }

    public double num(String key, double fallback) {
        Object value = params.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public int integer(String key, int fallback) {
        return (int) Math.round(num(key, fallback));
    }

    public boolean bool(String key, boolean fallback) {
        Object value = params.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    public String str(String key, String fallback) {
        Object value = params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** Seconds in the yaml, ticks in the code. */
    public int seconds(String key, double fallbackSeconds) {
        return (int) Math.round(num(key, fallbackSeconds) * 20);
    }
}
