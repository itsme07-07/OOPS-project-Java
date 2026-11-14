package relay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class RelayServer implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String COOKIE = "relay_session";
    private static final int MAX_BODY = 65536;
    private static final int MAX_ATTACHMENT_BODY = 1500000;
    private static final Map<String, String> ASSETS = Map.of(
        "/index.html", "text/html; charset=utf-8", "/app.js", "text/javascript; charset=utf-8",
        "/style.css", "text/css; charset=utf-8", "/mark.svg", "image/svg+xml");
    private static final Set<String> POST_ENDPOINTS = Set.of("/api/register", "/api/login", "/api/logout", "/api/action", "/api/attachment");
    private static final System.Logger LOG = System.getLogger(RelayServer.class.getName());

    private final HttpServer server;
    private final SessionStore sessions = new SessionStore();
    private final AuthLimiter limiter = new AuthLimiter();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
        4, 16, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.CallerRunsPolicy());
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private final Desk desk;
    private final boolean demo;
    private final boolean secureCookie;

    RelayServer(InetSocketAddress address, Desk desk, boolean demo, boolean secureCookie) throws IOException {
        this.desk = desk;
        this.demo = demo;
        this.secureCookie = secureCookie;
        server = HttpServer.create(address, 64);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    void start() {
        server.start();
        monitor.scheduleWithFixedDelay(() -> {
            try {
                desk.monitor();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "SLA monitoring will retry on the next cycle", e);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    int port() {
        return server.getAddress().getPort();
    }

    @Override public void close() {
        server.stop(0);
        monitor.shutdownNow();
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            dispatch(exchange);
        } catch (Problem e) {
            json(exchange, e.status, Map.of("error", e.getMessage()));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Client connection closed", e);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Workspace request failed", e);
            json(exchange, 500, Map.of("error", "The workspace could not complete the request."));
        } finally {
            exchange.close();
        }
    }

    private void dispatch(HttpExchange ex) throws Exception {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if (method.equals("GET") || method.equals("HEAD")) {
            if (path.startsWith("/api/attachment/")) {
                int id;
                try { id = Integer.parseInt(path.substring("/api/attachment/".length())); }
                catch (NumberFormatException e) { throw new Problem(404, "Attachment not found."); }
                var attachment = desk.attachment(sessions.resolve(token(ex)), id);
                String safeName = attachment.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + safeName + "\"");
                send(ex, 200, attachment.mediaType(), attachment.content());
                return;
            }
            switch (path) {
                case "/api/config" -> json(ex, 200, Map.of("demo", demo));
                case "/healthz" -> json(ex, 200, Map.of("status", "ok"));
                case "/api/state" -> json(ex, 200, desk.snapshot(sessions.resolve(token(ex))));
                default -> asset(ex, path.equals("/") ? "/index.html" : path);
            }
            return;
        }
        if (!method.equals("POST")) {
            ex.getResponseHeaders().set("Allow", "GET, HEAD, POST");
            throw new Problem(405, "Unsupported HTTP method.");
        }
        if (!POST_ENDPOINTS.contains(path)) throw new Problem(404, "Unknown endpoint.");
        Map<String,Object> data = input(ex, path.equals("/api/attachment") ? MAX_ATTACHMENT_BODY : MAX_BODY);
        String previous = token(ex);
        switch (path) {
            case "/api/logout" -> {
                sessions.revoke(previous);
                setCookie(ex, "");
                json(ex, 200, Map.of());
            }
            case "/api/login", "/api/register" -> {
                limiter.check(ex.getRemoteAddress().getAddress().getHostAddress());
                if (!(data.get("email") instanceof String email) || !(data.get("password") instanceof String password)) {
                    throw new Problem(400, "Enter an email address and password.");
                }
                Map<String,Object> user = path.equals("/api/login") ? desk.login(email, password) : desk.register(data);
                sessions.revoke(previous);
                setCookie(ex, sessions.create(((Number)user.get("id")).intValue()));
                json(ex, 200, Map.of("user", user));
            }
            case "/api/action" -> {
                int userId = sessions.resolve(previous);
                if (!(data.remove("action") instanceof String action) || action.isBlank()) {
                    throw new Problem(400, "Choose a valid workspace action.");
                }
                json(ex, 200, desk.mutate(userId, action, data));
            }
            case "/api/attachment" -> {
                int userId = sessions.resolve(previous);
                int incidentId = Desk.number(data, "incident_id", 1, Integer.MAX_VALUE);
                String fileName = Desk.required(data, "file_name", 160);
                String mediaType = Desk.required(data, "media_type", 80);
                String encoded = Desk.required(data, "content", MAX_ATTACHMENT_BODY);
                byte[] content;
                try { content = Base64.getDecoder().decode(encoded); }
                catch (IllegalArgumentException e) { throw new Problem(400, "The attachment is not valid base64 data."); }
                json(ex, 200, Map.of("id", desk.addAttachment(userId, incidentId, fileName, mediaType, content)));
            }
            default -> throw new Problem(404, "Unknown endpoint.");
        }
    }

    private static Map<String,Object> input(HttpExchange ex, int limit) throws IOException {
        Headers h = ex.getRequestHeaders();
        String origin = h.getFirst("Origin");
        if (origin != null) {
            String authority = null;
            try { authority = URI.create(origin).getRawAuthority(); } catch (IllegalArgumentException ignored) {}
            if (authority == null || !authority.equalsIgnoreCase(h.getFirst("Host"))) {
                throw new Problem(403, "This request came from another origin.");
            }
        }
        String type = h.getFirst("Content-Type");
        if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())) {
            throw new Problem(415, "Send a JSON object using application/json.");
        }
        byte[] bytes = ex.getRequestBody().readNBytes(limit + 1);
        if (bytes.length == 0 || bytes.length > limit) {
            throw new Problem(413, "The request exceeds the allowed size.");
        }
        String raw;
        try {
            raw = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new Problem(400, "Text fields must contain valid Unicode characters.");
        }
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new Problem(400, "Send a JSON object.");
            reader.beginObject();
            Map<String, Object> fields = new HashMap<>();
            while (reader.hasNext()) {
                String name = validText(reader.nextName());
                Object value = switch (reader.peek()) {
                    case STRING -> validText(reader.nextString());
                    case BOOLEAN -> reader.nextBoolean();
                    case NUMBER -> Long.parseLong(reader.nextString());
                    case NULL -> { reader.nextNull(); yield null; }
                    default -> throw new Problem(400, "Use text, integer or boolean values for fields.");
                };
                fields.put(name, value);
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new Problem(400, "Invalid JSON.");
            return fields;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new Problem(400, "The request contains invalid JSON or an unsupported number.");
        }
    }

    private static String validText(String value) {
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw new Problem(400, "Text fields must contain valid Unicode characters.");
        }
        return value;
    }

    private static String token(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return "";
        for (String pair : header.split(";")) {
            pair = pair.trim();
            if (pair.startsWith(COOKIE + "=")) return pair.substring(COOKIE.length() + 1);
        }
        return "";
    }

    private void setCookie(HttpExchange ex, String value) {
        long age = value.isEmpty() ? 0 : SessionStore.LIFETIME.toSeconds();
        ex.getResponseHeaders().set("Set-Cookie", COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + age
                + (secureCookie ? "; Secure" : ""));
    }

    private static void asset(HttpExchange ex, String path) throws IOException {
        String mime = ASSETS.get(path);
        if (mime == null) throw new Problem(404, "Page not found.");
        try (InputStream in = RelayServer.class.getResourceAsStream("/static" + path)) {
            if (in == null) throw new Problem(404, "Page not found.");
            send(ex, 200, mime, in.readAllBytes());
        }
    }

    private static void json(HttpExchange ex, int status, Object object) throws IOException {
        send(ex, status, "application/json; charset=utf-8", JSON.toJson(object).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        Headers headers = ex.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options","nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "same-origin");
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        boolean head = ex.getRequestMethod().equals("HEAD");
        if (head) headers.set("Content-Length", Integer.toString(body.length));
        ex.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) ex.getResponseBody().write(body);
    }
}
