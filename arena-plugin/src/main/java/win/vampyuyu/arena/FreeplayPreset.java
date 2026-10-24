package win.vampyuyu.arena;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Free play: score or race objectives configured per arena, scored only by engine events. */
public final class FreeplayPreset implements ArenaPreset {
  private static final Gson GSON = new Gson();
  private final GameHooks hooks;

  public FreeplayPreset(NexusArenaEngine plugin) {
    hooks = new Hooks(plugin);
  }

  public String id() {
    return "freeplay";
  }

  public String title() {
    return "Free play";
  }

  public Material icon() {
    return Material.GRASS_BLOCK;
  }

  public int menuSlot() {
    return 15;
  }

  public GamePreset create(ArenaConfig c, Collection<UUID> ids) {
    return new FreeplayGame(ids, GameRules.Freeplay.of(c));
  }

  public GameHooks hooks() {
    return hooks;
  }

  @Override
  public void validate(ArenaConfig c, World world) {
    var rules = GameRules.Freeplay.of(c);
    if (c.options().json().has("places")
        && c.options().json().get("places").isJsonObject()
        && c.options().json().getAsJsonObject("places").has("seats")
        && !c.options().json().getAsJsonObject("places").getAsJsonArray("seats").isEmpty())
      throw new IllegalArgumentException("Seats and places require Werewolf");
    if (rules.objective().equals("PVP") && !world.getPVP())
      throw new IllegalArgumentException(
          "This world has PvP disabled. Enable it in Server settings or choose another world.");
  }

  @Override
  public JsonObject migrate(JsonObject options) {
    JsonObject out = options.deepCopy();
    if (!out.has("freeplay")) out.add("freeplay", GSON.toJsonTree(GameRules.Freeplay.defaults()));
    return out;
  }

  @Override
  public JsonObject defaultOptions() {
    JsonObject o = new JsonObject();
    o.add("freeplay", GSON.toJsonTree(GameRules.Freeplay.defaults()));
    // Phase timers no longer mean anything here; they stay so an older plugin can still read the file.
    o.addProperty("discussionSeconds", 60);
    o.addProperty("voteSeconds", 30);
    o.addProperty("nightSeconds", 30);
    return o;
  }

  private static final class Hooks implements GameHooks {
    private final NexusArenaEngine plugin;

    Hooks(NexusArenaEngine plugin) {
      this.plugin = plugin;
    }

    @Override
    public void onJoin(ArenaSession s, Player p) {
      var rules = GameRules.Freeplay.of(s.config);
      p.setAllowFlight(rules.allowFlight());
      if (rules.personalTime() >= 0) p.setPlayerTime(rules.personalTime(), false);
      if (!rules.weather().equals("WORLD"))
        p.setPlayerWeather(WeatherType.valueOf(rules.weather()));
      for (var item : rules.kit().entrySet())
        p.getInventory()
            .addItem(
                new org.bukkit.inventory.ItemStack(
                    Material.matchMaterial(item.getKey()), item.getValue()));
      for (var effect : rules.effects().entrySet())
        p.addPotionEffect(
            new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.getByKey(
                    NamespacedKey.minecraft(effect.getKey())),
                s.config.durationSeconds() * 20,
                effect.getValue() - 1));
      p.sendMessage(Component.text(rules.instructions(), NamedTextColor.GOLD));
    }

    @Override
    public boolean onDamage(ArenaSession s, Player victim, Player attacker, EntityDamageEvent e) {
      if (s.game == null || e.getFinalDamage() < victim.getHealth()) return false;
      e.setCancelled(true);
      if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId()))
        s.game.event(attacker.getUniqueId(), "PVP");
      plugin.arenas.respawn(victim);
      return true;
    }

    @Override
    public boolean hunger(ArenaSession s) {
      return GameRules.Freeplay.of(s.config).hunger();
    }
  }
}
