package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.GameHooks;

/**
 * An arena file written before the zone and the game were split (a copy of the one on the creative
 * server) must load unchanged and be saved in a form the old plugin still reads.
 */
public class LegacyArenaFileTest {
  private static final Gson GSON = new Gson();

  private JsonObject legacy() throws Exception {
    try (var in = getClass().getResourceAsStream("/legacy/werewolf-arena.json")) {
      return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private ArenaConfig load() throws Exception {
    var preset = new WerewolfPreset(GameHooks.NONE);
    ArenaConfig read = GSON.fromJson(legacy(), ArenaConfig.class);
    return read.withOptions(
        new ArenaConfig.Options(preset.migrate(read.options().json())));
  }

  @Test
  public void theZoneKeepsItsGenericFields() throws Exception {
    ArenaConfig c = load();
    assertEquals("werewolf", c.id());
    assertEquals("Moonlit village", c.name());
    assertEquals("world", c.worldName());
    assertEquals("werewolf", c.preset());
    assertEquals(new ArenaConfig.Bounds(1241, -64, -982, 1315, 320, -887), c.bounds());
    assertEquals(1286.5, c.spawn().x(), 0);
    assertEquals(4, c.minPlayers());
    assertEquals(20, c.maxPlayers());
    assertEquals(1800, c.durationSeconds());
    assertTrue(c.allowDoors());
    assertFalse(c.allowPvP());
    // The file has no rewards: the defaults apply, as before.
    assertEquals(10, c.rewards().participation());
    assertEquals(5, c.rewards().victory());
  }

  @Test
  public void gameSettingsMoveIntoOpaqueOptions() throws Exception {
    ArenaConfig c = load();
    assertTrue(c.options().has("werewolf"));
    assertTrue(c.options().has("freeplay"));
    assertTrue(c.options().has("discussionSeconds"));
    // Nothing generic leaks into the options.
    assertFalse(c.options().has("id"));
    assertFalse(c.options().has("minPlayers"));
  }

  @Test
  public void werewolfReadsItsTimersRulesAndEmptyPlaces() throws Exception {
    WerewolfOptions options = WerewolfOptions.of(load());
    assertEquals(60, options.discussionSeconds());
    assertEquals(30, options.voteSeconds());
    assertEquals(30, options.nightSeconds());
    assertEquals(15, options.rules().cupidSeconds());
    assertTrue(options.rules().roles().isEmpty());
    assertTrue(options.rules().ambienceEnabled());
    assertTrue(options.places().seats().isEmpty());
    assertNull(options.places().circle());
  }

  @Test
  public void everyOldKeyIsStillWrittenAtTheTopLevel() throws Exception {
    JsonObject before = legacy();
    JsonObject after = GSON.toJsonTree(load()).getAsJsonObject();
    for (var entry : before.entrySet()) {
      assertTrue("missing " + entry.getKey(), after.has(entry.getKey()));
      if (!entry.getKey().equals("werewolf"))
        assertEquals(entry.getKey(), entry.getValue(), after.get(entry.getKey()));
    }
    // The block the old plugin edits gained its defaults but kept what the file said.
    assertEquals(
        before.getAsJsonObject("werewolf").get("cupidSeconds"),
        after.getAsJsonObject("werewolf").get("cupidSeconds"));
    assertTrue(after.has("places"));
    assertFalse("options are flat on disk", after.has("options"));
  }

  @Test
  public void savingAndReadingAgainChangesNothing() throws Exception {
    ArenaConfig once = load();
    ArenaConfig twice = GSON.fromJson(GSON.toJson(once), ArenaConfig.class);
    assertEquals(GSON.toJsonTree(once), GSON.toJsonTree(twice));
    assertEquals(WerewolfOptions.of(once), WerewolfOptions.of(twice));
  }

  @Test
  public void aNestedOptionsObjectIsAcceptedAndAFlatKeyWins() throws Exception {
    JsonObject file = legacy();
    JsonObject nested = new JsonObject();
    nested.addProperty("discussionSeconds", 99);
    nested.addProperty("futureSetting", 45);
    file.add("options", nested);
    ArenaConfig c = GSON.fromJson(file, ArenaConfig.class);
    var preset = new WerewolfPreset(GameHooks.NONE);
    c = c.withOptions(new ArenaConfig.Options(preset.migrate(c.options().json())));
    assertEquals("the flat key wins", 60, WerewolfOptions.of(c).discussionSeconds());
    assertEquals("a nested key is kept", 45, c.options().json().get("futureSetting").getAsInt());
  }

  @Test
  public void werewolfRefusesWhatTheOldConstructorRefused() throws Exception {
    JsonObject file = legacy();
    file.addProperty("allowPvP", true);
    ArenaConfig pvp = GSON.fromJson(file, ArenaConfig.class);
    var error = assertThrows(IllegalArgumentException.class, () -> WerewolfOptions.of(pvp));
    assertEquals("Werewolf requires PvP disabled", error.getMessage());
    JsonObject few = legacy();
    few.addProperty("minPlayers", 3);
    ArenaConfig small = GSON.fromJson(few, ArenaConfig.class);
    assertThrows(IllegalArgumentException.class, () -> WerewolfOptions.of(small));
    JsonObject fast = legacy();
    fast.addProperty("voteSeconds", 1);
    ArenaConfig quick = GSON.fromJson(fast, ArenaConfig.class);
    assertThrows(IllegalArgumentException.class, () -> WerewolfOptions.of(quick));
  }
}
