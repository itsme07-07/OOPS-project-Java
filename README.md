# ServiceForce service desk

**Live website:** [Open ServiceForce](https://oops-project-java-production.up.railway.app/)

**Administrator sign-in:** Choose **Administrator** and use the existing username `admin7900@service.adm`. The production password is itsme@07.

ServiceForce is the Java implementation of the Intelligent IT Service Management & Incident Resolution System for CS F213 / MAC F212. Employees report incidents, technicians investigate them, and administrators manage the service catalog, routing, policies and SLA violations.

## Demo sign-in

Open [ServiceForce on this computer](http://127.0.0.1:8000/) after starting the Java server. A fresh local demo database contains exactly three accounts:

| Role | Display name | Username | Demo password |
| --- | --- | --- | --- |
| Administrator | Admin 1 | `admin1111@service.adm` | `RelayDemo2026!` |
| Technician | Tech 1 | `tech2222@service.tech` | `RelayDemo2026!` |
| Employee | Employee 1 | `emp3333@service.emp` | `RelayDemo2026!` |

Choose the matching role on the sign-in screen. The sign-in form accepts only the assigned username. New accounts created after setup receive a unique, system-generated four-digit code. After sign-in, **My profile** shows the username and reminds users to use it for future sign-ins. New employees receive a verification code by email and enter their workspace immediately after verification. Their verified email is used for account recovery and incident updates. Account passwords are stored as hashes; production passwords are not in Git. The shared password above is only for disposable local demo accounts.

## Run locally

Install a **JDK version 17 or later**. In Terminal, download the repository, build it and start the server:

```sh
git clone https://github.com/itsme07-07/OOPS-project-Java.git
cd OOPS-project-Java
./mvnw clean verify
java -jar target/serviceforce.jar
```

On Windows use `mvnw.cmd clean verify`. The first build downloads Maven and the pinned dependencies. The resulting JAR includes the server, database driver and browser interface. Open [http://127.0.0.1:8000/](http://127.0.0.1:8000/) in the browser. Stop the server with Ctrl+C.

The first local launch creates `data/itsm.sqlite3`, the three demo accounts above, a service catalog and SLA policies. The incident list starts empty. Existing databases retain their existing accounts; to start over, stop the server and delete `data/itsm.sqlite3` before restarting it. Sign in again after restarting the server.

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
  screenshots/              Local demo views of one incident across the three roles
  demonstration.md          Three end-to-end workflows for the team presentation
  features.md               Implemented feature inventory and account rules
  change-log.md             Dated record of project changes
  railway.md                Railway deployment instructions
```

- The [project report](docs/project-report.pdf) covers stakeholders, requirements, the complete language and technology stack, dependency versions, configuration key names, security controls, algorithms, architecture and database design, sample data, four required diagrams, a worked priority/SLA example, Java source excerpts, three role screenshots, and 28 documented test cases linked to automated tests. It lists no private credential values. Editable vector diagrams are in [docs/diagrams](docs/diagrams/).
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
