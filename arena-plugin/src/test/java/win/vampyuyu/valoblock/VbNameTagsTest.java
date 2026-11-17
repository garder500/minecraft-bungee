package win.vampyuyu.valoblock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VbNameTagsTest {
  @Test
  void aSquadKeepsItsTeamWhateverSideItPlays() {
    var score = new VbScore(VbData.rules());
    for (int round = 1; round <= 30; round++) {
      // the two squads are on opposite sides every round, and their team names never change
      assertNotEquals(score.sideOf(0, round), score.sideOf(1, round));
      assertEquals("vbxa", VbNameTags.teamName("x", 0));
      assertEquals("vbxb", VbNameTags.teamName("x", 1));
    }
    assertNotEquals(VbNameTags.teamName("x", 0), VbNameTags.teamName("x", 1));
    assertNotEquals(VbNameTags.teamName("x", 0), VbNameTags.teamName("y", 0));
  }

  @Test
  void onlyAlliesSeeATag() {
    assertTrue(VbNameTags.shows(0, 0));
    assertTrue(VbNameTags.shows(1, 1));
    assertFalse(VbNameTags.shows(0, 1));
    assertFalse(VbNameTags.shows(1, 0));
  }
}
