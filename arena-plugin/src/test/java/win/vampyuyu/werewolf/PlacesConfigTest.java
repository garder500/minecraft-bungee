package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import win.vampyuyu.arena.ArenaConfig;

public class PlacesConfigTest {
  private final ArenaConfig.Spawn at = new ArenaConfig.Spawn(0, 65, 0, 0, 0);

  private Seat seat(String name, boolean sit) {
    return new Seat(name, at, sit);
  }

  @Test
  public void needsAsManySeatsAsPlayersOnceAnySeatIsSet() {
    var places = new Places(List.of(seat("a", true)), at);
    assertNull(places.missing(1));
    assertNotNull(places.missing(2));
    // No seats at all: players simply stay where they spawn, whatever the table size.
    assertNull(Places.defaults().missing(45));
  }

  @Test
  public void oldFilesWithADenOrNightSpotsStillLoad() {
    var json = "{seats:[],circle:{x:0,y:65,z:0,yaw:0,pitch:0},den:{x:1,y:65,z:1,yaw:0,pitch:0},night:[]}";
    var places = new Gson().fromJson(json, Places.class);
    assertNotNull(places.circle());
  }

  @Test
  public void rejectsDuplicateOrInvalidSeatNames() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Places(List.of(seat("a", true), seat("a", false)), null));
    assertThrows(IllegalArgumentException.class, () -> seat("bad name!", true));
    assertThrows(IllegalArgumentException.class, () -> new Seat("ok", null, true));
  }

  @Test
  public void oldJsonLoadsWithDefaultsAndPlacesRoundTrip() throws Exception {
    Gson gson = new Gson();
    String json = Files.readString(Path.of("src/main/resources/presets/werewolf.json"));
    ArenaConfig old = gson.fromJson(json, ArenaConfig.class);
    assertTrue(WerewolfOptions.of(old).places().seats().isEmpty());
    assertEquals(10, old.rewards().participation());
    JsonObject edited = JsonParser.parseString(json).getAsJsonObject();
    var places = new Places(List.of(seat("s1", true), seat("s2", false)), at);
    edited.add("places", gson.toJsonTree(places));
    var c = gson.fromJson(edited, ArenaConfig.class);
    var roundtrip = gson.fromJson(gson.toJson(c), ArenaConfig.class);
    assertEquals(WerewolfOptions.of(c).places(), WerewolfOptions.of(roundtrip).places());
    edited.getAsJsonObject("places").add("circle", gson.toJsonTree(new ArenaConfig.Spawn(1000, 65, 0, 0, 0)));
    // Places outside the zone are refused when the Werewolf options are read, not by the zone itself.
    assertThrows(
        RuntimeException.class, () -> WerewolfOptions.of(gson.fromJson(edited, ArenaConfig.class)));
  }
}
