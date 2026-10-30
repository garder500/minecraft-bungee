package win.vampyuyu.survival.boss;

import java.util.Locale;
import java.util.Optional;

/** Every ability the library knows. The id is what {@code boss.yml} writes under {@code type:}. */
public enum AbilityType {
    FIREBALL_BARRAGE("fireball_barrage", "Barrage of homing fireballs"),
    GROUND_SLAM("ground_slam", "Expanding shockwave ring: jump it"),
    SUMMON_MINIONS("summon_minions", "Calls capped, tagged minions"),
    LIGHTNING_STRIKES("lightning_strikes", "Marked spots are struck after a delay"),
    VACUUM("vacuum", "Pulls everyone in, then detonates"),
    WHIRLPOOL("whirlpool", "Spinning pull that drags players around the boss"),
    POISON_CLOUD("poison_cloud", "Lingering poison zones"),
    WITHER_CLOUD("wither_cloud", "Lingering wither zones"),
    BREATH_CLOUD("breath_cloud", "Dragon breath zones"),
    LAVA_POOLS("lava_pools", "Lingering lava zones"),
    VOID_ZONES("void_zones", "Lingering void zones that blind and slow"),
    ICE_SPIKES("ice_spikes", "Marked spots erupt into ice spikes"),
    WEB_TRAPS("web_traps", "Roots players in webs"),
    TELEPORT_BEHIND("teleport_behind", "Blinks behind a player and strikes"),
    SHIELD_CRYSTALS("shield_crystals", "Invulnerable until the crystals are destroyed"),
    LASER_BEAM("laser_beam", "Sweeping beam with damage ticks"),
    CHARGE_DASH("charge_dash", "Telegraphed dash along a line"),
    DIVE_BOMB("dive_bomb", "Flyer dives on a marked player"),
    TIDAL_WAVE("tidal_wave", "Wall of water that sweeps the arena"),
    FIRE_RING("fire_ring", "Expanding ring of fire with a gap"),
    BONE_CAGE("bone_cage", "Traps a player inside a bone cage"),
    SOUL_DRAIN("soul_drain", "Beam that drains a player and heals the boss"),
    BLIZZARD("blizzard", "Freezing storm: stay near the warm braziers");

    private final String id;
    private final String description;

    AbilityType(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() { return id; }

    public String description() { return description; }

    /** Looks an ability up by its yaml id (case insensitive, dashes accepted). */
    public static Optional<AbilityType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (AbilityType type : values()) {
            if (type.id.equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
