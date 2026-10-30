package win.vampyuyu.survival.economy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Optional append-only text log of every purse change: {@code economy/ledger/yyyy-MM-dd.log}, written off-thread. */
final class LedgerLog {
    private final Path dir;
    private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "survival-economy-ledger");
        t.setDaemon(true);
        return t;
    });

    LedgerLog(Path root, Logger log) {
        this.dir = root.resolve("economy").resolve("ledger");
        this.log = log;
    }

    void append(EconomyService.Tx tx) {
        String line = tx.t() + "\t" + tx.player() + "\t" + tx.delta() + "\t" + tx.after() + "\t"
            + tx.reason().replace('\t', ' ').replace('\n', ' ') + "\n";
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        try {
            io.execute(() -> {
                try {
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(day + ".log"), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    log.log(Level.WARNING, "Could not append to the economy ledger log", e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    void close() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
