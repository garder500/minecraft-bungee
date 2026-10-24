package win.vampyuyu.werewolf;

import java.io.IOException;
import java.util.Objects;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.ambience.WerewolfAmbience;
import win.vampyuyu.werewolf.hud.ClickControls;
import win.vampyuyu.werewolf.hud.CustomHud;
import win.vampyuyu.werewolf.hud.VoteMarks;
import win.vampyuyu.werewolf.sky.SkyStage;
import win.vampyuyu.werewolf.sky.SkyWorld;

/**
 * The Werewolf game as a module of the arena engine: its preset and hooks, the scenery (the sky
 * platform, ambience), the HUD helpers and the /ww command. The engine builds one and registers
 * {@link #preset()}; nothing else of the engine knows Werewolf.
 */
public final class WerewolfModule {
  private final NexusArenaEngine plugin;
  public final WerewolfLogs logs;
  public final WerewolfAmbience ambience;
  public final PlaceController places;
  public final SkyStage stage;
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
    stage = SkyStage.forPlugin(plugin, new SkyWorld());
    customHud = new CustomHud();
    controls = new ClickControls(plugin);
    votes = new VoteMarks(plugin);
    compass = new WerewolfCompass(plugin);
    tools = new WerewolfTools(plugin);
    preset = new WerewolfPreset(new WerewolfHooks(plugin, this));
  }

  private volatile String skyError;

  /**
   * Creates the sky world (plugin enable, main thread). A failure does not stop the plugin: games
   * of this module just refuse to start, and /ww check says why.
   */
  public void prepareSky() {
    skyError = stage.world().create();
    if (skyError != null) plugin.getLogger().severe(skyError);
  }

  /** Why the sky world could not be created, or null. */
  public String skyError() {
    return skyError;
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
