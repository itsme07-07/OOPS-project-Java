package relay;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static relay.Database.*;
import static relay.Desk.number;
import static relay.Desk.required;

/** Category templates are copied into incidents so later edits do not rewrite investigation history. */
final class Playbooks {
    private static final Map<String,List<String>> DEFAULTS = defaults();

    private static Map<String,List<String>> defaults() {
        var result = new LinkedHashMap<String,List<String>>();
        result.put("Application", List.of("Confirm the affected application and error", "Check recent application changes", "Test the fix with the reporter"));
        result.put("Network", List.of("Confirm whether one device or many are affected", "Check connectivity and network path", "Verify the service is reachable again"));
        result.put("Authentication", List.of("Confirm the sign-in error without collecting passwords", "Check account status and identity service", "Verify access with the reporter"));
        result.put("Database", List.of("Confirm which queries or services are affected", "Check database availability and recent changes", "Verify data access after the fix"));
        result.put("Storage", List.of("Confirm the affected storage location", "Check capacity, permissions and service health", "Verify file access after the fix"));
        result.put("Hardware", List.of("Identify the affected device and symptoms", "Check power, connections and diagnostics", "Verify the device works after repair"));
        result.put("Cloud", List.of("Identify the affected cloud service and region", "Check provider status and recent configuration changes", "Verify the service after recovery"));
        result.put("Security", List.of("Record the reported indicator without exposing secrets", "Contain the affected account or system if required", "Verify remediation and document the impact"));
        result.put("Software", List.of("Reproduce the software issue", "Check version, configuration and recent changes", "Verify the solution with the reporter"));
        return result;
    }

    static void seed(Connection c) throws SQLException {
        if (one(c, "SELECT value FROM settings WHERE name='playbooks_seeded'") != null) return;
        for (var entry : DEFAULTS.entrySet()) {
            if (one(c, "SELECT 1 FROM categories WHERE name=?", entry.getKey()) == null) continue;
            int position = 1;
            for (String title : entry.getValue())
                exec(c, "INSERT INTO playbook_templates(category,title,position) VALUES(?,?,?)",
                        entry.getKey(), title, position++);
        }
        for (var incident : rows(c, "SELECT id,category FROM incidents"))
            copyToIncident(c, ((Number)incident.get("id")).intValue(), incident.get("category").toString());
        exec(c, "INSERT INTO settings(name,value) VALUES('playbooks_seeded','1')");
    }

    static void copyToIncident(Connection c, int incidentId, String category) throws SQLException {
        if (one(c, "SELECT 1 FROM incident_playbook_steps WHERE incident_id=? LIMIT 1", incidentId) != null) return;
        exec(c, """
            INSERT INTO incident_playbook_steps(incident_id,title,position)
            SELECT ?,title,position FROM playbook_templates WHERE category=? ORDER BY position,id
            """, incidentId, category);
    }

    static Object addTemplate(Connection c, Map<String,Object> data) throws SQLException {
        String category = required(data, "category", 80);
        String title = required(data, "title", 180);
        if (one(c, "SELECT 1 FROM categories WHERE name=?", category) == null)
            throw new Problem(400, "Choose an existing category.");
        int count = ((Number)one(c, "SELECT count(*) AS total FROM playbook_templates WHERE category=?", category).get("total")).intValue();
        if (count >= 12) throw new Problem(409, "A category can have up to 12 playbook steps.");
        if (one(c, "SELECT 1 FROM playbook_templates WHERE category=? AND title=? COLLATE NOCASE", category, title) != null)
            throw new Problem(409, "This playbook step already exists in the category.");
        int next = ((Number)one(c, "SELECT COALESCE(max(position),0)+1 AS next FROM playbook_templates WHERE category=?",
                category).get("next")).intValue();
        int id = insert(c, "INSERT INTO playbook_templates(category,title,position) VALUES(?,?,?)",
                category, title, next);
        return Map.of("id", id);
    }

    static Object removeTemplate(Connection c, Map<String,Object> data) throws SQLException {
        int id = number(data, "id", 1, Integer.MAX_VALUE);
        if (exec(c, "DELETE FROM playbook_templates WHERE id=?", id) == 0)
            throw new Problem(404, "Playbook step not found.");
        return Map.of();
    }

    private Playbooks() {}
}
