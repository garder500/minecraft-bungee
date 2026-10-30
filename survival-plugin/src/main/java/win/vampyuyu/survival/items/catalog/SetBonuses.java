package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.stats.StatProvider;

/** Detects a worn full set and contributes its stat bonus ("set" source). Pieces of mixed tiers use the lowest tier. */
public final class SetBonuses implements StatProvider {
    /** A complete set worn by a player. */
    public record Active(ArmorSet set, int tier) {}

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /** Default numeric parameters of the bonus descriptions and effects. */
    private static final Map<String, Map<String, Double>> DEFAULTS = Map.of(
        "lumberjack", Map.of("timber", 10.0, "seconds", 8.0),
        "farmer", Map.of("chance", 12.0),
        "hunter", Map.of("lifesteal", 3.0),
        "void_walker", Map.of("cooldown", 30.0),
        "frostborne", Map.of("slow", 3.0, "reduction", 10.0));

    private final Gear gear;
    private volatile ItemsSettings settings;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    public SetBonuses(Gear gear, ItemsSettings settings) {
        this.gear = gear;
        this.settings = settings;
    }

    public void settings(ItemsSettings settings) { this.settings = settings; }

    /** Tunable value of a set effect. */
    public double param(String setId, String key) {
        double fallback = DEFAULTS.getOrDefault(setId, Map.of()).getOrDefault(key, 0.0);
        return settings.tuned("sets." + setId + "." + key, fallback);
    }

    /** The set worn right now (as of the last recompute), if complete. */
    public Optional<Active> activeOf(UUID player) {
        return Optional.ofNullable(active.get(player));
    }

    public boolean has(UUID player, String setId) {
        Active a = active.get(player);
        return a != null && a.set().id().equals(setId);
    }

    public void forget(UUID player) {
        active.remove(player);
    }

    /** Pure detection from four stacks. */
    public Optional<Active> detect(ItemStack[] armor) {
        String setId = null;
        int tier = Integer.MAX_VALUE;
        int pieces = 0;
        for (ItemStack piece : armor) {
            Optional<GearInfo> info = gear.read(piece);
            if (info.isEmpty()) {
                return Optional.empty();
            }
            String id = info.get().def().pdcStrings().get(Keys.SET);
            if (id == null || (setId != null && !setId.equals(id))) {
                return Optional.empty();
            }
            setId = id;
            tier = Math.min(tier, info.get().def().pdcLongs().getOrDefault(Keys.TIER, 1L).intValue());
            pieces++;
        }
        if (pieces != 4 || setId == null) {
            return Optional.empty();
        }
        final int finalTier = Math.max(1, Math.min(3, tier));
        return ArmorSets.get(setId).map(set -> new Active(set, finalTier));
    }

    @Override
    public String source() { return "set"; }

    @Override
    public Map<Stat, Double> stats(Player player) {
        Optional<Active> found = detect(player.getInventory().getArmorContents());
        if (found.isEmpty()) {
            active.remove(player.getUniqueId());
            return Map.of();
        }
        active.put(player.getUniqueId(), found.get());
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        double factor = ArmorSet.tierFactor(found.get().tier());
        found.get().set().bonusStats().forEach((stat, value) -> out.put(stat, Math.round(value * factor * 10) / 10.0));
        return out;
    }

    @Override
    public int signature(Player player) { return 0; }

    /** Lore lines describing the full set bonus of a set (shown on every piece). */
    public List<Component> loreFor(String setId) {
        List<Component> out = new ArrayList<>();
        Optional<ArmorSet> found = ArmorSets.get(setId);
        if (found.isEmpty()) {
            return out;
        }
        ArmorSet set = found.get();
        out.add(Component.text("Full Set Bonus: " + set.bonusName(), NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        for (String line : set.bonusLines()) {
            out.add(MINI.deserialize(fill(setId, line)).decoration(TextDecoration.ITALIC, false));
        }
        StringBuilder stats = new StringBuilder();
        for (Map.Entry<Stat, Double> entry : set.bonusStats().entrySet()) {
            if (stats.length() > 0) {
                stats.append(", ");
            }
            stats.append(entry.getKey().displayName()).append(' ').append(win.vampyuyu.survival.items.gear.ItemRender
                .signed(entry.getValue(), entry.getKey()));
        }
        out.add(Component.text("Bonus (x tier): " + stats, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        return out;
    }

    private String fill(String setId, String line) {
        String out = line;
        for (String key : DEFAULTS.getOrDefault(setId, Map.of()).keySet()) {
            double value = param(setId, key);
            out = out.replace("{" + key + "}", value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value));
        }
        return out;
    }
}
