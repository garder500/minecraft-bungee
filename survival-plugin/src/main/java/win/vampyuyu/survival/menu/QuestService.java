package win.vampyuyu.survival.menu;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.menu.QuestEngine.Completion;

/**
 * Glue between game events and the {@link QuestEngine}: records progress (main thread, Skyblock worlds only), pays
 * rewards exactly once and keeps unpaid rewards pending when the economy or skills feature is unavailable.
 */
final class QuestService implements win.vampyuyu.survival.api.QuestGateApi {
    private final MenuRuntime rt;
    private final IslandQuestStore islandStore;
    /** Island id -> key of the last fan-out event it counted (the events of one fan-out arrive one after another). */
    private final Map<UUID, Long> lastShare = new HashMap<>();

    QuestService(MenuRuntime rt) {
        this.rt = rt;
        this.islandStore = new IslandQuestStore(rt.core.store(), rt.core.getPlugin().getLogger());
    }

    /** Writes the islands' shared quest records that changed. */
    void flush() {
        islandStore.flush();
    }

    @Override
    public Optional<String> forgeLock(UUID player, String item) {
        return active() ? rt.engine().forgeLock(rt.quests(player), item).map(QuestDef::name) : Optional.empty();
    }

    /** How many items the player could hand in to an active ore quest right now. */
    int handInAvailable(Player player, QuestDef def) {
        QuestData data = rt.quests(player.getUniqueId());
        if (!active() || def.type() != QuestType.ORE_FOUND || !rt.engine().isActive(data, def)
                || !MenuRuntime.inSkyblock(player.getWorld())) {
            return 0;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return 0; // record() would ignore it and the items would be lost
        }
        int missing = def.amount() - rt.engine().progress(data, def, false);
        return rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class)
            .map(api -> Math.min(missing, api.oreHeld(player, def.target()))).orElse(0);
    }

    /** Takes what an active ore quest still needs from the inventory (a Miner's chest, ores found earlier). Returns how many. */
    int handIn(Player player, QuestDef def) {
        int count = handInAvailable(player, def);
        if (count <= 0) {
            return 0;
        }
        int taken = rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class)
            .map(api -> api.takeOre(player, def.target(), count)).orElse(0);
        record(player, QuestType.ORE_FOUND, def.target(), taken);
        return taken;
    }

    private boolean active() {
        return rt.config().questsEnabled;
    }

    LocalDate today() {
        return rt.config().dailyClock.dayOf(java.time.Instant.now());
    }

    // ---------------------------------------------------------------- recording

    /** Records something the player did. Ignored outside Skyblock worlds, in creative/spectator and for offline players. */
    void record(Player player, QuestType type, String subject, int value) {
        record(player, type, subject, value, 0, java.util.Set.of());
    }

    /**
     * Same; {@code shareKey} and {@code sharedWith} come from an event fanned out to the island's members: the island's
     * shared quests count it once and credit every player it reached.
     */
    void record(Player player, QuestType type, String subject, int value, long shareKey, java.util.Set<UUID> sharedWith) {
        if (!active() || value <= 0 || !player.isOnline() || !MenuRuntime.inSkyblock(player.getWorld())) {
            return;
        }
        switch (player.getGameMode()) {
            case CREATIVE, SPECTATOR -> {
                return;
            }
            default -> { }
        }
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        engine.ensureDay(data, today(), rt.config().dailyCount);
        QuestEngine.Event event = new QuestEngine.Event(type, subject, value);
        Optional<IslandInfo> island = sharedIsland(player);
        QuestEngine.Scope scope = island.isPresent() ? QuestEngine.Scope.PERSONAL : QuestEngine.Scope.ALL;
        List<Completion> done = new ArrayList<>(engine.catchUp(data, event, scope));
        done.addAll(engine.apply(data, event, scope));
        finish(player, data, done, true, scope);
        island.ifPresent(info -> recordIsland(player, info, event, shareKey, sharedWith));
    }

    /** Re-checks absolute quests (island ownership, skill levels), rolls the day over and pays what is pending. */
    void sync(Player player) {
        if (!active() || !player.isOnline()) {
            return;
        }
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        migrate(player, data);
        boolean changed = engine.ensureDay(data, today(), rt.config().dailyCount);
        Optional<IslandInfo> island = sharedIsland(player);
        QuestEngine.Scope scope = QuestEngine.Scope.ALL;
        if (island.isPresent()) {
            scope = QuestEngine.Scope.PERSONAL;
            IslandInfo info = island.get();
            IslandQuests shared = islandRecord(info);
            changed |= mirror(player, data, shared);
            welcome(player, info, shared);
            List<Completion> islandDone = engine.sync(shared.quests, def -> probeShared(player, shared, def),
                QuestEngine.Scope.ISLAND);
            if (!islandDone.isEmpty()) {
                completeIsland(info, shared, islandDone);
            }
            settleOwed(player, info, shared);
        }
        List<Completion> done = new ArrayList<>(engine.sync(data, def -> probe(player, def), scope));
        finish(player, data, done, changed || !done.isEmpty(), scope);
        flushPending(player);
    }

    /**
     * A player who started before the quest plan keeps what their data proves, once and without paying anything: the
     * tier quests up to the generator tier they reached, and the cobblestone mining quests up to the generator blocks
     * they broke. Everything else (ores, forging, placing) counts again from the plan's first quest.
     */
    private void migrate(Player player, QuestData data) {
        QuestEngine engine = rt.engine();
        if (data.planMigrated || engine.catalog().categories().isEmpty()) {
            return;
        }
        data.planMigrated = true;
        var api = rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class);
        if (api.isEmpty()) {
            data.planMigrated = false; // the quarry feature is not loaded yet: try again at the next sync
            return;
        }
        int tier = api.get().tier(player.getUniqueId());
        long blocks = api.get().blocksMined(player.getUniqueId());
        for (QuestDef def : engine.catalog().tutorial()) {
            boolean proven = switch (def.type()) {
                case GENERATOR_TIER -> def.amount() <= tier;
                case BREAK_BLOCK -> def.target().equalsIgnoreCase("COBBLESTONE") && def.amount() <= blocks;
                default -> false;
            };
            if (proven) {
                data.completed.add(def.id());
            }
        }
        rt.dirty(player.getUniqueId());
    }

    private OptionalInt probe(Player player, QuestDef def) {
        UUID id = player.getUniqueId();
        return switch (def.type()) {
            case GENERATOR_TIER -> rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class)
                .map(api -> OptionalInt.of(api.tier(id))).orElse(OptionalInt.empty());
            case ISLAND_CREATE -> rt.islands().map(api -> OptionalInt.of(api.islandOf(id).isPresent() ? 1 : 0))
                .orElse(OptionalInt.empty());
            case SKILL_LEVEL -> rt.skills().map(api -> {
                String target = def.target();
                if (target.isBlank() || target.equalsIgnoreCase("ANY")) {
                    int best = 0;
                    for (Skill skill : Skill.values()) {
                        best = Math.max(best, api.level(id, skill));
                    }
                    return OptionalInt.of(best);
                }
                try {
                    return OptionalInt.of(api.level(id, Skill.valueOf(target.toUpperCase(Locale.ROOT))));
                } catch (IllegalArgumentException e) {
                    return OptionalInt.empty();
                }
            }).orElse(OptionalInt.empty());
            case FORGE_ITEM -> OptionalInt.of(forgedProof(player, def.target()));
            default -> OptionalInt.empty();
        };
    }

    /**
     * What a player's state proves was forged before the Forge tally existed: an upgrade already used, a Voidwyrm
     * defeated (its sigil is used up by the summon), machines standing on the island, items carried.
     */
    private int forgedProof(Player player, String item) {
        UUID id = player.getUniqueId();
        int tier = rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class).map(api -> api.tier(id)).orElse(1);
        int count = switch (item) {
            case "bronze_core" -> tier >= 2 ? 1 : 0;
            case "ember_core" -> tier >= 3 ? 1 : 0;
            case "void_lens" -> tier >= 4 || rt.core.service(win.vampyuyu.survival.api.EndAccessApi.class)
                .map(api -> api.canEnterEnd(id)).orElse(false) ? 1 : 0;
            case "void_sigil" -> rt.core.service(win.vampyuyu.survival.api.BossApi.class)
                .map(api -> api.kills(id, "voidwyrm")).orElse(0) > 0 ? 1 : 0;
            // a Miner II is forged out of a Miner I
            case "miner_1" -> placed(id, "miner_1") + placed(id, "miner_2");
            default -> placed(id, item);
        };
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
            if (item.equals(rt.core.items().idOf(stack).orElse(null))) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private int placed(UUID player, String item) {
        return rt.islands().flatMap(api -> api.islandOf(player)).flatMap(island -> rt.core
            .service(win.vampyuyu.survival.api.MachineApi.class).map(api -> api.placed(island.owner(), item))).orElse(0);
    }

    private void finish(Player player, QuestData data, List<Completion> done, boolean dirty, QuestEngine.Scope scope) {
        QuestEngine engine = rt.engine();
        List<Completion> all = new ArrayList<>(done);
        // completions can unlock absolute quests that are already satisfied
        if (!done.isEmpty()) {
            all.addAll(engine.sync(data, def -> probe(player, def), scope));
        }
        for (Completion completion : all) {
            pay(player, data, completion);
        }
        int plan = (int) all.stream().filter(c -> !c.daily()).count();
        if (plan > 0) {
            record(player, QuestType.EVENT, "quest_done", plan); // the "complete N quests" quests count these
        }
        Optional<QuestEngine.StreakBonus> bonus = engine.claimDailyBonus(data, today());
        bonus.ifPresent(b -> payBonus(player, data, b));
        if (dirty || bonus.isPresent()) {
            rt.dirty(player.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- rewards

    private void pay(Player player, QuestData data, Completion completion) {
        pay(player, data, completion, completion.def().rewards().coins());
    }

    /** Pays a quest; {@code coins} replaces the quest's coins (an island's daily cap can lower them). */
    private void pay(Player player, QuestData data, Completion completion, double coins) {
        QuestDef def = completion.def();
        QuestDef.Rewards rewards = def.rewards();
        String reason = (completion.daily() ? "daily:" : "quest:") + def.id();
        List<String> parts = new ArrayList<>();
        if (coins > 0) {
            parts.add("<gold>+" + Msg.number(coins) + " coins");
            giveCoins(player, data, coins, reason);
        }
        rewards.skillXp().forEach((skillName, xp) -> {
            Optional<Skill> skill = skill(skillName);
            if (skill.isPresent() && xp > 0) {
                parts.add("<aqua>+" + Msg.number(xp) + " " + skill.get().displayName() + " XP");
                giveXp(player, data, skill.get(), xp, reason);
            }
        });
        rewards.items().forEach((itemId, amount) -> {
            if (rt.core.items().def(itemId).isPresent()) {
                parts.add("<green>" + amount + "x " + rt.core.items().def(itemId).get().name());
                give(player, rt.core.items().create(itemId, amount));
            } else {
                rt.core.debug("Quest " + def.id() + ": reward item '" + itemId + "' does not exist; skipped");
            }
        });
        announce(player, def, completion.daily(), parts);
    }

    private void payBonus(Player player, QuestData data, QuestEngine.StreakBonus bonus) {
        MenuConfig cfg = rt.config();
        double coins = cfg.dailyBonusCoins + cfg.dailyStreakCoins * Math.min(bonus.streak(), cfg.dailyStreakCap);
        List<String> parts = new ArrayList<>();
        if (coins > 0) {
            giveCoins(player, data, coins, "daily:bonus");
            parts.add("<gold>+" + Msg.number(coins) + " coins");
        }
        if (cfg.dailyBonusXp > 0) {
            for (Skill skill : new Skill[] {Skill.COMBAT, Skill.MINING, Skill.FARMING, Skill.FORAGING}) {
                // one bonus chunk spread evenly keeps it simple and fair
                giveXp(player, data, skill, cfg.dailyBonusXp / 4, "daily:bonus");
            }
            parts.add("<aqua>+" + Msg.number(cfg.dailyBonusXp) + " XP");
        }
        player.sendMessage(Component.empty());
        rt.core.msg().mini(player, "<green><bold>ALL DAILY QUESTS DONE!</bold> <gray>Streak: <yellow>" + bonus.streak()
            + (bonus.streak() == 1 ? " day" : " days") + (parts.isEmpty() ? "" : " <dark_gray>| " + String.join("<gray>, ", parts)));
        rt.play(player, "quest-complete");
    }

    private Optional<Skill> skill(String name) {
        try {
            return Optional.of(Skill.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void giveCoins(Player player, QuestData data, double coins, String reason) {
        Optional<win.vampyuyu.survival.api.EconomyApi> economy = rt.economy();
        if (economy.isPresent()) {
            economy.get().add(player.getUniqueId(), coins, reason);
        } else {
            data.pendingCoins += coins;
        }
    }

    private void giveXp(Player player, QuestData data, Skill skill, double xp, String reason) {
        Optional<win.vampyuyu.survival.api.SkillsApi> skills = rt.skills();
        if (skills.isPresent()) {
            skills.get().addXp(player, skill, xp, reason);
        } else {
            data.pendingXp.merge(skill.name(), xp, Double::sum);
        }
    }

    private void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        left.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** Pays rewards that were earned while a feature was off. Each unit is zeroed BEFORE it is paid. */
    void flushPending(Player player) {
        QuestData data = rt.quests(player.getUniqueId());
        if (data.pendingCoins > 0 && rt.economy().isPresent()) {
            double coins = data.pendingCoins;
            data.pendingCoins = 0;
            rt.dirty(player.getUniqueId());
            rt.economy().get().add(player.getUniqueId(), coins, "quest:pending");
            rt.core.msg().ok(player, "You received " + Msg.coins(coins) + " from earlier quests.");
        }
        if (!data.pendingXp.isEmpty() && rt.skills().isPresent()) {
            Map<String, Double> pending = new HashMap<>(data.pendingXp);
            data.pendingXp.clear();
            rt.dirty(player.getUniqueId());
            pending.forEach((name, xp) -> skill(name).ifPresent(s -> rt.skills().get().addXp(player, s, xp, "quest:pending")));
        }
    }

    private void announce(Player player, QuestDef def, boolean daily, List<String> rewards) {
        Component header = Component.text(daily ? "DAILY QUEST COMPLETE" : "QUEST COMPLETE", NamedTextColor.GREEN,
            TextDecoration.BOLD);
        player.sendMessage(Component.empty());
        player.sendMessage(header.append(Component.text("  " + def.name(), NamedTextColor.YELLOW)
            .decoration(TextDecoration.BOLD, false)));
        if (!rewards.isEmpty()) {
            player.sendMessage(Msg.component("<gray>Rewards: " + String.join("<gray>, ", rewards)));
        }
        if (!daily) {
            QuestData data = rt.quests(player.getUniqueId());
            List<QuestDef> next = rt.engine().activeTutorial(data);
            if (!next.isEmpty()) {
                player.sendMessage(Msg.component("<gray>Next: <yellow>" + escape(next.get(0).name())
                    + " <dark_gray>- " + escape(next.get(0).description())));
            }
        }
        player.showTitle(Title.title(Component.text(daily ? "Daily Quest" : "Quest Complete", NamedTextColor.GREEN),
            Component.text(def.name(), NamedTextColor.YELLOW),
            Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1500),
                java.time.Duration.ofMillis(400))));
        rt.play(player, "quest-complete");
    }

    private static String escape(String text) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(text);
    }

    // ---------------------------------------------------------------- the island's shared quests

    /**
     * The island whose shared record the player's main-chain quests live in; empty (everything stays personal, as it was)
     * when the player has no island or the plan has no main-chain quest.
     */
    private Optional<IslandInfo> sharedIsland(Player player) {
        boolean any = rt.engine().catalog().tutorial().stream().anyMatch(rt.engine()::islandScope);
        return any ? rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())) : Optional.empty();
    }

    /**
     * The island's record. Created on first use from the personal progress the owner (even offline) and the members online
     * already have, without paying anything, so nobody loses what they did before quests were shared.
     */
    IslandQuests islandRecord(IslandInfo info) {
        boolean fresh = !islandStore.exists(info.id());
        IslandQuests shared = islandStore.get(info.id());
        if (!fresh) {
            return shared;
        }
        QuestEngine engine = rt.engine();
        List<UUID> sources = new ArrayList<>();
        sources.add(info.owner());
        info.members().stream().filter(id -> !id.equals(info.owner()) && org.bukkit.Bukkit.getPlayer(id) != null)
            .forEach(sources::add);
        for (UUID id : sources) {
            QuestData own = rt.quests(id);
            for (QuestDef def : engine.catalog().tutorial()) {
                if (!engine.islandScope(def)) {
                    continue;
                }
                if (own.completed.contains(def.id())) {
                    shared.quests.completed.add(def.id());
                } else if (own.progress.getOrDefault(def.id(), 0) > 0) {
                    shared.quests.progress.merge(def.id(), own.progress.get(def.id()), Math::max);
                }
            }
            own.forged.forEach((item, count) -> shared.quests.forged.merge(item, count, Math::max));
        }
        shared.quests.completed.forEach(shared.quests.progress::remove);
        info.members().forEach(id -> shared.known.add(id.toString()));
        islandStore.markDirty(info.id());
        return shared;
    }

    /** Copies the island's state of the main chain into a member's own data. True when something changed. */
    private boolean mirror(Player player, QuestData data, IslandQuests shared) {
        boolean changed = false;
        for (QuestDef def : rt.engine().catalog().tutorial()) {
            if (rt.engine().islandScope(def)) {
                changed |= mirrorQuest(data, shared, def);
            }
        }
        if (changed) {
            rt.dirty(player.getUniqueId());
        }
        return changed;
    }

    private boolean mirrorQuest(QuestData data, IslandQuests shared, QuestDef def) {
        boolean changed = false;
        if (shared.quests.completed.contains(def.id())) {
            changed |= data.completed.add(def.id());
            changed |= data.progress.remove(def.id()) != null;
        } else {
            changed |= data.completed.remove(def.id());
            int progress = shared.quests.progress.getOrDefault(def.id(), 0);
            if (progress > 0) {
                changed |= !Integer.valueOf(progress).equals(data.progress.put(def.id(), progress));
            } else {
                changed |= data.progress.remove(def.id()) != null;
            }
        }
        return changed;
    }

    private void mirrorOnline(IslandInfo info, IslandQuests shared, List<QuestDef> defs) {
        for (UUID id : info.members()) {
            Player member = org.bukkit.Bukkit.getPlayer(id);
            if (member == null) {
                continue;
            }
            QuestData data = rt.quests(id);
            boolean changed = false;
            for (QuestDef def : defs) {
                changed |= mirrorQuest(data, shared, def);
            }
            if (changed) {
                rt.dirty(id);
            }
        }
    }

    /** What the player's state says about an island quest's value; remembers it as the player's contribution. */
    private OptionalInt probeShared(Player player, IslandQuests shared, QuestDef def) {
        OptionalInt value = probe(player, def);
        if (value.isPresent() && value.getAsInt() > 0) {
            credit(shared, def, player.getUniqueId(), value.getAsInt());
        }
        return value;
    }

    private void credit(IslandQuests shared, QuestDef def, UUID player, int value) {
        Map<String, Integer> byPlayer = shared.contributions.computeIfAbsent(def.id(), k -> new HashMap<>());
        if (def.type().absolute()) {
            byPlayer.merge(player.toString(), Math.min(value, def.amount()), Math::max);
        } else {
            byPlayer.merge(player.toString(), value, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
    }

    private void recordIsland(Player player, IslandInfo info, QuestEngine.Event event, long shareKey,
            java.util.Set<UUID> sharedWith) {
        if (shareKey != 0) {
            if (Long.valueOf(shareKey).equals(lastShare.get(info.id()))) {
                return; // another player's copy of an event the island already counted
            }
            lastShare.put(info.id(), shareKey);
        }
        QuestEngine engine = rt.engine();
        IslandQuests shared = islandRecord(info);
        List<QuestDef> matching = engine.matching(shared.quests, event, QuestEngine.Scope.ISLAND);
        if (matching.isEmpty()) {
            return;
        }
        java.util.Set<UUID> doers = shareKey != 0 && !sharedWith.isEmpty() ? sharedWith : java.util.Set.of(player.getUniqueId());
        for (QuestDef def : matching) {
            doers.forEach(id -> credit(shared, def, id, event.value()));
        }
        List<Completion> done = new ArrayList<>(engine.catchUp(shared.quests, event, QuestEngine.Scope.ISLAND));
        done.addAll(engine.apply(shared.quests, event, QuestEngine.Scope.ISLAND));
        if (!done.isEmpty()) {
            done.addAll(engine.sync(shared.quests, def -> probeShared(player, shared, def), QuestEngine.Scope.ISLAND));
        }
        islandStore.markDirty(info.id());
        mirrorOnline(info, shared, matching);
        if (!done.isEmpty()) {
            completeIsland(info, shared, done);
        }
    }

    /** True when the member earns the reward of an island quest: enough of it was theirs, and time was spent on the island. */
    private boolean eligible(IslandInfo info, IslandQuests shared, QuestDef def, UUID member) {
        int added = shared.contributions.getOrDefault(def.id(), Map.of()).getOrDefault(member.toString(), 0);
        MenuConfig cfg = rt.config();
        int need = def.type().absolute() ? def.amount() : def.amount() <= 10 ? 1 : (int) Math.ceil(cfg.coopMinShare * def.amount());
        if (added < need) {
            return false;
        }
        return member.equals(info.owner()) || shared.seconds.getOrDefault(member.toString(), 0) >= cfg.coopMinMinutes * 60;
    }

    /**
     * The island finished quests: members who contributed are paid in full (now when online, else at their next visit), the
     * others only see the quest ticked. The unlocks are the quests' completion itself, so they are the island's, once.
     */
    private void completeIsland(IslandInfo info, IslandQuests shared, List<Completion> done) {
        QuestEngine engine = rt.engine();
        for (Completion completion : done) {
            QuestDef def = completion.def();
            if (def.type().absolute()) {
                // members online who reached the level too count, even if the island completed on someone else's check
                for (UUID id : info.members()) {
                    Player member = org.bukkit.Bukkit.getPlayer(id);
                    OptionalInt value = member == null ? OptionalInt.empty() : probe(member, def);
                    if (value.isPresent() && value.getAsInt() > 0) {
                        credit(shared, def, id, value.getAsInt());
                    }
                }
            }
            for (UUID id : info.members()) {
                Player member = org.bukkit.Bukkit.getPlayer(id);
                boolean paid = eligible(info, shared, def, id);
                if (paid) {
                    if (!shared.settled.computeIfAbsent(def.id(), k -> new java.util.HashSet<>()).add(id.toString())) {
                        continue;
                    }
                    if (member != null) {
                        payShared(member, shared, completion);
                    } else {
                        shared.owed.computeIfAbsent(id.toString(), k -> new java.util.LinkedHashSet<>()).add(def.id());
                    }
                } else if (member != null) {
                    rt.core.msg().mini(member, "<green><bold>ISLAND QUEST DONE</bold> <yellow>" + escape(def.name()));
                }
            }
        }
        islandStore.markDirty(info.id());
        mirrorOnline(info, shared, done.stream().map(Completion::def).toList());
        for (UUID id : info.members()) {
            Player member = org.bukkit.Bukkit.getPlayer(id);
            if (member != null) {
                int counted = (int) done.stream().filter(c -> eligible(info, shared, c.def(), id)).count();
                if (counted > 0) {
                    record(member, QuestType.EVENT, "quest_done", counted);
                }
                rt.dirty(id);
                sync(member); // the personal quests waiting on these ones may open now
            }
        }
    }

    /** Pays an island quest to one member, within the island's daily coin cap. */
    private void payShared(Player member, IslandQuests shared, Completion completion) {
        QuestData data = rt.quests(member.getUniqueId());
        double coins = completion.def().rewards().coins();
        String day = today().toString();
        if (!day.equals(shared.coinDay)) {
            shared.coinDay = day;
            shared.coinsToday = 0;
        }
        double cap = rt.config().coopDailyCoinCap;
        if (cap > 0) {
            coins = Math.max(0, Math.min(coins, cap - shared.coinsToday));
        }
        shared.coinsToday += coins;
        rt.core.msg().mini(member, "<green><bold>ISLAND QUEST DONE</bold> <gray>You helped with it.");
        pay(member, data, completion, coins);
        rt.dirty(member.getUniqueId());
    }

    /** Pays the rewards that waited for this member to come online. */
    private void settleOwed(Player player, IslandInfo info, IslandQuests shared) {
        java.util.Set<String> owed = shared.owed.remove(player.getUniqueId().toString());
        if (owed == null) {
            return;
        }
        islandStore.markDirty(info.id());
        for (String id : owed) {
            rt.engine().catalog().tutorialQuest(id)
                .ifPresent(def -> payShared(player, shared, new Completion(def, false)));
        }
    }

    /** Arrival of a new member: the kit of the island's current generator tier, once. */
    private void welcome(Player player, IslandInfo info, IslandQuests shared) {
        if (!shared.known.add(player.getUniqueId().toString())) {
            return;
        }
        islandStore.markDirty(info.id());
        if (player.getUniqueId().equals(info.owner())) {
            return;
        }
        int tier = rt.core.service(win.vampyuyu.survival.api.GeneratorApi.class).map(api -> api.tier(info.owner())).orElse(1);
        List<List<String>> kit = rt.config().coopKit;
        List<String> ids = kit.isEmpty() ? List.of() : kit.get(Math.max(0, Math.min(tier, kit.size()) - 1));
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            ItemStack stack = rt.core.items().def(id).isPresent() ? rt.core.items().create(id) : materialStack(id);
            if (stack != null) {
                give(player, stack);
                names.add(id);
            }
        }
        rt.core.msg().mini(player, "<green>Welcome to the island! <gray>Its finished quests are ticked for you, and you earn the next "
            + "ones by helping." + (names.isEmpty() ? "" : " Here is a starter kit."));
    }

    private static ItemStack materialStack(String name) {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
        return material == null || material.isAir() ? null : new ItemStack(material);
    }

    /** Counts the time a player spends on their own island (called by a repeating task, {@code seconds} apart). */
    void presence(Player player, int seconds) {
        if (!active() || !player.isOnline()) {
            return;
        }
        Optional<IslandInfo> own = sharedIsland(player);
        if (own.isEmpty()) {
            return;
        }
        boolean there = rt.islands().flatMap(api -> api.islandAt(player.getLocation()))
            .map(at -> at.id().equals(own.get().id())).orElse(false);
        if (there) {
            IslandQuests shared = islandRecord(own.get());
            shared.seconds.merge(player.getUniqueId().toString(), seconds, Integer::sum);
            islandStore.markDirty(own.get().id());
        }
    }

    // ---------------------------------------------------------------- queries used by the sidebar and GUI

    Optional<QuestEngine.Tracked> tracked(Player player) {
        if (!active()) {
            return Optional.empty();
        }
        return rt.engine().tracked(rt.quests(player.getUniqueId()));
    }

    /** Tracks a quest in the sidebar; returns false for an unknown or inactive quest. */
    boolean track(Player player, String key) {
        QuestData data = rt.quests(player.getUniqueId());
        QuestEngine engine = rt.engine();
        if (key.startsWith("daily:")) {
            String id = key.substring(6);
            boolean ok = engine.dailyQuests(data).stream().anyMatch(d -> d.id().equals(id))
                && !data.dailyDone.contains(id);
            if (!ok) {
                return false;
            }
        } else {
            Optional<QuestDef> def = engine.catalog().tutorialQuest(key);
            if (def.isEmpty() || !engine.isActive(data, def.get())) {
                return false;
            }
        }
        data.tracked = key;
        rt.dirty(player.getUniqueId());
        return true;
    }

    /** Called for every player command (typed or run by a menu). */
    void command(Player player, String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            lower = lower.substring(lower.indexOf(':') + 1);
        }
        // PlayerCommandPreprocessEvent fires before dispatch: an unknown command, or one the player may not run,
        // must not count (a disabled bazaar would otherwise still complete its quest).
        org.bukkit.command.Command command = org.bukkit.Bukkit.getCommandMap().getCommand(lower);
        if (command == null || !command.testPermissionSilent(player)) {
            return;
        }
        StringBuilder subject = new StringBuilder(lower);
        subject.append('|').append(command.getName().toLowerCase(Locale.ROOT));
        for (String alias : command.getAliases()) {
            subject.append('|').append(alias.toLowerCase(Locale.ROOT));
        }
        record(player, QuestType.COMMAND, subject.toString(), 1);
    }
}
