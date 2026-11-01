package win.vampyuyu.survival.dungeon.run;

/** Who a boss attack reaches once its warning is over. Pure geometry, on the floor plane. */
final class BossAttacks {
    private BossAttacks() {}

    enum Kind { SLAM, NOVA, MARKS, CHARGE }

    /** Radius of the circle of each attack. */
    static double radius(Kind kind) {
        return switch (kind) {
            case SLAM -> 7;
            case NOVA -> 6;
            case MARKS -> 3;
            case CHARGE -> 2;
        };
    }

    /** SLAM and MARKS hit inside their circle, NOVA everywhere but inside it. */
    static boolean hitsCircle(Kind kind, double dx, double dz) {
        boolean inside = dx * dx + dz * dz <= radius(kind) * radius(kind);
        return kind == Kind.NOVA ? !inside : inside;
    }

    /** CHARGE hits close to the line it rushes along. */
    static boolean hitsLine(double ax, double az, double bx, double bz, double px, double pz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len = dx * dx + dz * dz;
        double t = len <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len));
        double ox = ax + t * dx - px;
        double oz = az + t * dz - pz;
        return ox * ox + oz * oz <= radius(Kind.CHARGE) * radius(Kind.CHARGE);
    }
}
