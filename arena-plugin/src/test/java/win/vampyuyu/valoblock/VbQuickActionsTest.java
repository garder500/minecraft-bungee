package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import win.vampyuyu.valoblock.VbQuickActions.Action;

public class VbQuickActionsTest {
  @Test
  public void buttonsRunAllowedCommands() {
    VbHooks hooks = new VbHooks(null);
    List<VbQuickActions.Button> b = VbQuickActions.buttons();
    assertEquals(List.of(Action.SHOP, Action.AGENT, Action.MAP, Action.VOTE), b.stream().map(VbQuickActions.Button::action).toList());
    for (VbQuickActions.Button x : b) assertTrue(x.command(), hooks.allowsCommand(x.command()));
  }

  @Test
  public void availabilityByPhase() {
    assertNotNull(VbQuickActions.unavailable(Action.SHOP, false, VbPhase.BUY, false));
    assertNull(VbQuickActions.unavailable(Action.SHOP, true, VbPhase.BUY, false));
    assertNotNull(VbQuickActions.unavailable(Action.SHOP, true, VbPhase.COMBAT, false));
    assertNull(VbQuickActions.unavailable(Action.AGENT, true, VbPhase.AGENT_SELECT, false));
    assertNotNull(VbQuickActions.unavailable(Action.AGENT, true, VbPhase.BUY, false));
    assertNull(VbQuickActions.unavailable(Action.VOTE, true, VbPhase.QUEUE, true));
    assertNotNull(VbQuickActions.unavailable(Action.VOTE, true, VbPhase.QUEUE, false));
    assertNull(VbQuickActions.unavailable(Action.MAP, true, VbPhase.COMBAT, false));
    assertNotNull(VbQuickActions.unavailable(Action.MAP, true, VbPhase.BUY, false));
  }

  @Test
  public void dialogJsonIsValidAndComplete() {
    JsonObject d = JsonParser.parseString(VbQuickActions.dialogJson()).getAsJsonObject();
    assertEquals("minecraft:multi_action", d.get("type").getAsString());
    JsonArray actions = d.getAsJsonArray("actions");
    assertEquals(4, actions.size());
    for (int i = 0; i < 4; i++) {
      JsonObject act = actions.get(i).getAsJsonObject().getAsJsonObject("action");
      assertEquals("minecraft:run_command", act.get("type").getAsString());
      assertEquals(VbQuickActions.buttons().get(i).command().substring(1), act.get("command").getAsString());
    }
    JsonObject tag = JsonParser.parseString(VbQuickActions.tagJson()).getAsJsonObject();
    assertEquals(VbQuickActions.DIALOG_ID, tag.getAsJsonArray("values").get(0).getAsString());
    JsonParser.parseString(VbQuickActions.mcmeta());
  }

  @Test
  public void writeIsIdempotent() throws Exception {
    Path dir = Files.createTempDirectory("vbqa").resolve("pack");
    assertTrue(VbQuickActions.write(dir));
    assertFalse(VbQuickActions.write(dir));
    Files.writeString(dir.resolve("data/minecraft/tags/dialog/quick_actions.json"), "{}");
    assertTrue(VbQuickActions.write(dir));
    assertEquals(VbQuickActions.tagJson(), Files.readString(dir.resolve("data/minecraft/tags/dialog/quick_actions.json")));
  }
}
