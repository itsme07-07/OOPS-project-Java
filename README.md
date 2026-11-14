# ServiceForce service desk

ServiceForce is the Java implementation of the Intelligent IT Service Management & Incident Resolution System for CS F213 / MAC F212. Employees report incidents, technicians investigate them, and administrators manage the service catalog, routing, policies and SLA violations.

## Demo sign-in

Open [ServiceForce on this computer](http://127.0.0.1:8000/) after starting the Java server. On the sign-in screen, enter one of these **email addresses** in the **Username or email** field:

| Role | Username or email | Password |
|---|---|---|
| Administrator | `admin@relay.test` | `RelayDemo2026!` |
| Technician | `tech@relay.test` | `RelayDemo2026!` |
| Employee | `employee@relay.test` | `RelayDemo2026!` |

These are local demo accounts only. To find the generated username for an account, sign in with its email, then select the **profile button beside Sign out** in the sidebar. **My profile** shows **Your generated username**, for example `exampleA1B2C3D4@service.tech`. Each installation generates different codes, so exact usernames cannot be listed in Git. Registered users choose their own passwords; those passwords are stored as hashes in the local database and cannot be read from Git. The generated usernames are identifiers, not working email addresses.

## Run locally

Install a **JDK version 17 or later**. In Terminal, download the repository, build it and start the server:

```sh
git clone https://github.com/itsme07-07/OOPS-project-Java.git
cd OOPS-project-Java
./mvnw clean verify
java -jar target/relay.jar
```

On Windows use `mvnw.cmd clean verify`. The first build downloads Maven and the pinned dependencies. The resulting JAR includes the server, database driver and browser interface. Open [http://127.0.0.1:8000/](http://127.0.0.1:8000/) in the browser. Stop the server with Ctrl+C.

The first local launch creates `data/itsm.sqlite3` and demo accounts. If you already ran the earlier release, start the JAR from the same project directory to reuse the existing database. Sign in again after restarting the server.

If you already cloned the repository, stop the running server with Ctrl+C, then run `git pull`, `./mvnw clean verify`, and `java -jar target/relay.jar` from the repository directory. A browser refresh alone cannot load a new Java build into an old server process.

For a separate installation with your own administrator, use a fresh database:

```sh
java -jar target/relay.jar --db data/team.sqlite3 --create-admin
java -jar target/relay.jar --db data/team.sqlite3 --no-demo
```

## Repository layout and course deliverables

The assignment specifies submission artifacts and leaves the directory layout to the team. This repository follows Maven's standard Java layout.

```text
pom.xml                     Java version, dependencies, build and packaging
mvnw, mvnw.cmd, .mvn/       Maven wrapper for macOS, Linux and Windows
src/
  main/
    java/relay/             HTTP, sessions, users, incidents, routing and SQLite access
    resources/
      schema.sql            Relational schema, keys and indexes
      static/               HTML, CSS and JavaScript for all three workspaces
  test/java/relay/          JUnit tests for domain rules and HTTP behavior
docs/
  project-report.pdf        Analysis, requirements, design, diagrams and testing
  demonstration.md          Three end-to-end workflows for the team presentation
  features.md               Implemented feature inventory and account rules
```

- The [project report](docs/project-report.pdf) covers stakeholders, business rules, quality requirements, technology choices, system architecture, use cases, class relationships, database design, sample data, and test cases.
- The [demonstration guide](docs/demonstration.md) walks the team through reporting and routing an outage, investigating and verifying a fix, and correlating incidents while monitoring deadlines. The group records and submits its own presentation.
- The [feature inventory](docs/features.md) lists the implemented workflows, access rules, reporting, notifications and escalation behavior.
- Run `./mvnw test` for the automated checks. Tests create isolated databases and HTTP tests start their own loopback server. Maven writes detailed results under `target/surefire-reports/`.

## Deployment scope

The included server is configured for local course demonstrations. Hosting the application requires a JVM and persistent storage for SQLite. For a public deployment, configure TLS, individual credentials and database backups. Email notifications are simulated in the application.

Incident attachments are stored in the SQLite database. Back up the database to preserve uploaded files, incident history and knowledge articles.
