# ServiceForce service desk

**Live website:** [Open ServiceForce](https://oops-project-java-production.up.railway.app/)

**Administrator sign-in:** Choose **Administrator** and use the existing username `admin7090@service.admin`. The production password is kept private and is not stored in this public repository.

ServiceForce is the Java implementation of the Intelligent IT Service Management & Incident Resolution System for CS F213 / MAC F212. Employees report incidents, technicians investigate them, and administrators manage the service catalog, routing, policies and SLA violations.

## Demo sign-in

Open [ServiceForce on this computer](http://127.0.0.1:8000/) after starting the Java server. Choose **Employee**, **Technician** or **Administrator**, then use that role's demo button. The demo password is `RelayDemo2026!` for all three. The sign-in form accepts only the assigned username, whose exact four-digit code differs per installation. After demo sign-in, **My profile** shows it and reminds users to use it for future sign-ins. New employees receive a verification code by email and enter their workspace immediately after verification. Their verified email is used for account recovery and incident updates. Passwords are stored as hashes, never in Git.

## Run locally

Install a **JDK version 17 or later**. In Terminal, download the repository, build it and start the server:

```sh
git clone https://github.com/itsme07-07/OOPS-project-Java.git
cd OOPS-project-Java
./mvnw clean verify
java -jar target/serviceforce.jar
```

On Windows use `mvnw.cmd clean verify`. The first build downloads Maven and the pinned dependencies. The resulting JAR includes the server, database driver and browser interface. Open [http://127.0.0.1:8000/](http://127.0.0.1:8000/) in the browser. Stop the server with Ctrl+C.

The first local launch creates `data/itsm.sqlite3` and demo accounts. If you already ran the earlier release, start the JAR from the same project directory to reuse the existing database. Sign in again after restarting the server.

If you already cloned the repository, stop the running server with Ctrl+C, then run `git pull`, `./mvnw clean verify`, and `java -jar target/serviceforce.jar` from the repository directory. A browser refresh alone cannot load a new Java build into an old server process.

For a separate installation with your own administrator, use a fresh database:

```sh
java -jar target/serviceforce.jar --db data/team.sqlite3 --create-admin
java -jar target/serviceforce.jar --db data/team.sqlite3 --no-demo
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
.github/workflows/ci.yml     Build, test and package on pushes and pull requests
docs/
  project-report.pdf        Analysis, requirements, design, diagrams and testing
  project-report.md         Editable source for the project report
  generate-report.py        Regenerates the project report PDF
  report_diagrams.py         Generates vector diagrams for the report
  diagrams/                 Architecture, use-case, class and ER diagrams (SVG)
  demonstration.md          Three end-to-end workflows for the team presentation
  features.md               Implemented feature inventory and account rules
  change-log.md             Dated record of project changes
  railway.md                Railway deployment instructions
```

- The [project report](docs/project-report.pdf) covers stakeholders, business rules, quality requirements, technology choices, system architecture, use cases, class relationships, database design, sample data, four required diagrams, and 28 documented test cases linked to automated tests. Editable vector diagrams are in [docs/diagrams](docs/diagrams/).
- The [demonstration guide](docs/demonstration.md) walks the team through reporting and routing an outage, investigating and verifying a fix, and correlating incidents while monitoring deadlines. The group records and submits its own presentation.
- The [feature inventory](docs/features.md) lists the implemented workflows, access rules, reporting, notifications and escalation behavior.
- The [change log](docs/change-log.md) records dated code and documentation changes. Core incident rules use typed Java domain records for actor, service, SLA policy, technician and incident state.
- [GitHub Actions](.github/workflows/ci.yml) compiles, tests and packages the project on each push and pull request; a failed build uploads its test reports.
- When a feature changes, update `docs/features.md` and `docs/project-report.md`, then regenerate the PDF with the bundled Python runtime or another Python environment containing ReportLab: `python3 docs/generate-report.py`.
- Run `./mvnw test` for the automated checks. Tests create isolated databases and HTTP tests start their own loopback server. Maven writes detailed results under `target/surefire-reports/`.

## Deployment scope

The included server is configured for local course demonstrations. Hosting the application requires a JVM and persistent storage for SQLite. For a public deployment, configure TLS, individual credentials and database backups. Real email delivery uses Brevo’s HTTPS API when `BREVO_API_KEY` and `SERVICEFORCE_EMAIL_FROM` are configured. Registration and recovery require that configuration.

Incident attachments are stored in the SQLite database. Back up the database to preserve uploaded files, incident history and knowledge articles.

To host the Java backend and browser interface together, follow the [Railway deployment guide](docs/railway.md). Railway deployments require an attached volume and first-administrator credentials in Railway service variables. Local demo accounts are disabled on Railway.
