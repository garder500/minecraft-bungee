package win.vampyuyu.valoblock;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class VbNoticeTest {
  @Test
  void aNoticeIsASubtitleWithAnEmptyTitle() {
    var t = VbNotice.title(Component.text("Rift Seed planted", NamedTextColor.LIGHT_PURPLE));
    assertEquals(Component.empty(), t.title());
    assertEquals("Rift Seed planted", PlainTextComponentSerializer.plainText().serialize(t.subtitle()));
    assertTrue(t.times().stay().toMillis() <= 2000);
  }

  @Test
  void theActionBarTextIsOnlyTheEliminatedLine() {
    assertEquals("Eliminated: spectating", PlainTextComponentSerializer.plainText().serialize(VbNotice.eliminated()));
  }

  @Test
  void botTagIsOneColouredNamePerTeam() {
    assertEquals("[BOT] Gale", PlainTextComponentSerializer.plainText().serialize(BotTag.text("[BOT] Gale", 0)));
    assertNotEquals(BotTag.colour(0), BotTag.colour(1));
    assertEquals(BotTag.colour(1), BotTag.colour(3));
    assertEquals(BotTag.colour(0), BotTag.text("x", 0).color());
  }
}
