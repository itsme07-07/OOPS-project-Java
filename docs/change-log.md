# ServiceForce change log

This file records implementation and delivery changes. [The feature inventory](features.md) is the current list of application capabilities; [the project report](project-report.md) explains their design and tests. Entries describe changes made in this repository, not proposed features.

## 22 November 2026

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
