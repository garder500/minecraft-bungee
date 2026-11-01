package win.vampyuyu.survival.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The private bearer API behind the panel's Skyblock data page (same pattern and token as the arena engine's
 * {@code ArenaHttpApi}: the controller reaches it on the container's private network, port 8086). Fixed routes, no
 * command dispatch; every table name is checked against {@link ContentDomains} by {@link DataAdmin}.
 *
 * <pre>
 * GET    /skyblock/data                                  domains, roots, children
 * GET    /skyblock/data/audit?table&amp;key&amp;actor&amp;offset&amp;limit
 * GET    /skyblock/data/{domain}/{table}                 columns of the root and of its children
 * GET    /skyblock/data/{domain}/{table}/rows?q&amp;origin&amp;offset&amp;limit
 * GET    /skyblock/data/{domain}/{table}/row?key=        one row with its children
 * PUT    /skyblock/data/{domain}/{table}/row?key=&amp;actor=      body {"row": {...}, "children": {"table": [...]}}
 * DELETE /skyblock/data/{domain}/{table}/row?key=&amp;actor=
 * POST   /skyblock/data/{domain}/{table}/reset?key=&amp;actor=   back to the plugin's default
 * </pre>
 * The row key is a query parameter, not a path segment: keys such as {@code economy.bank.interest} or a pattern with a
 * slash must survive every proxy on the way.
 */
public final class DataHttpApi implements AutoCloseable {
    /** Reply of {@link #route}: an HTTP status and a JSON-able body. */
    public record Reply(int status, Object body) {}

    private static final int MAX_BODY = 256 * 1024;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

    private final DataAdmin admin;
    private final Logger log;
    private final byte[] authorization;
    private final HttpServer server;
    private final ThreadPoolExecutor threads = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
        r -> Thread.ofPlatform().daemon().name("skyblock-data-api").unstarted(r), new ThreadPoolExecutor.AbortPolicy());

    public DataHttpApi(DataAdmin admin, String token, int port, Logger log) throws IOException {
        this.admin = admin;
        this.log = log;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 16);
        server.setExecutor(threads);
        server.createContext("/skyblock/data", this::handle);
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }

    private void handle(HttpExchange request) throws IOException {
        try {
            String auth = request.getRequestHeaders().getFirst("Authorization");
            Reply reply;
            if (!MessageDigest.isEqual(authorization, auth == null ? new byte[0] : auth.getBytes(StandardCharsets.UTF_8))) {
                reply = new Reply(401, Map.of("error", "unauthorized"));
            } else {
                String body = "";
                if (request.getRequestMethod().equals("PUT") || request.getRequestMethod().equals("POST")) {
                    byte[] bytes = request.getRequestBody().readNBytes(MAX_BODY + 1);
                    if (bytes.length > MAX_BODY) {
                        throw new DataAdmin.Invalid("Request exceeds " + MAX_BODY / 1024 + " KiB");
                    }
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
                reply = route(request.getRequestMethod(), request.getRequestURI().getPath(), query(request.getRequestURI().getRawQuery()), body);
            }
            send(request, reply);
        } catch (RuntimeException e) {
            send(request, failure(e));
        } finally {
            request.close();
        }
    }

    private Reply failure(RuntimeException e) {
        if (e instanceof DataAdmin.Invalid invalid) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", invalid.getMessage());
            out.put("column", invalid.column());
            return new Reply(invalid.status(), out);
        }
        if (e instanceof JsonParseException || e instanceof IllegalStateException || e instanceof ClassCastException) {
            return new Reply(400, Map.of("error", "Invalid JSON body"));
        }
        log.log(Level.WARNING, "Skyblock data request failed", e);
        return new Reply(503, Map.of("error", "The Skyblock database is unavailable: " + e.getMessage()));
    }

    private static void send(HttpExchange request, Reply reply) throws IOException {
        byte[] bytes = GSON.toJson(reply.body()).getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.sendResponseHeaders(reply.status(), bytes.length);
        request.getResponseBody().write(bytes);
    }

    static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.putIfAbsent(k, v);
        }
        return out;
    }

    private static int number(Map<String, String> q, String name, int fallback) {
        try {
            return q.containsKey(name) ? Integer.parseInt(q.get(name)) : fallback;
        } catch (NumberFormatException e) {
            throw new DataAdmin.Invalid("Invalid " + name);
        }
    }

    private static String actor(Map<String, String> q) {
        String actor = q.get("actor");
        if (actor == null || actor.isBlank()) throw new DataAdmin.Invalid("actor is required for a change");
        return actor;
    }

    private static String key(Map<String, String> q) {
        String key = q.get("key");
        if (key == null || key.isEmpty()) throw new DataAdmin.Invalid("key is required");
        return key;
    }

    /** Routing without a socket (tests call it directly). Throws {@link DataAdmin.Invalid} or a Gson error for a bad request. */
    Reply route(String method, String path, Map<String, String> q, String body) {
        String[] parts = path.replaceAll("^/+|/+$", "").split("/");
        // parts: skyblock, data, [domain, table, verb]
        if (parts.length < 2 || !parts[0].equals("skyblock") || !parts[1].equals("data")) {
            throw new DataAdmin.Invalid("Unknown endpoint", null, 404);
        }
        if (parts.length == 2 && method.equals("GET")) {
            return new Reply(200, Map.of("domains", admin.domains()));
        }
        if (parts.length == 3 && parts[2].equals("audit") && method.equals("GET")) {
            return new Reply(200, admin.audit(q.get("table"), q.get("key"), q.get("actor"), number(q, "offset", 0), number(q, "limit", 50)));
        }
        if (parts.length == 4 && method.equals("GET")) {
            return new Reply(200, admin.schema(parts[2], parts[3]));
        }
        if (parts.length == 5) {
            String domain = parts[2];
            String table = parts[3];
            switch (parts[4] + " " + method) {
                case "rows GET":
                    return new Reply(200, admin.rows(domain, table, q.get("q"), q.get("origin"), number(q, "offset", 0), number(q, "limit", 50)));
                case "row GET":
                    return new Reply(200, admin.get(domain, table, key(q)));
                case "row PUT": {
                    JsonElement parsed = JsonParser.parseString(body.isBlank() ? "{}" : body);
                    JsonObject in = parsed.getAsJsonObject();
                    Map<String, Object> row = in.has("row") && !in.get("row").isJsonNull()
                        ? GSON.fromJson(in.get("row"), new TypeToken<Map<String, Object>>() {}.getType()) : null;
                    Map<String, List<Map<String, Object>>> children = in.has("children") && !in.get("children").isJsonNull()
                        ? GSON.fromJson(in.get("children"), new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType()) : null;
                    return new Reply(200, admin.put(domain, table, key(q), row, children, actor(q)));
                }
                case "row DELETE":
                    admin.delete(domain, table, key(q), actor(q));
                    return new Reply(200, Map.of("ok", true));
                case "reset POST":
                    return new Reply(200, admin.reset(domain, table, key(q), actor(q)));
                default:
                    break;
            }
        }
        throw new DataAdmin.Invalid("Unknown endpoint", null, 404);
    }
}
