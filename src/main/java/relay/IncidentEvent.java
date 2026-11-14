package relay;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Each observer participates in the same transaction as the incident change. */
record IncidentEvent(Map<String,Object> incident, String actor, String action, String message,
                     String before, String after, Instant at) {
    interface Observer {
        void record(Connection c, IncidentEvent event) throws SQLException;
    }

    static final class AuditLog implements Observer {
        public void record(Connection c, IncidentEvent e) throws SQLException {
            Database.exec(c, """
                INSERT INTO audit(incident_id,actor,action,description,previous_state,new_state,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, e.incident.get("id"), e.actor, e.action, e.message, e.before, e.after, e.at.toString());
        }
    }

    static final class NotificationOutbox implements Observer {
        public void record(Connection c, IncidentEvent e) throws SQLException {
            Set<Object> recipients = new HashSet<>();
            recipients.add(e.incident.get("reporter_id"));
            recipients.add(e.incident.get("technician_id"));
            if (e.action.startsWith("SLA") || e.action.equals("Awaiting assignment") || e.action.equals("Escalated")) {
                for (var user : Database.rows(c, "SELECT id FROM users WHERE role='admin'")) {
                    recipients.add(user.get("id"));
                }
            }
            recipients.remove(null);
            for (var recipient : recipients) {
                for (String channel : List.of("In-app", "Simulated email")) {
                    Database.exec(c, """
                        INSERT INTO notifications(incident_id,user_id,message,channel,created_at) VALUES(?,?,?,?,?)
                        """, e.incident.get("id"), recipient, e.message, channel, e.at.toString());
                }
            }
        }
    }
}
