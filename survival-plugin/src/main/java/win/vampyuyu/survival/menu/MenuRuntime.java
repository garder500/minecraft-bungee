package win.vampyuyu.survival.menu;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Shared state of the menu feature: the core, the current configuration and the helpers every part needs. Services of
 * other features are looked up on every call (they may be absent, and they enable after or before this feature).
 */
final class MenuRuntime {
    final Core core;
    private volatile MenuConfig config;
    private volatile QuestEngine engine;

    MenuRuntime(Core core, MenuConfig config) {
        this.core = core;
        this.config = config;
        this.engine = new QuestEngine(config.catalog);
    }

    MenuConfig config() {
        return config;
    }

    QuestEngine engine() {
        return engine;
    }

    void reload(MenuConfig fresh) {
        this.config = fresh;
        this.engine = new QuestEngine(fresh.catalog);
    }

    // ---------------------------------------------------------------- profile sections (main thread)

    MenuData prefs(UUID player) {
        return core.profiles().section(player, "menu", MenuData.class, MenuData::new);
    }

    QuestData quests(UUID player) {
        return core.profiles().section(player, "quests", QuestData.class, QuestData::new);
    }

    FairyData fairy(UUID player) {
        return core.profiles().section(player, "fairy_souls", FairyData.class, FairyData::new);
    }

    void dirty(UUID player) {
        core.profiles().markDirty(player);
    }

    // ---------------------------------------------------------------- other features

    Optional<IslandApi> islands() { return core.service(IslandApi.class); }

    Optional<EconomyApi> economy() { return core.service(EconomyApi.class); }

    Optional<SkillsApi> skills() { return core.service(SkillsApi.class); }

    Optional<StatsApi> stats() { return core.service(StatsApi.class); }

    Optional<DungeonApi> dungeons() { return core.service(DungeonApi.class); }

    boolean inDungeon(Player player) {
        return dungeons().map(d -> d.inDungeon(player)).orElse(false);
    }

    static boolean inSkyblock(World world) {
        return world != null && SkyblockWorlds.isSkyblock(world.getName());
    }

    // ---------------------------------------------------------------- sounds

    /** Plays a configured sound (see {@code sounds:}) to the player unless they turned sounds off. */
    void play(Player player, String key) {
        if (!player.isOnline() || !prefs(player.getUniqueId()).sounds) {
            return;
        }
        playAlways(player, key);
    }

    void playAlways(Player player, String key) {
        String name = config.sounds.get(key);
        if (name == null || name.isBlank()) {
            return;
        }
        NamespacedKey sound = NamespacedKey.fromString(name.toLowerCase(java.util.Locale.ROOT));
        Sound resolved = sound == null ? null : Registry.SOUNDS.get(sound);
        if (resolved != null) {
            player.playSound(player.getLocation(), resolved, 0.8f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- linked features' commands

    /** Registered, usable command for a menu entry (first match of {@code commands.<entry>}), if any. */
    Optional<String> commandFor(String entry, org.bukkit.command.CommandSender who) {
        List<String> names = config.commands.getOrDefault(entry, List.of());
        for (String name : names) {
            String first = name.split(" ")[0];
            Command command = Bukkit.getCommandMap().getCommand(first);
            if (command != null && command.testPermissionSilent(who)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /** True when the entry's command exists (permission aside), used to grey out entries of disabled features. */
    boolean commandExists(String entry) {
        for (String name : config.commands.getOrDefault(entry, List.of())) {
            if (Bukkit.getCommandMap().getCommand(name.split(" ")[0]) != null) {
                return true;
            }
        }
        return false;
    }

    /** The configured create-island command when it exists and the player may use it. */
    Optional<String> createIslandCommand(org.bukkit.command.CommandSender who) {
        String command = config.createIslandCommand;
        if (command.isBlank()) {
            return Optional.empty();
        }
        Command registered = Bukkit.getCommandMap().getCommand(command.split(" ")[0]);
        return registered != null && registered.testPermissionSilent(who) ? Optional.of(command) : Optional.empty();
    }
}
