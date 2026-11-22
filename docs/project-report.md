# ServiceForce: Intelligent IT Service Management and Incident Resolution

CS F213 / MAC F212 | Project report | Updated 22 November 2026

ServiceForce is a Java web application for reporting, assigning, investigating and resolving IT incidents. Employees, technicians and administrators use role-specific browser workspaces backed by the same authorization and incident rules. The application is available as one deployable service; a local demonstration can run from the repository with Java 17 or later.

This report describes the implementation as it exists in the repository. The maintained feature inventory is `docs/features.md`. Ideas discussed but not implemented, such as a Service Outage Hub, are excluded from the implemented feature list.

## 1. Problem and project objectives

An organization needs a reliable way to receive service reports, determine urgency, route work to suitable technicians and show the progress of each case. A shared incident record reduces ambiguity about ownership, deadlines and previous actions. The project aims to give employees visibility into their own requests, technicians a focused work queue, and administrators a full operational view.

- Employee: report a problem, attach evidence, answer questions, track updates, verify a fix, leave feedback and reopen a case when needed.
- Technician: view assigned work, acknowledge and investigate a case, add notes, consult knowledge articles and record a resolution.
- Administrator: manage people, teams, services, routing and SLA policies; reassign work; inspect reports, audit records and escalations.

## 2. Scope and quality goals

The implementation targets one organization and a modest service-desk workload. Records survive application restarts through SQLite storage. HTTP requests enforce role and ownership checks on the server. Forms remain usable by keyboard and on narrow screens. The Java backend and browser interface are packaged together so the application can be demonstrated locally or deployed as one Railway service.

---

## 3. Architecture and object-oriented design

The browser uses HTML, CSS and JavaScript for the landing page, role-specific sign-in screens and workspaces. A Java 17 HTTP server serves these assets and a JSON API. The domain layer contains account rules, incident transitions, assignment, SLA monitoring, event recording and notification delivery. SQLite stores operational data, while Maven builds the runnable JAR and JUnit exercises domain and HTTP behavior.

- `RelayServer` is the HTTP boundary. It validates requests, reads session cookies, dispatches API actions and serves static files.
- `Desk` coordinates user accounts, profile changes, authorization, monitoring and email delivery.
- `Incidents` owns incident creation, lifecycle transitions, assignment, escalation, visibility, response-team membership and incident-specific playbook progress.
- `Playbooks` seeds category templates and copies their steps into a new incident. Template edits affect future incidents without rewriting existing investigations.
- `AssignmentStrategy` provides interchangeable skill-first and least-workload routing policies.
- `DomainModels` defines typed records for actors, services, SLA policies, technician candidates and incident state. Database rows are converted to these records before routing, priority calculation, deadline checks and core access decisions. Maps remain at the JSON and database boundaries.
- `IncidentEvent.Observer` separates audit and notification recording from the incident change. The change and observer records share a database transaction.
- `SessionStore` holds hashed session-token identifiers in memory and records a coarse browser/device label for each active session.

The database uses foreign keys and parameterized queries. Main entities include users, services, categories, policies, incidents, responders, playbook templates and incident steps, audit entries, notifications, email outbox items, knowledge articles, reviews, attachments and SLA events. An incident has one reporter, may have one lead technician, and may have additional responders. The `incident_responders` table records teammate membership without overwriting the lead assignment.

## 4. Deployment and data boundaries

The public Railway deployment packages frontend and backend together and mounts a persistent volume for SQLite. Railway provides HTTPS and the service port. Local mode binds to loopback by default and creates demo data. Demo credentials are not created on the Railway production database. The application needs a database backup because the SQLite file also holds incident attachments. Sessions are in memory, last eight hours and end on server restart; this is an operational limitation rather than durable session storage.

---

## 5. Accounts, identity and access

People choose an Employee, Technician or Administrator sign-in page. The selected role is checked against the authenticated account. Sign-in accepts the assigned username, not the contact email. Usernames follow a name-based format such as `samrivera5678@service.tech`; the system generates a unique four-digit numeric code. Users and administrators cannot choose or change that code. The real email address remains the verified recovery and notification address.

New employees verify their email with an eight-digit code and enter the workspace after verification. Verified recovery email supports forgotten-username delivery, password reset and a code for password changes. A profile change requires the current password; a password change also requires an emailed code. Passwords are salted and hashed. Admin-created accounts require their password after verification.

Each person can edit their own permitted profile fields. Administrators can manage roles, skills, availability, capacity and accounts, but cannot see another person's password. Removed accounts lose access; active technician cases must be reassigned first. Staff incident access is checked against assignment and role; employees see only incidents they reported. Private attachments and internal notes use the same server-side access checks.

People may sign in on multiple devices. The Signed-in devices section in My profile lists their sessions with a device label and last activity. Every device has a Sign out action; selecting the current device ends the current session, while selecting another device revokes only that session. Session tokens are sent in HttpOnly, SameSite cookies. The server also checks request origins, limits request sizes and uses parameterized SQL.

---

## 6. Incident lifecycle and response teams

Employees report a title, description, affected service, category, impact and urgency. Service criticality, impact and urgency determine P1-P4 priority. The system chooses an available technician within capacity using the configured skill-first or least-workload strategy. An administrator may reassign the lead. Normal lifecycle states are Classified, Assigned, Acknowledged, In progress, Resolved, Verified and Closed. Illegal transitions are rejected.

### Assigning teammates

Every incident displays its response team: the lead technician in one field, additional assigned technicians in another, and a clear empty state when no one is assigned. The incident queue shows the lead and teammate count. Additional responders can see the incident's history and attachments, receive updates, record playbook steps and work on the case.

- Manual assignment: an administrator can add or remove teammates on an open incident. The server checks availability and capacity, blocks duplicates and limits the response team to three additional technicians. Each change is recorded in the timeline.
- Automatic assignment: after the original resolution deadline, the monitor adds up to two available technicians whose primary skill matches the incident category. It considers weighted workload, capacity and competing equal- or higher-priority work. The lead remains assigned. The automatic stage runs once; if no suitable teammate exists, an administrator is alerted.

The first missed resolution deadline also starts a separate recovery deadline of 15-120 minutes based on priority. The original deadline is retained for audit and reporting.

If the recovery deadline also passes, the incident becomes Highly Critical and administrators receive an alert. The escalation stage is recorded once, even though the monitor runs repeatedly. Reopening a completed case begins a new escalation cycle while preserving prior history. Critical P1 cases can also trigger a separate one-time response escalation when they are not acknowledged by their response deadline.

## 7. Supporting incident work

Users can add public notes; IT staff can add internal notes hidden from employee views. Authorized users can upload PNG, JPEG, PDF or text evidence up to 1 MiB per file. Resolved P1 incidents can receive a post-incident review covering root cause, impact and prevention. Reporters or administrators can reopen a case with a reason. The app suggests similar incidents on the same service and can surface a prior resolution, but it does not merge cases automatically.

### Incident response playbooks and the knowledge base

Administrators maintain checklists for issue categories. On creation, an incident receives its own copy of the matching template. Assigned technicians, additional responders and administrators can record each step as completed, skipped or still to do, with a required result or reason. The step stores the actor and time; the audit history preserves changes. Reporters cannot see internal diagnostic results. New teammates see the recorded work and can continue without repeating checks. Updating a template changes future incidents only.

A knowledge article is a reusable explanation of a known issue or fix. A playbook is an interactive, incident-specific record of what was actually checked. The two features complement each other: a technician can consult an article while working through a playbook, but reading an article does not mark an investigation step complete.

---

## 8. Notifications, knowledge and reporting

Incident events write audit and in-app notification records within the same transaction as the incident change. Verified participants also receive emails through a persistent outbox when Brevo's HTTPS API and a verified sender are configured. Nearby updates for the same recipient and incident wait briefly and are grouped into one email; individual events remain visible in the app. Failed sends are retried with backoff. Email verification, recovery and delivery depend on correct provider configuration.

Technicians and administrators can write knowledge articles linked to services. Employees can search and read them. The dashboard and reports show incident activity, service demand, technician workload and SLA violations. Administrators can inspect the audit log and the People and Teams pages. Teams group technicians by primary skill and expose each member's active incident load.

## 9. Verification

The JUnit suite covers registration, email verification, username-only sign-in, recovery and password reset; role and ownership authorization; incident transitions, routing and SLA behavior; attachments, notes, knowledge and reviews; response-team escalation; playbook permissions and template copying; device revocation; and email grouping. HTTP integration tests start a loopback server with a temporary SQLite database. Run `./mvnw clean verify` from the repository root. The latest full local test run for this report passed on 22 November 2026: 54 tests, zero failures, errors or skips. GitHub Actions now runs the same build on each push and pull request and retains test reports for failed builds.

For a demonstration, sign in with separate browser profiles for the three roles. Report an incident as an employee, inspect priority and routing as an administrator, investigate and resolve it as the assigned technician, then verify and give feedback as the employee. Show a response team and its history, and explain the two deadline stages. The step-by-step script is in `docs/demonstration.md`.

## 10. Limitations and future work

SQLite is appropriate for a modest queue but would need reevaluation for a large number of concurrent writers. Session listings are cleared when the server restarts. SLA monitoring needs a running application process; it does not model business-hour calendars or holidays. Email delivery depends on an external provider. A proposed Service Outage Hub could group confirmed related reports under one shared outage, but it has not been implemented and is not counted among the delivered features.

## 11. Running the project and maintained records

Install Java 17 or later. Clone `https://github.com/itsme07-07/OOPS-project-Java`, run `./mvnw clean verify`, then start `java -jar target/relay.jar`. Open `http://127.0.0.1:8000/`. The local demo provides role-based demo buttons; exact demo usernames vary by installation and are visible in My profile. Production administrator credentials are intentionally not published in this report or repository.

The live application is at `https://oops-project-java-production.up.railway.app/`. The repository's `README.md` is the starting guide; `docs/railway.md` covers deployment; `docs/demonstration.md` contains the presentation walkthrough; `docs/features.md` is the maintained implementation inventory; and `docs/change-log.md` records dated changes. Update the feature inventory and change log when a feature is added, removed or materially changed, then regenerate this report from the editable Markdown source using `docs/generate-report.py`.

---

## Appendix A. System architecture diagram

[[ARCHITECTURE]]

The browser calls the Java HTTP server. RelayServer authenticates requests and delegates business actions to Desk and Incidents. Database transactions persist incident changes, audit history, notifications and outbox entries. A scheduled SLA monitor applies deadline rules. The mail worker sends queued messages through Brevo's HTTPS API. The browser, API and scheduler run in one Java deployment; SQLite is stored on a persistent Railway volume in production.

---

## Appendix B. UML use-case diagram

[[USE_CASE]]

The system has three actors: employee, technician and administrator. Employees report, track and verify their own incidents. Technicians investigate assigned work, follow incident playbooks and resolve incidents. Administrators manage people, teams, services, SLA policies, routing, playbook templates and reporting. Authentication and personal profile actions are available to all roles.

---

## Appendix C. UML class diagram

[[CLASS]]

The diagram shows the principal Java classes and interfaces. RelayServer uses Desk; Desk coordinates Incidents and Database. Incidents copies Playbooks into a new case and emits events to IncidentEvent.Observer implementations, AuditLog and NotificationOutbox. AssignmentStrategy is implemented by SkillFirst and LeastLoad. MailDelivery is an interface for the email provider. The typed Actor, Service, SlaPolicy, Technician and Incident records in DomainModels represent values passed through the core rules. The implementation uses interfaces where behavior varies; entities are not forced into an artificial inheritance hierarchy.

---

## Appendix D. Entity-relationship diagram

[[ER]]

The central incidents table references reporter and lead technician in users, the affected service and category, and its priority policy. The incident_responders join table gives an incident many additional technicians without duplicating the lead. Each incident can have many audit entries, notifications and copied playbook steps. A notification can have at most one queued email. The diagram shows the main relationships; the schema also contains email_codes, settings, sla_events, attachments, incident_reviews and knowledge_articles. The authoritative definitions, keys and constraints are in `src/main/resources/schema.sql`.

---

## Appendix E. Documented test cases

Each row states a test condition and the observable expected result. The evidence column names a JUnit method in `src/test/java/relay/DeskTest.java` (D) or `src/test/java/relay/HttpIntegrationTest.java` (H). Run `./mvnw clean verify` to reproduce the automated checks. The latest local run on 22 November 2026 had 54 tests with no failures, errors or skips.

| ID | Test condition | Expected result | Automated evidence |
| --- | --- | --- | --- |
| T01 | Register with an unverified email, then verify it and sign in by username | Registration requires verification; username works for sign-in and email does not | D: registrationRequiresVerifiedEmailAndOnlyUsernameCanSignIn |
| T02 | Register using an email already in use | Duplicate account is rejected | D: duplicateAccountsAreRejected |
| T03 | Enter an invalid email address at registration | Input is rejected without creating an account | D: invalidEmailIsRejected |
| T04 | Enter a password shorter than the minimum | Input is rejected without creating an account | D: shortPasswordIsRejected |
| T05 | Reset password through a verified recovery email | New password works; previous password stops working | D: recoveryRequiresVerifiedEmailAndResetInvalidatesOldPassword |
| T06 | Attempt to register with an administrator role | Public registration cannot grant elevated privileges | H: registrationRejectsPrivilegeEscalation |
| T07 | Sign in with a wrong password | Authentication fails and private workspace stays inaccessible | H: wrongPasswordIsRejected |
| T08 | Sign in, use the workspace, then sign out | Session works before logout and is invalid afterward | H: sessionPermitsWorkspaceAccessUntilLogout |
| T09 | Sign out another listed device | Target device session is revoked | H: userCanSeeAndSignOutAnotherDevice |
| T10 | Employee attempts to edit routing settings | Role check rejects the action | D: employeeCannotConfigureRouting |
| T11 | Open another reporter's incident or notes | Ownership check blocks access | D: newEmployeesCannotChooseTheirRoleOrReadOtherCases |
| T12 | Submit an invalid incident report | No partial incident is committed | D: invalidReportRollsBack |
| T13 | Change impact or urgency and calculate priority | Priority follows impact, urgency and service criticality | D: priorityUsesImpactUrgencyAndCriticality |
| T14 | Route work with uneven technician load | Least-busy eligible technician is selected | D: loadRoutingChoosesLeastBusyEligibleTechnician |
| T15 | Resolve, verify and close an incident | Workflow reaches closure only after required verification | D: closureRequiresVerification |
| T16 | Add an internal note to an incident | Reporter cannot see the internal note | D: internalNotesStayHiddenFromReporter |
| T17 | Upload evidence and try access from an unrelated account | File size/type rules apply and unrelated account cannot read evidence | D: attachmentsAreLimitedAndPrivateToIncidentParticipants |
| T18 | Complete incident playbook steps with internal results | Staff can track steps; reporter cannot see internal results | D: playbookTracksWorkWithoutExposingInternalResultsToReporter |
| T19 | Edit a playbook template after an incident is created | Existing incident retains its copied steps; new incidents get the edit | D: playbookTemplateChangesApplyOnlyToNewIncidents |
| T20 | Miss resolution then recovery deadlines | Matching teammates join once; later breach marks incident Highly Critical | D: resolutionBreachAddsMatchingTeammatesOnceAndThenMarksHighlyCritical |
| T21 | Run SLA monitoring repeatedly | No duplicate escalation events or repeated assignments | D: monitoringIsIdempotent |
| T22 | Miss a deadline with no eligible teammate | Administrator is alerted; repeated monitor runs do not assign twice | D: noMatchingTeammateAlertsAdminWithoutRepeatedAssignment |
| T23 | Consider a technician already handling more urgent work | Escalation does not pull that technician into the lower-priority incident | D: escalationDoesNotPullTechnicianFromMoreUrgentWork |
| T24 | Change response-team membership | Access follows the current membership | D: administratorCanAdjustTeamAndResponderAccessFollowsMembership |
| T25 | Generate closely spaced updates for one incident | Recipient receives one grouped email instead of separate messages | D: nearbyUpdatesForOneIncidentArriveInOneEmail |
| T26 | Call mutation endpoint from another origin | Cross-origin request is rejected | H: blocksCrossOriginMutation |
| T27 | Submit oversized request content | HTTP payload limit is enforced | H: enforcesPayloadLimit |
| T28 | Request attachment without incident permission | Download is denied | H: attachmentDownloadRequiresIncidentAccess |
