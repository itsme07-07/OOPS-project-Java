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
    private static ServiceForceServer server;
    private static URI base;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson JSON = new Gson();
    private static final java.util.Map<String,String> SENT = new java.util.concurrent.ConcurrentHashMap<>();

    private String demoUsername(String role) throws Exception {
        return JsonParser.parseString(get("/api/config").body()).getAsJsonObject()
                .getAsJsonObject("demoUsers").get(role).getAsString();
    }

    @BeforeAll static void start() throws Exception {
        Desk desk = new Desk(directory.resolve("desk.sqlite3"), true, (to, subject, body) -> SENT.put(to, body));
        server = new ServiceForceServer(new InetSocketAddress("127.0.0.1", 0), desk, true, false);
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
        assertEquals(401, post("/api/login", JSON.toJson(Map.of("identifier",demoUsername("admin"),"password","wrong","role","admin"))).statusCode());
        assertEquals(401, post("/api/login", JSON.toJson(Map.of("identifier",demoUsername("admin"),"password","RelayDemo2026!","role","employee"))).statusCode());
    }

    @Test void sessionPermitsWorkspaceAccessUntilLogout() throws Exception {
        var res = post("/api/login", JSON.toJson(Map.of("identifier", demoUsername("employee"), "password", "RelayDemo2026!", "role", "employee")));
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

    @Test void userCanSeeAndSignOutAnotherDevice() throws Exception {
        String credentials=JSON.toJson(Map.of("identifier",demoUsername("employee"),"password","RelayDemo2026!","role","employee"));
        String first=post("/api/login",credentials,"User-Agent","Mozilla/5.0 (Mac OS X) Chrome/120").headers()
                .firstValue("Set-Cookie").orElseThrow().split(";",2)[0];
        String second=post("/api/login",credentials,"User-Agent","Mozilla/5.0 (iPhone) Safari/605").headers()
                .firstValue("Set-Cookie").orElseThrow().split(";",2)[0];
        var listed=get("/api/devices","Cookie",first);
        assertEquals(200,listed.statusCode());
        var devices=JsonParser.parseString(listed.body()).getAsJsonArray();
        assertTrue(devices.size()>=2);
        String otherId=null;
        for(var item:devices) if(!item.getAsJsonObject().get("current").getAsBoolean()&&
                item.getAsJsonObject().get("device").getAsString().contains("iPhone"))
            otherId=item.getAsJsonObject().get("id").getAsString();
        assertNotNull(otherId);
        assertEquals(200,post("/api/revoke-device",JSON.toJson(Map.of("id",otherId)),"Cookie",first).statusCode());
        assertEquals(401,get("/api/state","Cookie",second).statusCode());
        assertEquals(200,get("/api/state","Cookie",first).statusCode());
    }

    @Test void attachmentDownloadRequiresIncidentAccess() throws Exception {
        var login=post("/api/login",JSON.toJson(Map.of("identifier",demoUsername("employee"),"password","RelayDemo2026!","role","employee")));
        String cookie=login.headers().firstValue("Set-Cookie").orElseThrow().split(";",2)[0];
        var created=post("/api/action",JSON.toJson(Map.of("action","create",
                "title","Attachment test incident","description","Upload a diagnostic file.",
                "service_id",1,"impact",1,"urgency",1)),"Cookie",cookie);
        assertEquals(200,created.statusCode());
        int incidentId=JsonParser.parseString(created.body()).getAsJsonObject().get("id").getAsInt();
        var upload=post("/api/attachment",JSON.toJson(Map.of("incident_id",incidentId,"file_name","error.txt",
                "media_type","text/plain","content","SGVsbG8=")),"Cookie",cookie);
        assertEquals(200,upload.statusCode());
        int id=JsonParser.parseString(upload.body()).getAsJsonObject().get("id").getAsInt();
        assertEquals("Hello",get("/api/attachment/"+id,"Cookie",cookie).body());
        assertEquals(401,get("/api/attachment/"+id).statusCode());
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
        var data = new java.util.HashMap<>(Map.of("name","HTTP reporter","role","admin","email","http-user@example.org","password","StrongPass1!"));
        assertEquals(404,post("/api/verification-code",JSON.toJson(Map.of("email",data.get("email")))).statusCode());
        var res = post("/api/register", JSON.toJson(data));
        assertEquals(200, res.statusCode());
        assertTrue(res.headers().firstValue("Set-Cookie").isEmpty());
        String code=SENT.get("http-user@example.org").replaceAll("(?s).*code is ([0-9]{8}).*", "$1");
        var verified=post("/api/verify-email", JSON.toJson(Map.of("email","http-user@example.org","code",code)));
        var user = JsonParser.parseString(verified.body()).getAsJsonObject().getAsJsonObject("user");
        assertEquals("employee", user.get("role").getAsString());
        assertTrue(verified.headers().firstValue("Set-Cookie").isPresent());
        String cookie = verified.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var denied = post("/api/action", "{\"action\":\"strategy\",\"strategy\":\"load\"}", "Cookie", cookie);
        assertEquals(403, denied.statusCode());
    }
}
