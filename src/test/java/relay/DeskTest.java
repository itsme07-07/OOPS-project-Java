package relay;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.Executors;

@SuppressWarnings("unchecked")
class DeskTest {
    @TempDir Path folder;
    Desk desk;
    Path db;
    int employee, admin;
    static final String PASSWORD = "RelayDemo2026!";

    @BeforeEach void setup() throws Exception {
        db = folder.resolve("desk.sqlite3");
        desk = new Desk(db, true);
        employee = id(desk.login("employee@relay.test", PASSWORD));
        admin = id(desk.login("admin@relay.test", PASSWORD));
    }

    int id(Object row) { return ((Number)((Map<?,?>)row).get("id")).intValue(); }

    List<Map<String,Object>> list(int actor, String key) throws Exception {
        return (List<Map<String,Object>>)((Map<?,?>)desk.snapshot(actor)).get(key);
    }

    int report(int service, int impact, int urgency) throws Exception {
        return id(desk.mutate(employee, "create", Map.of("title", "Payment terminals fail", "description",
                "Checkout fails on all terminals.", "service_id", service, "impact", impact, "urgency", urgency)));
    }

    Map<String,Object> incident(int number) throws Exception {
        return list(admin, "incidents").stream().filter(i -> id(i) == number).findFirst().orElseThrow();
    }

    Map<String,Object> registration(String email, String password) {
        return Map.of("name", "New Reporter", "email", email, "password", password, "role", "admin");
    }

    @Test void newEmployeesCannotChooseTheirRoleOrReadOtherCases() throws Exception {
        var person = desk.register(registration("test@example.org", PASSWORD));
        assertEquals("employee", person.get("role"));
        assertTrue(list(id(person), "incidents").isEmpty());
    }

    @Test void duplicateAccountsAreRejected() throws Exception {
        var data = registration("test@example.org", PASSWORD);
        desk.register(data);
        assertThrows(Problem.class, () -> desk.register(data));
    }

    @Test void invalidEmailIsRejected() {
        assertThrows(Problem.class, () -> desk.register(registration("no-domain", PASSWORD)));
    }

    @Test void shortPasswordIsRejected() {
        assertThrows(Problem.class, () -> desk.register(registration("test@example.org", "123")));
    }

    @Test void accountsSurviveRestart() throws Exception {
        var person = desk.register(registration("test@example.org", PASSWORD));
        assertEquals(id(person), id(new Desk(db, false).login("test@example.org", PASSWORD)));
    }

    @Test void employeeCannotConfigureRouting() {
        assertEquals(403, assertThrows(Problem.class,
                () -> desk.mutate(employee,"strategy",Map.of("strategy","load"))).status);
    }

    @ParameterizedTest @CsvSource({"6,1,1,P4","6,2,2,P3","1,1,3,P2","1,2,3,P1"})
    void priorityUsesImpactUrgencyAndCriticality(int service,int impact,int urgency,String priority) throws Exception {
        assertEquals(priority,incident(report(service,impact,urgency)).get("priority"));
    }

    @Test void invalidReportRollsBack() throws Exception {
        int count = list(admin,"incidents").size();
        assertThrows(Problem.class, () -> report(1,4,3));
        assertThrows(Problem.class, () -> report(9999,1,1));
        assertEquals(count,list(admin,"incidents").size());
    }

    @Test void relatedCasesKeepSeparateHistories() throws Exception {
        int first=report(1,3,3), second=report(1,3,3);
        assertNotEquals(first,second);
        assertEquals(first,((Number)incident(second).get("related_id")).intValue());
    }

    @Test void closureRequiresVerification() throws Exception {
        int ticket=report(1,3,3);
        assertThrows(Problem.class, () -> desk.mutate(admin,"transition",Map.of("id",ticket,"status","Closed")));
        assertEquals("Assigned",incident(ticket).get("status"));
    }

    @Test void technicianAndReporterCanCompleteLifecycle() throws Exception {
        int ticket=report(1,3,3);
        int tech=((Number)incident(ticket).get("technician_id")).intValue();
        for (String status : List.of("Acknowledged","In progress","Resolved","Verified","Closed")) {
            int actor=List.of("Verified","Closed").contains(status) ? employee : tech;
            desk.mutate(actor,"transition",Map.of("id",ticket,"status",status,"resolution","Restored gateway."));
        }
        desk.mutate(employee,"feedback",Map.of("id",ticket,"feedback","Confirmed working."));
        var row=incident(ticket);
        for (String key : List.of("acknowledged_at","resolved_at","closed_at","resolution","feedback"))
            assertNotNull(row.get(key));
        var channels=new HashSet<>();
        list(employee,"notifications").stream().filter(n -> n.get("incident_id").equals(ticket))
                .forEach(n -> channels.add(n.get("channel")));
        assertEquals(Set.of("In-app","Simulated email"),channels);
    }

    @Test void ownershipProtectsNotes() throws Exception {
        int stranger=id(desk.register(registration("stranger@example.org",PASSWORD)));
        int ticket=report(1,3,3);
        assertThrows(Problem.class, () -> desk.mutate(stranger,"note",Map.of("id",ticket,"text","Unrelated")));
    }

    @Test void loadRoutingChoosesLeastBusyEligibleTechnician() throws Exception {
        desk.mutate(admin,"strategy",Map.of("strategy","load"));
        Map<Integer,Integer> loads=new TreeMap<>();
        for (var user:list(admin,"users"))
            if ("technician".equals(user.get("role"))) loads.put(id(user),0);
        for (var incident:list(admin,"incidents"))
            if (!Set.of("Closed","Verified","Resolved").contains(incident.get("status")))
                loads.computeIfPresent((Integer)incident.get("technician_id"),(k,v)->v+1);
        int expected=Collections.min(loads.entrySet(),Map.Entry.comparingByValue()).getKey();
        assertEquals(expected,incident(report(1,3,3)).get("technician_id"));
    }

    @Test void monitoringIsIdempotent() throws Exception {
        int ticket=report(1,3,3);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);
            var ps=c.prepareStatement("UPDATE incidents SET response_due='2000-01-01T00:00:00+00:00' WHERE id=?")) {
            ps.setInt(1,ticket);
            ps.executeUpdate();
        }
        desk.monitor();
        desk.monitor();
        assertEquals(1,list(admin,"audit").stream().filter(a->a.get("incident_id").equals(ticket)
                && "SLA Breached".equals(a.get("action"))).count());
        assertEquals(1,list(admin,"audit").stream().filter(a->a.get("incident_id").equals(ticket)
                && "Escalated".equals(a.get("action"))).count());
        assertEquals(true, incident(ticket).get("escalated"));
        assertTrue(list(admin,"notifications").stream().anyMatch(n->n.get("incident_id").equals(ticket)
                && String.valueOf(n.get("message")).contains("Administrator action required")));
    }

    @Test void acknowledgedCriticalIncidentDoesNotEscalate() throws Exception {
        int ticket=report(1,3,3);
        int tech=((Number)incident(ticket).get("technician_id")).intValue();
        desk.mutate(tech,"transition",Map.of("id",ticket,"status","Acknowledged"));
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);
            var ps=c.prepareStatement("UPDATE incidents SET response_due='2000-01-01T00:00:00+00:00' WHERE id=?")) {
            ps.setInt(1,ticket);
            ps.executeUpdate();
        }
        desk.monitor();
        assertEquals(false, incident(ticket).get("escalated"));
    }

    @Test void editingImpactReprioritizes() throws Exception {
        int ticket=report(1,1,1);
        desk.mutate(employee,"update",Map.of("id",ticket,"impact",3,"urgency",3));
        assertEquals("P1",incident(ticket).get("priority"));
    }

    @Test void policyEditOnlyAffectsNewCases() throws Exception {
        int first=report(1,3,3);
        Object deadline=incident(first).get("resolution_due");
        desk.mutate(admin,"policy",Map.of("priority","P1","response_minutes",1,"resolution_minutes",2));
        assertEquals(deadline,incident(first).get("resolution_due"));
        assertNotEquals(deadline,incident(report(1,3,3)).get("resolution_due"));
    }

    @Test void concurrentReportsReceiveDistinctIds() throws Exception {
        var pool=Executors.newFixedThreadPool(4);
        try {
            var ids=new HashSet<Integer>();
            var futures=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<4;i++) futures.add(pool.submit(()->report(1,3,3)));
            for(var result:futures) ids.add(result.get());
            assertEquals(4,ids.size());
        } finally {pool.shutdownNow();}
    }
}
