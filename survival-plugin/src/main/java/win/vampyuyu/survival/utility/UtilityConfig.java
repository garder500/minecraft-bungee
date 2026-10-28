package win.vampyuyu.survival.utility;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import win.vampyuyu.survival.core.Rarity;

/** Immutable snapshot of {@code utility.yml}. Every value has a default here so a partial file still works. */
public record UtilityConfig(
        int warmupSeconds, double moveTolerance, boolean cancelOnDamage, boolean showTitle,
        boolean combatEnabled, int combatSeconds, boolean combatIncludeMobs,
        int homesDefault, int homesMax, int homeNameLength, String defaultHomeName,
        int tpaExpireSeconds, int tpaSendCooldownSeconds, int tpaMaxPending, int tpaTeleportCooldownSeconds,
        String spawnWorld,
        boolean rtpEnabled, String rtpWorld, int rtpCooldownSeconds, int rtpMinRadius, int rtpMaxRadius,
        boolean rtpUseWorldSpawn, int rtpCenterX, int rtpCenterZ, int rtpAttempts, int rtpTimeoutSeconds,
        double seatHeight, double seatOffset,
        List<String> trashKeepIds, Rarity trashKeepRarity,
        int nearDefault, int nearMax) {

    /** Reads the configuration; out-of-range values are clamped to something that works. */
    public static UtilityConfig from(ConfigurationSection c) {
        int rtpMin = Math.max(0, c.getInt("rtp.min-radius", 200));
        int rtpMax = Math.max(rtpMin + 16, c.getInt("rtp.max-radius", 5000));
        int nearMax = Math.max(1, c.getInt("near.max-radius", 250));
        return new UtilityConfig(
            Math.max(0, c.getInt("teleport.warmup-seconds", 3)),
            Math.max(0.1, c.getDouble("teleport.move-tolerance", 0.6)),
            c.getBoolean("teleport.cancel-on-damage", true),
            c.getBoolean("teleport.show-title", true),
            c.getBoolean("combat.enabled", true),
            Math.max(0, c.getInt("combat.tag-seconds", 10)),
            c.getBoolean("combat.include-mobs", true),
            Math.max(0, c.getInt("homes.default-limit", 3)),
            Math.max(1, c.getInt("homes.max-limit", 100)),
            Math.max(3, Math.min(32, c.getInt("homes.max-name-length", 16))),
            homeName(c.getString("homes.default-name", "home")),
            Math.max(5, c.getInt("tpa.expire-seconds", 60)),
            Math.max(0, c.getInt("tpa.send-cooldown-seconds", 10)),
            Math.max(1, c.getInt("tpa.max-pending-per-target", 5)),
            Math.max(0, c.getInt("tpa.teleport-cooldown-seconds", 5)),
            c.getString("spawn.world", ""),
            c.getBoolean("rtp.enabled", true),
            c.getString("rtp.world", ""),
            Math.max(0, c.getInt("rtp.cooldown-seconds", 600)),
            rtpMin, rtpMax,
            c.getBoolean("rtp.use-world-spawn", true),
            c.getInt("rtp.center-x", 0),
            c.getInt("rtp.center-z", 0),
            Math.max(1, c.getInt("rtp.max-attempts", 12)),
            Math.max(3, c.getInt("rtp.search-timeout-seconds", 20)),
            Math.max(0.0, c.getDouble("sit.seat-height", 0.6)),
            c.getDouble("sit.seat-offset", -0.2),
            c.getStringList("trash.keep-ids").isEmpty() && !c.isSet("trash.keep-ids")
                ? List.of("wings") : List.copyOf(c.getStringList("trash.keep-ids")),
            rarity(c.getString("trash.keep-rarity", "LEGENDARY")),
            Math.max(1, Math.min(nearMax, c.getInt("near.default-radius", 100))),
            nearMax);
    }

    private static String homeName(String raw) {
        String name = HomeBook.normalise(raw);
        return name != null && HomeBook.validate(name, 32) == null ? name : "home";
    }

    private static Rarity rarity(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rarity.LEGENDARY;
        }
    }

    /** Defaults only (tests, missing file). */
    public static UtilityConfig defaults() {
        return from(new org.bukkit.configuration.file.YamlConfiguration());
    }
}
