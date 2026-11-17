package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import win.vampyuyu.werewolf.DataFiles;

public class VbDataTest {
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

  private Path put(String resource, String text) throws Exception {
    Path file = tmp.getRoot().toPath().resolve("overrides").resolve(resource.substring(1));
    Files.createDirectories(file.getParent());
    return Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  @Test
  public void shippedDefaultsMatchTheDesign() {
    VbRules r = VbData.rules();
    assertEquals(5, r.teamSize());
    assertEquals(7, r.winScore());
    assertEquals(6, r.swapAfter());
    assertEquals(60, r.agentSelectSeconds());
    assertEquals(25, r.buySeconds());
    assertEquals(100, r.combatSeconds());
    assertEquals(40, r.fuseSeconds());
    assertEquals(6, r.roundEndSeconds());
    assertEquals(7.0, r.sealSeconds(), 0);
    assertEquals(3.5, r.sealCheckpointSeconds(), 0);
    assertEquals(100, r.maxHealth());
    assertEquals(java.util.List.of(25, 50), r.shields());
    VbRules.Economy e = r.economy();
    assertEquals(800, e.pistolCredits());
    assertEquals(3000, e.roundWon());
    assertEquals(1900, e.lossBonus(1));
    assertEquals(2400, e.lossBonus(2));
    assertEquals(2900, e.lossBonus(3));
    assertEquals(2900, e.lossBonus(9));
    assertEquals(0, e.lossBonus(0));
    assertEquals(200, e.kill());
    assertEquals(300, e.plantTeam());
    assertEquals(9000, e.cap());
    assertEquals(2, VbData.sites().size());
  }

  @Test
  public void overrideWinsOnReloadAndBrokenFileKeepsTheOldData() throws Exception {
    String json = new String(VbData.class.getResourceAsStream(VbData.MATCH).readAllBytes(), StandardCharsets.UTF_8);
    Path file = put(VbData.MATCH, json.replace("\"buySeconds\": 25", "\"buySeconds\": 30"));
    assertEquals(25, VbData.rules().buySeconds()); // nothing changes before a reload
    VbData.reload();
    assertEquals(30, VbData.rules().buySeconds());
    assertEquals(java.util.List.of("valoblock/match.json"), VbData.overrides());

    Files.writeString(file, "{not json");
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals(30, VbData.rules().buySeconds());

    Files.writeString(file, json.replace("\"sealCheckpointSeconds\": 3.5", "\"sealCheckpointSeconds\": 9"));
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals(30, VbData.rules().buySeconds());

    Files.delete(file);
    VbData.reload();
    assertEquals(25, VbData.rules().buySeconds());
  }

  @Test
  public void brokenSitesFileIsRefusedAsAWhole() throws Exception {
    put(VbData.SITES, "{\"sites\": []}");
    assertThrows(IllegalArgumentException.class, VbData::reload);
    assertEquals(2, VbData.sites().size());
  }

  @Test
  public void siteContainsItsBox() {
    VbSite a = VbData.sites().get(0);
    assertTrue(a.contains(-25, 62, 25));
    assertFalse(a.contains(0, 62, 25));
    assertEquals("A", a.id());
  }
}
