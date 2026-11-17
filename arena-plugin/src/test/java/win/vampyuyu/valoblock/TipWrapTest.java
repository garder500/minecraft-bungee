package win.vampyuyu.valoblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TipWrapTest {
  @Test
  void linesStayWithinTheWidthAndWordsAreNeverCut() {
    String text = "One body shot kills. Hold right-click to aim, release to fire, F to cancel (x3.5, slowed)";
    List<String> lines = TipWrap.wrap(text, TipWrap.WIDTH);
    assertTrue(lines.size() > 1);
    for (String l : lines) assertTrue(l.length() <= TipWrap.WIDTH, l);
    assertEquals(text, String.join(" ", lines));
  }

  @Test
  void aShortLineIsKeptAndALongWordStaysWhole() {
    assertEquals(List.of("Price: 4,500 Shards"), TipWrap.wrap("Price: 4,500 Shards", TipWrap.WIDTH));
    String word = "x".repeat(60);
    assertEquals(List.of("a", word, "b"), TipWrap.wrap("a " + word + " b", TipWrap.WIDTH));
    assertEquals(List.of(""), TipWrap.wrap("", TipWrap.WIDTH));
  }
}
