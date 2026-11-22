package relay;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Typed domain values at the SQL-row boundary. HTTP payloads remain maps at the API boundary. */
final class DomainModels {
    record Actor(int id, String name, String role) {
        static Actor from(Map<String,Object> row) {
            return new Actor(integer(row, "id"), string(row, "name"), string(row, "role"));
        }

        boolean isAdministrator() { return role.equals("admin"); }
        boolean isTechnician() { return role.equals("technician"); }
    }

    record Service(int id, String category, int criticality) {
        static Service from(Map<String,Object> row) {
            return new Service(integer(row, "id"), string(row, "category"), integer(row, "criticality"));
        }

        int priorityScore(int impact, int urgency) {
            return impact * urgency * criticality;
        }
    }

    record SlaPolicy(String priority, int responseMinutes, int resolutionMinutes) {
        static SlaPolicy from(Map<String,Object> row) {
            return new SlaPolicy(string(row, "priority"), integer(row, "response_minutes"),
                    integer(row, "resolution_minutes"));
        }

        Instant responseDue(Instant start) { return start.plus(responseMinutes, ChronoUnit.MINUTES); }
        Instant resolutionDue(Instant start) { return start.plus(resolutionMinutes, ChronoUnit.MINUTES); }
    }

    record Technician(int id, String name, String skill, int workload) {
        static Technician from(Map<String,Object> row) {
            return new Technician(integer(row, "id"), string(row, "name"), string(row, "skill"),
                    integer(row, "workload"));
        }
    }

    record Incident(int id, int reporterId, Integer leadId, String category, String status,
                    String priority, Instant createdAt, Instant responseDue, Instant resolutionDue,
                    Instant acknowledgedAt, Instant resolvedAt, Instant recoveryDue, int escalationStage) {
        static Incident from(Map<String,Object> row) {
            return new Incident(integer(row, "id"), integer(row, "reporter_id"), nullableInteger(row, "technician_id"),
                    string(row, "category"), string(row, "status"), string(row, "priority"),
                    instant(row, "created_at"), instant(row, "response_due"), instant(row, "resolution_due"),
                    nullableInstant(row, "acknowledged_at"), nullableInstant(row, "resolved_at"),
                    nullableInstant(row, "recovery_due"), integer(row, "escalation_stage"));
        }

        boolean isComplete() { return status.equals("Resolved") || status.equals("Verified") || status.equals("Closed"); }
        boolean belongsTo(int userId) { return reporterId == userId; }
        boolean ledBy(int userId) { return leadId != null && leadId == userId; }
    }

    private static int integer(Map<String,Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static Integer nullableInteger(Map<String,Object> row, String key) {
        return row.get(key) == null ? null : integer(row, key);
    }
    private static String string(Map<String,Object> row, String key) { return row.get(key).toString(); }
    private static Instant instant(Map<String,Object> row, String key) { return Instant.parse(string(row, key)); }
    private static Instant nullableInstant(Map<String,Object> row, String key) {
        return row.get(key) == null ? null : instant(row, key);
    }

    private DomainModels() {}
}
