package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import win.vampyuyu.werewolf.DataFiles;

public class VbAgentsTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void on() {
    DataFiles.init(tmp.getRoot().toPath());
    VbData.reload();
  }

  @After
  public void off() {
    DataFiles.init(null);
    VbData.reload();
  }

  private String shipped(String resource) throws Exception {
    return new String(VbData.class.getResourceAsStream(resource).readAllBytes(), StandardCharsets.UTF_8);
  }

  private Path put(String resource, String text) throws Exception {
    Path file = tmp.getRoot().toPath().resolve("overrides").resolve(resource.substring(1));
    Files.createDirectories(file.getParent());
    return Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  @Test
  public void anAgentFileOverrideWinsAndIsListed() throws Exception {
    put(VbData.agentFile("cinder"), shipped(VbData.agentFile("cinder")).replace("Slag Pot", "Molten Pot"));
    VbData.reload();
    assertEquals("Molten Pot", VbData.agents().get("cinder").ability(4).name());
    assertEquals(List.of("valoblock/agents/cinder.json"), VbData.overrides());
  }

  @Test
  public void aBrokenAgentFileKeepsTheOldDataForEverything() throws Exception {
    Path f = put(VbData.agentFile("murk"), shipped(VbData.agentFile("murk")).replace("\"slot\": 7", "\"slot\": 9"));
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals("Bog Tide", VbData.agents().get("murk").ability(7).name());
    Files.writeString(f, "{ nope");
    assertThrows(IllegalArgumentException.class, VbData::reload);
    // a wrong face pixel
    Files.writeString(f, shipped(VbData.agentFile("murk")).replace("\"hhhhhhhh\"", "\"hhhhhhhz\""));
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals(8, VbData.agents().ids().size());
    Files.delete(f);
    VbData.reload();
  }

  @Test
  public void anIndexNamingAMissingFileIsRefused() throws Exception {
    put(VbData.AGENTS_INDEX, shipped(VbData.AGENTS_INDEX).replace("\"murk\"", "\"ghost\""));
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals(8, VbData.agents().ids().size());
  }

  @Test
  public void anAgentWithTheWrongNumberOfAbilitiesIsRefused() throws Exception {
    String json = shipped(VbData.agentFile("gale"));
    int cut = json.indexOf("\"slot\": 7");
    int start = json.lastIndexOf("{", cut);
    int end = json.indexOf("\n    }", cut) + "\n    }".length();
    put(VbData.agentFile("gale"), json.substring(0, start).replaceAll(",\\s*$", "") + json.substring(end));
    assertThrows(IllegalArgumentException.class, VbData::reload);
  }

  @Test
  public void eachRoleHasTwoAgentsAndTheTooltipTextComesFromTheData() {
    Map<String, Integer> perRole = new TreeMap<>();
    for (VbAgent a : VbData.agents().all()) perRole.merge(a.role(), 1, Integer::sum);
    assertEquals(Map.of("Seer", 2, "Vanguard", 2, "Warden", 2, "Weaver", 2), perRole);
    VbAgent vesper = VbData.agents().get("vesper");
    List<AgentText.Line> t = AgentText.tooltip(vesper, "Click to select, then Lock in.");
    assertEquals("Vesper", t.get(0).text());
    assertEquals("Seer · Ethereal Necropolis", t.get(1).text());
    assertEquals("\"The dead tell me where the living hide.\"", t.get(2).text());
    assertEquals("[4] Echo Step · 200", t.get(3).text());
    assertEquals("#6affd8", t.get(3).color());
    assertEquals("[7] Verdict · 8 pts", t.get(9).text());
    assertEquals("#ffd36a", t.get(9).color());
    assertEquals("Click to select, then Lock in.", t.get(t.size() - 1).text());
    assertEquals("Choose your agent · 0:44", AgentText.title(44));
    assertEquals("Choose your agent · 1:00", AgentText.title(60));
    assertEquals("Choose your agent · 0:05", AgentText.title(5));
  }
}
