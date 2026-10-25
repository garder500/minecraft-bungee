package win.vampyuyu.werewolf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import win.vampyuyu.werewolf.ambience.AmbienceProfile;
import win.vampyuyu.werewolf.roles.RoleInfo;
import win.vampyuyu.werewolf.sky.DecorConfig;

public class DataFilesTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void on() {
    DataFiles.init(tmp.getRoot().toPath());
  }

  @After
  public void off() {
    DataFiles.init(null);
    RoleInfo.reload();
    AmbienceProfile.reload();
  }

  private Path put(String resource, String text) throws Exception {
    Path file = tmp.getRoot().toPath().resolve("overrides").resolve(resource.substring(1));
    Files.createDirectories(file.getParent());
    return Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  @Test
  public void roleOverrideAppliesOnReloadAndABrokenFileKeepsTheOldRoles() throws Exception {
    assertEquals("Seer", RoleInfo.of("SEER").name());
    Path file = put("/roles/SEER.json", "{\"name\":\"Oracle\",\"team\":\"village\",\"max\":1}");
    assertEquals("Seer", RoleInfo.of("SEER").name()); // nothing changes before a reload
    RoleInfo.reload();
    assertEquals("Oracle", RoleInfo.of("SEER").name());

    Files.writeString(file, "{not json");
    assertThrows(IllegalArgumentException.class, RoleInfo::reload);
    assertEquals("Oracle", RoleInfo.of("SEER").name());

    Files.delete(file);
    RoleInfo.reload();
    assertEquals("Seer", RoleInfo.of("SEER").name());
  }

  @Test
  public void decorOverrideWinsAndBrokenOneThrows() throws Exception {
    String json = new String(DecorConfig.class.getResourceAsStream(DecorConfig.DEFAULT_RESOURCE).readAllBytes(), StandardCharsets.UTF_8);
    int shipped = DecorConfig.loadDefault().maxBlocks();
    put(DecorConfig.DEFAULT_RESOURCE, json.replaceFirst("\"maxBlocks\"\\s*:\\s*\\d+", "\"maxBlocks\": 17000"));
    assertEquals(17000, DecorConfig.loadDefault().maxBlocks());
    put(DecorConfig.DEFAULT_RESOURCE, "{\"bogus\": 1}");
    assertThrows(IllegalArgumentException.class, DecorConfig::loadDefault);
    Files.delete(tmp.getRoot().toPath().resolve("overrides").resolve(DecorConfig.DEFAULT_RESOURCE.substring(1)));
    assertEquals(shipped, DecorConfig.loadDefault().maxBlocks());
  }

  @Test
  public void ambienceReloadKeepsTheCurrentProfileWhenTheFileIsBroken() throws Exception {
    AmbienceProfile before = AmbienceProfile.reload();
    put("/werewolf/ambience.json", "{\"day\": 1}");
    assertThrows(RuntimeException.class, AmbienceProfile::reload);
    assertSame(before, AmbienceProfile.defaults());
  }

  @Test
  public void overridesCannotEscapeTheirFolder() {
    assertEquals(null, DataFiles.override("/../secret.json"));
  }
}
