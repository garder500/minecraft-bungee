package win.vampyuyu.arena;

import java.util.List;

/** The role modules of a game, in the order their hooks are asked. */
final class Modules {
  private Modules() {}

  /** A fresh instance of every module: modules keep per-game state, so games never share them. */
  static List<RoleModule> create() {
    return List.of(
        new InsightModule(),
        new ProtectionModule(),
        new CourtModule(),
        new SocialModule(),
        new ShapeModule(),
        new VampireModule(),
        new CultModule(),
        new SoloModule());
  }
}
