package win.vampyuyu.survival.back;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class BackServiceTest {
    private static final BackService.Spot SPOT = new BackService.Spot("world", 1, 64, 2, 90, 0);
    private final UUID player = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000_000);

    private BackService service() throws Exception {
        return new BackService(Files.createTempDirectory("back").resolve("back.json"), now::get, BackService.COOLDOWN_MS);
    }

    @Test
    public void noDeathMeansNothingToReturnTo() throws Exception {
        assertTrue(service().peek(player, false) instanceof BackService.Result.NoDeath);
    }

    @Test
    public void cooldownStartsOnUseNotOnDeath() throws Exception {
        BackService s = service();
        s.recordDeath(player, SPOT);
        assertEquals(new BackService.Result.Ok(SPOT), s.peek(player, false));
        s.confirm(player, false);
        s.recordDeath(player, SPOT);
        now.addAndGet(60_000);
        var r = (BackService.Result.Cooldown) s.peek(player, false);
        assertEquals(240_000, r.remainingMs());
        now.addAndGet(240_000);
        assertEquals(new BackService.Result.Ok(SPOT), s.peek(player, false));
    }

    @Test
    public void bypassIgnoresAndDoesNotStartCooldown() throws Exception {
        BackService s = service();
        s.recordDeath(player, SPOT);
        s.confirm(player, true);
        s.recordDeath(player, SPOT);
        assertEquals(new BackService.Result.Ok(SPOT), s.peek(player, false));
    }

    @Test
    public void stateSurvivesARestart() throws Exception {
        Path file = Files.createTempDirectory("back").resolve("back.json");
        BackService a = new BackService(file, now::get, BackService.COOLDOWN_MS);
        a.recordDeath(player, SPOT);
        a.confirm(player, false);
        a.recordDeath(player, SPOT);
        BackService b = new BackService(file, now::get, BackService.COOLDOWN_MS);
        b.load();
        assertTrue(b.peek(player, false) instanceof BackService.Result.Cooldown);
    }
}
