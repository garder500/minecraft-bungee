package win.vampyuyu.survival.items.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.stats.PlayerState;

/** All abilities and the single code path that runs one (cost, cooldown, notices). */
public final class AbilityRegistry {
    private final Map<String, Ability> byId = new LinkedHashMap<>();
    private final Cooldowns cooldowns = new Cooldowns();
    private AbilityEnv env;

    public void env(AbilityEnv env) { this.env = env; }

    public void register(Ability ability) {
        if (byId.putIfAbsent(ability.id(), ability) != null) {
            throw new IllegalStateException("Duplicate ability id: " + ability.id());
        }
    }

    public Optional<Ability> get(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Collection<Ability> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Abilities of an item definition (ids in the {@code abilities} PDC value, comma separated). */
    public List<Ability> forItem(GearInfo info) {
        String ids = info.def().pdcStrings().get(Keys.ABILITIES);
        List<Ability> out = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return out;
        }
        for (String id : ids.split(",")) {
            get(id.trim().toLowerCase(Locale.ROOT)).ifPresent(out::add);
        }
        return out;
    }

    /** Remaining cooldown in millis. */
    public long remaining(Player player, Ability ability) {
        return cooldowns.remaining(player.getUniqueId(), ability.id());
    }

    public void clearCooldowns(Player player) {
        for (Ability ability : byId.values()) {
            cooldowns.clear(player.getUniqueId(), ability.id());
        }
    }

    /** Mana cost after the Wise ultimate. */
    public double cost(Ability ability, PlayerState state) {
        double base = env.mana(ability);
        int wise = state.enchantLevel("wise");
        if (wise > 0) {
            base *= Math.max(0.2, 1.0 - env.gear().enchants().value("wise", wise) / 100.0);
        }
        return Math.max(0, Math.floor(base));
    }

    /**
     * Tries to use an ability: cooldown, mana, executor, then charges. Returns true when it ran.
     * Failures show a short notice in the HUD (never chat spam).
     */
    public boolean use(Player player, ItemStack item, GearInfo info, Ability ability, @Nullable Event event) {
        if (ability.executor() == null) {
            return false;
        }
        PlayerState state = env.engine().state(player);
        long left = cooldowns.remaining(player.getUniqueId(), ability.id());
        if (left > 0) {
            env.notice(player, "§c" + ability.name() + " on cooldown: " + String.format("%.1fs", left / 1000.0), 1200);
            return false;
        }
        double cost = cost(ability, state);
        if (cost > 0 && !state.mana.canAfford(cost)) {
            env.notice(player, "§cNOT ENOUGH MANA", 1200);
            return false;
        }
        boolean ok;
        try {
            ok = ability.executor().run(new AbilityContext(env, player, item, info, state, ability, event));
        } catch (RuntimeException e) {
            env.core().getPlugin().getLogger().warning("Ability " + ability.id() + " failed: " + e);
            return false;
        }
        if (!ok) {
            return false;
        }
        if (cost > 0) {
            state.mana.spend(cost);
        }
        double cd = env.cooldown(ability);
        if (cd > 0) {
            cooldowns.set(player.getUniqueId(), ability.id(), Math.round(cd * 1000));
        }
        if (ability.trigger() != Ability.Trigger.PASSIVE && ability.trigger() != Ability.Trigger.BREAK_BLOCK) {
            env.notice(player, "§b" + (cost > 0 ? "-" + (long) cost + " Mana " : "") + "§7(" + ability.name() + ")", 1500);
        }
        return true;
    }

    /** Runs the SHOOT abilities of a bow. */
    public void onShoot(Player player, ItemStack bow, @Nullable GearInfo info, EntityShootBowEvent event) {
        if (info == null || env == null) {
            return;
        }
        for (Ability ability : forItem(info)) {
            if (ability.trigger() == Ability.Trigger.SHOOT) {
                use(player, bow, info, ability, event);
            }
        }
    }
}
