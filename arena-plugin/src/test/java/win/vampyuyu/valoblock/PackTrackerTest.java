package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.Test;

public class PackTrackerTest {
  private static final String SHA = "0691dae7af81aea015365bf57a1c6b3de2997a8a";
  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  @Test
  public void theIdIsStablePerRevisionAndNotTheBasePacksId() {
    assertEquals(PackTracker.packId(SHA), PackTracker.packId(SHA));
    assertNotEquals(PackTracker.packId(SHA), PackTracker.packId(SHA.replace('0', '1')));
    assertNotEquals(
        UUID.nameUUIDFromBytes(("vampyuyu-menus:" + SHA).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        PackTracker.packId(SHA)); // both packs live on the client at once
    assertThrows(IllegalArgumentException.class, () -> PackTracker.packId("nope"));
  }

  @Test
  public void theUrlIsTheHashAddressedRouteOfThePanel() {
    assertEquals("https://play.example/pack/" + SHA + ".zip", PackTracker.packUrl("https://play.example//", SHA));
    assertThrows(IllegalArgumentException.class, () -> PackTracker.packUrl("ftp://x", SHA));
  }

  @Test
  public void declinedFailedAndDroppedAnswersEjectProgressDoesNot() {
    for (Status s : new Status[] {Status.DECLINED, Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED})
      assertTrue(s.name(), PackTracker.ejects(s));
    for (Status s : new Status[] {Status.ACCEPTED, Status.DOWNLOADED, Status.SUCCESSFULLY_LOADED})
      assertFalse(s.name(), PackTracker.ejects(s));
  }

  @Test
  public void aPlayerIsOfferedTheOnceAndLoadsIt() {
    var t = new PackTracker();
    assertTrue(t.offer(alice));
    assertFalse(t.offer(alice)); // never a second offer
    assertTrue(t.holds(alice));
    assertEquals(PackTracker.Verdict.WAIT, t.status(alice, Status.ACCEPTED));
    assertEquals(PackTracker.Verdict.WAIT, t.status(alice, Status.DOWNLOADED));
    assertFalse(t.loaded(alice));
    assertEquals(PackTracker.Verdict.READY, t.status(alice, Status.SUCCESSFULLY_LOADED));
    assertTrue(t.loaded(alice));
  }

  @Test
  public void aRefusalIsReportedAndForgetsTheLoadedFlag() {
    var t = new PackTracker();
    t.offer(alice);
    t.status(alice, Status.SUCCESSFULLY_LOADED);
    assertEquals(PackTracker.Verdict.REFUSED, t.status(alice, Status.FAILED_RELOAD));
    assertFalse(t.loaded(alice));
  }

  @Test
  public void answersFromAPlayerWhoWasNeverSentThePackAreIgnored() {
    var t = new PackTracker();
    assertEquals(PackTracker.Verdict.WAIT, t.status(bob, Status.DECLINED));
    assertFalse(t.loaded(bob));
  }

  @Test
  public void releaseRemovesOnceAndForgetsEverything() {
    var t = new PackTracker();
    t.offer(alice);
    t.status(alice, Status.SUCCESSFULLY_LOADED);
    assertTrue(t.release(alice)); // the pack must be removed from the client
    assertFalse(t.release(alice)); // quit after leave: nothing to remove twice
    assertFalse(t.holds(alice));
    assertFalse(t.loaded(alice));
    assertFalse(t.release(bob));
    assertTrue(t.offer(alice)); // a later match offers it again
  }

  @Test
  public void twoPlayersDoNotShareState() {
    var t = new PackTracker();
    t.offer(alice);
    t.offer(bob);
    t.status(alice, Status.SUCCESSFULLY_LOADED);
    assertEquals(PackTracker.Verdict.REFUSED, t.status(bob, Status.DECLINED));
    assertTrue(t.loaded(alice));
    assertTrue(t.holds(alice));
  }
}
