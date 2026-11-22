# ServiceForce change log

This file records implementation and delivery changes. [The feature inventory](features.md) is the current list of application capabilities; [the project report](project-report.md) explains their design and tests. Entries describe changes made in this repository, not proposed features.

## 22 November 2026

- Expanded the project report with a worked P1 priority and SLA example, source excerpts explaining Strategy and Observer, three screenshots of one local demo incident across roles, and a current 54-test Maven result. Regenerated and visually checked the PDF.
- Assigned incident-search and reporting SQL queries to Anurag's workstream; Raj retains the database schema, migrations and persistence components.
- Edited the report for submission: removed internal planning notes, editing instructions and defensive implementation disclaimers while retaining technical limitations and evidence.
- Expanded Anurag's planned workstream to include incident search, the reporting API and integration tests alongside the interface and accessibility work.
- Rebalanced the project-member page so each person has a major implementation area plus explicit Java/OOP design and tests. Shared documentation and presentation are stated separately; the allocation is a plan for team confirmation, not an assertion of past individual work.
- Redesigned the report opening as a dedicated project-members first page with four equal cards. The technical report now begins on page two; member details remain editable in the Markdown source and render into the PDF.
- Rebalanced the four-member responsibility allocation into frontend, identity/API, incident/OOP, and data/operations workstreams. Each includes implementation, tests, documentation and demonstration so no one person is assigned all testing or delivery work.
- Added the four team members, their student IDs and a clear allocation of UI, API/security, incident/OOP and database/testing/deployment responsibilities to the project report. The allocation is for report and demonstration planning and should be confirmed by the team before submission.
- Audited the report against the assignment deliverables. Added explicit problem challenges and assumptions, nine functional requirements, six non-functional requirements with implementation evidence, and UML aggregation/composition notation and explanations. Regenerated and visually checked the PDF.
- Expanded the project report with a source-checked technology inventory, dependency versions, external services, deployment configuration names, security and secret handling, domain algorithms, persistence schema, sample data, API workflows, alternatives and limitations. The report lists configuration key names but contains no private key or password values.
- Renamed the Java HTTP server class to `ServiceForceServer` and the runnable artifact to `serviceforce.jar`. Updated the Docker image, tests, startup instructions and report diagrams so the project report consistently uses the ServiceForce name.
- Added typed Java domain records for actors, services, SLA policies, technician candidates and incident state. Incident routing, priority calculation, deadline calculation, visibility checks and SLA monitoring now use these types after reading database rows. The browser API and database schema did not change.
- Added a GitHub Actions workflow that runs `./mvnw -B --no-transfer-progress clean verify` on pushes and pull requests. It uploads Surefire test reports when a build fails.
- Updated the feature inventory, project report and README to describe these technical changes.
- Verified locally with the full Maven build: 54 tests passed with no failures, errors or skips.

## 21 November 2026

- Added architecture, UML use-case, UML class and ER diagrams to the project report as vector figures.
- Documented 28 test cases with links to automated JUnit methods. The 54-test suite passed when the report was generated.

Earlier application features and behavior are listed in the [feature inventory](features.md). Future changes should add a dated entry here and update that inventory and report when behavior changes.

## 22 November 2026: local demo reset

- Cleared the existing local SQLite databases and started a fresh one with exactly three fixed role accounts: `admin1111@service.adm`, `tech2222@service.tech` and `emp3333@service.emp`.
- Removed automatically seeded incidents. The local demo now starts with an empty incident list so presenters can create cases live; service catalog, categories, policies and playbook templates remain available.
- Kept production Railway data separate. The local demo password and sign-in steps are in the README.

## 22 November 2026: report presentation and evidence map

- Reworked the report opening with a ServiceForce-specific incident journey, measured project snapshot and guide to implementation evidence. Kept the four team members on a separate first page.
- Added an OOP concept-to-code matrix, an HTTP trust-boundary appendix and a reproducible repository map. Documented the three-account local demo and its empty starting incident list.
- Regenerated the 27-page PDF and checked its extracted text and rendered pages. Counts reflect the executed 42 domain and 12 HTTP tests, rather than figures from the reference report.
- Replaced the older screenshots with one current demo incident shown from Employee 1, Tech 1 and Admin 1 accounts. Updated the figure captions and worked-example context to match the new images.
