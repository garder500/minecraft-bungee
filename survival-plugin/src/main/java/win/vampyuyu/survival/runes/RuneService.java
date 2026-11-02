package win.vampyuyu.survival.runes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.RuneApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.items.gear.ItemKind;

/**
 * Engraving, scraping and crafting of runes. A stat rune is written into the item's own {@code bonus_<stat>} (so
 * {@code Items.statsOf} counts it like any other bonus) and recorded in the {@code runes} PDC value, which is what
 * the slot count and the effects read.
 */
public final class RuneService implements RuneApi {
    /** Result of an action: shown to the player as is. */
    public record Outcome(boolean ok, String message) {}

    /** One thing a craft spends. */
    public record Cost(String label, Predicate<ItemStack> match, int amount) {}

    private final Core core;
    private volatile RuneConfig config;
    private final Map<UUID, Long> verdantReady = new HashMap<>();

    public RuneService(Core core, RuneConfig config) {
        this.core = core;
        this.config = config;
    }

    public RuneConfig config() { return config; }

    public void config(RuneConfig config) { this.config = config; }

    // ---------------------------------------------------------------- reading

    @Override
    public Map<String, Integer> runesOf(ItemStack stack) {
        return RuneRules.parse(core.items().getString(stack, RuneItems.ENGRAVED).orElse(null));
    }

    /** The rune a rune item is, with its rank; empty for any other stack. */
    public Optional<Map.Entry<Rune, Integer>> runeItem(ItemStack stack) {
        Optional<String> id = core.items().getString(stack, RuneItems.RUNE);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return config.rune(id.get()).map(r -> Map.entry(r, core.items().getLong(stack, RuneItems.RANK).orElse(0L).intValue()));
    }

    private static int tier(CustomItemDef def) {
        return def.tier() == null ? 1 : def.tier();
    }

    // ---------------------------------------------------------------- engrave / scrape

    /** Engraves {@code rune} (a rune item) onto {@code target}, modified in place. The caller consumes the rune item. */
    public Outcome engrave(ItemStack target, ItemStack rune) {
        Optional<Map.Entry<Rune, Integer>> source = runeItem(rune);
        Optional<CustomItemDef> def = core.items().idOf(target).flatMap(core.items()::def);
        if (source.isEmpty() || def.isEmpty()) {
            return new Outcome(false, "Nothing happens.");
        }
        ItemKind kind = ItemKind.of(def.get().typeLabel());
        Map<String, Integer> engraved = runesOf(target);
        String problem = RuneRules.problem(config, source.get().getKey(), source.get().getValue(), engraved, kind.weapon(),
            kind.armor(), tier(def.get()));
        if (problem != null) {
            return new Outcome(false, problem);
        }
        Map<String, Integer> next = new java.util.LinkedHashMap<>(engraved);
        next.put(source.get().getKey().id(), source.get().getValue());
        write(target, engraved, next);
        Rune r = source.get().getKey();
        return new Outcome(true, "Engraved " + r.name() + (r.realm() ? "" : " " + Rune.roman(source.get().getValue()))
            + " (" + next.size() + "/" + config.slotsFor(tier(def.get())) + " slots).");
    }

    /** Removes every rune from {@code target}, in place. The runes are lost. */
    public Outcome scrape(ItemStack target) {
        Map<String, Integer> engraved = runesOf(target);
        if (engraved.isEmpty()) {
            return new Outcome(false, "This item carries no rune.");
        }
        write(target, engraved, Map.of());
        return new Outcome(true, "Scraped " + engraved.size() + " rune(s). They crumble to dust.");
    }

    /** Moves the item's stat bonuses from the old runes to the new ones, stores the list and redraws the lore. */
    private void write(ItemStack stack, Map<String, Integer> before, Map<String, Integer> after) {
        Map<Stat, Double> was = RuneRules.bonus(config, before);
        Map<Stat, Double> now = RuneRules.bonus(config, after);
        Set<Stat> stats = EnumSet.noneOf(Stat.class);
        stats.addAll(was.keySet());
        stats.addAll(now.keySet());
        Map<Stat, Double> current = core.items().bonuses(stack);
        for (Stat stat : stats) {
            double delta = now.getOrDefault(stat, 0.0) - was.getOrDefault(stat, 0.0);
            if (delta != 0) {
                core.items().setBonus(stack, stat, Math.max(0, current.getOrDefault(stat, 0.0) + delta));
            }
        }
        core.items().set(stack, RuneItems.ENGRAVED, RuneRules.format(after));
        core.items().refresh(stack);
    }

    /** The lore lines of an item's runes (registered with {@code Items.loreExtra}). */
    public List<Component> lore(ItemStack stack) {
        Map<String, Integer> engraved = runesOf(stack);
        if (engraved.isEmpty()) {
            return List.of();
        }
        int slots = core.items().idOf(stack).flatMap(core.items()::def).map(d -> config.slotsFor(tier(d))).orElse(engraved.size());
        List<Component> out = new ArrayList<>();
        out.add(line(Component.text("Runes (" + engraved.size() + "/" + slots + ")", NamedTextColor.LIGHT_PURPLE)));
        engraved.forEach((id, rank) -> config.rune(id).ifPresent(rune -> {
            String what = rune.realm() ? effectText(rune) : "+" + config.value(rank) + " " + rune.stat().displayName();
            out.add(line(Component.text(" ✧ " + rune.name() + (rune.realm() ? "" : " " + Rune.roman(rank)) + ": ", NamedTextColor.GRAY)
                .append(Component.text(what, rune.realm() ? NamedTextColor.GOLD : NamedTextColor.AQUA))));
        }));
        return out;
    }

    private String effectText(Rune rune) {
        return switch (rune.id()) {
            case "scorch" -> "your Surge sets the target ablaze";
            case "verdant" -> "taking a hit grants Regeneration";
            default -> "a Realm effect";
        };
    }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---------------------------------------------------------------- crafting

    /** What the craft of {@code rune} at {@code rank} spends (rank is ignored for a Realm rune). */
    public List<Cost> costs(Rune rune, int rank) {
        List<Cost> costs = new ArrayList<>();
        if (rune.realm()) {
            costs.add(item(RuneItems.POWDER, config.realmPowder()));
            costs.add(item(rune.component(), config.realmResource()));
            if (config.realmDiamonds() > 0) {
                costs.add(vanilla(Material.DIAMOND, config.realmDiamonds()));
            }
        } else if (rank == 1) {
            costs.add(item(RuneItems.POWDER, config.powder()));
        } else if (rank == 2) {
            costs.add(item(rune.itemId(1), config.fuse()));
            costs.add(item(rune.component(), 1));
        } else {
            costs.add(item(rune.itemId(2), config.fuse()));
            costs.add(vanilla(Material.DIAMOND, 1));
        }
        return costs;
    }

    private Cost item(String id, int amount) {
        String label = core.items().def(id).map(CustomItemDef::name).orElse(id);
        return new Cost(label, stack -> core.items().is(stack, id), amount);
    }

    private Cost vanilla(Material material, int amount) {
        String label = material == Material.DIAMOND ? "Diamond" : material.name();
        return new Cost(label, stack -> stack.getType() == material && !core.items().isCustom(stack), amount);
    }

    /** How many of a cost the player holds in the 36 main slots. */
    public static int count(Player player, Cost cost) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && !stack.getType().isAir() && cost.match().test(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void take(Player player, Cost cost) {
        int left = cost.amount();
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir() || !cost.match().test(stack)) {
                continue;
            }
            int used = Math.min(left, stack.getAmount());
            left -= used;
            if (used == stack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - used);
            }
        }
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** Crafts one rune from the player's inventory. Nothing is spent unless everything is there. */
    public Outcome craft(Player player, Rune rune, int rank) {
        int rankToMake = rune.realm() ? 0 : rank;
        List<Cost> costs = costs(rune, rankToMake);
        for (Cost cost : costs) {
            int have = count(player, cost);
            if (have < cost.amount()) {
                return new Outcome(false, "You need " + cost.amount() + " " + cost.label() + " (you have " + have + ").");
            }
        }
        costs.forEach(cost -> take(player, cost));
        give(player, core.items().create(rune.itemId(rankToMake)));
        return new Outcome(true, "Crafted a " + rune.name() + " Rune" + (rune.realm() ? "" : " " + Rune.roman(rank)) + ".");
    }

    /** Grinds one amethyst shard into Rune Powder. */
    public Outcome grind(Player player) {
        Cost shard = vanilla(Material.AMETHYST_SHARD, 1);
        if (count(player, shard) < 1) {
            return new Outcome(false, "You need an Amethyst Shard.");
        }
        take(player, shard);
        give(player, core.items().create(RuneItems.POWDER, config.grindOutput()));
        return new Outcome(true, "Ground an Amethyst Shard into " + config.grindOutput() + " Rune Powder.");
    }

    // ---------------------------------------------------------------- Realm effects

    @Override
    public void onSurge(Player attacker, LivingEntity target, ItemStack weapon) {
        if (!runesOf(weapon).containsKey("scorch")) {
            return;
        }
        target.setFireTicks(Math.max(target.getFireTicks(), config.burnTicks()));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.5, 0), 14, 0.25, 0.3, 0.25, 0.02);
    }

    /** Verdant: a hit taken grants Regeneration I, at most once per cooldown. */
    public void onHurt(Player victim) {
        boolean worn = false;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece != null && runesOf(piece).containsKey("verdant")) {
                worn = true;
                break;
            }
        }
        if (!worn) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (verdantReady) {
            if (verdantReady.getOrDefault(victim.getUniqueId(), 0L) > now) {
                return;
            }
            verdantReady.put(victim.getUniqueId(), now + config.regenCooldownSeconds() * 1000L);
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, config.regenTicks(), 0, true, true));
        victim.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, victim.getLocation().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0);
    }

    public void forget(UUID player) {
        synchronized (verdantReady) {
            verdantReady.remove(player);
        }
    }
}
