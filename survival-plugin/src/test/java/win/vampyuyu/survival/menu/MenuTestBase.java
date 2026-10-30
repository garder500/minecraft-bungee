package win.vampyuyu.survival.menu;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.SkillsApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;

/** Boots a Core with the shipped menu.yml on MockBukkit, plus fake services of the other features. */
abstract class MenuTestBase {
    ServerMock server;
    JavaPlugin plugin;
    Core core;
    MenuRuntime rt;

    @Before
    public void baseSetUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        core = new Core(plugin);
        core.start();
        rt = new MenuRuntime(core, shipped());
    }

    @After
    public void baseTearDown() {
        core.stop();
        MockBukkit.unmock();
    }

    static MenuConfig shipped() {
        return new MenuConfig(shippedYaml());
    }

    static YamlConfiguration shippedYaml() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
            MenuTestBase.class.getResourceAsStream("/menu.yml"), StandardCharsets.UTF_8));
    }

    World world(String name) {
        World world = server.getWorld(name);
        return world != null ? world : server.addSimpleWorld(name);
    }

    /** A player standing in the named world. */
    PlayerMock playerIn(String worldName) {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world(worldName), 0, 64, 0));
        return player;
    }

    // ------------------------------------------------------------ fakes

    static final class FakeEconomy implements EconomyApi {
        final Map<UUID, Double> balances = new HashMap<>();
        final List<String> reasons = new ArrayList<>();

        @Override public double coins(UUID player) { return balances.getOrDefault(player, 0.0); }
        @Override public boolean has(UUID player, double amount) { return coins(player) >= amount; }
        @Override public boolean take(UUID player, double amount, String reason) {
            if (!has(player, amount)) { return false; }
            balances.merge(player, -amount, Double::sum);
            return true;
        }
        @Override public void add(UUID player, double amount, String reason) {
            balances.merge(player, amount, Double::sum);
            reasons.add(reason);
        }
        @Override public void addTransactionListener(Consumer<Transaction> listener) { }
    }

    static final class FakeSkills implements SkillsApi {
        final Map<String, Integer> levels = new HashMap<>();
        final Map<Skill, Double> xpGiven = new EnumMap<>(Skill.class);

        @Override public void addXp(Player player, Skill skill, double amount, String source) {
            xpGiven.merge(skill, amount, Double::sum);
        }
        @Override public int level(UUID player, Skill skill) { return levels.getOrDefault(skill.name(), 0); }
        @Override public double xp(UUID player, Skill skill) { return 0; }
        @Override public int maxLevel(Skill skill) { return 50; }
    }

    static final class FakeStats implements StatsApi {
        final Map<Stat, Double> modifiers = new EnumMap<>(Stat.class);
        int recomputes;

        @Override public double stat(Player player, Stat stat) { return stat.base() + modifiers.getOrDefault(stat, 0.0); }
        @Override public void addModifier(UUID player, String source, Stat stat, double amount) {
            if (source.equals("fairy")) { modifiers.put(stat, amount); }
        }
        @Override public void removeModifiers(UUID player, String source) {
            if (source.equals("fairy")) { modifiers.clear(); }
        }
        @Override public void recompute(Player player) { recomputes++; }
    }

    static final class FakeIslands implements IslandApi {
        final Map<UUID, IslandInfo> byPlayer = new HashMap<>();
        final List<String> teleports = new ArrayList<>();
        boolean homeWorks = true;
        /** The next this many teleportHub calls fail (the hub is still generating). */
        int hubFailures;
        /** When set, a successful teleportHub really moves the player into this world. */
        World hubWorld;

        IslandInfo give(UUID owner, Location center) {
            IslandInfo info = new IslandInfo(UUID.randomUUID(), owner, Set.of(owner), center.getWorld().getName(), 0, 0,
                center, 50);
            byPlayer.put(owner, info);
            return info;
        }

        @Override public Optional<IslandInfo> islandAt(Location location) {
            return byPlayer.values().stream().filter(i -> i.center().getWorld().equals(location.getWorld())
                && i.center().distanceSquared(location) < 50 * 50).findFirst();
        }
        @Override public Optional<IslandInfo> islandOf(UUID player) { return Optional.ofNullable(byPlayer.get(player)); }
        @Override public boolean isSkyblockWorld(World world) { return world.getName().startsWith("sb_"); }
        @Override public boolean canBuild(Player player, Location location) { return true; }
        @Override public boolean teleportHome(Player player) {
            teleports.add("home:" + player.getName());
            return homeWorks && byPlayer.containsKey(player.getUniqueId());
        }
        @Override public boolean teleportHub(Player player) {
            teleports.add("hub:" + player.getName());
            if (hubFailures > 0) {
                hubFailures--;
                return false;
            }
            if (hubWorld != null) {
                player.teleport(new Location(hubWorld, 0, 64, 0));
            }
            return true;
        }
        @Override public Collection<IslandInfo> all() { return byPlayer.values(); }
    }
}
