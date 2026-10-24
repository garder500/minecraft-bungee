package win.vampyuyu.arena;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArenaHttpGameActionTest {
  @Test
  public void gameSessionActionsMapToTheZoneOperations() {
    assertEquals("arena-save", ArenaHttpApi.gameAction("ww-config-save"));
    assertEquals("arena-start", ArenaHttpApi.gameAction("ww-start"));
    assertEquals("arena-stop", ArenaHttpApi.gameAction("ww-stop"));
    assertEquals("arena-practice-start", ArenaHttpApi.gameAction("ww-practice-start"));
    assertEquals("arena-reload", ArenaHttpApi.gameAction("arena-reload"));
    assertEquals("ww-config-get", ArenaHttpApi.gameAction("ww-config-get")); // rejected as unsupported
  }
}
