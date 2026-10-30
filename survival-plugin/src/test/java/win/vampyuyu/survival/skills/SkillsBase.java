package win.vampyuyu.survival.skills;

import static org.junit.Assert.assertFalse;

import org.bukkit.World;
import org.junit.After;
import org.junit.Before;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.core.Core;

/** Boots the whole plugin (every feature, the real skills.yml) with fake stats and economy services. */
abstract class SkillsBase {
    ServerMock server;
    SurvivalPlugin plugin;
    Core core;
    SkillsService service;
    Fakes.Stats stats;
    Fakes.Economy economy;
    World world;
    PlayerMock player;

    @Before
    public void boot() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        core = plugin.core();
        service = (SkillsService) core.require(win.vampyuyu.survival.api.SkillsApi.class);
        stats = new Fakes.Stats();
        economy = new Fakes.Economy();
        core.replace(win.vampyuyu.survival.api.StatsApi.class, stats);
        core.replace(win.vampyuyu.survival.api.EconomyApi.class, economy);
        world = server.addSimpleWorld("sb_world");
        player = server.addPlayer("Miner");
        player.setLocation(new org.bukkit.Location(world, 0.5, 70, 0.5));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        // The real island protection is on: these tests break blocks in a bare world, outside any island.
        player.addAttachment(plugin, win.vampyuyu.survival.island.IslandService.BYPASS, true);
    }

    /**
     * Moves {@link #world} to a plain world and lets skills earn there ({@code only_skyblock_worlds: false}). Needed by
     * tests that fire events with no player behind them (spawner spawns, pistons, fire): in a Skyblock world the real
     * island protection rightly cancels them when no island covers the spot, and a cancelled event never reaches the
     * skills handlers (which ignore cancelled events).
     */
    void useUnprotectedWorld() throws java.io.IOException {
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("skills.yml");
        String text = java.nio.file.Files.readString(file);
        java.nio.file.Files.writeString(file, text.replace("only_skyblock_worlds: true", "only_skyblock_worlds: false"));
        server.dispatchCommand(server.getConsoleSender(), "skilladmin reload");
        assertFalse(service.config().onlySkyblockWorlds);
        world = server.addSimpleWorld("plain_skills_world");
        // quests (menu feature) count kills by the killer's world: outside Skyblock they pay no bonus XP
        player.setLocation(new org.bukkit.Location(world, 0.5, 70, 0.5));
    }

    @After
    public void shutdown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }
}
