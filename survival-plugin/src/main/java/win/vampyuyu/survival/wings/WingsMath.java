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

    /** Sideways offset of each wing root from the body axis (blocks): the shoulder joint of the spread wings. */
    public static final double ROOT_X = 0.3;

    /** Root offset of one wing for a variant: the folded halves are modelled about the spine, so no offset. */
    public static double rootOffset(WingsPose.Variant v) {
        return v == WingsPose.Variant.SPREAD ? ROOT_X : 0;
    }

    /**
     * Translation of one wing display from its anchor (display frame). The body is rotated about the feet by the lean
     * (what the client does to a gliding player), so the shoulder anchor is rotated the same way.
     */
    public static Vector3f translation(WingsPose.Stance s, double leanDeg, WingsPose.Variant v, WingsPose.Side side) {
        Vector3f p = new Vector3f((float) (side.sign() * rootOffset(v)), (float) shoulderY(s), (float) -BACK);
        p.rotateX((float) Math.toRadians(leanDeg));
        return p.sub(0, (float) attachY(s), 0);
    }

    public static Quaternionf leanRotation(double leanDeg) {
        return new Quaternionf().rotationX((float) Math.toRadians(leanDeg));
    }

    /** Dihedral (degrees the wing tip is raised toward the back, which is up while gliding) of the open wing, flap phase 0. */
    public static final double DIHEDRAL_OPEN = 38;
    /** The same with the wings drawn in (flap phase 1). */
    public static final double DIHEDRAL_DRAWN = 16;
    /** Dihedral of the wings held out without flapping (upright creative style flight). */
    public static final double DIHEDRAL_HELD = 22;
    /** Sweep back (degrees the tip moves toward the feet) while gliding. */
    public static final double SWEEP_GLIDE = 20;

    /** Dihedral for a look, in degrees (0 for the folded wings). */
    public static double dihedral(WingsPose.Variant v, boolean flap, int phase) {
        if (v != WingsPose.Variant.SPREAD) {
            return 0;
        }
        return flap ? (phase == 0 ? DIHEDRAL_OPEN : DIHEDRAL_DRAWN) : DIHEDRAL_HELD;
    }

    /** Sweep back for a look, in degrees. */
    public static double sweep(WingsPose.Variant v, WingsPose.Stance s) {
        return v == WingsPose.Variant.SPREAD && s == WingsPose.Stance.LEANED ? SWEEP_GLIDE : 0;
    }

    /**
     * Rotation of one wing about its own root, in the display frame (applied as the display's right rotation, so
     * before the lean). The dihedral turns the tip toward -z (behind the body: up while gliding), the sweep turns it
     * toward -y (the feet: backwards while gliding); the two sides mirror each other.
     */
    public static Quaternionf wingRotation(WingsPose.Side side, WingsPose.Variant v, WingsPose.Stance s, boolean flap, int phase) {
        double d = Math.toRadians(dihedral(v, flap, phase)), w = Math.toRadians(sweep(v, s));
        int k = side.sign();
        return new Quaternionf().rotationY((float) (k * d)).rotateZ((float) (-k * w));
    }

    /**
     * Bounding box of one wing model in blocks relative to its pivot (|x| measured outward from the root) (matches ENVELOPE in art/wings_art.py):
     * {max |x|, min y, max y, min behind, max behind}.
     */
    public static double[] envelope(WingsPose.Variant v) {
        return v == WingsPose.Variant.SPREAD ? new double[] {1.32, -1.20, 0.20, 0.0, 0.50}
            : new double[] {0.60, -1.25, 0.20, 0.0, 0.14};
    }

    /**
     * True when some part of the wings falls inside the wearer's view cone while standing (first person, camera at the
     * eyes, looking {@code pitchDeg} down). {@code coneHalfDeg} is the half angle of the widest part of the screen.
     * Body frame: forward z, up y; the wings lie behind the body (negative z).
     */
    public static boolean inView(WingsPose.Stance s, WingsPose.Variant v, double pitchDeg, double coneHalfDeg) {
        if (s == WingsPose.Stance.LEANED) {
            return true;   // gliding: the camera sits at the feet end of the leaned body, the wings are right in front of it
        }
        double[] e = envelope(v);
        double eye = eyeY(s), sh = shoulderY(s);
        double p = Math.toRadians(pitchDeg);
        float fy = (float) -Math.sin(p), fz = (float) Math.cos(p);   // view direction
        double cos = Math.cos(Math.toRadians(coneHalfDeg));
        for (WingsPose.Side side : WingsPose.Side.values()) {
            // the wing is modelled about its root: corners relative to the root, turned like the display turns them
            Quaternionf q = wingRotation(side, v, s, true, 0);
            Quaternionf q2 = wingRotation(side, v, s, false, 0);
            for (Quaternionf rot : new Quaternionf[] {q, q2}) {
                for (double sx : new double[] {0, e[0]}) {
                    for (double y : new double[] {e[1], e[2]}) {
                        for (double behind : new double[] {e[3], e[4]}) {
                            Vector3f c = new Vector3f((float) (side.sign() * sx), (float) y, (float) -behind);
                            rot.transform(c);
                            double px = c.x + side.sign() * rootOffset(v), py = sh + c.y - eye, pz = c.z - BACK;
                            double len = Math.sqrt(px * px + py * py + pz * pz);
                            if ((py * fy + pz * fz) / len > cos) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * While gliding (leaned body) the client's camera stays near the player's position, i.e. at the FEET end of the
     * rendered body, so the wings at the shoulders are 1.35 blocks in front of it (seen on a real client: they fill
     * the sides of the view). The roots are set {@link #ROOT_X} off the spine so the centre of that view stays clear.
     * Hiding the pair while gliding would clear the view completely but {@code hideEntity} also hides it from the
     * wearer's own F5 view (tried, shots-wings4 v2: the wearer saw no wings at all), so it stays off.
     */
    public static final boolean HIDE_OWN_WHILE_GLIDING = false;

    /** Whether the pair should be hidden from its own wearer. {@code wasHidden} gives the hysteresis. */
    public static boolean hideFromOwner(WingsPose.Stance s, double pitchDeg, boolean wasHidden) {
        if (s == WingsPose.Stance.LEANED) {
            return HIDE_OWN_WHILE_GLIDING;
        }
        return wasHidden ? pitchDeg > SHOW_PITCH : pitchDeg > HIDE_PITCH;
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
