package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.logging.Logger;

/** The running ability effects of the server, advanced once a tick on the server thread. Each one cleans up after itself. */
final class Effects {
  /** One effect: a step run every tick (return false to end), a life in ticks, cleanups run when it ends. */
  static final class Fx {
    final VbMatch match;
    final UUID owner;
    final int life;
    final IntPredicate step;
    final List<Runnable> cleanup = new ArrayList<>();
    int age;
    boolean done;

    Fx(VbMatch match, UUID owner, int life, IntPredicate step) {
      this.match = match;
      this.owner = owner;
      this.life = life;
      this.step = step;
    }

    Fx onEnd(Runnable r) {
      cleanup.add(r);
      return this;
    }

    /** Ends the effect at the next tick. */
    void stop() {
      age = life;
    }
  }

  private final List<Fx> list = new ArrayList<>();
  private final Logger log;

  Effects(Logger log) {
    this.log = log;
  }

  Fx add(VbMatch m, UUID owner, int life, IntPredicate step) {
    Fx f = new Fx(m, owner, Math.max(1, life), step);
    list.add(f);
    return f;
  }

  void tick() {
    for (Fx f : new ArrayList<>(list)) {
      if (f.done) continue;
      f.age++;
      boolean go = true;
      try {
        go = f.step == null || f.step.test(f.age);
      } catch (RuntimeException e) {
        log.warning("ValoBlock ability effect failed: " + e);
        go = false;
      }
      if (!go || f.age >= f.life) end(f);
    }
  }

  void end(Fx f) {
    if (f.done) return;
    f.done = true;
    list.remove(f);
    for (Runnable r : f.cleanup) {
      try {
        r.run();
      } catch (RuntimeException e) {
        log.warning("ValoBlock ability cleanup failed: " + e);
      }
    }
  }

  /** Ends every effect of a match (all of them when {@code m} is null). */
  void clear(VbMatch m) {
    for (Fx f : new ArrayList<>(list)) if (m == null || f.match == m) end(f);
  }

  int size() {
    return list.size();
  }
}
