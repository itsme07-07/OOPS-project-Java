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

    @Test void systemGeneratesPrivateRoleBasedUsernames() throws Exception {
        var employeeAccount=(Map<?,?>)((Map<?,?>)desk.snapshot(employee)).get("user");
        var adminAccount=(Map<?,?>)((Map<?,?>)desk.snapshot(admin)).get("user");
        int technician=id(desk.login("tech@relay.test",PASSWORD));
        var technicianAccount=(Map<?,?>)((Map<?,?>)desk.snapshot(technician)).get("user");
        assertTrue(employeeAccount.get("username").toString().matches("example[A-F0-9]{8}@service\\.emp"));
        assertTrue(adminAccount.get("username").toString().matches("example[A-F0-9]{8}@service\\.adm"));
        assertTrue(technicianAccount.get("username").toString().matches("example[A-F0-9]{8}@service\\.tech"));
        assertEquals(employee,id(desk.login(employeeAccount.get("username").toString(),PASSWORD)));
        assertEquals(employee,id(desk.login("employee@relay.test",PASSWORD)));
        assertTrue(list(admin,"users").stream().noneMatch(u->u.containsKey("username")));
        desk.mutate(admin,"user",Map.of("id",employee,"username","example00000000@service.emp"));
        assertEquals(employeeAccount.get("username"),((Map<?,?>)((Map<?,?>)desk.snapshot(employee)).get("user")).get("username"));
    }

    @Test void existingDatabaseReceivesGeneratedUsernames() throws Exception {
        Path old=folder.resolve("old.sqlite3");
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+old);
            var statement=c.createStatement()) {
            statement.execute("""
                CREATE TABLE users(id INTEGER PRIMARY KEY,name TEXT NOT NULL,email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,role TEXT NOT NULL,skill TEXT NOT NULL DEFAULT '',
                available INTEGER NOT NULL DEFAULT 1,capacity INTEGER NOT NULL DEFAULT 20,active INTEGER NOT NULL DEFAULT 1)
                """);
        }
        var upgraded=new Desk(old,true);
        int existing=id(upgraded.login("admin@relay.test",PASSWORD));
        var current=(Map<?,?>)((Map<?,?>)upgraded.snapshot(existing)).get("user");
        assertTrue(current.get("username").toString().matches("example[A-F0-9]{8}@service\\.adm"));
    }

    @Test void oldUsernameFormatMigratesWithoutChangingCode() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);
            var ps=c.prepareStatement("UPDATE users SET username=? WHERE id=?")) {
            ps.setString(1,"example@service.empA1B2C3D4");
            ps.setInt(2,employee);
            ps.executeUpdate();
        }
        var upgraded=new Desk(db,false);
        var current=(Map<?,?>)((Map<?,?>)upgraded.snapshot(employee)).get("user");
        assertEquals("exampleA1B2C3D4@service.emp",current.get("username"));
        assertEquals(employee,id(upgraded.login("exampleA1B2C3D4@service.emp",PASSWORD)));
    }

    @Test void everyRoleCanEditOnlyItsOwnProfile() throws Exception {
        int technician=id(desk.login("tech@relay.test",PASSWORD));
        for (int actor : List.of(employee, technician, admin)) {
            String email="profile-"+actor+"@example.org";
            desk.mutate(actor,"profile",Map.of("name","Updated Person","email",email,
                    "current_password",PASSWORD,"password","NewPassword2026!"));
            assertEquals(actor,id(desk.login(email,"NewPassword2026!")));
            var current=(Map<?,?>)((Map<?,?>)desk.snapshot(actor)).get("user");
            assertEquals("Updated Person",current.get("name"));
            assertThrows(Problem.class,()->desk.mutate(actor,"profile",Map.of("name","Wrong","email",email,
                    "current_password","wrong")));
        }
    }

    @Test void adminCanRemovePersonWithoutLosingIncidentHistory() throws Exception {
        int ticket=report(1,3,3);
        assertEquals(403,assertThrows(Problem.class,()->desk.mutate(employee,"remove-user",Map.of("id",admin))).status);
        assertEquals(409,assertThrows(Problem.class,()->desk.mutate(admin,"remove-user",
                Map.of("id",incident(ticket).get("technician_id")))).status);
        assertEquals(400,assertThrows(Problem.class,()->desk.mutate(admin,"remove-user",Map.of("id",admin))).status);
        desk.mutate(admin,"remove-user",Map.of("id",employee));
        assertThrows(Problem.class,()->desk.login("employee@relay.test",PASSWORD));
        assertEquals(401,assertThrows(Problem.class,()->desk.snapshot(employee)).status);
        assertEquals(ticket,id(incident(ticket)));
        assertFalse(list(admin,"users").stream().anyMatch(u->id(u)==employee));
        assertEquals("employee",desk.register(registration("employee@relay.test",PASSWORD)).get("role"));
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

    @Test void attachmentsAreLimitedAndPrivateToIncidentParticipants() throws Exception {
        int ticket=report(1,3,3);
        int stranger=id(desk.register(registration("stranger@example.org",PASSWORD)));
        byte[] content="What happened".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int fileId=desk.addAttachment(employee,ticket,"error.txt","text/plain",content);
        assertArrayEquals(content,desk.attachment(admin,fileId).content());
        assertEquals("error.txt",desk.attachment(employee,fileId).fileName());
        assertEquals(404,assertThrows(Problem.class,()->desk.attachment(stranger,fileId)).status);
        assertEquals(404,assertThrows(Problem.class,()->desk.addAttachment(stranger,ticket,"x.txt","text/plain",content)).status);
        assertEquals(415,assertThrows(Problem.class,()->desk.addAttachment(employee,ticket,"x.html","text/html",content)).status);
        assertEquals(1,((List<?>)incident(ticket).get("attachments")).size());
    }

    @Test void internalNotesStayHiddenFromReporter() throws Exception {
        int ticket=report(1,3,3);
        int technician=((Number)incident(ticket).get("technician_id")).intValue();
        assertEquals(403,assertThrows(Problem.class,()->desk.mutate(employee,"note",
                Map.of("id",ticket,"text","Private","visibility","internal"))).status);
        desk.mutate(technician,"note",Map.of("id",ticket,"text","Check server logs","visibility","internal"));
        desk.mutate(technician,"note",Map.of("id",ticket,"text","We are investigating","visibility","public"));
        assertTrue(list(admin,"audit").stream().anyMatch(a->"Internal note".equals(a.get("action"))));
        assertFalse(list(employee,"audit").stream().anyMatch(a->"Internal note".equals(a.get("action"))));
        assertFalse(list(employee,"notifications").stream().anyMatch(n->"Check server logs".equals(n.get("message"))));
        assertTrue(list(employee,"notifications").stream().anyMatch(n->"We are investigating".equals(n.get("message"))));
    }

    @Test void reporterCanReopenResolvedIncidentAndStaffCanReviewP1() throws Exception {
        int ticket=report(1,3,3);
        int technician=((Number)incident(ticket).get("technician_id")).intValue();
        for(String status:List.of("Acknowledged","In progress","Resolved"))
            desk.mutate(technician,"transition",Map.of("id",ticket,"status",status,"resolution","Fixed gateway"));
        assertEquals(403,assertThrows(Problem.class,()->desk.mutate(employee,"review",
                Map.of("id",ticket,"root_cause","Bad config","impact","Payments failed","prevention","Review changes"))).status);
        desk.mutate(technician,"review",Map.of("id",ticket,"root_cause","Bad config",
                "impact","Payments failed","prevention","Review changes"));
        assertNotNull(incident(ticket).get("review"));
        desk.mutate(employee,"reopen",Map.of("id",ticket,"reason","Payments failed again"));
        assertEquals("In progress",incident(ticket).get("status"));
        assertNull(incident(ticket).get("resolved_at"));
        assertTrue(list(employee,"audit").stream().anyMatch(a->"Reopened".equals(a.get("action"))));
        assertEquals(409,assertThrows(Problem.class,()->desk.mutate(employee,"reopen",
                Map.of("id",ticket,"reason","Again"))).status);
    }

    @Test void knowledgeArticlesAreReadableAndStaffManaged() throws Exception {
        int technician=id(desk.login("tech@relay.test",PASSWORD));
        var article=Map.<String,Object>of("title","Restore checkout","summary","Check gateway status",
                "steps","Restart the gateway after approval.","service_id",1);
        assertEquals(403,assertThrows(Problem.class,()->desk.mutate(employee,"article-save",article)).status);
        int articleId=id(desk.mutate(technician,"article-save",article));
        assertTrue(list(employee,"articles").stream().anyMatch(a->id(a)==articleId));
        desk.mutate(admin,"article-delete",Map.of("id",articleId));
        assertTrue(list(employee,"articles").isEmpty());
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
