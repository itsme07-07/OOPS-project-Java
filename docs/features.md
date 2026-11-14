# ServiceForce feature inventory

## Accounts and access

- Employee registration with an email address and password; registration signs the employee in immediately.
- Password-based sign-in and sign-out with sessions. Demo accounts are available in local demo mode.
- Employee, technician and administrator roles. Employees see their own incidents, technicians see assignments, and administrators see the full queue and settings.
- Administrator management of people, roles, technician skills, availability and workload capacity.
- Every signed-in person can edit their own name, email address, password and (for technicians) primary skill after confirming their current password.
- Administrators can remove accounts. Removed people lose access, while incident history remains available; active technician cases must be reassigned first.

## Incident work

- Report incidents with a title, description, affected service, category, business impact and urgency.
- Automatic P1–P4 priority calculation from impact, urgency and service criticality.
- Automatic technician assignment using either skill-first or least-workload routing, subject to availability and capacity. Administrators can reassign incidents.
- Lifecycle: classified, assigned, acknowledged, in progress, resolved, verified and closed, with role-checked transitions.
- Edit open reports, add notes, request information, record a resolution and leave reporter feedback.
- Find similar incidents on the same service and suggest a previous resolution when available.
- Search and filter the incident queue by priority, status, category, service, technician, SLA, date and lifecycle.

## Service management and oversight

- Administrator management of service catalog, incident categories, routing strategy and response/resolution SLA policies.
- Response and resolution deadlines, at-risk and breached indicators, and periodic monitoring every 15 seconds while the server runs.
- **P1 response escalation:** when a critical incident passes its response deadline without acknowledgment, ServiceForce creates one escalation event, alerts administrators in-app, records it in the audit log and marks the incident Escalated in the queue and detail view. The marker records that escalation occurred, even if the incident is acknowledged later. Administrators can inspect and reassign the case.
- In-app notifications for incident activity and SLA events. A simulated email notification record is also created; it does not send an email.
- Incident history and administrator audit log, operational overview, service volume, technician workload and SLA violation reports.

## Technical and delivery

- Java 17 HTTP backend, browser interface and SQLite storage in one runnable JAR.
- Maven wrapper, automated JUnit tests, project report and demonstration guide.
