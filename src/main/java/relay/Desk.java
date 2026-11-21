package relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
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
    private final MailDelivery mail;

    public Desk(Path path, boolean demo) {
        this(path, demo, MailDelivery.configured());
    }

    Desk(Path path, boolean demo, MailDelivery mail) {
        this.mail = mail;
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
            if (rows(c, "PRAGMA table_info(users)").stream().noneMatch(r -> "email_verified".equals(r.get("name"))) ) {
                exec(c, "ALTER TABLE users ADD COLUMN email_verified INTEGER NOT NULL DEFAULT 0");
                exec(c, "UPDATE users SET email_verified=2");
            }
            if (rows(c, "PRAGMA table_info(incidents)").stream().noneMatch(r -> "recovery_due".equals(r.get("name"))))
                exec(c, "ALTER TABLE incidents ADD COLUMN recovery_due TEXT");
            if (rows(c, "PRAGMA table_info(incidents)").stream().noneMatch(r -> "escalation_stage".equals(r.get("name"))))
                exec(c, "ALTER TABLE incidents ADD COLUMN escalation_stage INTEGER NOT NULL DEFAULT 0");
            exec(c, "CREATE UNIQUE INDEX IF NOT EXISTS users_username_unique ON users(username)");
            if (one(c, "SELECT 1 FROM settings LIMIT 1") == null) initialize(c, demo);
            Playbooks.seed(c);
            for (var row : rows(c, "SELECT id,name,role,username FROM users")) {
                Object current = row.get("username");
                String name = row.get("name").toString();
                String role = row.get("role").toString();
                String code = current == null ? null : usernameCode(current.toString());
                String updated = code == null || !codeAvailable(c, code, ((Number) row.get("id")).intValue())
                        ? newUsername(c, name, role) :
                        current.toString();
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
            exec(c, "INSERT INTO users(name,email,role,skill,password,email_verified) VALUES(?,?,?,?,?,1)",
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
        for (String key : List.of("id", "name", "email", "email_verified", "username", "role", "skill", "available", "capacity")) safe.put(key, row.get(key));
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
                "SELECT * FROM users WHERE active=1 AND username=? COLLATE NOCASE",
                identifier.strip()));
        String encoded = account == null ? DUMMY_HASH : (String) account.get("password");
        String[] parts = encoded.split(":", 2);
        if (!MessageDigest.isEqual(digest(password, parts[0]), HexFormat.of().parseHex(parts[1])) || account == null)
            throw new Problem(401, "Invalid username or password.");
        if (!Set.of(1, 2).contains(((Number) account.get("email_verified")).intValue()))
            throw new Problem(403, "Verify your recovery email before signing in.");
        return publicUser(account);
    }

    public Map<String,Object> register(Map<String,Object> data) {
        var employee = new HashMap<String,Object>();
        for (String key : List.of("name", "email", "password")) employee.put(key, data.get(key));
        employee.put("role", "employee");
        return database.transaction(true, c -> {
            var person = user(c, saveUser(c, employee));
            issueCode(c, person, "verify");
            return Map.of("email", person.get("email"));
        });
    }

    private void issueCode(Connection c, Map<String,Object> person, String purpose) throws SQLException {
        var prior = one(c, "SELECT sent_at FROM email_codes WHERE user_id=? AND purpose=?", person.get("id"), purpose);
        Instant now = Instant.now();
        if (prior != null && now.isBefore(Instant.parse(prior.get("sent_at").toString()).plusSeconds(60)))
            throw new Problem(429, "Please wait one minute before requesting another code.");
        String code = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        exec(c, """
            INSERT INTO email_codes(user_id,purpose,code_hash,sent_at,expires_at,attempts)
            VALUES(?,?,?,?,?,0) ON CONFLICT(user_id,purpose) DO UPDATE SET
            code_hash=excluded.code_hash,sent_at=excluded.sent_at,expires_at=excluded.expires_at,attempts=0
            """, person.get("id"), purpose, hash(code), now.toString(), now.plusSeconds(600).toString());
        String subject = switch (purpose) {
            case "verify" -> "Verify your ServiceForce recovery email";
            case "password" -> "ServiceForce password change code";
            default -> "ServiceForce password recovery code";
        };
        mail.send(person.get("email").toString(), subject,
                "Your ServiceForce code is " + code + ". It expires in 10 minutes. Do not share this code.");
    }

    private boolean useCode(Connection c, Map<String,Object> person, String purpose, String code) throws SQLException {
        var row = one(c, "SELECT * FROM email_codes WHERE user_id=? AND purpose=?", person.get("id"), purpose);
        if (row == null || ((Number)row.get("attempts")).intValue() >= 5 ||
                Instant.now().isAfter(Instant.parse(row.get("expires_at").toString()))) return false;
        String[] parts = row.get("code_hash").toString().split(":", 2);
        boolean valid = code != null && code.matches("[0-9]{8}") &&
                MessageDigest.isEqual(digest(code, parts[0]), HexFormat.of().parseHex(parts[1]));
        if (valid) exec(c, "DELETE FROM email_codes WHERE user_id=? AND purpose=?", person.get("id"), purpose);
        else exec(c, "UPDATE email_codes SET attempts=attempts+1 WHERE user_id=? AND purpose=?", person.get("id"), purpose);
        return valid;
    }

    private static Map<String,Object> accountByEmail(Connection c, String email) throws SQLException {
        return one(c, "SELECT * FROM users WHERE active=1 AND email=? COLLATE NOCASE", email.strip());
    }

    public void requestVerification(String email) {
        try { database.transaction(true, c -> {
            var person = accountByEmail(c, email);
            if (person != null && ((Number)person.get("email_verified")).intValue() != 1)
                issueCode(c, person, "verify");
            return null;
        }); } catch (Problem e) { if (e.status != 429) throw e; }
    }

    record Verification(Map<String,Object> user, boolean newAccount) {}

    Verification verifyEmailWithStatus(String email, String code) {
        var result = database.transaction(true, c -> {
            var person = accountByEmail(c, email);
            if (person == null || !useCode(c, person, "verify", code)) return null;
            boolean newAccount = ((Number)person.get("email_verified")).intValue() == 0;
            exec(c, "UPDATE users SET email_verified=1 WHERE id=?", person.get("id"));
            return new Verification(publicUser(user(c, ((Number)person.get("id")).intValue())), newAccount);
        });
        if (result == null) throw new Problem(400, "The code is invalid or expired.");
        return result;
    }

    public Map<String,Object> verifyEmail(String email, String code) {
        return verifyEmailWithStatus(email, code).user();
    }

    public void recoverUsername(String email) {
        database.transaction(false, c -> {
            var person = accountByEmail(c, email);
            if (person != null && ((Number)person.get("email_verified")).intValue() == 1)
                mail.send(person.get("email").toString(), "Your ServiceForce username",
                        "Your assigned username is " + person.get("username") + ".");
            return null;
        });
    }

    public void requestReset(String email) {
        try { database.transaction(true, c -> {
            var person = accountByEmail(c, email);
            if (person != null && ((Number)person.get("email_verified")).intValue() == 1)
                issueCode(c, person, "reset");
            return null;
        }); } catch (Problem e) { if (e.status != 429) throw e; }
    }

    public int resetPassword(String email, String code, String password) {
        String encoded = hash(password);
        int id = database.transaction(true, c -> {
            var person = accountByEmail(c, email);
            if (person == null || ((Number)person.get("email_verified")).intValue() != 1 ||
                    !useCode(c, person, "reset", code)) return 0;
            exec(c, "UPDATE users SET password=? WHERE id=?", encoded, person.get("id"));
            exec(c, "DELETE FROM email_codes WHERE user_id=?", person.get("id"));
            return ((Number)person.get("id")).intValue();
        });
        if (id == 0) throw new Problem(400, "The code is invalid or expired.");
        return id;
    }

    public void requestPasswordCode(int uid) {
        database.transaction(true, c -> {
            var person = user(c, uid);
            if (((Number)person.get("email_verified")).intValue() != 1)
                throw new Problem(409, "Verify your recovery email first.");
            issueCode(c, person, "password");
            return null;
        });
    }

    public Map<String,Object> bootstrapAdmin(String name, String email, String password) {
        return database.transaction(true, c -> {
            if (one(c, "SELECT 1 FROM users WHERE role='admin' LIMIT 1") != null)
                throw new Problem(409, "An administrator already exists.");
            int id = saveUser(c, Map.of("name", name, "email", email, "password", password, "role", "admin"));
            exec(c, "UPDATE users SET email_verified=3 WHERE id=?", id);
            return publicUser(user(c, id));
        });
    }

    public boolean hasAdmin() {
        return database.transaction(false, c -> one(c,
                "SELECT 1 FROM users WHERE role='admin' AND active=1 LIMIT 1") != null);
    }

    public Map<String,String> demoUsers() {
        return database.transaction(false, c -> {
            var result = new HashMap<String,String>();
            for (var row : rows(c, "SELECT role,username FROM users WHERE email IN ('admin@relay.test','tech@relay.test','employee@relay.test')"))
                result.put(row.get("role").toString(), row.get("username").toString());
            return result;
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

    private static String namePart(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return ascii.isEmpty() ? "user" : ascii.substring(0, Math.min(ascii.length(), 32));
    }

    private static String formatUsername(String name, String role, String code) {
        return namePart(name) + code + "@service." + roleCode(role);
    }

    private static String usernameCode(String username) {
        var match = java.util.regex.Pattern.compile("([0-9]{4})@service\\.(adm|admin|tech|technician|emp|employee)$")
                .matcher(username);
        return match.find() ? match.group(1) : null;
    }

    private static boolean codeAvailable(Connection c, String code, int excludeId) throws SQLException {
        return one(c, "SELECT 1 FROM users WHERE id<>? AND username LIKE ?", excludeId,
                "%" + code + "@service.%") == null;
    }

    private static String newUsername(Connection c, String name, String role) throws SQLException {
        for (int attempt = 0; attempt < 1000; attempt++) {
            String code = String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10000));
            if (codeAvailable(c, code, -1))
                return formatUsername(name, role, code);
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
                one(c, """
                    SELECT 1 FROM incidents i WHERE i.status NOT IN ('Closed','Resolved','Verified')
                    AND (i.technician_id=? OR EXISTS(SELECT 1 FROM incident_responders r
                        WHERE r.incident_id=i.id AND r.user_id=?))
                    """, existing.get("id"), existing.get("id")) != null)
            throw new Problem(400, "Reassign this technician's active incidents before changing their role.");
        Object password = data.get("password");
        if (editing && password instanceof String value && !value.isBlank())
            throw new Problem(403, "People must change their own passwords using an email code.");
        Object encoded = editing && (password == null || "".equals(password)) ? existing.get("password") : hash(password);
        String username = editing ? (name.equals(existing.get("name")) && role.equals(existing.get("role"))
                ? existing.get("username").toString()
                : formatUsername(name, role.toString(), usernameCode(existing.get("username").toString())))
                : newUsername(c, name, role.toString());
        if (editing) {
            int verified = email.equalsIgnoreCase(existing.get("email").toString()) ?
                    ((Number)existing.get("email_verified")).intValue() : 3;
            exec(c, "UPDATE users SET name=?,email=?,email_verified=?,username=?,role=?,skill=?,available=?,capacity=?,password=? WHERE id=?",
                    name, email, verified, username, role, skill, availability, capacity, encoded, existing.get("id"));
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
        if (password instanceof String value && !value.isBlank() &&
                !useCode(c, existing, "password", String.valueOf(data.get("code"))))
            return Map.of("_otp_error", true);
        String encoded = password == null || "".equals(password) ? existing.get("password").toString() : hash(password);
        String skill = existing.get("skill").toString();
        if ("technician".equals(existing.get("role")) && data.get("skill") instanceof String value) {
            skill = value.equals("general") ? "" : value.strip();
            if (!skill.isEmpty()) category(c, skill);
        }
        boolean changedEmail = !email.equalsIgnoreCase(existing.get("email").toString());
        String username = name.equals(existing.get("name")) ? existing.get("username").toString() :
                formatUsername(name, existing.get("role").toString(), usernameCode(existing.get("username").toString()));
        exec(c, "UPDATE users SET name=?,email=?,email_verified=?,username=?,password=?,skill=? WHERE id=?",
                name, email, changedEmail ? 3 : existing.get("email_verified"), username, encoded, skill, uid);
        if (changedEmail) {
            issueCode(c, user(c, uid), "verify");
        }
        return Map.of("id", uid);
    }

    private Object removeUser(Connection c, int actorId, Map<String,Object> data) throws SQLException {
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        if (id == actorId) throw new Problem(400, "You cannot remove your own account.");
        var target = user(c, id);
        if ("admin".equals(target.get("role")) &&
                one(c, "SELECT 1 FROM users WHERE role='admin' AND active=1 AND id<>?", id) == null)
            throw new Problem(400, "Keep at least one administrator.");
        if (one(c, """
                SELECT 1 FROM incidents i WHERE i.status NOT IN ('Closed','Resolved','Verified')
                AND (i.technician_id=? OR EXISTS(SELECT 1 FROM incident_responders r
                    WHERE r.incident_id=i.id AND r.user_id=?))
                """, id, id) != null)
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
        var pending = database.transaction(false, c -> rows(c, """
                SELECT o.id,o.recipient,o.subject,o.body,o.attempts,n.incident_id FROM email_outbox o
                JOIN notifications n ON n.id=o.notification_id
                JOIN users u ON u.id=n.user_id AND u.email=o.recipient COLLATE NOCASE
                  AND u.active=1 AND u.email_verified=1
                JOIN incidents i ON i.id=n.incident_id
                WHERE o.status='pending' AND julianday(o.next_attempt_at)<=julianday(?)
                  AND (u.role='admin' OR i.reporter_id=u.id OR i.technician_id=u.id
                    OR EXISTS(SELECT 1 FROM incident_responders r
                      WHERE r.incident_id=i.id AND r.user_id=u.id))
                ORDER BY o.id LIMIT 100
                """, Instant.now().toString()));
        Map<String,List<Map<String,Object>>> batches = new LinkedHashMap<>();
        for (var message : pending) {
            String key = message.get("recipient") + ":" + message.get("incident_id");
            batches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(message);
        }
        for (var batch : batches.values()) {
            var first = batch.get(0);
            String body = batch.size() == 1 ? first.get("body").toString() :
                    "ServiceForce has " + batch.size() + " updates for incident #" + first.get("incident_id") + ".\n\n" +
                    String.join("\n\n---\n\n", batch.stream().map(m -> m.get("body").toString()).toList());
            try {
                mail.send(first.get("recipient").toString(), first.get("subject").toString(), body);
                database.transaction(true, c -> {
                    for (var message : batch) exec(c, "UPDATE email_outbox SET status='sent' WHERE id=?", message.get("id"));
                    return null;
                });
            } catch (Exception ignored) {
                database.transaction(true, c -> {
                    for (var message : batch) {
                        int attempts = ((Number)message.get("attempts")).intValue() + 1;
                        exec(c, "UPDATE email_outbox SET attempts=?,status=?,next_attempt_at=? WHERE id=?",
                                attempts, attempts >= 8 ? "failed" : "pending",
                                Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts, 6))).toString(), message.get("id"));
                    }
                    return null;
                });
            }
        }
    }

    public Object mutate(int uid, String action, Map<String,Object> data) {
        Object result = database.transaction(true, c -> {
            var actor = publicUser(user(c, uid));
            if (action == null || data == null) throw new Problem(400, "Enter a valid command.");
            return switch (action) {
                case "create" -> Map.of("id", incidents.create(c, actor, data, Instant.now().truncatedTo(ChronoUnit.SECONDS)));
                case "profile" -> saveProfile(c, uid, data);
                case "article-save" -> saveArticle(c, actor, data);
                case "article-delete" -> deleteArticle(c, actor, data);
                case "assign", "feedback", "transition", "update", "note", "reopen", "review", "responder-add", "responder-remove", "playbook-step" -> incidents.act(c, actor, action, data);
                default -> {
                    if (!"admin".equals(actor.get("role"))) throw new Problem(403, "Administrator access is required.");
                    if ("remove-user".equals(action)) yield removeUser(c, uid, data);
                    yield configure(c, action, data);
                }
            };
        });
        if (result instanceof Map<?,?> map && Boolean.TRUE.equals(map.get("_otp_error")))
            throw new Problem(400, "The password code is invalid or expired.");
        return result;
    }

    private Object configure(Connection c, String action, Map<String,Object> data) throws SQLException {
        switch (action) {
            case "playbook-template-add":
                return Playbooks.addTemplate(c, data);
            case "playbook-template-remove":
                return Playbooks.removeTemplate(c, data);
            case "user": {
                int id = saveUser(c, data);
                var person = user(c, id);
                if (!data.containsKey("id")) {
                    exec(c, "UPDATE users SET email_verified=3 WHERE id=?", id);
                    person = user(c, id);
                }
                if (((Number)person.get("email_verified")).intValue() != 1) issueCode(c, person, "verify");
                return Map.of("id", id);
            }
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
