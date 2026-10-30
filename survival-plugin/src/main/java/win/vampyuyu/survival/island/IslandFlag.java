package win.vampyuyu.survival.island;

import java.util.Locale;
import org.bukkit.Material;

/** Per-island on/off settings shown in the settings menu. The id is stable (stored in the island file). */
public enum IslandFlag {
    PVP("PvP", Material.IRON_SWORD, false, "Players can hurt each other on the island."),
    VISITOR_INTERACT("Visitor interaction", Material.OAK_DOOR, true, "Visitors may use doors, buttons and levers."),
    VISITOR_CHESTS("Visitor chests", Material.CHEST, false, "Visitors and trusted guests may open containers."),
    MOB_SPAWNING("Monster spawning", Material.ZOMBIE_HEAD, true, "Hostile mobs spawn naturally."),
    ANIMALS_SPAWNING("Animal spawning", Material.WHEAT, true, "Passive mobs spawn naturally."),
    FIRE_SPREAD("Fire spread", Material.FLINT_AND_STEEL, false, "Fire spreads and burns blocks."),
    TNT("Explosions", Material.TNT, false, "Explosions (TNT, creepers, crystals) destroy blocks."),
    WEATHER("Weather", Material.WATER_BUCKET, true, "Rain and storms are visible on the island.");

    private final String label;
    private final Material icon;
    private final boolean defaultValue;
    private final String description;

    IslandFlag(String label, Material icon, boolean defaultValue, String description) {
        this.label = label;
        this.icon = icon;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    public static IslandFlag byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.toUpperCase(Locale.ROOT).replace('-', '_');
        for (IslandFlag flag : values()) {
            if (flag.name().equals(wanted)) {
                return flag;
            }
        }
        return null;
    }
}
