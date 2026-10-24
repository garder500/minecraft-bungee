package win.vampyuyu.werewolf.ambience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AmbienceProfileTest {
  private static final String BASE_ENTRY =
      "{\"ambient\":{\"sound\":\"vampyuyu:werewolf.ambience_night\",\"loopMillis\":32000,\"volume\":0.4},"
          + "\"sting\":{\"sound\":\"vampyuyu:werewolf.night\",\"volume\":0.8},"
          + "\"fallback\":\"minecraft:ambient.cave\"}";

  private static AmbienceProfile parse(String json) throws IOException {
    return AmbienceProfile.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  private static final String DAY_ENTRY =
      "{\"ambient\":{\"sound\":\"vampyuyu:werewolf.ambience_day\",\"loopMillis\":32000,\"volume\":0.45},"
          + "\"sting\":{\"sound\":\"vampyuyu:werewolf.dawn\",\"volume\":0.7},"
          + "\"fallback\":\"minecraft:music.game\"}";

  /** A valid profile with day and night, plus the extra keys given as "key":entry pairs. */
  private static String profile(String... extra) {
    StringBuilder json = new StringBuilder("{\"day\":" + DAY_ENTRY + ",\"night\":" + BASE_ENTRY);
    for (String pair : extra) json.append(',').append(pair);
    return json.append('}').toString();
  }

  private static String withAmbient(String ambient) {
    return "{\"ambient\":" + ambient + ",\"sting\":{\"sound\":\"vampyuyu:werewolf.dawn\",\"volume\":0.5},"
        + "\"fallback\":\"minecraft:music.game\"}";
  }

  @Test
  public void shippedProfileHasEveryPhaseAndTheOverrides() {
    AmbienceProfile profile = AmbienceProfile.defaults();
    for (String phase : new String[] {"lobby", "day", "night", "vote", "trial", "finale", "DAY_VOTE", "DEFENSE"})
      assertNotNull(phase, profile.forPhase(phase, false));
    assertNotEquals(profile.forPhase("day", false), profile.forPhase("night", true));
  }

  @Test
  public void eachMainPhaseHasItsOwnBed() {
    AmbienceProfile profile = AmbienceProfile.defaults();
    String day = profile.forPhase("day", false).ambient().sound();
    String night = profile.forPhase("night", true).ambient().sound();
    String vote = profile.forPhase("vote", false).ambient().sound();
    String finale = profile.forPhase("finale", false).ambient().sound();
    assertEquals(4, new HashSet<>(List.of(day, night, vote, finale)).size());
    assertNotEquals(profile.forPhase("lobby", false).ambient().sound(), day);
  }

  @Test
  public void exactPhaseNameWinsOverTheGenericKey() {
    AmbienceProfile profile = AmbienceProfile.defaults();
    assertEquals(profile.forPhase("DAY_VOTE", false), profile.forPhase("day_vote", true));
    assertNotEquals(profile.forPhase("DAY_VOTE", false), profile.forPhase("vote", false));
    assertEquals(profile.forPhase("DEFENSE", true), profile.forPhase("DEFENSE", false));
  }

  @Test
  public void upperCaseNameResolvesToTheLowerCaseKey() {
    AmbienceProfile profile = AmbienceProfile.defaults();
    assertEquals(profile.forPhase("trial", false), profile.forPhase("TRIAL", true));
    assertEquals(profile.forPhase("night", true), profile.forPhase("NIGHT", false));
  }

  @Test
  public void unknownPhaseFallsBackToNightOrDay() throws IOException {
    AmbienceProfile profile = parse(profile());
    assertEquals(profile.forPhase("night", true), profile.forPhase("SOMETHING_NEW", true));
    assertEquals(profile.forPhase("day", false), profile.forPhase("SOMETHING_NEW", false));
    assertEquals(profile.forPhase("day", false), profile.forPhase(null, false));
    assertEquals(profile.forPhase("night", true), profile.forPhase("", true));
  }

  @Test
  public void allSoundsListsEveryBedAndStingOfThePhases() throws IOException {
    Set<String> sounds = AmbienceProfile.defaults().allSounds();
    assertTrue(sounds.contains("vampyuyu:werewolf.ambience_night"));
    assertTrue(sounds.contains("vampyuyu:werewolf.ambience_finale"));
    assertTrue(sounds.contains("vampyuyu:werewolf.music"));
    assertTrue(sounds.contains("vampyuyu:werewolf.game_over"));
    assertFalse(sounds.contains("minecraft:ambient.cave"));
    assertEquals(
        Set.of(
            "vampyuyu:werewolf.ambience_day",
            "vampyuyu:werewolf.dawn",
            "vampyuyu:werewolf.ambience_night",
            "vampyuyu:werewolf.night"),
        parse(profile()).allSounds());
  }

  @Test
  public void loadRejectsAPhaseWithoutAnAmbientBed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse("{\"day\":{\"sting\":{\"sound\":\"vampyuyu:werewolf.dawn\",\"volume\":0.5},"
            + "\"fallback\":\"minecraft:music.game\"},\"night\":" + BASE_ENTRY + "}"));
  }

  @Test
  public void loadRejectsAMissingSoundKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"loopMillis\":16000,\"volume\":0.4}"))));
  }

  @Test
  public void loadRejectsASoundOutsideTheWerewolfPack() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"minecraft:ambient.cave\",\"loopMillis\":16000,\"volume\":0.4}"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"vampyuyu:werewolf/night\",\"loopMillis\":16000,\"volume\":0.4}"))));
  }

  @Test
  public void loadRejectsANonPositiveLoop() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"vampyuyu:werewolf.vote\",\"loopMillis\":0,\"volume\":0.4}"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"vampyuyu:werewolf.vote\",\"loopMillis\":-5,\"volume\":0.4}"))));
  }

  @Test
  public void loadRejectsAVolumeOutsideZeroToOne() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"vampyuyu:werewolf.vote\",\"loopMillis\":16000,\"volume\":0}"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":" + withAmbient("{\"sound\":\"vampyuyu:werewolf.vote\",\"loopMillis\":16000,\"volume\":1.5}"))));
  }

  @Test
  public void loadRejectsAFallbackThatIsNotAVanillaKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parse(profile("\"vote\":{\"ambient\":{\"sound\":\"vampyuyu:werewolf.vote\",\"loopMillis\":16000,\"volume\":0.4},"
            + "\"sting\":{\"sound\":\"vampyuyu:werewolf.dawn\",\"volume\":0.5},\"fallback\":\"Not A Key\"}")));
  }

  @Test
  public void loadRequiresDayAndNight() {
    assertThrows(IllegalArgumentException.class, () -> parse("{\"night\":" + BASE_ENTRY + "}"));
    assertThrows(IllegalArgumentException.class, () -> parse("{\"day\":" + BASE_ENTRY + "}"));
  }
}
