package win.vampyuyu.survival.items.pet;

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
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.Keys;
import win.vampyuyu.survival.items.pet.PetType.Perk;
import win.vampyuyu.survival.items.stats.StatProvider;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Pet ownership, levels, stats and the "pet" stat source. Persisted in the profile section {@code pets}. */
public final class PetService implements StatProvider {
    public static final String SECTION = "pets";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** The summoned pet of a player, derived once per change. */
    public record Active(PetData.Entry entry, PetType type, int level, Rarity rarity) {}

    private final Core core;
    private final Gear gear;
    private final StatsEngine engine;
    private final PetVisuals visuals;
    private volatile ItemsSettings settings;
    private volatile PetLeveling leveling;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();

    public PetService(Core core, Gear gear, StatsEngine engine, PetVisuals visuals, ItemsSettings settings) {
        this.core = core;
        this.gear = gear;
        this.engine = engine;
        this.visuals = visuals;
        settings(settings);
    }

    public void settings(ItemsSettings settings) {
        this.settings = settings;
        this.leveling = new PetLeveling(settings.petBaseXp, settings.petXpGrowth, settings.petRarityXp);
    }

    public ItemsSettings settings() { return settings; }

    public PetLeveling leveling() { return leveling; }

    public Gear gear() { return gear; }

    public PetVisuals visuals() { return visuals; }

    public PetData data(UUID id) {
        return core.profiles().section(id, SECTION, PetData.class, PetData::new);
    }

    public Optional<PetData.Entry> find(UUID owner, String entryId) {
        return data(owner).pets.stream().filter(e -> e.id.equals(entryId)).findFirst();
    }

    public Optional<Active> activeOf(UUID owner) {
        return Optional.ofNullable(active.get(owner));
    }

    private void bump(UUID id) {
        versions.merge(id, 1, Integer::sum);
        engine.markDirty(id, false);
    }

    // ---------------------------------------------------------------- derived values

    public Rarity rarityOf(PetData.Entry entry) {
        try {
            return Rarity.valueOf(entry.rarity);
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    /** Rarity used for stats: one tier higher with a Tier Boost held (never above MYTHIC). */
    public Rarity effectiveRarity(PetData.Entry entry) {
        Rarity base = rarityOf(entry);
        if ("pet_tier_boost".equals(entry.held) && base.ordinal() < Rarity.MYTHIC.ordinal()) {
            return Rarity.values()[base.ordinal() + 1];
        }
        return base;
    }

    public int level(PetData.Entry entry) {
        return leveling.levelFor(entry.xp, rarityOf(entry));
    }

    /** Stat multiplier of a rarity (config {@code pets.stat-multiplier}). */
    public double statMultiplier(Rarity rarity) {
        double fallback = switch (rarity) {
            case COMMON -> 0.5;
            case UNCOMMON -> 0.7;
            case RARE -> 1.0;
            case EPIC -> 1.35;
            case LEGENDARY -> 1.8;
            default -> 2.3;
        };
        return settings.tuned("pets.stat-multiplier." + rarity.name(), fallback);
    }

    /** Pure stat computation for a pet (also shown in the menu). */
    public Map<Stat, Double> statsFor(PetType type, int level, Rarity effective, @Nullable String held) {
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        double mult = statMultiplier(effective);
        type.perLevel().forEach((stat, perLevel) -> out.merge(stat, round(perLevel * level * mult), Double::sum));
        for (Perk perk : type.perks()) {
            if (perk.kind() == PetType.Kind.STAT && perk.stat() != null) {
                out.merge(perk.stat(), round(perk.value(level) * mult), Double::sum);
            }
        }
        if (held != null) {
            switch (held) {
                case "pet_big_teeth" -> out.merge(Stat.CRIT_CHANCE, 5.0, Double::sum);
                case "pet_hardened_scales" -> out.merge(Stat.DEFENSE, 25.0, Double::sum);
                case "pet_lucky_clover" -> {
                    out.merge(Stat.PET_LUCK, 5.0, Double::sum);
                    out.merge(Stat.MAGIC_FIND, 5.0, Double::sum);
                }
                default -> { }
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private Active derive(PetData.Entry entry) {
        PetType type = PetTypes.get(entry.type).orElse(null);
        if (type == null) {
            return null;
        }
        return new Active(entry, type, level(entry), effectiveRarity(entry));
    }

    // ---------------------------------------------------------------- StatProvider

    @Override
    public String source() { return "pet"; }

    @Override
    public Map<Stat, Double> stats(Player player) {
        Active a = active.get(player.getUniqueId());
        if (a == null) {
            return Map.of();
        }
        return statsFor(a.type(), a.level(), a.rarity(), a.entry().held.isEmpty() ? null : a.entry().held);
    }

    @Override
    public int signature(Player player) {
        return versions.getOrDefault(player.getUniqueId(), 0);
    }

    // ---------------------------------------------------------------- collection

    /** Adds a pet to the collection. Returns empty when the collection is full. */
    public Optional<PetData.Entry> add(UUID owner, String typeId, Rarity rarity, double xp, @Nullable String held) {
        if (PetTypes.get(typeId).isEmpty()) {
            return Optional.empty();
        }
        PetData data = data(owner);
        if (data.pets.size() >= settings.petMaxOwned) {
            return Optional.empty();
        }
        PetData.Entry entry = new PetData.Entry();
        entry.id = UUID.randomUUID().toString();
        entry.type = typeId;
        entry.rarity = rarity.name();
        entry.xp = Math.max(0, xp);
        entry.held = held == null ? "" : held;
        data.pets.add(entry);
        core.profiles().markDirty(owner);
        return Optional.of(entry);
    }

    /** Removes a pet from the collection (despawning it when active). */
    public boolean remove(UUID owner, String entryId) {
        PetData data = data(owner);
        boolean removed = data.pets.removeIf(e -> e.id.equals(entryId));
        if (removed) {
            if (entryId.equals(data.active)) {
                data.active = "";
                dismiss(owner);
            }
            core.profiles().markDirty(owner);
        }
        return removed;
    }

    // ---------------------------------------------------------------- summon

    /** Summons a pet (replacing the current one). */
    public boolean summon(Player player, String entryId) {
        Optional<PetData.Entry> entry = find(player.getUniqueId(), entryId);
        if (entry.isEmpty()) {
            return false;
        }
        Active a = derive(entry.get());
        if (a == null) {
            return false;
        }
        PetData data = data(player.getUniqueId());
        data.active = entryId;
        core.profiles().markDirty(player.getUniqueId());
        active.put(player.getUniqueId(), a);
        bump(player.getUniqueId());
        if (engine.active(player)) {
            visuals.spawn(player, a.type());
        }
        return true;
    }

    /** Despawns the pet but remembers it as the active one (world change, logout). */
    public void dismiss(UUID owner) {
        active.remove(owner);
        visuals.despawn(owner);
        bump(owner);
    }

    /** Player command: stop having a pet out. */
    public void despawn(UUID owner) {
        PetData data = data(owner);
        data.active = "";
        core.profiles().markDirty(owner);
        dismiss(owner);
    }

    /** Re-creates the display and the cache for the remembered active pet (join, respawn, world change). */
    public void restore(Player player) {
        PetData data = data(player.getUniqueId());
        if (data.active.isEmpty() || !engine.active(player)) {
            if (active.containsKey(player.getUniqueId()) && !engine.active(player)) {
                dismiss(player.getUniqueId());
            }
            return;
        }
        Optional<PetData.Entry> entry = find(player.getUniqueId(), data.active);
        if (entry.isEmpty()) {
            data.active = "";
            return;
        }
        Active a = derive(entry.get());
        if (a == null) {
            return;
        }
        active.put(player.getUniqueId(), a);
        bump(player.getUniqueId());
        visuals.spawn(player, a.type());
    }

    public void forget(UUID owner) {
        active.remove(owner);
        versions.remove(owner);
        visuals.despawn(owner);
    }

    /** Re-derives the cache after the active pet's entry changed (xp, held item). */
    public void refresh(UUID owner) {
        PetData data = data(owner);
        Active current = active.get(owner);
        if (current == null) {
            return;
        }
        find(owner, data.active).map(this::derive).ifPresent(a -> active.put(owner, a));
        bump(owner);
    }

    // ---------------------------------------------------------------- experience

    /**
     * Adds experience to a pet. Returns the number of levels gained and tells the owner when online.
     */
    public int addXp(UUID owner, PetData.Entry entry, double amount) {
        if (amount <= 0 || !Double.isFinite(amount)) {
            return 0;
        }
        Rarity rarity = rarityOf(entry);
        int before = leveling.levelFor(entry.xp, rarity);
        double cap = leveling.totalForLevel(PetLeveling.MAX_LEVEL, rarity);
        entry.xp = Math.min(cap, entry.xp + amount);
        int after = leveling.levelFor(entry.xp, rarity);
        core.profiles().markDirty(owner);
        if (after > before) {
            Player player = org.bukkit.Bukkit.getPlayer(owner);
            PetType type = PetTypes.get(entry.type).orElse(null);
            if (player != null && type != null) {
                core.msg().mini(player, "<green>Your <white>" + type.name() + "</white> leveled up to level <aqua>" + after + "</aqua>!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
            }
            if (entry.id.equals(data(owner).active)) {
                refresh(owner);
            }
        }
        return after - before;
    }

    /** Experience a pet gets for {@code gained} skill XP of {@code skill} (0 when the skill does not feed it). */
    public double xpFor(PetData.Entry entry, Skill skill, double gained) {
        PetType type = PetTypes.get(entry.type).orElse(null);
        if (type == null) {
            return 0;
        }
        double share = type.skill() == skill ? settings.petXpShare : settings.petOtherSkillShare;
        double bonus = "pet_xp_share".equals(entry.held) ? 1.25 : 1.0;
        return gained * share * bonus;
    }

    // ---------------------------------------------------------------- egg items

    /** Creates the item form of a pet (the egg). */
    public ItemStack createEgg(PetData.Entry entry) {
        ItemStack egg = gear.items().create("pet_egg");
        PetType type = PetTypes.get(entry.type).orElseThrow();
        gear.items().set(egg, Keys.PET_TYPE, entry.type);
        gear.items().set(egg, Keys.PET_RARITY, entry.rarity);
        gear.items().set(egg, Keys.PET_XP, entry.xp);
        gear.items().set(egg, Keys.PET_ID, entry.id);
        if (!entry.held.isEmpty()) {
            gear.items().set(egg, Keys.PET_HELD, entry.held);
        }
        ItemMeta meta = egg.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString("vampyuyu:items/pet_" + type.id());
        if (model != null) {
            meta.setItemModel(model);
        }
        egg.setItemMeta(meta);
        gear.rebuild(egg);
        return egg;
    }

    /** Reads an egg back into an entry (not yet added to any collection). */
    public Optional<PetData.Entry> readEgg(@Nullable ItemStack stack) {
        if (stack == null || !gear.items().is(stack, "pet_egg")) {
            return Optional.empty();
        }
        Optional<String> type = gear.items().getString(stack, Keys.PET_TYPE);
        if (type.isEmpty() || PetTypes.get(type.get()).isEmpty()) {
            return Optional.empty();
        }
        PetData.Entry entry = new PetData.Entry();
        entry.id = gear.items().getString(stack, Keys.PET_ID).orElse(UUID.randomUUID().toString());
        entry.type = type.get();
        entry.rarity = gear.items().getString(stack, Keys.PET_RARITY).orElse("COMMON");
        entry.xp = gear.items().getDouble(stack, Keys.PET_XP).orElse(0.0);
        entry.held = gear.items().getString(stack, Keys.PET_HELD).orElse("");
        return Optional.of(entry);
    }

    /** Name shown on an egg: {@code [Lvl 12] Wolf}. */
    public @Nullable String eggName(GearInfo info, ItemStack stack) {
        if (!info.id().equals("pet_egg")) {
            return null;
        }
        return readEgg(stack).map(entry -> "[Lvl " + level(entry) + "] " + PetTypes.get(entry.type).get().name())
            .orElse(null);
    }

    /** Lore lines of an egg or any pet description (stats, perks, progress). */
    public List<Component> eggLore(GearInfo info, ItemStack stack) {
        if (!info.id().equals("pet_egg")) {
            return List.of();
        }
        return readEgg(stack).map(this::describe).orElse(List.of());
    }

    /** Full description of a pet as lore components. */
    public List<Component> describe(PetData.Entry entry) {
        PetType type = PetTypes.get(entry.type).orElse(null);
        if (type == null) {
            return List.of();
        }
        Rarity rarity = rarityOf(entry);
        Rarity effective = effectiveRarity(entry);
        int level = level(entry);
        List<Component> out = new ArrayList<>();
        out.add(line(Component.text(type.skill().displayName() + " pet", NamedTextColor.DARK_GRAY)));
        out.add(Component.empty());
        statsFor(type, level, effective, entry.held.isEmpty() ? null : entry.held).forEach((stat, value) -> {
            if (value != 0) {
                out.add(line(Component.text(stat.displayName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text("+" + (value == Math.rint(value) ? Long.toString(value.longValue()) : String.valueOf(value))
                        + (stat.percent() ? "%" : "") + " " + stat.symbol(), stat.color()))));
            }
        });
        out.add(Component.empty());
        double mult = statMultiplier(effective);
        for (Perk perk : type.perks()) {
            out.add(line(Component.text(perk.name(), NamedTextColor.GOLD)));
            double value = perk.kind() == PetType.Kind.STAT ? round(perk.value(level) * mult) : round(perk.value(level));
            out.add(line(MINI.deserialize(perk.description().replace("{v}",
                value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value)))));
        }
        out.add(Component.empty());
        if (level >= PetLeveling.MAX_LEVEL) {
            out.add(line(Component.text("MAX LEVEL", NamedTextColor.AQUA, TextDecoration.BOLD)));
        } else {
            double into = leveling.xpIntoLevel(entry.xp, rarity);
            double need = leveling.xpToNext(level, rarity);
            out.add(line(Component.text("Progress to level " + (level + 1) + ": ", NamedTextColor.GRAY)
                .append(Component.text(Math.round(into * 100.0 / need) + "%", NamedTextColor.YELLOW))));
            out.add(line(Component.text(PetLeveling.bar(into / need) + " ", NamedTextColor.GREEN)
                .append(Component.text(Msg.number(into) + "/" + Msg.number(need) + " XP", NamedTextColor.YELLOW))));
        }
        if (!entry.held.isEmpty()) {
            out.add(Component.empty());
            out.add(line(Component.text("Held item: ", NamedTextColor.GRAY).append(Component.text(
                gear.items().def(entry.held).map(d -> d.name()).orElse(entry.held), NamedTextColor.GOLD))));
        }
        if (effective != rarity) {
            out.add(line(Component.text("Tier boosted to " + effective.label(), effective.color())));
        }
        return out;
    }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
