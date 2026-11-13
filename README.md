# Relay service desk

Relay is the Java implementation of the Intelligent IT Service Management & Incident Resolution System for CS F213 / MAC F212. Employees report incidents, technicians investigate them, and administrators manage the service catalog, routing, policies and SLA violations.

## Run locally

Install a **JDK version 17 or later**. From the repository root:

```sh
./mvnw clean verify
java -jar target/relay.jar
```

On Windows use `mvnw.cmd clean verify`. The first build downloads Maven and the pinned dependencies. The resulting JAR includes the server, database driver and browser interface. Open [Relay on this computer](http://127.0.0.1:8000/). Stop it with Ctrl+C.

The first local launch creates `data/itsm.sqlite3` and demo accounts. If you already ran the earlier release, start the JAR from the same project directory to reuse the existing database. Sign in again after restarting the server.

| Workspace | Demo email | Password |
|---|---|---|
| Employee | employee@relay.test | RelayDemo2026! |
| Technician | tech@relay.test | RelayDemo2026! |
| Administrator | admin@relay.test | RelayDemo2026! |

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
```

- The [project report](docs/project-report.pdf) covers stakeholders, business rules, quality requirements, technology choices, system architecture, use cases, class relationships, database design, sample data, and test cases.
- The [demonstration guide](docs/demonstration.md) walks the team through reporting and routing an outage, investigating and verifying a fix, and correlating incidents while monitoring deadlines. The group records and submits its own presentation.
- Run `./mvnw test` for the automated checks. Tests create isolated databases and HTTP tests start their own loopback server. Maven writes detailed results under `target/surefire-reports/`.

## Deployment scope

The included server is configured for local course demonstrations. Hosting the application requires a JVM and persistent storage for SQLite. For a public deployment, configure TLS, individual credentials and database backups. Email notifications are simulated in the application.

## Email verification for employee registration

Set these environment variables in the terminal that starts Relay, then restart the server. Use a sender authorized by your SMTP provider.

| Variable | Value |
|---|---|
| RELAY_SMTP_HOST | SMTP server hostname |
| RELAY_SMTP_PORT | STARTTLS port, default 587 |
| RELAY_SMTP_USER | SMTP account username |
| RELAY_SMTP_PASSWORD | SMTP credential or provider-issued app password |
| RELAY_SMTP_FROM | Verified sender email address |

New employees receive a six-digit email code before their account is created. Codes expire after ten minutes or five incorrect guesses. Resending is available after one minute. Restarting Relay invalidates pending codes. Existing users, demo accounts and administrator-provisioned accounts retain their usual sign-in flow. Keep SMTP credentials in the environment and out of source control.
