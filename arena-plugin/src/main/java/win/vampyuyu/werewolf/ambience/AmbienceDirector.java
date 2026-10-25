package win.vampyuyu.werewolf.ambience;

import java.util.Map;

/**
 * Pure policy for the werewolf sound ambience: which looping bed a player should hear in a phase,
 * and when it must be stopped, started or re-triggered. No Bukkit access, so it is unit-tested.
 */
public final class AmbienceDirector {
  /** Volume of the vanilla stand-in bed for players without the pack. */
  public static final float FALLBACK_VOLUME = 0.15f;

  /** Cooldown between two passes of the vanilla stand-in, whose tracks last longer than a minute. */
  public static final long FALLBACK_LOOP_MILLIS = 90_000;

  /** The bed a player currently hears: its sound key, its volume and when this pass started. */
  public record Bed(String sound, float volume, long startedAt) {}

  /**
   * What to do now. stop is the previous bed's sound to stop (or null), start the bed to trigger
   * (or null), next the state to remember (the last bed unchanged when there is nothing to do).
   */
  public record Decision(String stop, Bed start, Bed next) {
    public boolean idle() {
      return stop == null && start == null;
    }
  }

  /** Profile key of the one-shot stings, by the pack sound name used in the code. */
  private static final Map<String, String> STING_KEYS =
      Map.of(
          "night", "night",
          "dawn", "day",
          "vote", "vote",
          "howl", "lobby",
          "death", "trial",
          "game_over", "finale");

  private final java.util.function.Supplier<AmbienceProfile> source;

  public AmbienceDirector(AmbienceProfile profile) {
    this(() -> profile);
  }

  /** Reads the profile at each decision, so a reload takes effect at once. */
  public AmbienceDirector(java.util.function.Supplier<AmbienceProfile> source) {
    this.source = source;
  }

  /** The profile entry that governs this moment of the game. */
  public AmbienceProfile.Entry entry(String phase, boolean night, boolean finale) {
    if (finale || "FINISHED".equals(phase)) return source.get().forPhase("finale", night);
    if (phase == null || phase.isEmpty() || phase.equals("LOBBY") || phase.equals("WAITING"))
      return source.get().forPhase("lobby", night);
    if (phase.equals("ELECTION")) return source.get().forPhase("vote", night);
    return source.get().forPhase(phase, night);
  }

  /**
   * Decides the bed for one player. A changed bed stops the previous one first and starts the new
   * one; an unchanged bed is left alone until its loop has run out, then triggered again.
   */
  public Decision decide(
      String phase, boolean night, boolean finale, boolean skinned, Bed last, long now) {
    AmbienceProfile.Entry entry = entry(phase, night, finale);
    String sound;
    float volume;
    long loop;
    if (skinned) {
      sound = entry.ambient().sound();
      volume = entry.ambient().volume();
      loop = entry.ambient().loopMillis();
    } else {
      sound = entry.fallback();
      volume = FALLBACK_VOLUME;
      loop = FALLBACK_LOOP_MILLIS;
    }
    Bed bed = new Bed(sound, volume, now);
    if (last == null) return new Decision(null, bed, bed);
    if (!last.sound().equals(sound) || last.volume() != volume)
      return new Decision(last.sound(), bed, bed);
    if (now - last.startedAt() >= loop) return new Decision(null, bed, bed);
    return new Decision(null, null, last);
  }

  /** The pack sting for a cue name, or null when the profile has none for it. */
  public AmbienceProfile.Sting sting(String name) {
    String key = STING_KEYS.get(name);
    return key == null ? null : source.get().forPhase(key, false).sting();
  }
}
