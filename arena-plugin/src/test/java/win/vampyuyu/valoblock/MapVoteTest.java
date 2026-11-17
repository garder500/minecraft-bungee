package win.vampyuyu.valoblock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import win.vampyuyu.valoblock.MapVote.How;

class MapVoteTest {
  private final MapVote vote = new MapVote(List.of("hourfall", "rootvault", "stormbreak"));
  private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

  private MapVote open() {
    vote.open(1000);
    return vote;
  }

  @Test
  void theMajorityWins() {
    open();
    vote.cast(a, "rootvault");
    vote.cast(b, "rootvault");
    vote.cast(c, "stormbreak");
    for (long seed = 0; seed < 20; seed++) {
      MapVote.Result r = vote.resolve(new Random(seed), null);
      assertEquals("rootvault", r.map());
      assertEquals(How.VOTES, r.how());
      assertEquals(2, r.votes());
    }
  }

  @Test
  void aTieIsDrawnAmongTheTiedMapsOnly() {
    open();
    vote.cast(a, "hourfall");
    vote.cast(b, "stormbreak");
    Set<String> seen = new HashSet<>();
    Random rnd = new Random(42); // one generator: the first draw of nearby seeds is correlated
    for (int i = 0; i < 100; i++) {
      MapVote.Result r = vote.resolve(rnd, null);
      assertEquals(How.TIE, r.how());
      seen.add(r.map());
    }
    assertEquals(Set.of("hourfall", "stormbreak"), seen, "both tied maps can win, the third never does");
  }

  @Test
  void noVoteAtAllIsADrawAmongEveryMap() {
    open();
    Set<String> seen = new HashSet<>();
    Random rnd = new Random(42); // one generator: the first draw of nearby seeds is correlated
    for (int i = 0; i < 100; i++) {
      MapVote.Result r = vote.resolve(rnd, null);
      assertEquals(How.RANDOM, r.how());
      seen.add(r.map());
    }
    assertEquals(Set.copyOf(vote.maps()), seen, "not always the first map");
  }

  @Test
  void aForcedMapOverridesTheVotes() {
    open();
    vote.cast(a, "rootvault");
    vote.cast(b, "rootvault");
    MapVote.Result r = vote.resolve(new Random(1), "hourfall");
    assertEquals("hourfall", r.map());
    assertEquals(How.FORCED, r.how());
    assertEquals("hourfall", new MapVote(List.of("hourfall", "rootvault")).resolve(new Random(2), "hourfall").map(), "even with no ballot at all");
  }

  @Test
  void aVoterCanChangeTheirVoteAndOnlyTheLastCounts() {
    open();
    assertTrue(vote.cast(a, "hourfall"));
    assertTrue(vote.cast(a, "stormbreak"));
    assertEquals("stormbreak", vote.choice(a));
    assertEquals(0, vote.counts().get("hourfall"));
    assertEquals(1, vote.counts().get("stormbreak"));
    assertEquals("stormbreak", vote.resolve(new Random(3), null).map());
  }

  @Test
  void anUnknownMapOrAClosedBallotTakesNoVote() {
    assertFalse(vote.cast(a, "hourfall"), "not opened yet");
    open();
    assertFalse(vote.cast(a, "atlantis"));
    assertTrue(vote.cast(a, "hourfall"));
    vote.close();
    assertFalse(vote.cast(b, "hourfall"));
    assertEquals(0, vote.counts().get("hourfall"), "closing drops the votes");
    assertEquals(0, vote.ballot());
  }

  @Test
  void aLeavingVoterIsRetractedAndEveryMapIsCounted() {
    open();
    vote.cast(a, "rootvault");
    vote.cast(b, "rootvault");
    vote.retract(a);
    assertEquals(java.util.Map.of("hourfall", 0, "rootvault", 1, "stormbreak", 0), vote.counts());
    assertEquals(List.of("rootvault"), vote.leaders());
    vote.retract(b);
    assertEquals(List.of(), vote.leaders());
  }

  @Test
  void eachBallotIsANewGenerationAndTheClockCountsDown() {
    assertEquals(0, vote.ballot());
    vote.open(1000);
    int first = vote.ballot();
    assertTrue(first > 0);
    assertEquals(MapVote.WINDOW_SECONDS, vote.secondsLeft(1000));
    assertEquals(MapVote.WINDOW_SECONDS - 30, vote.secondsLeft(31_000));
    assertEquals(0, vote.secondsLeft(1000 + MapVote.WINDOW_SECONDS * 1000L + 5000));
    vote.close();
    vote.open(5000);
    assertTrue(vote.ballot() > first, "a new match, a new ballot: the menu is offered again");
  }

  @Test
  void theTooltipShowsTheMapTheRealmTheSizeTheMechanicAndTheVotes() {
    VbMap map = VbData.maps().get("hourfall");
    var lines = MapVoteMenu.tooltip(map, 2, false, true);
    String all = lines.stream().map(AgentText.Line::text).reduce("", (x, y) -> x + "\n" + y);
    assertTrue(all.contains(map.name()) && all.contains(map.realm()) && all.contains(map.width() + " x " + map.depth()));
    assertTrue(all.contains("Time Tide") && all.contains("Votes: 2 (leading)") && all.contains("Click to vote"));
    assertTrue(MapVoteMenu.tooltip(map, 1, true, false).stream().anyMatch(l -> l.text().startsWith("Your vote")));
    assertEquals(0, MapVoteMenu.mapAt(2 * 9, 3));
    assertEquals(1, MapVoteMenu.mapAt(4 * 9 + 4, 3));
    assertEquals(2, MapVoteMenu.mapAt(3 * 9 + 8, 3));
    assertEquals(-1, MapVoteMenu.mapAt(4, 3), "the title row");
    assertEquals(-1, MapVoteMenu.mapAt(9, 3), "above the tiles");
    assertEquals(-1, MapVoteMenu.mapAt(5 * 9, 3), "under the tiles");
  }
}
