package win.vampyuyu.survival.boss;

/**
 * Which clip a rigged boss plays, tick by tick. A looping base clip ("walk" while it moves, else "idle") runs under one-shot
 * clips cued by the fight: "death" beats "phase" beats an ability beats "attack" beats "hurt"; a cue of lower priority does
 * not cut a running higher one. An ability id without its own clip plays "ability". A finished one-shot returns to the
 * base clip, except a hold clip (death) which stays on its last frame. Pure logic, no Bukkit: BossAnimatorTest.
 */
final class BossAnimator {
    private final BossRig rig;
    private BossRig.Clip base;
    private long baseStart;
    private BossRig.Clip shot;
    private long shotStart;
    private int shotPriority;

    BossAnimator(BossRig rig, long now) {
        this.rig = rig;
        this.base = rig.clips.get("idle");
        this.baseStart = now;
    }

    static int priority(String cue) {
        return switch (cue) {
            case "death" -> 5;
            case "phase" -> 4;
            case "attack" -> 2;
            case "hurt" -> 1;
            default -> 3; // an ability
        };
    }

    /** Resolves a cue to a clip of this rig: the clip of that name, or "ability" for an ability id, or null. */
    BossRig.Clip clipFor(String cue) {
        BossRig.Clip c = rig.clips.get(cue);
        if (c == null && priority(cue) == 3 && !cue.equals("idle") && !cue.equals("walk")) {
            c = rig.clips.get("ability");
        }
        return c;
    }

    /** Plays a cue now; returns whether a clip started. */
    boolean cue(String cue, long now) {
        if (cue.equals("idle") || cue.equals("walk")) {
            BossRig.Clip c = rig.clips.get(cue);
            if (c != null) {
                base = c;
                baseStart = now;
                shot = null;
            }
            return c != null;
        }
        BossRig.Clip c = clipFor(cue);
        if (c == null) {
            return false;
        }
        int p = priority(cue);
        if (shot != null && !finished(now) && p < shotPriority) {
            return false;
        }
        shot = c;
        shotStart = now;
        shotPriority = p;
        return true;
    }

    /** Switches the base clip between walking and standing (keeps the phase of the clip when it does not change). */
    void moving(boolean moving, long now) {
        BossRig.Clip want = rig.clips.get(moving ? "walk" : "idle");
        if (want == null) {
            want = rig.clips.get("idle");
        }
        if (want != base) {
            base = want;
            baseStart = now;
        }
    }

    private boolean finished(long now) {
        return shot != null && !shot.hold() && now - shotStart > shot.ticks();
    }

    boolean dying() {
        return shot != null && shot.name().equals("death");
    }

    /** Ticks until the running death clip has played out (0 when there is none or it is over). */
    long deathLeft(long now) {
        return dying() ? Math.max(0, shot.ticks() - (now - shotStart)) : 0;
    }

    /** The name of the clip shown now (for the log and the tests). */
    String current(long now) {
        if (shot != null && !finished(now)) {
            return shot.name();
        }
        return base == null ? "rest" : base.name();
    }

    /** The pose of every bone now: frames[bone] = [tx, ty, tz, qx, qy, qz, qw, k]. */
    float[][] poses(long now) {
        if (shot != null && finished(now)) {
            shot = null;
        }
        BossRig.Clip c = shot != null ? shot : base;
        if (c == null || c.frameCount() == 0) {
            float[][] rest = new float[rig.bones.size()][];
            java.util.Arrays.fill(rest, BossRig.REST);
            return rest;
        }
        long t = now - (shot != null ? shotStart : baseStart);
        int i = (int) (t / Math.max(1, c.step()));
        if (c.loop()) {
            int n = Math.max(1, c.frameCount() - 1);   // the last frame repeats the first in a closed loop
            i = i % n;
        } else {
            i = Math.min(i, c.frameCount() - 1);
        }
        return c.frames()[i];
    }
}
