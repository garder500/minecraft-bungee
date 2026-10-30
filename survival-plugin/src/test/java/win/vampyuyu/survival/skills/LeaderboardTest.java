package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;

public class LeaderboardTest extends SkillsBase {
    private void profile(Path dir, UUID id, String name, String skillsJson) throws Exception {
        Files.writeString(dir.resolve(id + ".json"), "{\"version\":1,\"name\":\"" + name + "\",\"sections\":{\"skills\":"
            + skillsJson + "}}", StandardCharsets.UTF_8);
    }

    @Test
    public void ranksOfflinePlayersFromTheirDocuments() throws Exception {
        Path dir = Files.createTempDirectory("sbplayers");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        profile(dir, a, "Alpha", "{\"xp\":{\"mining\":1000.0,\"combat\":50.0}}");
        profile(dir, b, "Bravo", "{\"xp\":{\"mining\":9000.0}}");
        profile(dir, c, "Charlie", "{\"collections\":{}}"); // no xp section: not ranked
        Files.writeString(dir.resolve("broken.json"), "{{{", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("not-a-uuid.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(UUID.randomUUID() + ".json"), "{{{", StandardCharsets.UTF_8);
        Leaderboard board = new Leaderboard(java.util.logging.Logger.getAnonymousLogger());
        SkillsConfig cfg = service.config();
        board.rebuild(dir, cfg);
        List<Leaderboard.Entry> mining = board.top("mining", 10, service);
        assertEquals(2, mining.stream().filter(e -> e.id().equals(a) || e.id().equals(b)).count());
        assertEquals("Bravo", mining.get(0).name());
        assertEquals("Alpha", mining.get(1).name());
        assertEquals(cfg.levels.levelOf(9000, 60), (int) mining.get(0).level());
        assertEquals(1, board.top("combat", 10, service).size());
        assertTrue(board.top("alchemy", 10, service).isEmpty());
        List<Leaderboard.Entry> average = board.top(Leaderboard.AVERAGE, 10, service);
        assertEquals("Bravo", average.get(0).name());
        assertTrue(board.builtAt() > 0);
    }

    @Test
    public void onlinePlayersAreOverlaidLive() throws Exception {
        Path dir = Files.createTempDirectory("sbplayers2");
        profile(dir, player.getUniqueId(), "Miner", "{\"xp\":{\"mining\":10.0}}");
        UUID other = UUID.randomUUID();
        profile(dir, other, "Other", "{\"xp\":{\"mining\":500.0}}");
        Leaderboard board = new Leaderboard(java.util.logging.Logger.getAnonymousLogger());
        board.rebuild(dir, service.config());
        assertEquals("Other", board.top("mining", 5, service).get(0).name());
        service.adminSetXp(player.getUniqueId(), Skill.MINING, 4000);
        List<Leaderboard.Entry> top = board.top("mining", 5, service);
        assertEquals("Miner", top.get(0).name());
        assertEquals(4000, top.get(0).score(), 0);
        assertEquals(2, top.size()); // the stale cached row of the online player was replaced, not duplicated
    }

    @Test
    public void sizeIsLimitedAndMissingFolderIsFine() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("leaderboard.size", 3);
        SkillsConfig cfg = new SkillsConfig(yaml);
        Path dir = Files.createTempDirectory("sbplayers3");
        for (int i = 0; i < 6; i++) {
            profile(dir, UUID.randomUUID(), "P" + i, "{\"xp\":{\"mining\":" + (100 + i) + ".0}}");
        }
        Leaderboard board = new Leaderboard(java.util.logging.Logger.getAnonymousLogger());
        board.rebuild(dir, cfg);
        assertEquals(3, board.top("mining", 100, service).stream().filter(e -> e.name().startsWith("P")).count());
        board.rebuild(dir.resolve("missing"), cfg);
        assertTrue(board.top("mining", 100, service).stream().noneMatch(e -> e.name().startsWith("P")));
    }
}
