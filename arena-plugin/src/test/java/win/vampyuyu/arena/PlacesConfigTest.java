package win.vampyuyu.arena;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;

public class PlacesConfigTest {
  private final ArenaConfig.Spawn at = new ArenaConfig.Spawn(0, 65, 0, 0, 0);

  private ArenaConfig.Seat seat(String name, boolean sit) {
    return new ArenaConfig.Seat(name, at, sit);
  }

  @Test
  public void needsAsManySeatsAsPlayersOnceAnySeatIsSet() {
    var places = new ArenaConfig.Places(List.of(seat("a", true)), at, at, List.of());
    assertNull(places.missing(1));
    assertNotNull(places.missing(2));
    // No seats at all: players simply stay where they spawn, whatever the table size.
    assertNull(ArenaConfig.Places.defaults().missing(45));
    var night = new ArenaConfig.Places(List.of(seat("a", true), seat("b", false)), at, at, List.of(at));
    assertNotNull(night.missing(2));
  }

  @Test
  public void rejectsDuplicateOrInvalidSeatNames() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArenaConfig.Places(List.of(seat("a", true), seat("a", false)), null, null, null));
    assertThrows(IllegalArgumentException.class, () -> seat("bad name!", true));
    assertThrows(IllegalArgumentException.class, () -> new ArenaConfig.Seat("ok", null, true));
  }

  @Test
  public void oldJsonLoadsWithDefaultsAndPlacesRoundTrip() throws Exception {
    Gson gson = new Gson();
    String json = Files.readString(Path.of("src/main/resources/presets/werewolf.json"));
    ArenaConfig old = gson.fromJson(json, ArenaConfig.class);
    assertTrue(old.places().seats().isEmpty());
    assertEquals(10, old.rewards().participation());
    JsonObject edited = JsonParser.parseString(json).getAsJsonObject();
    var places = new ArenaConfig.Places(List.of(seat("s1", true), seat("s2", false)), at, at, List.of());
    edited.add("places", gson.toJsonTree(places));
    var c = gson.fromJson(edited, ArenaConfig.class);
    var roundtrip = gson.fromJson(gson.toJson(c), ArenaConfig.class);
    assertEquals(c.places(), roundtrip.places());
    edited.getAsJsonObject("places").add("den", gson.toJsonTree(new ArenaConfig.Spawn(1000, 65, 0, 0, 0)));
    assertThrows(RuntimeException.class, () -> gson.fromJson(edited, ArenaConfig.class));
  }
}
