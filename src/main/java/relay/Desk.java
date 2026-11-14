package relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static relay.Database.*;

public final class Desk {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY_HASH = "0".repeat(32) + ":" + "0".repeat(64);
    private final Database database;
    private final Incidents incidents;

    public Desk(Path path, boolean demo) {
        database = new Database(path);
        incidents = new Incidents(database);
        String schema;
        try (var stream = Objects.requireNonNull(Desk.class.getResourceAsStream("/schema.sql"), "Missing database schema")) {
            schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the database schema", e);
        }
        database.transaction(true, c -> {
            for (String statement : schema.split(";")) {
                if (!statement.isBlank()) exec(c, statement);
            }
            if (rows(c, "PRAGMA table_info(incidents)").stream().noneMatch(r -> "suggestion_source_id".equals(r.get("name")))) {
                exec(c, "ALTER TABLE incidents ADD COLUMN suggestion_source_id INTEGER REFERENCES incidents");
                exec(c, "UPDATE incidents SET suggested_resolution=''");
            }
            if (rows(c, "PRAGMA table_info(users)").stream().noneMatch(r -> "active".equals(r.get("name"))))
                exec(c, "ALTER TABLE users ADD COLUMN active INTEGER NOT NULL DEFAULT 1");
            if (rows(c, "PRAGMA table_info(users)").stream().noneMatch(r -> "username".equals(r.get("name"))))
                exec(c, "ALTER TABLE users ADD COLUMN username TEXT");
            exec(c, "CREATE UNIQUE INDEX IF NOT EXISTS users_username_unique ON users(username)");
            if (one(c, "SELECT 1 FROM settings LIMIT 1") == null) initialize(c, demo);
            for (var row : rows(c, "SELECT id,role,username FROM users")) {
                Object current = row.get("username");
                String updated = current == null ? newUsername(c, row.get("role").toString()) :
                        current.toString().matches("example@service\\.(adm|tech|emp)[A-F0-9]{8}") ?
                        formatUsername(row.get("role").toString(), current.toString().substring(current.toString().length() - 8)) : null;
                if (updated != null) exec(c, "UPDATE users SET username=? WHERE id=?", updated, row.get("id"));
            }
            return null;
        });
    }

    private void initialize(Connection c, boolean demo) throws SQLException {
        var categories = List.of("Application", "Network", "Authentication", "Database", "Storage", "Hardware", "Cloud", "Security", "Software");
        for (String category : categories) exec(c, "INSERT INTO categories VALUES(?)", category);
        var names = List.of("Payment gateway", "Corporate network", "Identity platform", "Analytics database", "Cloud storage", "Workplace devices");
        int[] criticality = {3, 3, 3, 2, 2, 1};
        for (int i = 0; i < names.size(); i++) {
            exec(c, "INSERT INTO services(name,category,criticality) VALUES(?,?,?)", names.get(i), categories.get(i), criticality[i]);
        }
        int[][] targets = {{5, 30}, {15, 120}, {60, 480}, {240, 1440}};
        for (int i = 0; i < targets.length; i++) {
            exec(c, "INSERT INTO policies VALUES(?,?,?)", "P" + (i + 1), targets[i][0], targets[i][1]);
        }
        exec(c, "INSERT INTO settings VALUES('strategy','skill')");
        if (!demo) return;
        String[][] accounts = {
            {"Alex Morgan", "admin", "admin", ""}, {"Sam Rivera", "tech", "technician", "Application"},
            {"Jordan Lee", "employee", "employee", ""}, {"Priya Shah", "priya", "technician", "Network"},
            {"Noah Chen", "noah", "technician", "Authentication"}
        };
        for (String[] a : accounts) {
            exec(c, "INSERT INTO users(name,email,role,skill,password) VALUES(?,?,?,?,?)",
                    a[0], a[1] + "@relay.test", a[2], a[3], hash("RelayDemo2026!"));
        }
        seedIncidents(c);
    }

    private record Example(String title, int service, int impact, int urgency, int ageMinutes, int transitions) {}

    private void seedIncidents(Connection c) throws SQLException {
        var examples = List.of(
            new Example("Checkout requests timing out", 1, 3, 3, 7, 0),
            new Example("VPN drops during video calls", 2, 2, 2, 22, 2),
            new Example("Password reset link expired", 3, 1, 1, 16, 0),
            new Example("Warehouse query latency", 4, 2, 3, 52, 1),
            new Example("Shared drive sync stalled", 5, 2, 1, 18, 3),
            new Example("Laptop docking station offline", 6, 1, 1, 25, 5)
        );
        var states = List.of("Acknowledged", "In progress", "Resolved", "Verified", "Closed");
        var reporter = user(c, 3);
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (var example : examples) {
            var at = now.minus(example.ageMinutes(), ChronoUnit.MINUTES);
            var data = Map.<String,Object>of("title", example.title(), "description", example.title() + ". Please investigate.",
                    "service_id", example.service(), "impact", example.impact(), "urgency", example.urgency());
            int id = incidents.create(c, reporter, data, at);
            for (int i = 0; i < example.transitions(); i++) {
                var incident = one(c, "SELECT * FROM incidents WHERE id=?", id);
                var actor = i >= 3 ? reporter : user(c, ((Number) incident.get("technician_id")).intValue());
                incidents.transition(c, actor, id, Map.of("status", states.get(i), "resolution", "Restored service and confirmed normal operation."),
                        at.plus(i + 1L, ChronoUnit.MINUTES));
            }
        }
    }

    static Map<String,Object> publicUser(Map<String,Object> row) {
        var safe = new LinkedHashMap<String,Object>();
        for (String key : List.of("id", "name", "email", "username", "role", "skill", "available", "capacity")) safe.put(key, row.get(key));
        return safe;
    }

    private static Map<String,Object> user(Connection c, int uid) throws SQLException {
        var row = one(c, "SELECT * FROM users WHERE id=?", uid);
        if (row == null || ((Number) row.get("active")).intValue() == 0) throw new Problem(401, "Sign in to continue.");
        return row;
    }

    private static byte[] digest(String password, String salt) {
        var spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 300000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String hash(Object value) {
        if (!(value instanceof String password) || password.codePointCount(0, password.length()) < 8
                || password.codePointCount(0, password.length()) > 256) {
            throw new Problem(400, "Use a password between 8 and 256 characters.");
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String salt = HexFormat.of().formatHex(bytes);
        return salt + ":" + HexFormat.of().formatHex(digest(password, salt));
    }

    public Map<String,Object> login(String identifier, String password) {
        if (identifier == null || password == null || password.codePointCount(0, password.length()) > 256)
            throw new Problem(401, "Invalid username or password.");
        var account = database.transaction(false, c -> one(c,
                "SELECT * FROM users WHERE active=1 AND (username=? COLLATE NOCASE OR email=?)",
                identifier.strip(), identifier.strip()));
        String encoded = account == null ? DUMMY_HASH : (String) account.get("password");
        String[] parts = encoded.split(":", 2);
        if (!MessageDigest.isEqual(digest(password, parts[0]), HexFormat.of().parseHex(parts[1])) || account == null)
            throw new Problem(401, "Invalid username or password.");
        return publicUser(account);
    }

    public Map<String,Object> register(Map<String,Object> data) {
        var employee = new HashMap<String,Object>();
        for (String key : List.of("name", "email", "password")) employee.put(key, data.get(key));
        employee.put("role", "employee");
        return database.transaction(true, c -> publicUser(user(c, saveUser(c, employee))));
    }

    public Map<String,Object> bootstrapAdmin(String name, String email, String password) {
        return database.transaction(true, c -> {
            if (one(c, "SELECT 1 FROM users WHERE role='admin' LIMIT 1") != null)
                throw new Problem(409, "An administrator already exists.");
            return publicUser(user(c, saveUser(c, Map.of("name", name, "email", email, "password", password, "role", "admin"))));
        });
    }

    static String required(Map<String,Object> data, String key, int limit) {
        if (!(data.get(key) instanceof String text) || text.isBlank() || text.codePointCount(0, text.length()) > limit)
            throw new Problem(400, "Enter a valid " + key.replace('_', ' ') + " (up to " + limit + " characters).");
        return text.strip();
    }

    static int number(Map<String,Object> data, String key, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(String.valueOf(data.get(key)));
        } catch (NumberFormatException e) {
            throw new Problem(400, "Enter a whole number for " + key + ".");
        }
        if (value < min || value > max) throw new Problem(400, key + " must be between " + min + " and " + max + ".");
        return value;
    }

    private static void category(Connection c, String value) throws SQLException {
        if (one(c, "SELECT 1 FROM categories WHERE name=?", value) == null)
            throw new Problem(400, "Choose an existing category.");
    }

    private static String roleCode(String role) {
        return switch (role) {
            case "admin" -> "adm";
            case "technician" -> "tech";
            default -> "emp";
        };
    }

    private static String formatUsername(String role, String code) {
        return "example" + code + "@service." + roleCode(role);
    }

    private static String newUsername(Connection c, String role) throws SQLException {
        for (int attempt = 0; attempt < 20; attempt++) {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            String code = HexFormat.of().withUpperCase().formatHex(bytes);
            if (one(c, "SELECT 1 FROM users WHERE substr(username,8,8)=? OR substr(username,length(username)-7,8)=?",
                    code, code) == null)
                return formatUsername(role, code);
        }
        throw new Problem(503, "Could not create a unique username. Try again.");
    }

    private int saveUser(Connection c, Map<String,Object> data) throws SQLException {
        boolean editing = data.containsKey("id");
        var existing = editing ? user(c, number(data, "id", 1, Integer.MAX_VALUE)) : Map.<String,Object>of();
        var fields = new HashMap<>(existing);
        fields.putAll(data);
        String name = required(fields, "name", 100);
        String email = required(fields, "email", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new Problem(400, "Enter a valid email address.");
        Object role = fields.getOrDefault("role", "employee");
        if (!Arrays.asList("employee", "technician", "admin").contains(role)) throw new Problem(400, "Choose a valid role.");
        String skill = String.valueOf(fields.getOrDefault("skill", "")).strip();
        if (!skill.isEmpty()) category(c, skill);
        fields.putIfAbsent("capacity", 20);
        int capacity = number(fields, "capacity", 1, 1000);
        Object available = fields.getOrDefault("available", 1);
        int availability;
        if (Boolean.TRUE.equals(available) || (available instanceof Number && "1".equals(available.toString()))) availability = 1;
        else if (Boolean.FALSE.equals(available) || (available instanceof Number && "0".equals(available.toString()))) availability = 0;
        else throw new Problem(400, "Availability must be true or false.");
        if ("admin".equals(existing.get("role")) && !"admin".equals(role) &&
                one(c, "SELECT 1 FROM users WHERE role='admin' AND active=1 AND id<>?", existing.get("id")) == null)
            throw new Problem(400, "Keep at least one administrator.");
        if ("technician".equals(existing.get("role")) && !"technician".equals(role) &&
                one(c, "SELECT 1 FROM incidents WHERE technician_id=? AND status NOT IN ('Closed','Resolved','Verified')", existing.get("id")) != null)
            throw new Problem(400, "Reassign this technician's active incidents before changing their role.");
        Object password = data.get("password");
        Object encoded = editing && (password == null || "".equals(password)) ? existing.get("password") : hash(password);
        String username = editing ? existing.get("username").toString() : newUsername(c, role.toString());
        if (editing && !role.equals(existing.get("role")))
            username = formatUsername(role.toString(), username.substring(7, 15));
        if (editing) {
            exec(c, "UPDATE users SET name=?,email=?,username=?,role=?,skill=?,available=?,capacity=?,password=? WHERE id=?",
                    name, email, username, role, skill, availability, capacity, encoded, existing.get("id"));
            return ((Number) existing.get("id")).intValue();
        }
        return insert(c, "INSERT INTO users(name,email,username,role,skill,available,capacity,password) VALUES(?,?,?,?,?,?,?,?)",
                name, email, username, role, skill, availability, capacity, encoded);
    }

    private Object saveProfile(Connection c, int uid, Map<String,Object> data) throws SQLException {
        var existing = user(c, uid);
        String current = required(data, "current_password", 256);
        String[] parts = existing.get("password").toString().split(":", 2);
        if (!MessageDigest.isEqual(digest(current, parts[0]), HexFormat.of().parseHex(parts[1])))
            throw new Problem(403, "Current password is incorrect.");
        String name = required(data, "name", 100);
        String email = required(data, "email", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new Problem(400, "Enter a valid email address.");
        Object password = data.get("password");
        String encoded = password == null || "".equals(password) ? existing.get("password").toString() : hash(password);
        String skill = existing.get("skill").toString();
        if ("technician".equals(existing.get("role")) && data.get("skill") instanceof String value) {
            skill = value.equals("general") ? "" : value.strip();
            if (!skill.isEmpty()) category(c, skill);
        }
        exec(c, "UPDATE users SET name=?,email=?,password=?,skill=? WHERE id=?", name, email, encoded, skill, uid);
        return Map.of("id", uid);
    }

    private Object removeUser(Connection c, int actorId, Map<String,Object> data) throws SQLException {
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        if (id == actorId) throw new Problem(400, "You cannot remove your own account.");
        var target = user(c, id);
        if ("admin".equals(target.get("role")) &&
                one(c, "SELECT 1 FROM users WHERE role='admin' AND active=1 AND id<>?", id) == null)
            throw new Problem(400, "Keep at least one administrator.");
        if (one(c, "SELECT 1 FROM incidents WHERE technician_id=? AND status NOT IN ('Closed','Resolved','Verified')", id) != null)
            throw new Problem(409, "Reassign this person's active incidents before removing them.");
        exec(c, "UPDATE users SET active=0,available=0,email=? WHERE id=?", "removed+" + id + "@serviceforce.invalid", id);
        return Map.of("id", id);
    }

    public Object snapshot(int uid) {
        return database.transaction(true, c -> incidents.snapshot(c, publicUser(user(c, uid))));
    }

    public int addAttachment(int uid, int incidentId, String fileName, String mediaType, byte[] content) {
        return database.transaction(true, c -> incidents.addAttachment(c, publicUser(user(c, uid)),
                incidentId, fileName, mediaType, content));
    }

    public Incidents.Attachment attachment(int uid, int id) {
        return database.transaction(false, c -> incidents.attachment(c, publicUser(user(c, uid)), id));
    }

    private Object saveArticle(Connection c, Map<String,Object> actor, Map<String,Object> data) throws SQLException {
        if ("employee".equals(actor.get("role"))) throw new Problem(403, "IT staff access is required.");
        String title = required(data, "title", 160);
        String summary = required(data, "summary", 1000);
        String steps = required(data, "steps", 6000);
        int serviceId = number(data, "service_id", 1, Integer.MAX_VALUE);
        if (one(c, "SELECT 1 FROM services WHERE id=?", serviceId) == null) throw new Problem(404, "Service not found.");
        String at = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        if (!data.containsKey("id")) return Map.of("id", insert(c, """
            INSERT INTO knowledge_articles(title,service_id,summary,steps,author_id,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?)
            """, title, serviceId, summary, steps, actor.get("id"), at, at));
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        var existing = one(c, "SELECT author_id FROM knowledge_articles WHERE id=?", id);
        if (existing == null) throw new Problem(404, "Article not found.");
        if (!"admin".equals(actor.get("role")) && !existing.get("author_id").equals(actor.get("id")))
            throw new Problem(403, "Only the article author or an administrator can edit it.");
        exec(c, "UPDATE knowledge_articles SET title=?,service_id=?,summary=?,steps=?,updated_at=? WHERE id=?",
                title, serviceId, summary, steps, at, id);
        return Map.of("id", id);
    }

    private Object deleteArticle(Connection c, Map<String,Object> actor, Map<String,Object> data) throws SQLException {
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        var existing = one(c, "SELECT author_id FROM knowledge_articles WHERE id=?", id);
        if (existing == null) throw new Problem(404, "Article not found.");
        if (!"admin".equals(actor.get("role")) && !existing.get("author_id").equals(actor.get("id")))
            throw new Problem(403, "Only the article author or an administrator can remove it.");
        exec(c, "DELETE FROM knowledge_articles WHERE id=?", id);
        return Map.of("id", id);
    }

    public void monitor() {
        database.transaction(true, c -> { incidents.monitor(c); return null; });
    }

    public Object mutate(int uid, String action, Map<String,Object> data) {
        return database.transaction(true, c -> {
            var actor = publicUser(user(c, uid));
            if (action == null || data == null) throw new Problem(400, "Enter a valid command.");
            return switch (action) {
                case "create" -> Map.of("id", incidents.create(c, actor, data, Instant.now().truncatedTo(ChronoUnit.SECONDS)));
                case "profile" -> saveProfile(c, uid, data);
                case "article-save" -> saveArticle(c, actor, data);
                case "article-delete" -> deleteArticle(c, actor, data);
                case "assign", "feedback", "transition", "update", "note", "reopen", "review" -> incidents.act(c, actor, action, data);
                default -> {
                    if (!"admin".equals(actor.get("role"))) throw new Problem(403, "Administrator access is required.");
                    if ("remove-user".equals(action)) yield removeUser(c, uid, data);
                    yield configure(c, action, data);
                }
            };
        });
    }

    private Object configure(Connection c, String action, Map<String,Object> data) throws SQLException {
        switch (action) {
            case "user": return Map.of("id", saveUser(c, data));
            case "strategy":
                String strategy = required(data, "strategy", 16);
                if (!List.of("load", "skill").contains(strategy)) throw new Problem(400, "Unknown assignment strategy.");
                exec(c, "UPDATE settings SET value=? WHERE name='strategy'", strategy);
                break;
            case "category":
                exec(c, "INSERT INTO categories VALUES(?)", required(data, "name", 80));
                break;
            case "policy":
                String priority = required(data, "priority", 2);
                int response = number(data, "response_minutes", 1, 525600);
                int resolution = number(data, "resolution_minutes", response, 525600);
                if (!List.of("P1", "P2", "P3", "P4").contains(priority)) throw new Problem(400, "Choose a valid priority.");
                exec(c, "UPDATE policies SET response_minutes=?,resolution_minutes=? WHERE priority=?", response, resolution, priority);
                break;
            case "service":
                String name = required(data, "name", 120);
                String category = required(data, "category", 80);
                category(c, category);
                int criticality = number(data, "criticality", 1, 3);
                if (!data.containsKey("id"))
                    return Map.of("id", insert(c, "INSERT INTO services(name,category,criticality) VALUES(?,?,?)", name, category, criticality));
                int updated = exec(c, "UPDATE services SET name=?,category=?,criticality=? WHERE id=?", name, category, criticality,
                        number(data, "id", 1, Integer.MAX_VALUE));
                if (updated == 0) throw new Problem(404, "Service not found.");
                break;
            default: throw new Problem(404, "Unknown command.");
        }
        return Map.of();
    }
}
