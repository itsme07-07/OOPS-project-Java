package relay;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HttpIntegrationTest {
    @TempDir static Path directory;
    private static RelayServer server;
    private static URI base;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson JSON = new Gson();

    @BeforeAll static void start() throws Exception {
        Desk desk = new Desk(directory.resolve("desk.sqlite3"), true);
        server = new RelayServer(new InetSocketAddress("127.0.0.1", 0), desk, true, false);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.port());
    }

    @AfterAll static void stop() {
        if (server != null) server.close();
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path));
        if (headers.length != 0) request.headers(headers);
        return CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json");
        if (headers.length != 0) request.headers(headers);
        return CLIENT.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void servesFrontendAssets() throws Exception {
        var root = get("/");
        assertEquals(200, root.statusCode());
        assertTrue(root.headers().firstValue("Content-Type").orElse("").contains("text/html"));
        assertTrue(root.body().contains("<html"));
        for (String path : new String[]{"/app.js", "/style.css", "/mark.svg"}) {
            assertEquals(200, get(path).statusCode(), path);
        }
    }

    @Test void exposesHealthAndDemoConfiguration() throws Exception {
        assertEquals("ok", JsonParser.parseString(get("/healthz").body()).getAsJsonObject().get("status").getAsString());
        assertTrue(JsonParser.parseString(get("/api/config").body()).getAsJsonObject().get("demo").getAsBoolean());
    }

    @Test void anonymousStateIsPrivate() throws Exception {
        assertEquals(401, get("/api/state").statusCode());
    }

    @Test void wrongPasswordIsRejected() throws Exception {
        assertEquals(401, post("/api/login", JSON.toJson(Map.of("email","admin@relay.test","password","wrong"))).statusCode());
    }

    @Test void sessionPermitsWorkspaceAccessUntilLogout() throws Exception {
        var res = post("/api/login", JSON.toJson(Map.of("email", "employee@relay.test", "password", "RelayDemo2026!")));
        assertEquals(200, res.statusCode());
        String cookie = res.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        String token = cookie.split(";", 2)[0];
        var state = get("/api/state","Cookie",token);
        assertEquals(200, state.statusCode());
        assertEquals("employee", JsonParser.parseString(state.body()).getAsJsonObject().getAsJsonObject("user").get("role").getAsString());
        assertEquals(200, post("/api/logout", "{}", "Cookie", token).statusCode());
        assertEquals(401, get("/api/state","Cookie",token).statusCode());
    }

    @Test void blocksCrossOriginMutation() throws Exception {
        assertEquals(403, post("/api/logout", "{}", "Origin", "https://elsewhere.test").statusCode());
    }

    @Test void acceptsOnlyFlatJsonObjects() throws Exception {
        for (String raw : new String[]{"[]", "{'a':1}", "{\"n\":2.4}", "{\"n\":{}}", "{}{}"}) {
            assertEquals(400, post("/api/login", raw).statusCode(), raw);
        }
    }

    @Test void enforcesPayloadLimit() throws Exception {
        assertEquals(413, post("/api/login", JSON.toJson(Map.of("x", "a".repeat(65536)))).statusCode());
    }

    @Test void rejectsStaticTraversalAndMissingEndpoints() throws Exception {
        for (String path : new String[]{"/%2e%2e/schema.sql", "/relay/Main.class", "/api/unknown"}) {
            assertEquals(404, get(path).statusCode(), path);
        }
    }

    @Test void registrationRejectsPrivilegeEscalation() throws Exception {
        var data = Map.of("name","HTTP reporter","role","admin","email","http-user@example.org","password","StrongPass1!");
        var res = post("/api/register", JSON.toJson(data));
        assertEquals(200, res.statusCode());
        var user = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("user");
        assertEquals("employee", user.get("role").getAsString());
        String cookie = res.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var denied = post("/api/action", "{\"action\":\"strategy\",\"strategy\":\"load\"}", "Cookie", cookie);
        assertEquals(403, denied.statusCode());
    }
}
