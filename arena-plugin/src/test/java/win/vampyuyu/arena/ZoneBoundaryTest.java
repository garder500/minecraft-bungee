package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The arena package is the generic zone engine: it must never depend on a game. Only the plugin
 * entry point wires the Werewolf module in (and registers its preset).
 */
public class ZoneBoundaryTest {
  private static final Path ZONE = Path.of("src/main/java/win/vampyuyu/arena");
  private static final Set<String> WIRING = Set.of("NexusArenaEngine.java");

  @Test
  public void noZoneClassMentionsTheWerewolfPackage() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.list(ZONE)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        if (WIRING.contains(file.getFileName().toString())) continue;
        if (Files.readString(file).contains("win.vampyuyu.werewolf"))
          offenders.add(file.getFileName().toString());
      }
    }
    assertTrue("zone classes depending on the werewolf package: " + offenders, offenders.isEmpty());
  }

  @Test
  public void theZonePackageIsWhereItIsExpected() throws IOException {
    try (Stream<Path> files = Files.list(ZONE)) {
      assertTrue(files.anyMatch(p -> p.getFileName().toString().equals("ArenaManager.java")));
    }
  }

  @Test
  public void theWiringOnlyImportsTheModuleEntryPoint() throws IOException {
    for (String line : Files.readAllLines(ZONE.resolve("NexusArenaEngine.java")))
      if (line.startsWith("import win.vampyuyu.werewolf."))
        assertEquals("import win.vampyuyu.werewolf.WerewolfModule;", line);
  }
}
