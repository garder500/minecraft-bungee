package win.vampyuyu.werewolf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.ArenaPreset;
import win.vampyuyu.arena.GameHooks;
import win.vampyuyu.arena.GamePreset;

public final class WerewolfPreset implements ArenaPreset {
  private static final Gson GSON = new Gson();
  private final GameHooks hooks;

  public WerewolfPreset(GameHooks hooks) {
    this.hooks = hooks;
  }

  public String id() {
    return "werewolf";
  }

  public String title() {
    return "Werewolf";
  }

  public Material icon() {
    return Material.BONE;
  }

  public int menuSlot() {
    return 11;
  }

  public int defaultMinPlayers() {
    return 4;
  }

  public GameHooks hooks() {
    return hooks;
  }

  public GamePreset create(ArenaConfig c, Collection<UUID> participants) {
    return create(c, participants, Map.of());
  }

  @Override
  public GamePreset create(ArenaConfig c, Collection<UUID> participants, Map<UUID, String> forced) {
    var options = WerewolfOptions.of(c);
    return new WerewolfGame(
        participants,
        options.discussionSeconds(),
        options.voteSeconds(),
        options.nightSeconds(),
        new SecureRandom(),
        options.rules(),
        forced);
  }

  @Override
  public void validate(ArenaConfig c, World world) {
    WerewolfOptions.of(c);
  }

  /** Fills in what files of earlier versions leave out, so an edit saves the whole block. */
  @Override
  public JsonObject migrate(JsonObject options) {
    JsonObject out = options.deepCopy();
    for (String timer : new String[] {"discussionSeconds", "voteSeconds", "nightSeconds"})
      if (!out.has(timer))
        out.addProperty(
            timer,
            timer.equals("discussionSeconds")
                ? WerewolfOptions.DISCUSSION
                : timer.equals("voteSeconds") ? WerewolfOptions.VOTE : WerewolfOptions.NIGHT);
    if (!out.has("werewolf")) out.add("werewolf", GSON.toJsonTree(WerewolfRules.defaults()));
    if (!out.has("places")) out.add("places", GSON.toJsonTree(Places.defaults()));
    // The admin tools append to the list: it must be there. Unknown keys stay untouched.
    if (out.get("places") instanceof JsonObject places && !places.has("seats"))
      places.add("seats", new JsonArray());
    return out;
  }

  @Override
  public JsonObject defaultOptions() {
    JsonObject o = new JsonObject();
    o.addProperty("discussionSeconds", WerewolfOptions.DISCUSSION);
    o.addProperty("voteSeconds", WerewolfOptions.VOTE);
    o.addProperty("nightSeconds", WerewolfOptions.NIGHT);
    o.add("werewolf", GSON.toJsonTree(WerewolfRules.defaults()));
    o.add("places", GSON.toJsonTree(Places.defaults()));
    return o;
  }
}
