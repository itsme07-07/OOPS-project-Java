# ServiceForce

CS F213 / MAC F212 | Project team

Intelligent IT Service Management and Incident Resolution

## Project members

[[TEAM_CARDS]]
- Anurag Reddy Goli | 2025A7IS2423H | Experience and reporting | Role screens and incident views; search and reporting APIs with SQL queries; Java view models, accessibility and integration tests.
- Angad Ahuja | 2025A7IS2409H | Identity and delivery | Accounts, sessions, recovery and email; identity classes and abstraction; security tests and deployment.
- Jaya Pranav Reddy Avuduri | 2025A7IS2421H | Incident and routing | Lifecycle, priority, SLA and teams; incident classes and strategy polymorphism; domain tests and demo.
- Raj Mittal | 2025A7IS2414H | Data and knowledge | SQLite schema and migrations, audit, playbooks and knowledge; persistence classes, observer pattern, data tests and CI.

[[COVER_SUMMARY]]

---

## ServiceForce in one page

[[AT_A_GLANCE]]

ServiceForce is a Java web application for reporting, assigning, investigating and resolving IT incidents. Employees, technicians and administrators use role-specific browser workspaces backed by the same authorization and incident rules. The application is available as one deployable service; a local demonstration can run from the repository with Java 17 or later.

### Follow one incident through the system

[[INCIDENT_PATH]]

An employee reports a service failure. The server computes priority and two deadlines, selects a lead technician, copies a category playbook, then commits the case with audit and notification records. The response team investigates in one shared timeline. If the original resolution deadline passes, eligible teammates join once and a recovery deadline begins. A second miss marks the case Highly Critical. The reporter verifies the fix before closure.

### Where to look for evidence

| Question | Report sections | Source or proof |
| --- | --- | --- |
| What does the application do? | 1-2, 5-8 | Three role workspaces; incident lifecycle; feature inventory |
| How is it built? | 3-4, 12-17 | Java components; `schema.sql`; API and deployment boundaries |
| Where is the OOP work? | 3, 14, 19 | `AssignmentStrategy`, `DomainModels`, `IncidentEvent` and tests |
| Does it work? | 9, 18, 20-21, Appendix E | Worked case, role screenshots, 54 executed tests |
| Which diagrams are submitted? | Appendices A-D | Architecture, use case, class and ER diagrams |

A fresh local demo starts with three accounts and an empty incident list; Railway uses a separate persistent database. The screenshots show an incident created using those demo accounts.

---

## 1. Problem and project objectives

An organization needs a reliable way to receive service reports, determine urgency, route work to suitable technicians and show the progress of each case. A shared incident record reduces ambiguity about ownership, deadlines and previous actions. The project aims to give employees visibility into their own requests, technicians a focused work queue, and administrators a full operational view.

Existing challenges addressed by the prototype are fragmented reporting through informal channels, unclear ownership, inconsistent priority decisions, missed deadlines and loss of investigation context when work is handed to another technician. The proposed solution is one role-aware incident record with automated priority and routing, response teams, a timed SLA monitor, audit history and two notification channels.

- Employee: report a problem, attach evidence, answer questions, track updates, verify a fix, leave feedback and reopen a case when needed.
- Technician: view assigned work, acknowledge and investigate a case, add notes, consult knowledge articles and record a resolution.
- Administrator: manage people, teams, services, routing and SLA policies; reassign work; inspect reports, audit records and escalations.

Assumptions: one organization owns the service catalog and its users; each technician has one primary skill category; administrators manage policies and staffing; calendar time rather than business hours determines SLA deadlines; one Java process owns the SQLite database; Brevo and a verified sender are available when real email is required. A local demo database is separate from the production database.

## 2. Scope and quality goals

The implementation targets one organization and a modest service-desk workload. Records survive application restarts through SQLite storage. HTTP requests enforce role and ownership checks on the server. Forms remain usable by keyboard and on narrow screens. The Java backend and browser interface are packaged together so the application can be demonstrated locally or deployed as one Railway service.

### Functional requirements and implementation evidence

- FR01 User management: employee self-registration, verified recovery email, assigned username sign-in and account recovery; administrators maintain accounts and technician availability. Implemented by `Desk`, `ServiceForceServer` and the People workspace.
- FR02 Incident lifecycle: create, view, edit, assign, investigate, resolve, verify, close and reopen with role-checked transitions. Implemented by `Incidents`, `Desk` and the incident workspace.
- FR03 Classification and priority: choose a service/category and calculate P1-P4 from impact, urgency and service criticality. Implemented by `Incidents` and `DomainModels.Service`.
- FR04 Assignment: provide at least two interchangeable strategies and allow administrator reassignment. Implemented by `AssignmentStrategy.SkillFirst`, `AssignmentStrategy.LeastLoad` and incident actions.
- FR05 SLA: calculate response/resolution deadlines, show at-risk/breached states, record violations and escalate overdue work. Implemented by the scheduled monitor, `Incidents` and `sla_events`.
- FR06 Notifications and audit: record in-app notices and complete incident history; queue email for verified recipients. Implemented by `IncidentEvent.Observer`, `audit`, `notifications` and `email_outbox`.
- FR07 Search and reporting: filter by ID/text, status, priority, category, service, technician, SLA condition and date; show service volume, workload and overview metrics. Implemented in the browser workspace using the role-filtered `/api/state` response.
- FR08 Intelligent assistance: suggest related cases and prior resolutions, route by skill/load, and add matching teammates after a missed deadline. Implemented by `Incidents` with deterministic rules.
- FR09 Persistence: retain users, services, policies, incidents, attachments, audit history and resolution details across application restarts. Implemented with SQLite, JDBC transactions and a Railway volume in production.

### Non-functional requirements and evidence

- NFR01 Security: server-side authentication, authorization, password hashing, input validation, request-origin checks and protected incident evidence. Section 15 details the controls and their limits.
- NFR02 Reliability: incident mutations, audit and notification writes share a transaction; email outbox entries survive restart and retry delivery. SQLite needs an operational backup.
- NFR03 Usability: separate role entry points, responsive browser layouts, keyboard-operable controls, light/dark themes and reduced-motion CSS.
- NFR04 Maintainability: typed domain records, documented interfaces and two explicit design patterns, Maven build, diagrams, source-controlled schema and a dated change log.
- NFR05 Verifiability: 28 documented cases, 54 passing automated tests and a GitHub Actions verification workflow.
- NFR06 Deployment: one reproducible Docker image with a health endpoint, private environment configuration and persistent SQLite storage. Scale-out is outside the current single-instance design.

## 3. Architecture and object-oriented design

The browser uses HTML, CSS and JavaScript for the landing page, role-specific sign-in screens and workspaces. A Java 17 HTTP server serves these assets and a JSON API. The domain layer contains account rules, incident transitions, assignment, SLA monitoring, event recording and notification delivery. SQLite stores operational data, while Maven builds the runnable JAR and JUnit exercises domain and HTTP behavior.

- `ServiceForceServer` is the HTTP boundary. It validates requests, reads session cookies, dispatches API actions and serves static files.
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

People choose an Employee, Technician or Administrator sign-in page. The selected role is checked against the authenticated account. Sign-in accepts the assigned username, not the contact email. Usernames follow the format `name_4-code@service.role`: the name is normalized, the system generates a unique four-digit numeric code, and the role suffix identifies the account type (`emp`, `tech` or `adm`). Users and administrators cannot choose or change the code. The real email address remains the verified recovery and notification address.

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

SQLite is appropriate for a modest queue but would need reevaluation for a large number of concurrent writers. Session listings are cleared when the server restarts. SLA monitoring needs a running application process; it does not model business-hour calendars or holidays. Email delivery depends on an external provider.

## 11. Running the project and maintained records

Install Java 17 or later. Clone `https://github.com/itsme07-07/OOPS-project-Java`, run `./mvnw clean verify`, then start `java -jar target/serviceforce.jar`. Open `http://127.0.0.1:8000/`. A fresh local demo has the three fixed role usernames listed in section 16; later user accounts receive generated usernames. Production administrator credentials are intentionally not published in this report or repository.

The live application is at `https://oops-project-java-production.up.railway.app/`. Supporting documents in the repository are `README.md` for setup, `docs/railway.md` for deployment, `docs/demonstration.md` for the presentation walkthrough, and `docs/features.md` for the feature inventory.

---

## 12. Technology selection and complete implementation stack

### Languages and browser layer

- Java 17 is the application language. It implements the HTTP server, authentication, incident domain rules, scheduled SLA monitoring, email integration, persistence access and command-line entry point. Records represent domain values, interfaces express replaceable behavior, and exceptions distinguish rejected operations from unexpected failures. Java 17 is also the compilation target in `pom.xml` and the runtime family in the Docker image.
- HTML5 provides semantic page structure. CSS3 implements the responsive layout, dark/light themes, animations and reduced-motion behavior. Plain JavaScript implements navigation, forms, API requests, client-side incident filtering and charts. The frontend uses no React, Angular, Vue, Node runtime or JavaScript package manager. `index.html`, `style.css`, `app.js` and `mark.svg` are packaged as classpath resources in the runnable JAR.
- SVG supplies the website mark and the report's vector architecture, use-case, class and ER diagrams. The report source is Markdown; `docs/generate-report.py` uses Python and ReportLab only to generate the submitted PDF. Python is not part of the running website or backend.

### Java libraries, build and test tools

- The JDK's `com.sun.net.httpserver.HttpServer` serves static assets and the JSON API. The JDK also supplies `java.net.http.HttpClient` for Brevo calls, JDBC interfaces for SQL, `ScheduledExecutorService` for monitoring, and cryptographic APIs for password hashes and session tokens. This keeps the runtime smaller than a full web framework for the course-scale application.
- SQLite is the relational database. `org.xerial:sqlite-jdbc:3.49.1.0` provides the Java driver; SQL schema and indexes are in `src/main/resources/schema.sql`. The application opens a connection per transaction, enables foreign keys and applies a 15-second busy timeout. Write transactions begin with `BEGIN IMMEDIATE`, so incident, audit and notification changes commit or roll back together.
- `com.google.code.gson:gson:2.13.1` serializes JSON responses and the Brevo request body. Incoming JSON is parsed in strict mode and restricted to a flat object of text, integer, boolean or null values.
- Apache Maven manages dependencies and the build. The project targets Java 17 using `maven-compiler-plugin:3.14.0`; `maven-surefire-plugin:3.5.3` runs JUnit tests; `maven-shade-plugin:3.6.0` packages dependencies and browser assets into `target/serviceforce.jar`. The Maven wrapper lets a reviewer use `./mvnw` without installing Maven separately. `junit-jupiter:5.12.2` is a test-only dependency.
- Git and GitHub store the source and submitted report. `.github/workflows/ci.yml` uses GitHub Actions with `actions/checkout@v4`, `actions/setup-java@v4` and `actions/upload-artifact@v4`. It runs a clean Maven verification on pushes and pull requests, and retains Surefire reports when a job fails.

### External services and deployment

- Railway hosts the single Java service and provides an HTTPS public domain, a service port, health checking and a persistent volume. The application stores SQLite at the mounted volume path; other container filesystem locations are temporary. The repository's multi-stage Dockerfile builds the JAR with `maven:3.9.9-eclipse-temurin-17` and runs it with `eclipse-temurin:17-jre`. `JAVA_TOOL_OPTIONS=-Xmx256m` limits the JVM heap in that image.
- Brevo is the transactional email provider. `MailDelivery.Brevo` calls `https://api.brevo.com/v3/smtp/email` over HTTPS for verification, recovery and incident updates. In-app notifications work independently. This release uses no SMTP server, SMS service, OAuth provider, AI model or cloud database.

### Why this architecture and what alternatives mean

The project needs role-based workflows, a durable incident history and demonstrable object-oriented patterns. A Java monolith keeps authentication, incident changes, audit records and notification-outbox writes in one transaction and produces one deployable JAR. SQLite gives relational keys and transactions without requiring a separate database account for the course demonstration. Plain browser technologies keep the frontend directly inspectable and remove a second build system. Brevo over HTTPS supports email delivery in the chosen hosting environment.

Spring Boot could provide a larger web framework, but would add framework configuration without changing the project's core domain rules. A React frontend would add a component toolchain, while the current screens fit in one browser bundle. PostgreSQL would be a stronger option for many concurrent writers or multiple application replicas; the current single-instance SQLite deployment is simpler but has that scaling limit. A microservice split would require distributed identity, data ownership and message delivery, which is unnecessary for the current workload.

## 13. Configuration keys, command options and secret handling

Application settings are supplied through environment variables. Credentials are stored in Railway's private service variables or a local environment.

- `BREVO_API_KEY`: Brevo transactional email API credential, sent as the `api-key` header to Brevo. Required for real email delivery.
- `SERVICEFORCE_EMAIL_FROM`: sender email address verified in Brevo. Both this and `BREVO_API_KEY` must be configured for email verification, recovery and updates to work.
- `SERVICEFORCE_ADMIN_NAME`, `SERVICEFORCE_ADMIN_EMAIL`, `SERVICEFORCE_ADMIN_PASSWORD`: first-administrator bootstrap values on Railway when the persistent database has no active administrator. They are not used to publish a password in Git. Once an administrator exists, the bootstrap values are no longer needed for normal startup.
- `SERVICEFORCE_DB_PATH`: optional explicit SQLite database path. It overrides the default path selection and is useful for a separate local database or another host.
- `RAILWAY_PROJECT_ID`: identifies the Railway environment. When present, the application requires a volume, binds to all interfaces, disables demo defaults and uses secure cookies.
- `RAILWAY_VOLUME_MOUNT_PATH`: Railway's persistent volume location. With Railway detection and no explicit DB override, the database file is `<mount>/itsm.sqlite3`.
- `PORT`: port supplied by Railway; the local default is 8000. The Docker image exposes 8080, while the process listens on the actual `PORT` supplied by the host.
- `JAVA_TOOL_OPTIONS`: Docker image JVM option set to `-Xmx256m`; it is a memory setting, not an application secret.

The command-line entry point also accepts `--host`, `--port`, `--db`, `--demo`, `--no-demo`, `--secure-cookie`, `--create-admin` and `--help`. A normal local run binds `127.0.0.1:8000`, uses `data/itsm.sqlite3` and seeds demo accounts on a new database. Railway defaults to `0.0.0.0`, no demo accounts, a secure cookie and the mounted SQLite file. `--create-admin` takes name, email and password through an interactive terminal. The `/healthz` endpoint returns a small status response for the host health check.

An email address is personal contact data, while an assigned `nameCODE@service.role` value is the login username. The four-digit code is generated by the system and cannot be changed by a user or administrator. The code is an identifier, not an authentication secret; sign-in also requires the password. Password hashes and one-time-code hashes are stored in SQLite. Session tokens are held by the browser as cookies; only their digests are retained in the server's in-memory session store.

## 14. Domain rules, algorithms and object-oriented design

The design uses interfaces for substitutable behavior. User and incident rules live in dedicated classes, while typed records carry the values those rules need. The following map links each course concept to its implementation.

| OOP concept | ServiceForce implementation | Why it matters |
| --- | --- | --- |
| Encapsulation | `Incidents` owns lifecycle and response-team mutations; `Desk` does not expose direct table edits to the browser | Illegal state changes are rejected at the domain boundary |
| Abstraction | `AssignmentStrategy` and `IncidentEvent.Observer` define small contracts | Routing and side effects can vary without rewriting incident creation |
| Interface polymorphism | `SkillFirst` and `LeastLoad` rank the same `Technician` records differently | An administrator can switch a strategy at runtime |
| Composition | `Desk` creates and owns `Incidents`; `ServiceForceServer` receives a `Desk` | HTTP concerns remain outside incident rules |
| Value objects | `DomainModels.Actor`, `Service`, `SlaPolicy`, `Technician` and `Incident` are typed records | Core calculations use named fields rather than untyped row positions |
| Event observer | `AuditLog` and `NotificationOutbox` process an `IncidentEvent` in one transaction | A failed write rolls back the incident change and its records together |
| Exceptions | `Problem` carries a status and safe message for invalid requests | Authorization and validation failures are distinct from server faults |

### Priority, routing and lifecycle

Incident priority uses `impact × urgency × service criticality`, with each factor in the range 1-3. Scores of 18 or more are P1; 9-17 are P2; 4-8 are P3; and lower scores are P4. The selected priority maps to a policy row containing response and resolution minutes. Seeded policies are P1: 5/30 minutes; P2: 15/120; P3: 60/480; and P4: 240/1440. Administrators can edit policy targets. An incident stores its calculated deadlines at creation; changing a policy affects future cases rather than retroactively rewriting existing deadlines.

The `AssignmentStrategy` interface has two implementations. Skill-first places available, below-capacity technicians with a matching primary skill before other eligible technicians, then orders by active workload and ID. Least-load orders all eligible technicians by active workload and ID. The administrator changes the strategy through service settings; the incident creation code calls the interface without switching its structure. A technician's active count includes lead and additional response-team assignments. If no candidate is eligible, the case remains Classified and alerts administrators instead of silently assigning an unavailable person.

The lifecycle follows Classified → Assigned → Acknowledged → In progress → Resolved → Verified → Closed. The server checks the next allowed state and the actor's role or relationship to the incident. A technician must supply resolution details when resolving; a reporter can verify and close; an administrator can intervene. A reporter or administrator can reopen completed work with a reason, beginning a new resolution deadline and escalation cycle while retaining the prior audit history. Public notes, internal staff notes, attachments, feedback, post-incident reviews and response-team changes have separate authorization checks.

### SLA risk, escalation, similarity and notifications

The scheduler runs every 15 seconds while the Java process is running. Response and resolution targets are marked At risk during the last fifth of their target interval, then Breached after their deadline. Unique `(incident_id,target,kind)` SLA-event keys make repeated monitoring idempotent. An unacknowledged P1 response breach creates a one-time escalation event and administrator notification.

When the original resolution deadline passes, a first escalation stage starts. The recovery interval is `resolution target ÷ 2`, bounded to 15-120 minutes. Up to two matching-skill technicians may join the response team, with a maximum of three additional responders overall. Candidates must be active, available, below capacity, not already on the case, and not occupied with an equal- or higher-priority incident. Selection orders candidates by weighted active workload, where P1/P2/P3/P4 contribute 8/4/2/1, then by incident count and user ID. If no one qualifies, the administrator receives an alert. A second missed deadline marks the case Highly Critical once. The original resolution deadline remains available for reporting and audit.

For possible related cases, the system compares title words on the same service after removing common words. It suggests a related open incident or a prior nonempty resolution when the word-set overlap reaches the implemented 0.3 threshold. The matching uses deterministic rules and keeps incident histories separate. Playbook templates are copied into incident-specific steps so later template edits do not rewrite work already done.

`IncidentEvent.Observer` implements the Observer pattern: `AuditLog` and `NotificationOutbox` record the same event within the incident transaction. The outbox stores notification email work durably. After a 45-second delay, pending messages for one recipient and incident are grouped into one email, while each original event remains in the in-app history. Failed sends retry with increasing delay and become failed after eight attempts. Direct verification and recovery messages use `MailDelivery`, whose Brevo implementation is an adapter around the HTTPS API. These patterns separate routing choice, event recording and external delivery from the core incident transitions.

## 15. Security, validation and error handling

Passwords use PBKDF2 with HMAC-SHA-256, a random 16-byte salt, 300,000 iterations and a 256-bit derived value. A new password must be 8-256 characters. Wrong-user login uses a dummy hash computation to reduce obvious timing differences. One-time email codes have eight digits, expire after 10 minutes, permit five attempts, and cannot be reissued for one minute. A password reset revokes that user's sessions.

Session tokens contain 32 random bytes and are replaced on sign-in. The browser receives an HttpOnly, SameSite=Strict cookie with an eight-hour maximum age; the `Secure` attribute is set for Railway HTTPS or with `--secure-cookie`. The server stores SHA-256 digests of the tokens, not raw tokens, and lets a user revoke individual signed-in devices. Sessions are process-local and disappear on restart. The HTTP layer limits sign-in and recovery requests per client address to 30 in one minute, with a bounded in-memory record of addresses. It is a basic abuse control, not a distributed rate limiter.

Every private API action resolves the session and performs role, ownership or assignment checks on the server. New public registrations are always employees. Administrators cannot obtain another person's password or alter the generated username code. Employees cannot read other reporters' cases; technicians see their own lead and teammate assignments. Internal notes and playbook diagnostic results are omitted from employee views. Attachment downloads repeat the incident-access check. Deleted accounts become inactive; history remains.

The server accepts only declared GET/HEAD/POST routes, checks the Origin of mutations against the Host, requires `application/json`, parses strict flat JSON and validates Unicode. Normal JSON bodies are limited to 65,536 bytes; the encoded attachment endpoint permits up to 1,500,000 bytes, with decoded files limited to 1 MiB and four allowed media types. Static paths are allow-listed. Responses include no-store caching, MIME sniffing and framing protections, a same-origin referrer policy and a restrictive Content Security Policy. Railway terminates public TLS; the application does not implement TLS itself.

`Problem` carries expected HTTP error status and a client-safe message for invalid input, unauthenticated use, forbidden actions, missing data, conflicting state, rate limits or temporary service failures. Unknown exceptions are logged server-side and return a generic 500 response. SQL statements use parameters; foreign keys and unique constraints reject inconsistent references or duplicates. A write transaction rolls back on SQL or runtime failures, so an incident mutation cannot commit without its audit and notification records. Network or Brevo failures keep incident emails pending for retry; direct verification and recovery email requests surface a delivery error rather than claiming success.

## 16. Relational data model and persistence details

The authoritative schema is `src/main/resources/schema.sql`, with startup migration checks in `Desk` for fields added after earlier releases. SQLite uses integer IDs, case-insensitive unique email and service-name constraints, foreign keys, a composite responder key, and indexes for assignment, responder lookup, attachment lookup and playbook ordering. The current deployed schema additionally has `recovery_due` and `escalation_stage` on incidents; startup migration adds these if an older database lacks them.

- `users`: account ID, display name, unique recovery email and username, password hash, role, technician skill, availability, capacity, active flag and email-verification status.
- `email_codes`: per-user and per-purpose code hash, issue and expiry times, and attempt count; the composite key allows separate email verification, password-change and password-reset flows.
- `categories`, `services`, `policies`, `settings`: configurable issue categories, affected services and criticality, P1-P4 SLA targets, and the active routing strategy.
- `incidents`: reporter and lead-technician references, category/service/priority references, title and description, impact and urgency, state, original deadlines, timestamps, resolution and feedback, related-case suggestion references, and recovery-escalation fields.
- `incident_responders`: many-to-many membership between incidents and additional technicians, with joined time. The lead is stored separately in `incidents.technician_id`.
- `audit` and `sla_events`: append-style history of actions and unique SLA milestones. Audit rows include actor, action, description, old/new state and time.
- `notifications` and `email_outbox`: per-user in-app events and one optional queued email per notification, including delivery status, attempts and next retry time.
- `attachments`: incident/uploader references, validated name and MIME type, BLOB content, size and creation time. A backup of the database must also protect these files.
- `knowledge_articles`: service-linked reusable reference articles with author and timestamps. `playbook_templates` defines category checklists; `incident_playbook_steps` stores the copied per-case steps, actor, result, status and update time.
- `incident_reviews`: one optional post-incident review per case with root cause, impact, prevention and author.

A new local demo database seeds nine categories, six services, four priority policies and exactly three role accounts. It starts with no incidents, so presenters can create cases during the demonstration. The local demo usernames are `admin1111@service.adm`, `tech2222@service.tech` and `emp3333@service.emp`; their shared demo password is documented in the README. Accounts subsequently created through the application receive system-generated four-digit codes. Railway production starts without demo accounts and bootstraps the first administrator from private variables when necessary. SQLite resides in the persistent Railway volume, so a redeploy can keep the incident history. Multiple running replicas are outside the current design because each would need coordinated persistent data and sessions.

The three local demo accounts are Admin 1, Tech 1 (an Application technician) and Employee 1. The six seeded services are Payment gateway, Corporate network, Identity platform, Analytics database, Cloud storage and Workplace devices. These accounts and catalog entries are demonstration records, not production users or real customer data.

## 17. API boundaries, workflows, validation evidence and limits

The frontend is served by the Java process. Public routes include the static assets, `/healthz`, `/api/config`, registration, role-aware sign-in, verification, username recovery and password reset. Authenticated routes include `/api/state` for role-filtered workspace data, `/api/devices`, device revocation, `/api/action` for controlled domain commands, and authorized attachment upload/download. The frontend uses `fetch` with JSON and displays server error messages; it does not bypass server authorization by hiding buttons.

Workflow 1: an employee reports an incident. The server validates service, category, impact and urgency; calculates priority and deadlines; selects an eligible lead or leaves the case awaiting assignment; commits the case, playbook copy, audit and notifications; and returns its ID. Workflow 2: a technician acknowledges, investigates with notes and playbook steps, resolves with details, and the reporter verifies and closes. SLA monitoring and notifications continue between those actions. Workflow 3: an unresolved case breaches its original deadline, gains suitable teammates once or alerts an administrator, receives a recovery deadline, and becomes Highly Critical if that deadline is also missed. The tests exercise normal and adverse paths for each workflow.

The 28 documented cases in Appendix E cover normal operations, boundary inputs, invalid data, role and ownership failures, conflict states, concurrency, escalation idempotency, private attachments and transport defenses. `DeskTest` and `HttpIntegrationTest` contain 54 executed JUnit tests in the latest local build, with no failures, errors or skips. GitHub Actions applies the same `clean verify` gate after each push.

The deployment supports one organization and one Java instance. SQLite serializes writes; session records are not durable; the SLA clock uses elapsed time rather than business calendars; email requires Brevo configuration and a verified sender; and the similar-case suggestion uses lexical matching. Backups of the Railway volume are operationally necessary but are not automated. Larger deployments would require durable shared sessions, a multi-writer database, scheduled backup and restore checks, or more advanced search.

---

## 18. Worked incident: priority, assignment and deadlines

Consider an employee who reports that checkout requests time out on the Payment gateway. The service has criticality 3; the employee selects impact 3 and urgency 3. `DomainModels.Service.priorityScore` multiplies these values, producing `3 × 3 × 3 = 27`. The P1 threshold is 18, so the new incident is P1. The configured P1 policy gives a five-minute response target and a 30-minute resolution target. If the incident is created at 10:00, its stored response and original resolution deadlines are 10:05 and 10:30. The score determines the SLA policy; the deadline timestamps are saved with the incident rather than recalculated from later policy edits.

| Stage | Time in example | Rule and visible evidence |
| --- | --- | --- |
| Submission | 10:00 | P1 priority, incident ID, two deadlines, creation audit and notifications. |
| Automatic assignment | At creation | Skill-first ranks an available Application technician below capacity ahead of a non-matching technician; the assigned lead is recorded. |
| Response at risk | After 10:04 | The last fifth of the five-minute response interval is flagged at risk. |
| Missed response | After 10:05 | If the P1 case is still unacknowledged, one escalation event and administrator alert are recorded. |
| Resolution at risk | After 10:24 | The last fifth of the 30-minute resolution interval is flagged at risk. |
| Missed original resolution | First monitor pass after 10:30 | Eligible Application teammates may be added once; the original deadline stays visible and a recovery deadline is set 15 minutes after this monitor pass. |
| Missed recovery | First monitor pass after the recovery deadline | The incident becomes Highly Critical and administrators are alerted once. |

The monitor runs every 15 seconds while the Java process is active, so displayed events can occur just after, rather than exactly at, a deadline. A candidate teammate must be active, available, below capacity, not already on the incident, and free of equal- or higher-priority competing work. The system adds at most two in this automatic stage and never lists the lead again as an additional responder. If no candidate qualifies, an administrator alert replaces an automatic team addition. Unique SLA event keys and the stored escalation stage prevent repeated monitor passes from duplicating either result.

The current local example shown in the following figures is `INC-0002`, a Payment gateway case reported by Employee 1 and assigned to Tech 1. The screenshots were captured shortly after creation, while the P1 case was Assigned and on track. No additional technician had joined. The worked 10:00 timeline above is a separate illustration of what the SLA rules would do if deadlines were missed.

---

## 19. Source evidence for object-oriented design

### Strategy: replaceable technician ranking

`AssignmentStrategy` defines one selection contract. `Incidents.create` constructs eligible candidates and calls the active strategy through this interface. The two implementations rank the same candidate type differently; the incident creation path does not need a separate version for each policy.

```java
interface AssignmentStrategy {
    Comparator<Technician> ranking(String category);

    default Technician choose(List<Technician> candidates, String category) {
        return candidates.stream().min(ranking(category)).orElse(null);
    }
}
```

In `AssignmentStrategy.java`, `LeastLoad` uses workload and ID as tie-breakers. `SkillFirst` first places a technician whose primary skill matches the incident category ahead of other eligible candidates, then applies the same workload order. This is runtime polymorphism: the configured strategy object supplies the ranking without changing the caller. Availability and capacity filtering happen before either ranking. The `loadRoutingChoosesLeastBusyEligibleTechnician` test checks a concrete least-load choice; other routing and capacity cases are listed in Appendix E.

### Observer: one incident event, separate records

The incident service creates one event and passes it to observers using the same database connection. `IncidentEvent.AuditLog` writes the history row; `IncidentEvent.NotificationOutbox` creates recipient notices and, for verified recipients, queued email work. The small excerpt below is from `Incidents.event`.

```java
var change = new IncidentEvent(i, actor.toString(), action,
        message, before, after, at);
for (var observer : observers) observer.record(c, change);
```

`Desk.mutate` opens a write transaction before invoking the incident action. Because both observers use the same connection, a SQL or runtime failure rolls back the incident change and its associated audit and notification writes together. Adding another observer would implement `IncidentEvent.Observer` without changing the event dispatch loop. The real email send happens later from the outbox, so a provider outage does not roll back a completed incident action. The `verifiedIncidentParticipantsReceiveQueuedEmailUpdates` and `nearbyUpdatesForOneIncidentArriveInOneEmail` tests check queued and grouped delivery behavior.

---

## 20. One incident across three role workspaces

The following screenshots show the same current demo case, `INC-0002`, in three role workspaces. The incident key, Payment gateway service, P1 priority, reporter and lead technician agree across the views. The controls change with the signed-in role; server-side visibility and mutation checks remain authoritative.

### Employee: report and follow progress

[[SCREEN_EMPLOYEE]]

Figure 1. Employee 1 sees the submitted incident, its response team and original deadlines. The employee can edit the report or provide more information, but has no technician acknowledgment or administrator reassignment control.

---

### Technician: investigate the assigned case

[[SCREEN_TECHNICIAN]]

Figure 2. Tech 1 sees `INC-0002` with Acknowledge, Request information and Add note actions. The incident response playbook begins below the summary. This role can record diagnostic work while the reporter remains able to track the case.

---

### Administrator: oversee staffing and escalation

[[SCREEN_ADMIN]]

Figure 3. Admin 1 sees `INC-0002` with Reassign and Add teammate controls, along with People, Teams, Service settings and Audit log navigation. The case is still on track at the time of capture.

---

## 21. Executed tests and traceable behavior

On 22 November 2026, `./mvnw clean verify` completed successfully using JDK 25 to compile the Java 17 target. Maven Surefire recorded 42 `DeskTest` methods and 12 `HttpIntegrationTest` methods: 54 tests run, zero failures, zero errors and zero skipped. The source-controlled GitHub Actions workflow runs the same Maven verification on pushes and pull requests. The local test reports are in `target/surefire-reports`; they are generated build output rather than submitted source files.

| Behavior checked | Automated evidence | Result |
| --- | --- | --- |
| Verified email and username-only sign-in | `DeskTest.registrationRequiresVerifiedEmailAndOnlyUsernameCanSignIn` | Passed |
| Least-workload routing | `DeskTest.loadRoutingChoosesLeastBusyEligibleTechnician` | Passed |
| One-time P1 response escalation | `DeskTest.monitoringIsIdempotent` | Passed |
| Team addition and second missed deadline | `DeskTest.resolutionBreachAddsMatchingTeammatesOnceAndThenMarksHighlyCritical` | Passed |
| Private attachment download | `HttpIntegrationTest.attachmentDownloadRequiresIncidentAccess` | Passed |
| Sign out of another device | `HttpIntegrationTest.userCanSeeAndSignOutAnotherDevice` | Passed |
| Group nearby incident emails | `DeskTest.nearbyUpdatesForOneIncidentArriveInOneEmail` | Passed |

These selected assertions tie a user-visible feature to the Java rule that implements it. Appendix E lists 28 documented test cases spanning normal workflows, invalid input, unauthorized access, lifecycle conflicts, SLA boundaries, persistence and HTTP behavior. The successful build verifies automated paths; the three current role screenshots separately document the browser presentation of one demo incident.

---

## Appendix A. System architecture diagram

[[ARCHITECTURE]]

The browser calls the Java HTTP server. ServiceForceServer authenticates requests and delegates business actions to Desk and Incidents. Database transactions persist incident changes, audit history, notifications and outbox entries. A scheduled SLA monitor applies deadline rules. The mail worker sends queued messages through Brevo's HTTPS API. The browser, API and scheduler run in one Java deployment; SQLite is stored on a persistent Railway volume in production.

---

## Appendix B. UML use-case diagram

[[USE_CASE]]

The system has three actors: employee, technician and administrator. Employees report, track and verify their own incidents. Technicians investigate assigned work, follow incident playbooks and resolve incidents. Administrators manage people, teams, services, SLA policies, routing, playbook templates and reporting. Authentication and personal profile actions are available to all roles.

---

## Appendix C. UML class diagram

[[CLASS]]

The open diamond at ServiceForceServer shows aggregation: its Desk is supplied through the constructor. The filled diamond at Desk shows composition: Desk creates and owns Incidents. Solid arrows show other associations or use. Dashed hollow triangles show that SkillFirst and LeastLoad implement AssignmentStrategy. IncidentEvent.Observer has AuditLog and NotificationOutbox implementations; MailDelivery is the email-provider interface. DomainModels contains typed Actor, Service, SlaPolicy, Technician and Incident records. No artificial class inheritance is used; interchangeable behavior is expressed through interfaces.

---

## Appendix D. Entity-relationship diagram

[[ER]]

The central incidents table references reporter and lead technician in users, the affected service and category, and its priority policy. The incident_responders join table gives an incident many additional technicians without duplicating the lead. Each incident can have many audit entries, notifications and copied playbook steps. A notification can have at most one queued email. The diagram shows the main relationships; the schema also contains email_codes, settings, sla_events, attachments, incident_reviews and knowledge_articles. The authoritative definitions, keys and constraints are in `src/main/resources/schema.sql`.

---

## Appendix E. Documented test cases

Each row states a test condition and the observable expected result. The evidence column names a JUnit method in `DeskTest.java` (D) or `HttpIntegrationTest.java` (H). Run `./mvnw clean verify` to reproduce the automated checks. The latest local run on 22 November 2026 had 54 tests with no failures, errors or skips.

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

---

## Appendix F. HTTP interface and trust boundaries

The browser and Java server share one origin. Public account endpoints validate input and apply sign-in rate limits; private endpoints resolve the session cookie before reading or changing data. `/api/action` accepts an `action` name and flat JSON fields, then delegates to role-checked commands in `Desk`. It is not a generic table-write API.

| Route | Access | Behavior |
| --- | --- | --- |
| `GET /healthz`, `GET /api/config` | Public | Health response and local demo configuration |
| `POST /api/register`, `POST /api/login` | Public | Begin verified registration; sign in by username and selected role |
| `POST /api/verify-email`, `POST /api/recover-username` | Public, rate limited | Verify account email; send a username to a verified recovery address |
| `POST /api/request-reset`, `POST /api/reset-password` | Public, rate limited | Issue a reset code; replace the password and revoke sessions |
| `GET /api/state`, `POST /api/action` | Signed in | Read a role-filtered snapshot; perform an authorized workspace command |
| `GET /api/devices`, `POST /api/revoke-device` | Signed in | List this account's sessions; revoke one selected device |
| `POST /api/attachment`, `GET /api/attachment/{id}` | Incident participant | Upload validated evidence; download only with incident access |

Responses are JSON except static assets and attachment downloads. HTTP tests cover authentication, cross-origin mutation rejection, payload size, private attachments and malformed requests. The server also checks the role and incident relationship inside the application; a hidden button in the browser is never the only protection.

## Appendix G. Repository map and reproducibility

```text
pom.xml                         Java 17, dependencies, tests, runnable JAR
src/main/java/serviceforce/            HTTP, identity, incidents, routing, events, email
src/main/resources/schema.sql   Relational schema, constraints and indexes
src/main/resources/static/      Browser interface and website assets
src/test/java/serviceforce/            Domain and HTTP integration tests
docs/project-report.md          Editable source for this report
docs/project-report.pdf         Submitted PDF generated from the source
docs/diagrams/                  Architecture, use-case, class and ER figures
docs/features.md                Maintained feature inventory
.github/workflows/ci.yml        Automated clean verification
Dockerfile                      One service with persistent database mount
```

From a fresh clone, run `./mvnw clean verify` and then `java -jar target/serviceforce.jar`. The local server uses port 8000 and creates a SQLite file under `data/`. The first launch has three fixed demo users and no incidents; the demo password is in the README. The source tree and report are sufficient to reproduce the same checks without access to the Railway account. A live Railway deployment uses its own volume and private variables, so deleting a local demo database does not clear production data.
