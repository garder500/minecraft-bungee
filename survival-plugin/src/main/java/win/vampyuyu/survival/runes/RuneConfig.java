package win.vampyuyu.survival.runes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * The {@code runes.*} setting rows. {@code runes.list} declares the runes as {@code id|name|spec|target|component}:
 * {@code spec} is {@code stat:<stat>} or {@code realm}; {@code target} is armor, weapon or any; {@code component} is the
 * custom item spent for rank II (stat rune) or the Realm resource (Realm rune).
 *
 * <p>Values and costs follow GDD 10: ranks +2 / +4 / +6, 1 / 2 / 3 / 4 / 4 slots by tier, rank I = 8 powder,
 * II = 3 runes I + a component, III = 3 runes II + a diamond. A Realm rune is worth a rank III (6 points).
 */
public record RuneConfig(List<Rune> runes, List<Integer> values, List<Integer> slots, int powder, int fuse, int grindOutput,
        int realmPowder, int realmResource, int realmDiamonds, int burnTicks, int regenTicks, int regenCooldownSeconds,
        int stasisTicks, Effects effects, String stationBlock, List<String> problems) {
    /** Rows of the Runecraft menu: no more stat runes fit (one row each, on the left). */
    public static final int MAX_RUNES = 6;
    /** Realm runes are one button each in three columns on the right: 3 x 6. */
    public static final int MAX_REALM_RUNES = 18;

    /** The numbers of the Realm runes of the later Realms ({@code runes.<id>.*}). */
    public record Effects(int blightTicks, int tempestChains, double tempestRadius, double tempestShare,
            double rewindBelow, double rewindHeal, int rewindCooldownSeconds, double undertowRadius, double undertowPush,
            int undertowSlowTicks, int undertowCooldownSeconds, double soulbindHeal) {
        static Effects parse(Settings s) {
            return new Effects(Math.max(1, s.getInt("blight.wither-ticks", 60)), Math.max(0, s.getInt("tempest.chains", 2)),
                Math.max(1, s.getDouble("tempest.radius", 4)), clamp(s.getDouble("tempest.share", 0.35)),
                clamp(s.getDouble("rewind.below", 0.3)), clamp(s.getDouble("rewind.heal", 0.3)),
                Math.max(0, s.getInt("rewind.cooldown-seconds", 90)), Math.max(1, s.getDouble("undertow.radius", 3)),
                Math.max(0, s.getDouble("undertow.push", 1.2)), Math.max(0, s.getInt("undertow.slow-ticks", 40)),
                Math.max(0, s.getInt("undertow.cooldown-seconds", 10)), clamp(s.getDouble("soulbind.heal", 0.08)));
        }

        private static double clamp(double v) {
            return Math.max(0, Math.min(1, v));
        }
    }

    public static RuneConfig load(Db db) {
        return parse(Settings.load(db, "runes"));
    }

    public static RuneConfig parse(Settings s) {
        List<String> problems = new ArrayList<>(s.problems());
        List<Rune> runes = new ArrayList<>();
        for (String row : s.getList("list")) {
            String[] p = row.split("\\|", -1);
            if (p.length != 5 || p[0].isBlank() || p[1].isBlank() || p[4].isBlank()) {
                problems.add("runes.list: bad row '" + row + "' (id|name|spec|target|component)");
                continue;
            }
            Rune.Target target = Rune.Target.parse(p[3]);
            Stat stat = null;
            String spec = p[2].trim().toLowerCase(Locale.ROOT);
            if (spec.startsWith("stat:")) {
                try {
                    stat = Stat.valueOf(spec.substring(5).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    problems.add("runes.list: unknown stat in '" + row + "'");
                    continue;
                }
            } else if (!spec.equals("realm")) {
                problems.add("runes.list: spec must be stat:<stat> or realm in '" + row + "'");
                continue;
            }
            String id = p[0].trim().toLowerCase(Locale.ROOT);
            if (target == null || !id.matches("[a-z0-9_]+") || runes.stream().anyMatch(r -> r.id().equals(id))) {
                problems.add("runes.list: bad target or id in '" + row + "'");
                continue;
            }
            boolean realm = stat == null;
            int max = realm ? MAX_REALM_RUNES : MAX_RUNES;
            if (runes.stream().filter(r -> r.realm() == realm).count() >= max) {
                problems.add("runes.list: only the first " + max + " " + (stat == null ? "Realm" : "stat")
                    + " runes fit the menu, '" + id + "' is ignored");
                continue;
            }
            runes.add(new Rune(id, p[1].trim(), stat, target, p[4].trim()));
        }
        List<Integer> values = List.of(Math.max(0, s.getInt("value.rank1", 2)), Math.max(0, s.getInt("value.rank2", 4)),
            Math.max(0, s.getInt("value.rank3", 6)));
        List<Integer> slots = new ArrayList<>();
        int[] defaults = {1, 2, 3, 4, 4};
        for (int tier = 1; tier <= 5; tier++) {
            slots.add(Math.max(0, Math.min(8, s.getInt("slots.tier" + tier, defaults[tier - 1]))));
        }
        return new RuneConfig(List.copyOf(runes), values, List.copyOf(slots), Math.max(1, s.getInt("cost.powder", 8)),
            Math.max(2, s.getInt("cost.fuse", 3)), Math.max(1, s.getInt("cost.grind-output", 4)),
            Math.max(0, s.getInt("realm.powder", 8)), Math.max(1, s.getInt("realm.resource", 4)),
            Math.max(0, s.getInt("realm.diamonds", 1)), Math.max(1, s.getInt("scorch.burn-ticks", 80)),
            Math.max(1, s.getInt("verdant.regen-ticks", 100)), Math.max(0, s.getInt("verdant.cooldown-seconds", 12)),
            Math.max(1, s.getInt("stasis.slow-ticks", 60)), Effects.parse(s),
            s.getString("station-block", "GRINDSTONE"), List.copyOf(problems));
    }

    public java.util.Optional<Rune> rune(String id) {
        return runes.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** Points of a stat rune at a rank (1-3). */
    public int value(int rank) {
        return values.get(Math.max(1, Math.min(3, rank)) - 1);
    }

    /** Rune slots of an item of this power tier (1-5). */
    public int slotsFor(int tier) {
        return slots.get(Math.max(1, Math.min(5, tier)) - 1);
    }
}
