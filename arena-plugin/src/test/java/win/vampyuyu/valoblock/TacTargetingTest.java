package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class TacTargetingTest {
  private static VbAbility ability(String agent, String id) {
    for (VbAbility a : VbData.agents().get(agent).abilities()) if (a.id().equals(id)) return a;
    throw new AssertionError(agent + "/" + id);
  }

  @Test
  public void theFarPlacedAbilitiesOpenTheMapAndTheRestStayInTheWorld() {
    Set<String> targeted = new TreeSet<>();
    for (String agent : VbData.agents().ids())
      for (VbAbility a : VbData.agents().get(agent).abilities()) if (TacTargeting.of(a.id()) != null) targeted.add(a.id());
    assertEquals(Set.of("sand_veil", "rot_cloud", "miasma_wall", "storm_call", "undertow"), targeted);
    assertNull(TacTargeting.of("rewind"));
    assertNull(TacTargeting.of("hourglass_trap"));
    assertNull(TacTargeting.of("ink_burst"));
  }

  @Test
  public void spotsPerAbility() {
    assertEquals(1, TacTargeting.spots(ability("sahrin", "sand_veil")));
    assertEquals(1, TacTargeting.spots(ability("murk", "rot_cloud")));
    assertEquals(2, TacTargeting.spots(ability("murk", "miasma_wall")));
    assertEquals(3, TacTargeting.spots(ability("gale", "storm_call")));
    assertEquals(1, TacTargeting.spots(ability("nacre", "undertow")));
    assertEquals(0, TacTargeting.spots(ability("sahrin", "rewind")));
  }

  @Test
  public void aRangeComesFromTheAgentDataOtherwiseTheWholeMap() {
    assertEquals(30, TacTargeting.range(ability("gale", "storm_call")), 1e-9);
    assertTrue(Double.isInfinite(TacTargeting.range(ability("sahrin", "sand_veil"))));
    assertTrue(Double.isInfinite(TacTargeting.range(ability("murk", "miasma_wall"))));
    assertTrue(TacTargeting.inRange(30, 0, 0, 18, 24));
    assertFalse(TacTargeting.inRange(30, 0, 0, 25, 25));
  }

  @Test
  public void theTitleNamesTheAbilityTheStepAndTheCharges() {
    assertEquals("Sand Veil  x2", TacTargeting.title(ability("sahrin", "sand_veil"), 0, 2));
    assertEquals("Miasma Wall end 1/2  x1", TacTargeting.title(ability("murk", "miasma_wall"), 0, 1));
    assertEquals("Miasma Wall end 2/2  x1", TacTargeting.title(ability("murk", "miasma_wall"), 1, 1));
    assertEquals("Storm Call spot 3/3  x1", TacTargeting.title(ability("gale", "storm_call"), 2, 1));
  }
}
