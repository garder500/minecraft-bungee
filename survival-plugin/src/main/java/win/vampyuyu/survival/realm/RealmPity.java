package win.vampyuyu.survival.realm;

/** The pity curve of GDD 15.1: p0 up to {@code soft} misses, then a linear climb to 1 at {@code dur} misses. */
public final class RealmPity {
    private RealmPity() {}

    public static double chance(double p0, int soft, int dur, int misses) {
        if (misses >= dur) {
            return 1.0;
        }
        if (misses < soft) {
            return p0;
        }
        return p0 + (1.0 - p0) * (misses - soft) / (double) (dur - soft);
    }
}
