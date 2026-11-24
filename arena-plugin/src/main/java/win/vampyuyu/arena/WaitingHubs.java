package win.vampyuyu.arena;

import com.google.gson.JsonObject;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * Built-in waiting-room hubs for ValoBlock and Werewolf inside each game's own void world.
 * Placed at negative X ({@value #HUB_X}), far away from every game slot ({@code x >= 0}), so
 * players can join directly from /menu without an admin having to create a dedicated arena first.
 */
public final class WaitingHubs {
  public static final String VALOBLOCK_WORLD = "vb_arena";
  public static final String WEREWOLF_WORLD = "ww_sky";
  public static final int HUB_X = -512;
  public static final int HUB_Z = 0;
  public static final int VALOBLOCK_Y = 65;
  public static final int WEREWOLF_Y = 100;
  private static final int RADIUS = 7;

  private WaitingHubs() {}

  public static ArenaConfig valoblockConfig(JsonObject defaultOptions) {
    return new ArenaConfig(
        "valoblock",
        "ValoBlock",
        VALOBLOCK_WORLD,
        "valoblock",
        new ArenaConfig.Bounds(
            HUB_X - 20, VALOBLOCK_Y - 10, HUB_Z - 20, HUB_X + 20, VALOBLOCK_Y + 30, HUB_Z + 20),
        new ArenaConfig.Spawn(HUB_X + 0.5, VALOBLOCK_Y, HUB_Z + 3.5, 180f, 0f),
        Set.of(),
        Set.of(),
        true,
        false,
        true,
        GameMode.ADVENTURE,
        3600,
        1,
        10,
        new ArenaConfig.Rewards(0, 0),
        new ArenaConfig.Options(defaultOptions));
  }

  public static ArenaConfig werewolfConfig(JsonObject defaultOptions) {
    return new ArenaConfig(
        "werewolf",
        "Moonlit Village",
        WEREWOLF_WORLD,
        "werewolf",
        new ArenaConfig.Bounds(
            HUB_X - 20, WEREWOLF_Y - 10, HUB_Z - 20, HUB_X + 20, WEREWOLF_Y + 30, HUB_Z + 20),
        new ArenaConfig.Spawn(HUB_X + 0.5, WEREWOLF_Y, HUB_Z + 3.5, 180f, 0f),
        Set.of(),
        Set.of(),
        false,
        false,
        true,
        GameMode.ADVENTURE,
        1800,
        4,
        20,
        new ArenaConfig.Rewards(0, 0),
        new ArenaConfig.Options(defaultOptions));
  }

  /**
   * Builds the waiting rooms in their void worlds and ensures the built-in {@code werewolf} and
   * {@code valoblock} arenas exist so /menu works out of the box.
   */
  public static void install(
      ArenaManager arenas,
      java.util.logging.Logger log,
      World wwWorld,
      JsonObject wwOptions,
      World vbWorld,
      JsonObject vbOptions) {
    if (wwWorld != null) {
      buildWerewolfHub(wwWorld);
      try {
        if (arenas.ensureBuiltin(werewolfConfig(wwOptions)))
          log.info("Provisioned built-in Werewolf waiting hub in " + WEREWOLF_WORLD);
      } catch (Exception e) {
        log.warning("Could not provision built-in Werewolf arena: " + e.getMessage());
      }
    }
    if (vbWorld != null) {
      buildValoBlockHub(vbWorld);
      try {
        if (arenas.ensureBuiltin(valoblockConfig(vbOptions)))
          log.info("Provisioned built-in ValoBlock waiting hub in " + VALOBLOCK_WORLD);
      } catch (Exception e) {
        log.warning("Could not provision built-in ValoBlock arena: " + e.getMessage());
      }
    }
  }

  /** Tactical ready room in vb_arena: deepslate & quartz deck, cyan glass walls, center hologram. */
  public static void buildValoBlockHub(World w) {
    int cx = HUB_X, fy = VALOBLOCK_Y - 1, cz = HUB_Z;
    clearDisplays(w, cx, VALOBLOCK_Y, cz);
    for (int dx = -RADIUS; dx <= RADIUS; dx++) {
      for (int dz = -RADIUS; dz <= RADIUS; dz++) {
        boolean edge = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS;
        boolean corner = Math.abs(dx) >= RADIUS - 1 && Math.abs(dz) >= RADIUS - 1;
        boolean accent = Math.abs(dx) == 3 || Math.abs(dz) == 3;
        Material floor =
            corner
                ? Material.SEA_LANTERN
                : edge
                    ? Material.POLISHED_DEEPSLATE
                    : accent ? Material.SMOOTH_QUARTZ : Material.DEEPSLATE_TILES;
        w.getBlockAt(cx + dx, fy, cz + dz).setType(floor, false);
        for (int dy = 1; dy <= 5; dy++) {
          Material wall = Material.AIR;
          if (edge) {
            if (dy == 1 || dy == 5)
              wall = corner ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE;
            else wall = corner ? Material.IRON_BARS : Material.CYAN_STAINED_GLASS_PANE;
          }
          w.getBlockAt(cx + dx, fy + dy, cz + dz).setType(wall, false);
        }
        w.getBlockAt(cx + dx, fy + 6, cz + dz)
            .setType(edge ? Material.POLISHED_DEEPSLATE : Material.TINTED_GLASS, false);
      }
    }
    // Tactical center hologram pedestal (at cx, cz - 2 so spawn at cz + 3.5 faces it).
    w.getBlockAt(cx, fy, cz - 2).setType(Material.SEA_LANTERN, false);
    w.getBlockAt(cx, fy + 1, cz - 2).setType(Material.POLISHED_DEEPSLATE_SLAB, false);
    w.getBlockAt(cx, fy + 2, cz - 2).setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
    spawnBanner(
        w,
        cx + 0.5,
        VALOBLOCK_Y + 3.2,
        cz - 1.5,
        Component.text("VALOBLOCK", TextColor.color(0x52e0ff)).decorate(TextDecoration.BOLD)
            .append(Component.newline())
            .append(Component.text("Salle d'attente · Waiting Room", NamedTextColor.GRAY)));
  }

  /** Moonlit village clearing in ww_sky: stone & dark-oak courtyard, soul lanterns, campfire. */
  public static void buildWerewolfHub(World w) {
    int cx = HUB_X, fy = WEREWOLF_Y - 1, cz = HUB_Z;
    clearDisplays(w, cx, WEREWOLF_Y, cz);
    for (int dx = -RADIUS; dx <= RADIUS; dx++) {
      for (int dz = -RADIUS; dz <= RADIUS; dz++) {
        boolean edge = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS;
        boolean pillar = Math.abs(dx) == RADIUS && Math.abs(dz) == RADIUS;
        boolean ring = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        Material floor =
            edge
                ? Material.STONE_BRICKS
                : ring ? Material.DARK_OAK_PLANKS : Material.COBBLESTONE;
        w.getBlockAt(cx + dx, fy, cz + dz).setType(floor, false);
        for (int dy = 1; dy <= 5; dy++) {
          Material wall = Material.AIR;
          if (pillar) {
            wall = dy == 5 ? Material.SOUL_LANTERN : Material.STONE_BRICK_WALL;
          } else if (edge) {
            wall = dy == 1 ? Material.SPRUCE_FENCE : Material.BARRIER;
          }
          w.getBlockAt(cx + dx, fy + dy, cz + dz).setType(wall, false);
        }
        w.getBlockAt(cx + dx, fy + 6, cz + dz).setType(Material.BARRIER, false);
      }
    }
    // Campfire focal point in front of spawn.
    w.getBlockAt(cx, fy + 1, cz - 2).setType(Material.SOUL_CAMPFIRE, false);
    spawnBanner(
        w,
        cx + 0.5,
        WEREWOLF_Y + 3.0,
        cz - 1.5,
        Component.text("LOUP-GAROU · WEREWOLF", TextColor.color(0xe9d99b)).decorate(TextDecoration.BOLD)
            .append(Component.newline())
            .append(Component.text("Salle d'attente · Waiting Room", NamedTextColor.GRAY)));
  }

  private static void clearDisplays(World w, int cx, int y, int cz) {
    Location center = new Location(w, cx + 0.5, y + 2.0, cz + 0.5);
    for (var entity : w.getNearbyEntities(center, RADIUS + 4, 10, RADIUS + 4)) {
      if (entity instanceof TextDisplay) entity.remove();
    }
  }

  private static void spawnBanner(World w, double x, double y, double z, Component text) {
    w.spawn(
        new Location(w, x, y, z),
        TextDisplay.class,
        d -> {
          d.text(text);
          d.setBillboard(Display.Billboard.CENTER);
          d.setSeeThrough(false);
          d.setPersistent(false);
        });
  }
}
