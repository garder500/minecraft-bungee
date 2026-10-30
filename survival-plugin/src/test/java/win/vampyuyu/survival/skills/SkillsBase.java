package win.vampyuyu.survival.skills;

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
        core = plugin.core();
        service = (SkillsService) core.require(win.vampyuyu.survival.api.SkillsApi.class);
        stats = new Fakes.Stats();
        economy = new Fakes.Economy();
        core.register(win.vampyuyu.survival.api.StatsApi.class, stats);
        core.register(win.vampyuyu.survival.api.EconomyApi.class, economy);
        world = server.addSimpleWorld("sb_world");
        player = server.addPlayer("Miner");
        player.setLocation(new org.bukkit.Location(world, 0.5, 70, 0.5));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
    }

    @After
    public void shutdown() {
        MockBukkit.unmock();
    }
}
