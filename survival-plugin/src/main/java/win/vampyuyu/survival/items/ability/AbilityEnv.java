package win.vampyuyu.survival.items.ability;

import java.util.Optional;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.combat.CombatService;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Services shared by every ability executor. */
public final class AbilityEnv {
    private final Core core;
    private final StatsEngine engine;
    private final Gear gear;
    private final CombatService combat;
    private volatile ArrowTagger tagger = (from, to, factor) -> { };

    public AbilityEnv(Core core, StatsEngine engine, Gear gear, CombatService combat) {
        this.core = core;
        this.engine = engine;
        this.gear = gear;
        this.combat = combat;
    }

    public Core core() { return core; }

    public ArrowTagger tagger() { return tagger; }

    public void tagger(ArrowTagger tagger) { this.tagger = tagger; }

    /** Shows a short message in the HUD (the HUD is the only action bar writer). */
    public void notice(Player player, String text, long millis) {
        var st = engine.state(player);
        st.notice = text;
        st.noticeUntil = System.currentTimeMillis() + millis;
    }

    public StatsEngine engine() { return engine; }

    public Gear gear() { return gear; }

    public CombatService combat() { return combat; }

    public ItemsSettings settings() { return engine.settings(); }

    public double param(Ability ability, String key) {
        double fallback = ability.params().getOrDefault(key, 0.0);
        return settings().tuned("abilities." + ability.id() + "." + key, fallback);
    }

    public double mana(Ability ability) {
        return settings().tuned("abilities." + ability.id() + ".mana", ability.mana());
    }

    public double cooldown(Ability ability) {
        return settings().tuned("abilities." + ability.id() + ".cooldown", ability.cooldownSeconds());
    }

    /** Entities an ability may damage or push: living, not players / NPCs / pets / villagers / armor stands. */
    public boolean targetable(Player caster, @Nullable LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead() || entity.equals(caster)) {
            return false;
        }
        if (entity instanceof Player || entity instanceof ArmorStand || entity instanceof Villager
                || entity instanceof WanderingTrader || entity.hasMetadata("NPC")) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !(entity instanceof Animals) || settings().b("abilities.hit-animals", true);
    }

    /** True when the player may change blocks at the location (island protection), true without the island feature. */
    public boolean canBuild(Player player, org.bukkit.Location where) {
        Optional<IslandApi> island = core.service(IslandApi.class);
        return island.map(api -> api.canBuild(player, where)).orElse(true);
    }
}
