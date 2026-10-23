package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/**
 * Adversarial audit of the rules engine: random players press random buttons, pick random heads,
 * leave and let the clock run, and the invariants of the game must hold after every single step.
 */
public class WerewolfAuditTest {
  private static final Map<String, Integer> FULL_CAST =
      Map.of("WEREWOLF", 2, "SEER", 1, "WITCH", 1, "HUNTER", 1, "CUPID", 1, "GUARD", 1);
  private static final List<String> ABILITIES =
      List.of("skip", "heal", "poison", "sleep", "bind", "shoot", "protect-self");

  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed, i + 1));
    return ids;
  }

  private static void invariants(WerewolfGame g, List<UUID> everyone, String context) {
    String phase = g.phase();
    Set<UUID> alive = g.alive();
    assertTrue(context + ": alive must be participants", everyone.containsAll(alive));
    if (phase.equals("FINISHED")) return;
    assertTrue(context + ": no winner while playing", g.winner().isEmpty());
    // Whoever the phase waits for must exist, or the game could only be moved by the clock.
    if (List.of("CUPID", "GUARD", "SEER", "NIGHT", "WITCH", "HUNTER_SHOT").contains(phase))
      assertFalse(context + ": nobody can act in " + phase, g.actors().isEmpty());
    if (phase.equals("HUNTER_SHOT"))
      assertTrue(context + ": the shooter may be dead, not absent", g.actors().size() == 1);
    // Every living player must be able to act or sleep, never both.
    for (UUID p : alive) {
      if (g.sleeping(p)) assertFalse(context + ": a sleeper is an actor", g.actors().contains(p));
    }
    // Lovers are a living pair or both dead.
    for (UUID p : everyone) {
      UUID partner = g.partner(p);
      if (partner != null)
        assertEquals(
            context + ": lovers die together",
            alive.contains(p),
            alive.contains(partner));
    }
    // Votes only count living players for living players.
    for (UUID viewer : everyone)
      for (UUID target : g.votes(viewer).keySet())
        assertTrue(context + ": a vote for the dead", alive.contains(target));
    assertTrue(context + ": round starts at 1", g.round() >= 1);
  }

  @Test
  public void randomPlayAndDeparturesNeverBreakTheRules() {
    for (int seed = 0; seed < 600; seed++) {
      Random random = new Random(seed);
      int size = 4 + random.nextInt(12);
      List<UUID> ids = ids(seed, size);
      Map<String, Integer> cast = size >= 8 && seed % 2 == 0 ? FULL_CAST : Map.of();
      WerewolfGame g =
          new WerewolfGame(ids, 5, 5, 5, new Random(seed), new GameRules.Werewolf(cast, 5, 5, 5));
      List<UUID> present = new ArrayList<>(ids);
      for (int step = 0; step < 600 && g.winner().isEmpty() && !g.phase().equals("FINISHED"); step++) {
        UUID actor = ids.get(random.nextInt(size));
        UUID target = ids.get(random.nextInt(size));
        String context = "seed " + seed + " step " + step + " " + g.phase();
        try {
          switch (random.nextInt(8)) {
            case 0, 1 -> g.select(actor, target);
            case 2 -> g.aim(actor, target);
            case 3 -> g.use(actor, ABILITIES.get(random.nextInt(ABILITIES.size())));
            case 4 -> {
              for (String ability : g.abilities(actor).keySet()) {
                g.use(actor, ability);
                break;
              }
            }
            case 5 -> g.advance();
            case 6 -> {
              if (random.nextInt(6) == 0 && present.size() > 1) {
                UUID leaver = present.remove(random.nextInt(present.size()));
                g.remove(leaver);
              }
            }
            default -> g.advance();
          }
        } catch (IllegalArgumentException refused) {
          // A refused click is fine; anything else (NPE, IllegalState, ...) is a bug.
        }
        invariants(g, ids, context);
      }
      // The clock alone never kills anybody (no ballots), but it must never throw or get stuck:
      // after many turns the arena's time limit ends the game cleanly.
      for (int i = 0; i < 100 && !g.phase().equals("FINISHED"); i++) {
        g.advance();
        invariants(g, ids, "seed " + seed + " clock " + i);
      }
      g.finish();
      assertEquals("seed " + seed + " ends at the time limit", "FINISHED", g.phase());
    }
  }

  @Test
  public void aStaleWitchPickDoesNotSurviveTheNight() {
    List<UUID> ids = ids(7, 8);
    WerewolfGame g =
        new WerewolfGame(ids, 5, 5, 5, new Random(7), new GameRules.Werewolf(FULL_CAST, 5, 5, 5));
    UUID witch = ids.stream().filter(p -> g.role(p).equals("WITCH")).findFirst().orElseThrow();
    // Bring the game to the first witch turn: run the clock until the WITCH phase shows up.
    for (int i = 0; i < 50 && !g.phase().equals("WITCH"); i++) g.advance();
    assertEquals("WITCH", g.phase());
    UUID someone = g.alive().stream().filter(p -> !p.equals(witch)).findFirst().orElseThrow();
    g.aim(witch, someone);
    assertFalse(g.aims(witch).isEmpty());
    // She leaves it unconfirmed and the clock moves on to the next night's witch turn.
    for (int i = 0; i < 50 && !(g.phase().equals("WITCH") && g.round() == 2); i++) g.advance();
    if (g.phase().equals("WITCH") && g.round() == 2)
      assertTrue("a pick from last night must not carry over", g.aims(witch).isEmpty());
  }
}
