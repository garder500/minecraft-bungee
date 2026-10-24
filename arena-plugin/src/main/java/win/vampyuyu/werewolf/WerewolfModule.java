package win.vampyuyu.werewolf;

import java.io.IOException;
import java.util.Objects;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.ambience.WerewolfAmbience;
import win.vampyuyu.werewolf.hud.ClickControls;
import win.vampyuyu.werewolf.hud.CustomHud;
import win.vampyuyu.werewolf.hud.VoteMarks;

/**
 * The Werewolf game as a module of the arena engine: its preset and hooks, the scenery (places,
 * ambience), the HUD helpers and the /ww command. The engine builds one and registers
 * {@link #preset()}; nothing else of the engine knows Werewolf.
 */
public final class WerewolfModule {
  private final NexusArenaEngine plugin;
  public final WerewolfLogs logs;
  public final WerewolfAmbience ambience;
  public final PlaceController places;
  public final CustomHud customHud;
  public final ClickControls controls;
  public final VoteMarks votes;
  public final WerewolfCompass compass;
  public final WerewolfTools tools;
  private final WerewolfPreset preset;

  public WerewolfModule(NexusArenaEngine plugin) throws IOException {
    this.plugin = plugin;
    logs =
        new WerewolfLogs(
            plugin.getDataFolder().toPath().resolve("logs").resolve("werewolf"),
            plugin.getLogger(),
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("WEREWOLF_LOG_CONSOLE", "")));
    ambience = new WerewolfAmbience(plugin);
    places = new PlaceController(plugin);
    customHud = new CustomHud();
    controls = new ClickControls(plugin);
    votes = new VoteMarks(plugin);
    compass = new WerewolfCompass(plugin);
    tools = new WerewolfTools(plugin);
    preset = new WerewolfPreset(new WerewolfHooks(plugin, this));
  }

  /** The game to register with the arena manager. */
  public WerewolfPreset preset() {
    return preset;
  }

  /** Registers /ww (declared in plugin.yml). */
  public void registerCommand() {
    WwCommand ww = new WwCommand(plugin);
    Objects.requireNonNull(plugin.getCommand("ww")).setExecutor(ww);
    Objects.requireNonNull(plugin.getCommand("ww")).setTabCompleter(ww);
  }
}
