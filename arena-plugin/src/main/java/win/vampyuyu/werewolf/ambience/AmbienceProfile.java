package win.vampyuyu.werewolf.ambience;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Which looping bed and which one-shot sting play in each werewolf phase, read from
 * werewolf/ambience.json. Pure data: no Bukkit access, so the resolution is unit-tested.
 *
 * <p>A phase resolves to the most specific entry: the phase name as given, then its upper-case
 * form (per-phase overrides such as DAY_VOTE or DEFENSE), then its lower-case form (phase keys such
 * as vote or trial). An unknown phase falls back to night or day.
 */
public final class AmbienceProfile {
  /** A looping bed. loopMillis is the length of the sound file, replayed by the plugin. */
  public record Ambient(String sound, long loopMillis, float volume) {}

  /** A one-shot played when the phase starts. */
  public record Sting(String sound, float volume) {}

  /** Everything one phase plays. fallback is the vanilla sound used when the pack is missing. */
  public record Entry(Ambient ambient, Sting sting, String fallback) {}

  static final String RESOURCE = "/werewolf/ambience.json";

  /** Pack sounds only: vampyuyu:werewolf.&lt;name&gt;, declared in resource-pack sounds.json. */
  private static final Pattern PACK_SOUND = Pattern.compile("vampyuyu:werewolf\\.[a-z0-9_]+");

  private static final Pattern VANILLA_SOUND = Pattern.compile("minecraft:[a-z0-9_./]+");

  private static final class Defaults {
    static final AmbienceProfile INSTANCE = loadResource();

    private static AmbienceProfile loadResource() {
      try (InputStream in = AmbienceProfile.class.getResourceAsStream(RESOURCE)) {
        if (in == null) throw new IllegalStateException("missing " + RESOURCE);
        return load(in);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private final Map<String, Entry> entries;

  private AmbienceProfile(Map<String, Entry> entries) {
    this.entries = entries;
  }

  /** The profile shipped with the plugin (werewolf/ambience.json). */
  public static AmbienceProfile defaults() {
    return Defaults.INSTANCE;
  }

  /**
   * Reads a profile and checks it: every phase needs an ambient bed, a sting and a fallback, every
   * sound must be a known pack key, loopMillis must be positive and volumes must lie in (0, 1].
   * The stream is left open. Throws IllegalArgumentException on invalid content.
   */
  public static AmbienceProfile load(InputStream in) throws IOException {
    JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    if (!root.isJsonObject()) throw new IllegalArgumentException("ambience root must be an object");
    Map<String, Entry> entries = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> phase : root.getAsJsonObject().entrySet()) {
      String name = phase.getKey();
      if (name.isBlank()) throw new IllegalArgumentException("blank phase key");
      entries.put(name, entry(name, phase.getValue()));
    }
    for (String required : List.of("day", "night"))
      if (!entries.containsKey(required))
        throw new IllegalArgumentException("ambience needs a '" + required + "' entry");
    return new AmbienceProfile(Collections.unmodifiableMap(entries));
  }

  /** The entry for a phase: exact name, then upper case, then lower case, then night or day. */
  public Entry forPhase(String phase, boolean night) {
    if (phase != null && !phase.isEmpty()) {
      for (String key :
          List.of(phase, phase.toUpperCase(Locale.ROOT), phase.toLowerCase(Locale.ROOT))) {
        Entry entry = entries.get(key);
        if (entry != null) return entry;
      }
    }
    return entries.get(night ? "night" : "day");
  }

  /** Every pack sound the profile plays, ambient beds and stings alike. */
  public Set<String> allSounds() {
    Set<String> sounds = new TreeSet<>();
    for (Entry entry : entries.values()) {
      sounds.add(entry.ambient().sound());
      sounds.add(entry.sting().sound());
    }
    return Collections.unmodifiableSet(sounds);
  }

  private static Entry entry(String phase, JsonElement element) {
    if (!element.isJsonObject()) throw new IllegalArgumentException(phase + " must be an object");
    JsonObject obj = element.getAsJsonObject();
    JsonObject ambient = object(phase, obj, "ambient");
    JsonObject sting = object(phase, obj, "sting");
    String fallback = string(phase, obj, "fallback");
    if (!VANILLA_SOUND.matcher(fallback).matches())
      throw new IllegalArgumentException(phase + ": fallback must be a vanilla sound, got " + fallback);
    return new Entry(
        new Ambient(
            packSound(phase, ambient),
            longValue(phase, ambient, "loopMillis"),
            volume(phase, ambient)),
        new Sting(packSound(phase, sting), volume(phase, sting)),
        fallback);
  }

  private static JsonObject object(String phase, JsonObject obj, String key) {
    JsonElement value = obj.get(key);
    if (value == null || !value.isJsonObject())
      throw new IllegalArgumentException(phase + " is missing the '" + key + "' object");
    return value.getAsJsonObject();
  }

  private static String string(String phase, JsonObject obj, String key) {
    JsonElement value = obj.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException(phase + " is missing the '" + key + "' string");
    return value.getAsString();
  }

  private static String packSound(String phase, JsonObject obj) {
    String sound = string(phase, obj, "sound");
    if (!PACK_SOUND.matcher(sound).matches())
      throw new IllegalArgumentException(phase + ": not a pack sound key: " + sound);
    return sound;
  }

  private static long longValue(String phase, JsonObject obj, String key) {
    JsonElement value = obj.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(phase + " is missing the '" + key + "' number");
    long millis = value.getAsLong();
    if (millis <= 0) throw new IllegalArgumentException(phase + ": " + key + " must be positive");
    return millis;
  }

  private static float volume(String phase, JsonObject obj) {
    JsonElement value = obj.get("volume");
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(phase + " is missing the 'volume' number");
    float volume = value.getAsFloat();
    if (!(volume > 0 && volume <= 1))
      throw new IllegalArgumentException(phase + ": volume must lie in (0, 1], got " + volume);
    return volume;
  }
}
