package relay;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static relay.Database.*;
import static relay.Desk.number;
import static relay.Desk.required;

/** Incident lifecycle, assignment, visibility and SLA rules, executed within the caller's transaction. */
final class Incidents {
    private static final Set<String> COMPLETE = Set.of("Resolved", "Verified", "Closed");
    private static final Set<String> STOP_WORDS = Set.of("", "a", "the", "of", "in", "an", "to", "and", "is", "for");
    private static final Map<String,String> NEXT = Map.of(
        "Assigned", "Acknowledged", "Acknowledged", "In progress", "In progress", "Resolved",
        "Resolved", "Verified", "Verified", "Closed");
    private static final Map<String,AssignmentStrategy> STRATEGIES = Map.of(
        "skill", new AssignmentStrategy.SkillFirst(), "load", new AssignmentStrategy.LeastLoad());
    private static final String PUBLIC_USER = "id,name,email,role,skill,available,capacity";
    private final List<IncidentEvent.Observer> observers =
        List.of(new IncidentEvent.AuditLog(), new IncidentEvent.NotificationOutbox());

    Incidents(Database database) {}

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static boolean isAdmin(Map<String,Object> u) {
        return "admin".equals(u.get("role"));
    }

    private static boolean sameId(Object a, Object b) {
        return a != null && b != null && a.toString().equals(b.toString());
    }

    private static boolean owns(Map<String,Object> user, Map<String,Object> incident) {
        return sameId(user.get("id"), incident.get("reporter_id"));
    }

    private static boolean visible(Map<String,Object> user, Map<String,Object> i) {
        return isAdmin(user) || owns(user, i) || ("technician".equals(user.get("role"))
                && sameId(user.get("id"), i.get("technician_id")));
    }

    private static Map<String,Object> incident(Connection c, Object id) throws SQLException {
        var i = one(c, "SELECT * FROM incidents WHERE id=?", id);
        if (i == null) throw new Problem(404, "Incident not found.");
        return i;
    }

    private void event(Connection c, Map<String,Object> i, Object actor, String action, String message,
                       String before, String after, Instant at) throws SQLException {
        var change = new IncidentEvent(i, actor.toString(), action, message, before, after, at);
        for (var observer : observers) observer.record(c, change);
    }

    private static List<Map<String,Object>> eligible(Connection c) throws SQLException {
        return rows(c, """
            SELECT u.id,u.skill,u.name,count(i.id) AS workload FROM users u
            LEFT JOIN incidents i ON i.technician_id=u.id AND i.status NOT IN ('Closed','Resolved','Verified')
            WHERE u.role='technician' AND u.available=1 AND u.active=1 GROUP BY u.id HAVING count(i.id)<u.capacity
            """);
    }

    private static String configuredStrategy(Connection c) throws SQLException {
        var setting = one(c, "SELECT value FROM settings WHERE name='strategy'");
        if (setting == null || !STRATEGIES.containsKey(setting.get("value")))
            throw new Problem(409, "Select a valid assignment strategy.");
        return setting.get("value").toString();
    }

    private static String priority(int score) {
        if (score >= 18) return "P1";
        if (score >= 9) return "P2";
        if (score >= 4) return "P3";
        return "P4";
    }

    private static Map<String,Object> policy(Connection c, String priority) throws SQLException {
        var p = one(c, "SELECT * FROM policies WHERE priority=?", priority);
        if (p == null) throw new Problem(409, "SLA configuration is missing.");
        return p;
    }

    private static String due(Instant created, Map<String,Object> policy, String field) {
        return created.plus(((Number) policy.get(field)).longValue(), ChronoUnit.MINUTES).toString();
    }

    private static Set<String> terms(Object title) {
        var words = new HashSet<>(Arrays.asList(title.toString().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
        words.removeAll(STOP_WORDS);
        return words;
    }

    private record Correlation(Object related, Object source, String resolution) {}

    private static Correlation correlate(Connection c, Object service, String title) throws SQLException {
        Set<String> wanted = terms(title);
        Object related = null, source = null;
        String resolution = "";
        for (var other : rows(c, "SELECT id,title,status,resolution FROM incidents WHERE service_id=? ORDER BY id DESC", service)) {
            Set<String> words = terms(other.get("title"));
            long overlap = words.stream().filter(wanted::contains).count();
            long union = words.size() + wanted.size() - overlap;
            if (overlap < 0.3 * Math.max(1, union)) continue;
            if (related == null && !COMPLETE.contains(other.get("status"))) related = other.get("id");
            if (source == null && other.get("resolution") instanceof String s && !s.isEmpty()) {
                resolution = s;
                source = other.get("id");
            }
            if (related != null && source != null) break;
        }
        return new Correlation(related, source, resolution);
    }

    int create(Connection c, Map<String,Object> user, Map<String,Object> data, Instant at) throws SQLException {
        String title = required(data, "title", 160), description = required(data, "description", 6000);
        var service = one(c, "SELECT * FROM services WHERE id=?", number(data, "service_id", 1, Integer.MAX_VALUE));
        if (service == null) throw new Problem(404, "Service not found.");
        Object supplied = data.get("category");
        if (supplied == null || "".equals(supplied)) supplied = service.get("category");
        if (!(supplied instanceof String category) || one(c, "SELECT name FROM categories WHERE name=?", category) == null)
            throw new Problem(400, "Choose an existing category.");
        int impact = number(data, "impact", 1, 3), urgency = number(data, "urgency", 1, 3);
        int score = impact * urgency * ((Number) service.get("criticality")).intValue();
        String priority = priority(score);
        var policy = policy(c, priority);
        var tech = STRATEGIES.get(configuredStrategy(c)).choose(eligible(c), category);
        String status = tech == null ? "Classified" : "Assigned";
        var match = correlate(c, service.get("id"), title);
        int id = insert(c, """
            INSERT INTO incidents(title,description,reporter_id,category,service_id,impact,urgency,priority,status,
                technician_id,created_at,response_due,resolution_due,related_id,suggestion_source_id,suggested_resolution)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, title, description, user.get("id"), category, service.get("id"), impact, urgency, priority, status,
            tech == null ? null : tech.get("id"), at.toString(), due(at, policy, "response_minutes"),
            due(at, policy, "resolution_minutes"), match.related, match.source, match.resolution);
        var i = incident(c, id);
        event(c, i, user.get("name"), "Reported", title, null, "New", at);
        event(c, i, "System", "Classified", category + " · " + priority + " (score " + score + ")", "New", "Classified", at);
        if (tech == null) event(c, i, "System", "Awaiting assignment", "No technician has available capacity.", null, null, at);
        else event(c, i, "System", "Assigned", "Assigned to " + tech.get("name"), "Classified", status, at);
        if (match.related != null) event(c, i, "System", "Related incident", "Similar wording detected on the same service.", null, null, at);
        return id;
    }

    void transition(Connection c, Map<String,Object> user, int id, Map<String,Object> data, Instant at) throws SQLException {
        var i = incident(c, id);
        String current = i.get("status").toString();
        if (!(data.get("status") instanceof String target) || !target.equals(NEXT.get(current)))
            throw new Problem(409, "This transition is unavailable for the current incident state.");
        boolean permitted = switch (target) {
            case "Verified", "Closed" -> owns(user, i);
            default -> "technician".equals(user.get("role")) && sameId(user.get("id"), i.get("technician_id"));
        };
        if (!isAdmin(user) && !permitted) throw new Problem(403, "Your role cannot perform this transition.");
        String resolution = target.equals("Resolved") ? required(data, "resolution", 6000) : (String)i.get("resolution");
        exec(c, """
            UPDATE incidents SET status=?,resolution=?,
                acknowledged_at=CASE WHEN ?='Acknowledged' THEN ? ELSE acknowledged_at END,
                resolved_at=CASE WHEN ?='Resolved' THEN ? ELSE resolved_at END,
                closed_at=CASE WHEN ?='Closed' THEN ? ELSE closed_at END WHERE id=?
            """, target, resolution, target, at.toString(), target, at.toString(), target, at.toString(), id);
        event(c, i, user.get("name"), target, target.equals("Resolved") ? resolution : "Moved to " + target + ".", current, target, at);
    }

    private void update(Connection c, Map<String,Object> user, Map<String,Object> i, Map<String,Object> data, Instant at) throws SQLException {
        if (!isAdmin(user) && !owns(user, i)) throw new Problem(403, "Only the reporter or an administrator can edit the report.");
        if (COMPLETE.contains(i.get("status"))) throw new Problem(409, "This incident has already been resolved.");
        var merged = new HashMap<>(i);
        merged.putAll(data);
        String title = required(merged, "title", 160), description = required(merged, "description", 6000);
        int impact = number(merged, "impact", 1, 3), urgency = number(merged, "urgency", 1, 3);
        var service = one(c, "SELECT criticality FROM services WHERE id=?", i.get("service_id"));
        int score = impact * urgency * ((Number) service.get("criticality")).intValue();
        String priority = priority(score);
        Object response = i.get("response_due"), resolution = i.get("resolution_due");
        if (!priority.equals(i.get("priority"))) {
            monitorIncident(c, i, at);
            var p = policy(c, priority);
            Instant created = Instant.parse(i.get("created_at").toString());
            if (i.get("acknowledged_at") == null) response = due(created, p, "response_minutes");
            resolution = due(created, p, "resolution_minutes");
            event(c, i, user.get("name"), "Priority changed", i.get("priority") + " → " + priority + " (score " + score + ").", null, null, at);
        }
        var fields = new LinkedHashMap<String,Object>();
        fields.put("title", title);
        fields.put("description", description);
        fields.put("impact", impact);
        fields.put("urgency", urgency);
        var changes = new ArrayList<String>();
        fields.forEach((k,v) -> {
            if (!v.toString().equals(i.get(k).toString())) changes.add(k + ": " + i.get(k) + " → " + v);
        });
        exec(c, """
            UPDATE incidents SET title=?,description=?,impact=?,urgency=?,priority=?,response_due=?,resolution_due=? WHERE id=?
            """, title, description, impact, urgency, priority, response, resolution, i.get("id"));
        if (!changes.isEmpty()) event(c, i, user.get("name"), "Report updated", String.join("\n", changes), null, null, at);
    }

    private void assign(Connection c, Map<String,Object> user, Map<String,Object> i, int destination, Instant at) throws SQLException {
        if (!isAdmin(user)) throw new Problem(403, "Administrator access is required.");
        if (COMPLETE.contains(i.get("status"))) throw new Problem(409, "Resolved incidents cannot be reassigned.");
        if (sameId(destination, i.get("technician_id"))) return;
        var tech = eligible(c).stream().filter(t -> sameId(t.get("id"), destination)).findFirst()
                .orElseThrow(() -> new Problem(409, "The requested technician is unavailable or at capacity."));
        String current = i.get("status").toString();
        String status = current.equals("Classified") ? "Assigned" : current;
        exec(c, "UPDATE incidents SET technician_id=?,status=? WHERE id=?", destination, status, i.get("id"));
        event(c, incident(c, i.get("id")), user.get("name"), "Assigned", "Assigned to " + tech.get("name"), current, status, at);
    }

    Object act(Connection c, Map<String,Object> user, String action, Map<String,Object> data) throws SQLException {
        Instant at = now();
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        var i = incident(c, id);
        if (!visible(user, i)) throw new Problem(404, "Incident not found.");
        switch (action) {
            case "transition" -> transition(c, user, id, data, at);
            case "update" -> update(c, user, i, data, at);
            case "assign" -> assign(c, user, i, number(data, "technician_id", 1, Integer.MAX_VALUE), at);
            case "note" -> event(c, i, user.get("name"), "Note", required(data, "text", 6000), null, null, at);
            case "feedback" -> {
                if (!owns(user, i) || !COMPLETE.contains(i.get("status")))
                    throw new Problem(403, "Only the reporter can give feedback after resolution.");
                String value = required(data, "feedback", 3000);
                exec(c, "UPDATE incidents SET feedback=? WHERE id=?", value, id);
                event(c, i, user.get("name"), "Feedback", value, null, null, at);
            }
            default -> throw new Problem(404, "Unknown command.");
        }
        return Map.of("id", id);
    }

    private record SlaEvent(String target, String kind) {}

    private static List<SlaEvent> sla(Map<String,Object> i, Instant at) {
        List<SlaEvent> events = new ArrayList<>();
        Instant created = Instant.parse(i.get("created_at").toString());
        for (String target : List.of("response", "resolution")) {
            Instant deadline = Instant.parse(i.get(target + "_due").toString());
            Object finish = i.get(target.equals("response") ? "acknowledged_at" : "resolved_at");
            Instant measured = finish == null ? at : Instant.parse(finish.toString());
            if (measured.isAfter(deadline)) events.add(new SlaEvent(target, "Breached"));
            else if (finish == null && !at.isBefore(deadline.minus(Duration.between(created, deadline).dividedBy(5))))
                events.add(new SlaEvent(target, "At risk"));
        }
        return events;
    }

    private void monitorIncident(Connection c, Map<String,Object> i, Instant at) throws SQLException {
        for (var condition : sla(i, at)) {
            if (exec(c, "INSERT OR IGNORE INTO sla_events(incident_id,target,kind) VALUES(?,?,?)",
                    i.get("id"), condition.target, condition.kind) == 0) continue;
            String label = condition.target.equals("response") ? "Response" : "Resolution";
            event(c, i, "System", "SLA " + condition.kind, label + " SLA · " + condition.kind.toLowerCase(Locale.ROOT) + ".", null, null, at);
        }
        if ("P1".equals(i.get("priority")) && i.get("acknowledged_at") == null
                && !COMPLETE.contains(i.get("status"))
                && at.isAfter(Instant.parse(i.get("response_due").toString()))
                && exec(c, "INSERT OR IGNORE INTO sla_events(incident_id,target,kind) VALUES(?,'response','Escalated')",
                    i.get("id")) != 0) {
            event(c, i, "System", "Escalated",
                    "Critical incident was not acknowledged by its response deadline. Administrator action required.",
                    null, null, at);
        }
    }

    void monitor(Connection c) throws SQLException {
        Instant at = now();
        for (var i : rows(c, "SELECT * FROM incidents")) monitorIncident(c, i, at);
    }

    private static String slaStatus(Map<String,Object> i, Instant at) {
        var events = sla(i, at);
        if (events.stream().anyMatch(e -> e.kind.equals("Breached"))) return "Breached";
        if (!events.isEmpty()) return "At risk";
        return i.get("resolved_at") != null ? "Met" : "On track";
    }

    Object snapshot(Connection c, Map<String,Object> user) throws SQLException {
        monitor(c);
        Instant at = now();
        var visible = rows(c, """
            SELECT i.*,s.name AS service,u.name AS reporter,t.name AS technician FROM incidents i
            JOIN services s ON i.service_id=s.id JOIN users u ON i.reporter_id=u.id
            LEFT JOIN users t ON i.technician_id=t.id
            WHERE ? OR i.reporter_id=? OR (? AND i.technician_id=?) ORDER BY i.id DESC
            """, isAdmin(user) ? 1 : 0, user.get("id"), "technician".equals(user.get("role")) ? 1 : 0, user.get("id"));
        Set<Object> visibleIds = new HashSet<>();
        for (var i : visible) visibleIds.add(i.get("id"));
        for (var i : visible) {
            Object source = i.remove("suggestion_source_id");
            if (source != null && !visibleIds.contains(source)) i.put("suggested_resolution", "");
            if (!visibleIds.contains(i.get("related_id"))) i.put("related_id", null);
            i.put("sla_status", slaStatus(i, at));
            i.put("escalated", one(c, "SELECT 1 AS value FROM sla_events WHERE incident_id=? AND target='response' AND kind='Escalated'",
                    i.get("id")) != null);
        }
        var audit = rows(c, "SELECT * FROM audit ORDER BY julianday(created_at) DESC,id DESC")
                .stream().filter(row -> visibleIds.contains(row.get("incident_id"))).toList();
        var notices = rows(c, "SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC", user.get("id"))
                .stream().filter(row -> visibleIds.contains(row.get("incident_id"))).toList();
        var users = rows(c, "SELECT " + PUBLIC_USER + " FROM users WHERE active=1 AND (? OR id=?) ORDER BY name",
                isAdmin(user) ? 1 : 0, user.get("id"));
        return Map.of("user", Desk.publicUser(user),
                "users", users, "incidents", visible, "audit", audit, "notifications", notices,
                "services", rows(c, "SELECT * FROM services ORDER BY id"),
                "categories", rows(c, "SELECT name FROM categories ORDER BY name").stream().map(r -> r.get("name")).toList(),
                "policies", rows(c, "SELECT * FROM policies ORDER BY priority"), "strategy", configuredStrategy(c));
    }
}
