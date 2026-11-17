package win.vampyuyu.survival.wings;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geometry of the worn Wings display (pure, tested). Frame: the display's own, x = the wearer's left, y up, z
 * forward (yaw already applied by the entity rotation). The display rides the player as a passenger, so its origin is
 * the player's passenger attachment point, which for a player is the top of the hitbox: 1.8 standing, 1.5 sneaking,
 * 0.6 swimming or gliding. The model pivot is the middle of the shoulder blades.
 */
public final class WingsMath {
    private WingsMath() { }

    /** Passenger attachment height above the feet. */
    public static double attachY(WingsPose.Stance s) {
        return switch (s) {
            case STAND -> 1.8;
            case SNEAK -> 1.5;
            case LEANED -> 0.6;
        };
    }

    /** Shoulder height above the feet along the body axis. */
    public static double shoulderY(WingsPose.Stance s) {
        return s == WingsPose.Stance.SNEAK ? 1.05 : 1.35;
    }

    /** Eye height above the feet along the body axis. */
    public static double eyeY(WingsPose.Stance s) {
        return s == WingsPose.Stance.SNEAK ? 1.27 : 1.62;
    }

    /** How far the pivot sits behind the body axis: half the torso (0.125) plus a margin. */
    public static final double BACK = 0.22;

    /** Body lean in degrees: 0 upright, 90 horizontal. Gliding and swimming follow the pitch (positive = looks down). */
    public static double lean(WingsPose.Stance s, double pitchDeg) {
        return s == WingsPose.Stance.LEANED ? clamp(90.0 + pitchDeg, 0, 180) : 0;
    }

    /**
     * Translation of the display from its anchor (display frame). The body is rotated about the feet by the lean (what
     * the client does to a gliding player), so the shoulder anchor is rotated the same way.
     */
    public static Vector3f translation(WingsPose.Stance s, double leanDeg) {
        Vector3f p = new Vector3f(0, (float) shoulderY(s), (float) -BACK);
        p.rotateX((float) Math.toRadians(leanDeg));
        return p.sub(0, (float) attachY(s), 0);
    }

    public static Quaternionf leanRotation(double leanDeg) {
        return new Quaternionf().rotationX((float) Math.toRadians(leanDeg));
    }

    /** Wing span scale for the flap phase: 0 open, 1 drawn in. */
    public static Vector3f flapScale(int phase) {
        return phase == 0 ? new Vector3f(1f, 1f, 1f) : new Vector3f(0.78f, 0.92f, 1f);
    }

    /**
     * Bounding box of the model in blocks relative to the pivot (matches ENVELOPE in art/wings_art.py):
     * {max |x|, min y, max y, min behind, max behind}.
     */
    public static double[] envelope(WingsPose.Variant v) {
        return v == WingsPose.Variant.SPREAD ? new double[] {1.35, -1.20, 0.20, 0.0, 0.50}
            : new double[] {0.60, -1.25, 0.20, 0.0, 0.14};
    }

    /**
     * True when some part of the wings falls inside the wearer's view cone while standing (first person, camera at the
     * eyes, looking {@code pitchDeg} down). {@code coneHalfDeg} is the half angle of the widest part of the screen.
     * Body frame: forward z, up y; the wings lie behind the body (negative z).
     */
    public static boolean inView(WingsPose.Stance s, WingsPose.Variant v, double pitchDeg, double coneHalfDeg) {
        if (s == WingsPose.Stance.LEANED) {
            return false;  // the camera is at the head end of the body axis, the wings hang behind it
        }
        double[] e = envelope(v);
        double eye = eyeY(s), sh = shoulderY(s);
        double p = Math.toRadians(pitchDeg);
        float fy = (float) -Math.sin(p), fz = (float) Math.cos(p);   // view direction
        double cos = Math.cos(Math.toRadians(coneHalfDeg));
        for (double sx : new double[] {-e[0], e[0]}) {
            for (double y : new double[] {e[1], e[2]}) {
                for (double behind : new double[] {e[3], e[4]}) {
                    double px = sx, py = sh + y - eye, pz = -(BACK + behind);
                    double len = Math.sqrt(px * px + py * py + pz * pz);
                    if ((py * fy + pz * fz) / len > cos) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** From this pitch down the display is hidden from its own wearer (first person view stays clear). */
    public static final double HIDE_PITCH = 20;
    /** Release point: hysteresis so the display does not flicker at the threshold. */
    public static final double SHOW_PITCH = 14;
    /** Half angle of the widest corner of a 16:9 screen at the default 70 degrees field of view, with margin. */
    public static final double VIEW_CONE = 60;

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
