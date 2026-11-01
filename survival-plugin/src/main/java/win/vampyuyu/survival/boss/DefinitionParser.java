package win.vampyuyu.survival.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The checks {@link BossRepo} and the slayer reader run on the rows of the boss tables before they become
 * {@link BossDefinition}s. Every problem is appended to the {@code errors} list with the path of the offending value; a
 * definition with errors is dropped (the others load).
 */
public final class DefinitionParser {
    public static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    public static final Set<String> TARGETS = Set.of("nearest", "random", "top", "far", "all");
    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "mainhand", "offhand");

    private DefinitionParser() {}

    /** The scalar columns of a boss row that have a valid range; adds an error per bad value. */
    public static void checkBoss(String path, double health, double damage, double scale, double speed, double knockback,
            String barColor, String barOverlay, List<String> errors) {
        if (!(health > 0) || health > 1e15) {
            errors.add(path + ".health: must be between 0 and 1e15");
        }
        if (damage < 0 || damage > 1000) {
            errors.add(path + ".damage: must be between 0 and 1000");
        }
        if (scale < 0.0625 || scale > 16) {
            errors.add(path + ".scale: must be between 0.0625 and 16");
        }
        if (speed <= 0 || speed > 5) {
            errors.add(path + ".speed: must be between 0 and 5");
        }
        if (knockback < 0 || knockback > 1) {
            errors.add(path + ".knockback-resistance: must be between 0 and 1");
        }
        try {
            BossBar.Color.valueOf(barColor);
        } catch (IllegalArgumentException e) {
            errors.add(path + ".bar.color: unknown colour " + barColor);
        }
        try {
            BossBar.Overlay.valueOf(barOverlay);
        } catch (IllegalArgumentException e) {
            errors.add(path + ".bar.overlay: unknown overlay " + barOverlay);
        }
    }

    public static @Nullable EntityType parseEntity(String text, String path, List<String> errors) {
        EntityType type;
        try {
            type = EntityType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(path + ": unknown entity type '" + text + "'");
            return null;
        }
        Class<?> cls = type.getEntityClass();
        if (type == EntityType.PLAYER || cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            errors.add(path + ": " + type + " is not a living mob");
            return null;
        }
        return type;
    }

    /** The arena as read; null when a value is out of range (errors tell which). */
    static BossDefinition.@Nullable Arena checkArena(BossDefinition.Arena a, String path, List<String> errors) {
        int before = errors.size();
        if (a.radius() <= 0 || a.radius() > 256) {
            errors.add(path + ".radius: must be between 0 and 256");
        }
        if (a.leash() < a.radius() || a.leash() > 512) {
            errors.add(path + ".leash: must be at least the radius and at most 512");
        }
        if (a.projectileRange() <= 0) {
            errors.add(path + ".projectile-range: must be positive");
        }
        if (a.minPlayers() < 1 || a.scalePerPlayer() < 0 || a.scaleCap() < 1) {
            errors.add(path + ": min-players >= 1, scale-per-player >= 0, scale-cap >= 1 required");
        }
        if (a.enrageSeconds() < 0 || a.idleDespawnSeconds() < 0 || a.timeLimitSeconds() < 0 || a.maxMinions() < 0
                || a.maxMinions() > 64) {
            errors.add(path + ": enrage-seconds, idle-despawn-seconds, time-limit-seconds must be >= 0 and max-minions 0-64");
        }
        return errors.size() > before ? null : a;
    }

    /** Phase thresholds: the first is 1, the others lie in (0, 1) and strictly decrease. False (with an error) when {@code at} is bad. */
    static boolean checkThreshold(double at, double previous, String path, List<String> errors) {
        if (!(at > 0 && at <= 1) || at >= previous) {
            errors.add(path + ".at: thresholds must be in (0, 1] and strictly decreasing");
            return false;
        }
        return true;
    }

    /** One ability from its row; null (with errors) when a value is bad. */
    public static @Nullable AbilitySpec parseAbility(String typeText, double cooldown, double telegraph, double damage,
            String targetText, int fromTier, Map<String, Object> params, String path, List<String> errors) {
        AbilityType type = AbilityType.byId(typeText).orElse(null);
        if (type == null) {
            errors.add(path + ".type: unknown ability '" + typeText + "'");
            return null;
        }
        String target = targetText.toLowerCase(Locale.ROOT);
        if (cooldown < 0.5 || cooldown > 600) {
            errors.add(path + ".cooldown: must be between 0.5 and 600 seconds");
        }
        if (telegraph < 0 || telegraph > 30) {
            errors.add(path + ".telegraph: must be between 0 and 30 seconds");
        }
        if (damage < 0 || damage > 100) {
            errors.add(path + ".damage: must be between 0 and 100");
        }
        if (!TARGETS.contains(target)) {
            errors.add(path + ".target: expected one of " + TARGETS);
        }
        if (fromTier < 1 || fromTier > 5) {
            errors.add(path + ".from-tier: must be 1-5");
        }
        return new AbilitySpec(type, (int) Math.round(cooldown * 20), (int) Math.round(telegraph * 20), damage, target,
            fromTier, new LinkedHashMap<>(params));
    }

    /** An equipment piece; null (with an error) for an unknown slot or item. */
    public static BossDefinition.@Nullable EquipSpec parseEquip(String slot, String materialText, @Nullable Integer rgb,
            String path, List<String> errors) {
        if (!SLOTS.contains(slot.toLowerCase(Locale.ROOT))) {
            errors.add(path + "." + slot + ": slot must be one of " + SLOTS);
            return null;
        }
        Material material = Material.matchMaterial(materialText.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            errors.add(path + "." + slot + ": unknown item '" + materialText + "'");
            return null;
        }
        if (rgb != null && (rgb < 0 || rgb > 0xFFFFFF)) {
            errors.add(path + "." + slot + ": colour must look like #5a1e8c");
            return null;
        }
        return new BossDefinition.EquipSpec(material, rgb);
    }

    /** The loot header values (coins, xp, shares). */
    public static void checkLoot(String path, double coins, double xp, double minShare, double floor, List<String> errors) {
        if (coins < 0 || xp < 0 || minShare < 0 || minShare > 1 || floor < 0 || floor > 1) {
            errors.add(path + ": coins, combat-xp >= 0 and min-share, share-floor within 0-1 required");
        }
    }

    /** One drop; null (with an error) when invalid. */
    public static BossDefinition.@Nullable LootEntry checkDrop(String path, BossDefinition.LootEntry e, List<String> errors) {
        if (!validItemSpec(e.item())) {
            errors.add(path + ".item: expected custom:<id> or vanilla:<MATERIAL>, got '" + e.item() + "'");
            return null;
        }
        if (e.chance() < 0 || e.chance() > 1 || e.min() < 1 || e.max() < e.min() || e.max() > 64) {
            errors.add(path + ": chance 0-1, 1 <= min <= max <= 64 required");
            return null;
        }
        return e;
    }

    /** {@code custom:id} or {@code vanilla:MATERIAL}. */
    public static boolean validItemSpec(String item) {
        if (item.startsWith("custom:")) {
            return ID.matcher(item.substring(7)).matches();
        }
        if (item.startsWith("vanilla:")) {
            Material m = Material.matchMaterial(item.substring(8));
            return m != null && !m.isAir() && m.isItem();
        }
        return false;
    }

    /** A summon rule; null (with errors) when invalid. */
    static BossDefinition.@Nullable Summon checkSummon(BossDefinition.Summon s, boolean altarMissing, String path,
            List<String> errors) {
        int before = errors.size();
        if (!ID.matcher(s.item()).matches()) {
            errors.add(path + ".item: must match [a-z0-9_]+");
        }
        if (!s.mode().equals("altar") && !s.mode().equals("free")) {
            errors.add(path + ".mode: expected altar or free");
        }
        if (s.mode().equals("altar") && altarMissing) {
            errors.add(path + ".altar: the altar mode needs world, x, y, z, radius");
        }
        if (s.cooldownSeconds() < 0 || s.globalCooldownSeconds() < 0 || s.maxConcurrent() < 1) {
            errors.add(path + ": cooldowns >= 0 and max-concurrent >= 1 required");
        }
        return errors.size() > before ? null : s;
    }
}
