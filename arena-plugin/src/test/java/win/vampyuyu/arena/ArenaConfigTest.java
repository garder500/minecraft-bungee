package win.vampyuyu.arena;

import static org.junit.Assert.*;

import org.junit.Test;

public class ArenaConfigTest {
  @Test
  public void exactBoundsIncludeVerticalConstraint() {
    var b = new ArenaConfig.Bounds(-20, -64, -20, 20, 320, 20);
    assertTrue(b.contains(0, 65, 0));
    assertFalse(b.contains(21, 65, 0));
    assertFalse(b.contains(0, 320, 0));
    assertFalse(b.contains(0, -65, 0));
    assertFalse(b.contains(19.9, 65, 0));
  }

  @Test
  public void rejectNonFiniteAndUndersizedCorners() {
    assertThrows(
        IllegalArgumentException.class, () -> new ArenaConfig.Bounds(0, 0, 0, Double.NaN, 20, 20));
    assertThrows(
        IllegalArgumentException.class, () -> new ArenaConfig.Bounds(20, 0, 0, 18, 20, 20));
  }

  @Test
  public void normalizeEitherCornerWithoutRoundingNegativeCoordinates() {
    var b = new ArenaConfig.Bounds(1315, 320, -887, 1241, -64, -982);
    assertEquals(new ArenaConfig.Bounds(1241, -64, -982, 1315, 320, -887), b);
    assertTrue(b.contains(1286.5, 91, -944.5));
    assertEquals(-20.75, new ArenaConfig.Bounds(10.5, 20, 5.25, -20.75, 0, -30.5).minX(), 0);
  }

  @Test
  public void overlappingAndAdjacentZones() {
    var b = new ArenaConfig.Bounds(0, 0, 0, 20, 30, 20);
    assertTrue(b.overlaps(new ArenaConfig.Bounds(10, 0, 10, 30, 30, 30)));
    assertFalse(b.overlaps(new ArenaConfig.Bounds(20, 0, 0, 40, 30, 20)));
  }

  private static final String ZONE =
      "{id:'zone',name:'Zone',worldName:'world',preset:'anything',"
          + "bounds:{minX:0,minY:0,minZ:0,maxX:20,maxY:30,maxZ:20},"
          + "spawn:{x:10,y:5,z:10,yaw:0,pitch:0},gameMode:'ADVENTURE',durationSeconds:600,"
          + "minPlayers:2,maxPlayers:8,mySetting:{a:1},other:true}";

  @Test
  public void anyKeyThatIsNotGenericIsAnOpaqueOption() {
    var gson = new com.google.gson.Gson();
    var c = gson.fromJson(ZONE, ArenaConfig.class);
    assertEquals("zone", c.id());
    assertTrue(c.options().has("mySetting"));
    assertTrue(c.options().has("other"));
    assertFalse(c.options().has("minPlayers"));
    var written = gson.toJsonTree(c).getAsJsonObject();
    assertEquals(1, written.getAsJsonObject("mySetting").get("a").getAsInt());
    assertEquals(c, gson.fromJson(written, ArenaConfig.class));
  }

  @Test
  public void optionViewsAreParsedOnce() {
    var c = new com.google.gson.Gson().fromJson(ZONE, ArenaConfig.class);
    int[] parses = {0};
    for (int i = 0; i < 3; i++)
      assertEquals(
          "a",
          c.options()
              .view(
                  String.class,
                  json -> {
                    parses[0]++;
                    return "a";
                  }));
    assertEquals(1, parses[0]);
  }

  @Test
  public void theZoneStillRejectsWhatIsNotAZone() {
    var gson = new com.google.gson.Gson();
    assertThrows(
        RuntimeException.class, () -> gson.fromJson(ZONE.replace("maxPlayers:8", "maxPlayers:99"), ArenaConfig.class));
    assertThrows(
        RuntimeException.class, () -> gson.fromJson(ZONE.replace("id:'zone'", "id:'Bad Id'"), ArenaConfig.class));
    assertThrows(
        RuntimeException.class, () -> gson.fromJson(ZONE.replace("x:10", "x:99"), ArenaConfig.class));
  }
}
