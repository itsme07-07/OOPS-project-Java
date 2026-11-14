# Demonstration guide

Allow 15–20 minutes. Each group member should introduce themselves and demonstrate the module they worked on. Use a fresh database for a repeatable demonstration. Keep the administrator, employee and technician in separate browser profiles so their sign-in cookies stay independent.

## Before the demonstration

Start the server with `java -jar target/relay.jar` and open [http://127.0.0.1:8000/](http://127.0.0.1:8000/). Sign in through **Username or email**:

| Role | Demo email | Demo password |
|---|---|---|
| Administrator | `admin@relay.test` | `RelayDemo2026!` |
| Technician | `tech@relay.test` | `RelayDemo2026!` |
| Employee | `employee@relay.test` | `RelayDemo2026!` |

Each account also has a generated username in the format `exampleCODE@service.role`. To show it, open **My profile** with the button beside Sign out. Exact codes are created locally, so they are not listed in Git.

## 1. Report and route an outage (4 minutes)

1. Sign in as an employee. Report that payment checkout fails for an entire department. Select a payment-related service and high impact and urgency.
2. Record the assigned incident ID. Open the detail view and identify the priority, technician, response target and resolution target.
3. Show the creation, classification and routing events. Inspect the notifications. Explain which inputs influence priority.
4. Open the administrator workspace and locate the same incident by ID. Compare its priority and assignment with another incident in the queue.

## 2. Investigate, resolve and verify (5 minutes)

1. Sign in as the assigned technician. If demonstrating with the preset technician account, first use the administrator to assign the incident to that technician.
2. Acknowledge the incident, start investigating and add a note explaining what you checked. Request the exact error from the employee.
3. Reply from the employee account. Return to the technician queue and record the fix and its verification evidence.
4. Resolve the incident. Switch to the employee, verify the fix, close the case with the appropriate role, and submit feedback. Review the incident history and the change in active workload.

## 3. Correlate and monitor (4 minutes)

1. Report two open incidents against the same service with overlapping symptoms and titles. Inspect the related incident information and explain the matching rule.
2. In the administrator workspace, change the assignment strategy. Report another incident and inspect which eligible technician receives it.
3. Open reporting and compare service volume, active workloads and SLA status. To demonstrate a deadline crossing, configure a one-minute response target before creating a fresh case and leave it unacknowledged.
4. After the deadline, refresh and show the recorded SLA breach and Escalated marker. Open the administrator notifications and audit log. Explain why repeated monitoring does not generate another identical escalation.

## Additional features to demonstrate

Attach a small PNG, JPEG, PDF or text file to an incident, then show that an unrelated employee cannot download it. Add a public note and an IT-only internal note, and compare what the reporter sees. Reopen a resolved incident with a reason. Search the Knowledge base for a fix and, for a resolved P1 incident, record its root cause, impact and prevention steps in a post-incident review.

## Design and evaluation (3–5 minutes)

Use the project report to explain the domain classes, alternate routing strategies, notification observers and transaction boundary. Run the automated test suite and discuss one invalid transition and one attempted access to another employee's record. Identify the local-demo deployment assumptions.

Record the actual team presentation and use the group number and upload destination supplied by the course.
