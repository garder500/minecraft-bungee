package win.vampyuyu.arena;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Built-in waiting-room hubs for ValoBlock and Werewolf inside each game's own void world.
 * Placed at negative X ({@value #HUB_X}), far away from every game slot ({@code x >= 0}), so
 * players can join directly from /menu without an admin having to create a dedicated arena first.
 *
 * <p>The arena configs always come from this code ({@link ArenaManager#ensureBuiltin} overwrites an older saved
 * copy). The hubs are built on demand, when the first player joins ({@link #prepare}): the ValoBlock Staging Deck
 * ({@link StagingDeck}) and the Werewolf Moonlit Village ({@link MoonlitVillage}), with their titles and display
 * models ({@link HubDisplays}) shown while someone is near. Waiting Werewolf players see the village at midnight.
 */
public final class WaitingHubs {
  public static final String VALOBLOCK_WORLD = "vb_arena";
  public static final String WEREWOLF_WORLD = "ww_sky";
  public static final int HUB_X = -512;
  public static final int HUB_Z = 0;
  public static final int VALOBLOCK_Y = 65;
  public static final int WEREWOLF_Y = 100;
  /** Half the side of the arena bounds (and of the player's world border). */
  static final int HALF = 40;
  /** The midnight shown to waiting Werewolf players. */
  static final long MIDNIGHT = 18000;

  /** The worlds (by uid) whose hub is built, with its displays. */
  private static final Map<UUID, HubDisplays> BUILT = new ConcurrentHashMap<>();

  private WaitingHubs() {}

  public static ArenaConfig valoblockConfig(JsonObject defaultOptions) {
    return new ArenaConfig(
        "valoblock",
        "ValoBlock",
        VALOBLOCK_WORLD,
        "valoblock",
        new ArenaConfig.Bounds(
            HUB_X - HALF, VALOBLOCK_Y - 30, HUB_Z - HALF, HUB_X + HALF, VALOBLOCK_Y + 45, HUB_Z + HALF),
        new ArenaConfig.Spawn(
            HUB_X + StagingDeck.SPAWN_X, VALOBLOCK_Y, HUB_Z + StagingDeck.SPAWN_Z, 180f, 0f),
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
            HUB_X - HALF, WEREWOLF_Y - 30, HUB_Z - HALF, HUB_X + HALF, WEREWOLF_Y + 40, HUB_Z + HALF),
        new ArenaConfig.Spawn(
            HUB_X + MoonlitVillage.SPAWN_X, WEREWOLF_Y, HUB_Z + MoonlitVillage.SPAWN_Z, 180f, 0f),
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
   * Ensures the built-in {@code werewolf} and {@code valoblock} arenas exist with this code's config, so /menu works
   * out of the box. Nothing is built here: {@link #prepare} builds a hub when its first player joins.
   */
  public static void install(
      ArenaManager arenas,
      java.util.logging.Logger log,
      World wwWorld,
      JsonObject wwOptions,
      World vbWorld,
      JsonObject vbOptions) {
    if (wwWorld != null) {
      try {
        if (arenas.ensureBuiltin(werewolfConfig(wwOptions)))
          log.info("Provisioned built-in Werewolf waiting hub in " + WEREWOLF_WORLD);
      } catch (Exception e) {
        log.warning("Could not provision built-in Werewolf arena: " + e.getMessage());
      }
    }
    if (vbWorld != null) {
      try {
        if (arenas.ensureBuiltin(valoblockConfig(vbOptions)))
          log.info("Provisioned built-in ValoBlock waiting hub in " + VALOBLOCK_WORLD);
      } catch (Exception e) {
        log.warning("Could not provision built-in ValoBlock arena: " + e.getMessage());
      }
    }
  }

  /** Which hub an arena config is, or null: the hub worlds' arenas whose spawn is at the hub. */
  static String hubOf(ArenaConfig c) {
    if (c == null || Math.abs(c.spawn().x() - HUB_X) > HALF || Math.abs(c.spawn().z() - HUB_Z) > HALF) return null;
    if (VALOBLOCK_WORLD.equals(c.worldName())) return VALOBLOCK_WORLD;
    if (WEREWOLF_WORLD.equals(c.worldName())) return WEREWOLF_WORLD;
    return null;
  }

  /**
   * Builds the hub an arena's players are about to be sent to, once per world (on the server thread, before the
   * teleport). Nothing for any other arena.
   */
  public static void prepare(Plugin plugin, ArenaConfig c) {
    String hub = hubOf(c);
    if (hub == null) return;
    World w = Bukkit.getWorld(c.worldName());
    if (w == null || BUILT.containsKey(w.getUID())) return;
    boolean vb = VALOBLOCK_WORLD.equals(hub);
    int fy = (vb ? VALOBLOCK_Y : WEREWOLF_Y) - 1;
    long started = System.nanoTime();
    HubPlan plan = vb ? StagingDeck.plan() : MoonlitVillage.plan();
    try {
      place(w, HUB_X, fy, HUB_Z, plan);
      // the worlds are recreated with a random seed each boot: fix the biome, or the grass and leaves take a random tint
      paintBiome(w, fy, vb ? Biome.PLAINS : Biome.FOREST);
    } catch (RuntimeException e) {
      // a broken hub must never stop players from joining: they land on whatever was placed
      plugin.getLogger().warning("Could not finish the " + hub + " waiting hub: " + e);
    }
    HubDisplays displays =
        new HubDisplays(plugin, hub, w, HUB_X, fy, HUB_Z, vb ? valoblockDisplays() : werewolfDisplays());
    BUILT.put(w.getUID(), displays);
    displays.start();
    plugin
        .getLogger()
        .info(
            "Built the "
                + (vb ? "ValoBlock" : "Werewolf")
                + " waiting hub: "
                + plan.size()
                + " blocks in "
                + (System.nanoTime() - started) / 1_000_000
                + " ms");
  }

  /** After the teleport into a hub: waiting Werewolf players see the village at midnight. */
  public static void joined(Player p, ArenaConfig c) {
    if (WEREWOLF_WORLD.equals(hubOf(c))) p.setPlayerTime(MIDNIGHT, false);
  }

  /** Sets one biome over the hub's whole volume (biomes are stored per 4x4x4 cell). */
  static void paintBiome(World w, int fy, Biome biome) {
    for (int x = -HALF; x <= HALF; x += 4)
      for (int z = -HALF; z <= HALF; z += 4)
        for (int y = -32; y <= 48; y += 4) w.setBiome(HUB_X + x, fy + y, HUB_Z + z, biome);
  }

  /** Places a plan at an origin, after clearing what the older 15x15 hub left (its barrier walls and ceiling). */
  static void place(World w, int ox, int oy, int oz, HubPlan plan) {
    for (int x = -7; x <= 7; x++)
      for (int y = -1; y <= 6; y++)
        for (int z = -7; z <= 7; z++)
          if (plan.at(x, y, z) == null) w.getBlockAt(ox + x, oy + y, oz + z).setType(Material.AIR, false);
    for (var e : plan.resolved().entrySet()) {
      long k = e.getKey();
      HubPlan.Block b = e.getValue();
      var block = w.getBlockAt(ox + HubPlan.kx(k), oy + HubPlan.ky(k), oz + HubPlan.kz(k));
      if (b.state() == null) block.setType(b.material(), false);
      else block.setBlockData(Bukkit.createBlockData(b.material(), b.state()), false);
    }
  }

  static List<HubDisplays.Spec> valoblockDisplays() {
    return List.of(
        HubDisplays.Spec.title(
            Component.text("VALOBLOCK", TextColor.color(0x52e0ff))
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Staging Deck · waiting for players", NamedTextColor.GRAY)),
            0.5,
            6.6,
            0.5)
            .scale(1.8f),
        HubDisplays.Spec.model("vb_holomap", 0.5, 2.45, 0.5, 2f).spin('y', 12),
        HubDisplays.Spec.model("vb_gate_ring", 0.5, 7.5, -21.0, 4f).spin('z', 10),
        HubDisplays.Spec.model("vb_drone", 0.5, 6.0, 0.5, 1.5f).orbit(8.5, 9));
  }

  static List<HubDisplays.Spec> werewolfDisplays() {
    double statueY = MoonlitVillage.rockTop() + 1 + 3.75;
    return List.of(
        HubDisplays.Spec.title(
            Component.text("WEREWOLF", TextColor.color(0xe9d99b))
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Moonlit Village · waiting for players", NamedTextColor.GRAY)),
            0.5,
            6.8,
            0.5)
            .scale(1.8f),
        HubDisplays.Spec.model("ww_moon_ring", 0.5, 10.5, 0.5, 4f).spin('y', 6).light(-1, -1),
        HubDisplays.Spec.model(
                "ww_wolf_statue", MoonlitVillage.ROCK_X + 0.5, statueY, MoonlitVillage.ROCK_Z + 0.5, 3f)
            .yaw(180)
            .light(9, 6),
        HubDisplays.Spec.model(
                "ww_weathervane", MoonlitVillage.TOWER_X + 0.5, 25.0, MoonlitVillage.TOWER_Z + 0.5, 2f)
            .spin('y', 15)
            .light(10, 6));
  }
}
