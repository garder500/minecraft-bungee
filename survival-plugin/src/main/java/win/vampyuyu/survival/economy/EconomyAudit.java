package win.vampyuyu.survival.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.JsonStore;

/**
 * Recomputes the economy totals from the data on disk (and memory for online players) and checks every player's
 * ledger chain: each retained entry must satisfy {@code after = previous.after + delta} and the last entry must equal
 * the purse; the bank history is checked the same way; the lifetime counters must satisfy
 * {@code purse = totalEarned - totalSpent} (this one reaches back past the retained ledger). Market contributors add
 * their escrow totals, invariants and burned taxes; the server-wide faucet and sink table is attached to the report.
 */
final class EconomyAudit {
    /** The numbers and problems found. */
    static final class Report {
        int players;
        long purseCents;
        long bankCents;
        final Map<String, Long> extras = new LinkedHashMap<>();
        /**
         * Coins destroyed by the markets WITHOUT passing through a purse debit (sale taxes), added to the destroyed
         * total of the faucet and sink view. Listing fees are taken from the purse (reason {@code auction:fee}), so
         * the flow table already counts them: they are only listed in {@link #info}.
         */
        final Map<String, Long> burns = new LinkedHashMap<>();
        /** Extra figures shown with the report, not part of any total. */
        final Map<String, Long> info = new LinkedHashMap<>();
        final List<String> problems = new ArrayList<>();
        List<Flows.Row> flows = List.of();
        long flowsSince;
        long minted;
        long burned;

        long totalCents() {
            long sum = purseCents + bankCents;
            for (long v : extras.values()) {
                sum += v;
            }
            return sum;
        }
    }

    /** A subsystem holding coins outside purses (bazaar, auction house). Runs on the main thread. */
    interface Contributor {
        void audit(Report report);
    }

    private final Core core;
    private final EconomyService economy;
    private final List<Contributor> contributors = new ArrayList<>();

    EconomyAudit(Core core, EconomyService economy) {
        this.core = core;
        this.economy = economy;
    }

    void addContributor(Contributor c) {
        contributors.add(c);
    }

    /** Runs the audit in the background and calls {@code done} on the main thread. */
    void run(Consumer<Report> done) {
        core.scheduler().async(() -> {
            Report report = new Report();
            try {
                scanPlayers(report);
            } catch (RuntimeException e) {
                report.problems.add("Player scan failed: " + e);
            }
            core.scheduler().sync(() -> {
                report.flows = economy.flows().rows();
                report.flowsSince = economy.flows().since();
                report.minted = economy.flows().minted();
                report.burned = economy.flows().burned();
                for (Contributor c : contributors) {
                    try {
                        c.audit(report);
                    } catch (RuntimeException e) {
                        report.problems.add("Contributor failed: " + e);
                    }
                }
                done.accept(report);
            });
        });
    }

    private void scanPlayers(Report report) {
        Path dir = core.store().root().resolve("players");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                UUID id;
                try {
                    id = UUID.fromString(name.substring(0, name.length() - 5));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                EconomyData data = core.profiles().isLoaded(id) ? snapshot(id) : read(file);
                if (data == null) {
                    continue;
                }
                report.players++;
                report.purseCents += data.cents;
                report.bankCents += data.bankCents;
                check(id, data, report.problems);
            }
        } catch (IOException e) {
            report.problems.add("Could not list player profiles: " + e.getMessage());
        }
    }

    /**
     * A private copy of a loaded profile's data, made under the profile lock (the main thread keeps appending to the
     * live ledger lists while this scan runs on another thread).
     */
    private EconomyData snapshot(UUID id) {
        EconomyData[] copy = new EconomyData[1];
        economy.update(id, (d, txs) -> {
            copy[0] = JsonStore.GSON.fromJson(JsonStore.GSON.toJsonTree(d), EconomyData.class);
            return null;
        });
        return copy[0];
    }

    private static EconomyData read(Path file) {
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement sections = doc.get("sections");
            if (sections == null || !sections.isJsonObject() || !sections.getAsJsonObject().has(EconomyService.SECTION)) {
                return null;
            }
            return JsonStore.GSON.fromJson(sections.getAsJsonObject().get(EconomyService.SECTION), EconomyData.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Checks one player's numbers; problems are appended (at most 25 in total are kept). */
    static void check(UUID id, EconomyData d, List<String> problems) {
        String who = id.toString().substring(0, 8);
        if (d.cents < 0 || d.bankCents < 0) {
            add(problems, who + ": negative balance (purse " + d.cents + ", bank " + d.bankCents + ")");
        }
        if (d.cents != d.totalEarned - d.totalSpent) {
            add(problems, who + ": purse " + d.cents + " differs from lifetime earned " + d.totalEarned
                + " minus spent " + d.totalSpent + " (" + (d.cents - (d.totalEarned - d.totalSpent)) + ")");
        }
        EconomyData.Entry previous = null;
        for (EconomyData.Entry e : d.ledger) {
            if (previous != null && previous.after + e.delta != e.after) {
                add(problems, who + ": ledger chain broken at " + e.t + " (" + e.reason + ")");
                break;
            }
            previous = e;
        }
        if (previous != null && previous.after != d.cents) {
            add(problems, who + ": purse " + d.cents + " differs from the last ledger balance " + previous.after);
        }
        EconomyData.BankEntry last = null;
        for (EconomyData.BankEntry e : d.bankHistory) {
            if (last != null && last.after + e.delta != e.after) {
                add(problems, who + ": bank history chain broken at " + e.t + " (" + e.kind + ")");
                break;
            }
            last = e;
        }
        if (last != null && last.after != d.bankCents) {
            add(problems, who + ": bank " + d.bankCents + " differs from the last history balance " + last.after);
        }
    }

    private static void add(List<String> problems, String text) {
        if (problems.size() < 25) {
            problems.add(text);
        }
    }
}
