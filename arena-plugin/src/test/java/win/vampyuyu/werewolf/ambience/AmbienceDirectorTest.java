package win.vampyuyu.werewolf.ambience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import win.vampyuyu.werewolf.ambience.AmbienceDirector.Bed;
import win.vampyuyu.werewolf.ambience.AmbienceDirector.Decision;

class AmbienceDirectorTest {
  private final AmbienceProfile profile = AmbienceProfile.defaults();
  private final AmbienceDirector director = new AmbienceDirector(profile);

  private Bed bed(String phase, boolean night, boolean finale) {
    Decision d = director.decide(phase, night, finale, true, null, 0);
    assertNotNull(d.start());
    return d.next();
  }

  private String sound(String phase, boolean night) {
    return profile.forPhase(phase, night).ambient().sound();
  }

  @Test
  void firstCallStartsTheBedWithoutStopping() {
    Decision d = director.decide("NIGHT", true, false, true, null, 1000);
    assertNull(d.stop());
    assertEquals(sound("night", true), d.start().sound());
    assertEquals(1000, d.next().startedAt());
  }

  @Test
  void unchangedBedIsNotRestartedBeforeItsLoopEnds() {
    Bed last = bed("NIGHT", true, false);
    long loop = profile.forPhase("night", true).ambient().loopMillis();
    Decision d = director.decide("SEER", true, false, true, last, loop - 1);
    assertTrue(d.idle());
    assertSame(last, d.next());
  }

  @Test
  void restartsExactlyWhenTheLoopExpires() {
    Bed last = bed("NIGHT", true, false);
    long loop = profile.forPhase("night", true).ambient().loopMillis();
    Decision d = director.decide("NIGHT", true, false, true, last, loop);
    assertNull(d.stop());
    assertEquals(last.sound(), d.start().sound());
    assertEquals(loop, d.next().startedAt());
  }

  @Test
  void fullCycleChangesBedAtEachTransition() {
    Bed bed = bed("NIGHT", true, false);
    long now = 1000;
    String[][] steps = {
      {"DISCUSSION", "false"}, {"DAY_VOTE", "false"}, {"TRIAL", "false"}, {"NIGHT", "true"}
    };
    for (String[] step : steps) {
      boolean night = Boolean.parseBoolean(step[1]);
      Decision d = director.decide(step[0], night, false, true, bed, now);
      assertEquals(bed.sound(), d.stop(), step[0] + " stops the previous bed");
      assertNotNull(d.start(), step[0]);
      bed = d.next();
      now += 1000;
    }
    Decision finale = director.decide("FINISHED", false, true, true, bed, now);
    assertEquals(bed.sound(), finale.stop());
    assertEquals(sound("finale", false), finale.start().sound());
  }

  @Test
  void dawnKeepsNightPhaseMappingToDay() {
    Bed night = bed("NIGHT", true, false);
    Decision d = director.decide("DISCUSSION", false, false, true, night, 5000);
    assertEquals(night.sound(), d.stop());
    assertEquals(sound("day", false), d.start().sound());
  }

  @Test
  void trialSharesTheVoteSoundButChangesVolume() {
    Bed vote = bed("DAY_VOTE", false, false);
    Decision d = director.decide("TRIAL", false, false, true, vote, 100);
    assertEquals(vote.sound(), d.stop());
    assertEquals(0.6f, d.start().volume());
  }

  @Test
  void electionUsesTheVoteBed() {
    assertEquals(sound("vote", false), bed("ELECTION", false, false).sound());
  }

  @Test
  void lobbyAndUnknownPhases() {
    assertEquals(sound("lobby", false), bed("WAITING", false, false).sound());
    assertEquals(sound("lobby", false), bed(null, false, false).sound());
    assertEquals(sound("night", true), bed("SOME_NEW_PHASE", true, false).sound());
  }

  @Test
  void withoutThePackTheVanillaFallbackPlaysQuietlyWithACooldown() {
    Decision d = director.decide("NIGHT", true, false, false, null, 0);
    assertEquals(profile.forPhase("night", true).fallback(), d.start().sound());
    assertEquals(AmbienceDirector.FALLBACK_VOLUME, d.start().volume());
    Bed last = d.next();
    assertTrue(director.decide("SEER", true, false, false, last, 60_000).idle());
    Decision again =
        director.decide("SEER", true, false, false, last, AmbienceDirector.FALLBACK_LOOP_MILLIS);
    assertNull(again.stop());
    assertNotNull(again.start());
  }

  @Test
  void stingsComeFromTheProfile() {
    assertEquals(profile.forPhase("night", true).sting(), director.sting("night"));
    assertEquals(profile.forPhase("day", false).sting(), director.sting("dawn"));
    assertEquals(profile.forPhase("finale", false).sting(), director.sting("game_over"));
    assertNull(director.sting("shot"));
  }
}
