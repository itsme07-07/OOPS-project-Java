"""Vector diagrams for the ServiceForce project report."""

from pathlib import Path

from reportlab.graphics import renderSVG
from reportlab.graphics.shapes import Circle, Drawing, Ellipse, Line, Polygon, Rect, String
from reportlab.lib import colors


INK = colors.HexColor("#211d3e")
MUTED = colors.HexColor("#625c78")
PURPLE = colors.HexColor("#6d4ed6")
PALE = colors.HexColor("#f3effd")
BLUE = colors.HexColor("#eaf3ff")
GOLD = colors.HexColor("#fff3dc")
LINE = colors.HexColor("#aea6c3")


def text(d, x, y, value, size=9, bold=False, color=INK, anchor="start"):
    d.add(String(x, y, value, fontName="Helvetica-Bold" if bold else "Helvetica",
                 fontSize=size, fillColor=color, textAnchor=anchor))


def box(d, x, y, w, h, title, lines=(), fill=PALE, title_size=10, body_size=8):
    d.add(Rect(x, y, w, h, rx=9, ry=9, fillColor=fill, strokeColor=LINE, strokeWidth=1))
    text(d, x + 10, y + h - 19, title, title_size, True)
    for n, line in enumerate(lines):
        text(d, x + 10, y + h - 36 - n * 13, line, body_size, color=MUTED)


def edge(d, x1, y1, x2, y2, label=None, dashed=False, arrow=True):
    line = Line(x1, y1, x2, y2, strokeColor=PURPLE, strokeWidth=1.5)
    if dashed:
        line.strokeDashArray = [4, 3]
    d.add(line)
    if arrow:
        import math
        angle = math.atan2(y2 - y1, x2 - x1)
        size = 7
        points = [x2, y2,
                  x2 - size * math.cos(angle - .4), y2 - size * math.sin(angle - .4),
                  x2 - size * math.cos(angle + .4), y2 - size * math.sin(angle + .4)]
        d.add(Polygon(points, fillColor=PURPLE, strokeColor=PURPLE))
    if label:
        text(d, (x1 + x2) / 2, (y1 + y2) / 2 + 5, label, 7, color=MUTED, anchor="middle")


def architecture():
    d = Drawing(470, 330)
    box(d, 10, 205, 110, 88, "Browser UI", ["Employee / Technician", "Administrator", "HTML, CSS, JavaScript"], BLUE)
    box(d, 165, 205, 130, 88, "RelayServer", ["Static files + JSON API", "Session cookie / validation", "Role-aware routing"], PALE)
    box(d, 340, 205, 120, 88, "Domain services", ["Desk + Incidents", "Playbooks + strategies", "Event observers"], PALE)
    box(d, 10, 30, 110, 78, "SLA monitor", ["15-second scheduled check", "Deadlines / escalation"], GOLD)
    box(d, 165, 30, 130, 78, "Brevo mail API", ["HTTPS transactional email", "Outbox + retry"], GOLD)
    box(d, 340, 30, 120, 78, "SQLite", ["Accounts + incidents", "Audit + attachments", "Railway volume"], BLUE)
    edge(d, 120, 249, 165, 249, "HTTP")
    edge(d, 295, 249, 340, 249, "calls")
    edge(d, 400, 205, 400, 108, "transactions")
    edge(d, 340, 225, 295, 108, "email")
    edge(d, 120, 68, 340, 218, "monitor()")
    text(d, 235, 310, "One Java service hosts the browser assets, API and background jobs", 9, True, anchor="middle")
    return d


def actor(d, x, y, name):
    d.add(Circle(x, y + 25, 8, strokeColor=INK, fillColor=None, strokeWidth=1.5))
    d.add(Line(x, y + 17, x, y - 7, strokeColor=INK, strokeWidth=1.5))
    d.add(Line(x - 13, y + 8, x + 13, y + 8, strokeColor=INK, strokeWidth=1.5))
    d.add(Line(x, y - 7, x - 11, y - 22, strokeColor=INK, strokeWidth=1.5))
    d.add(Line(x, y - 7, x + 11, y - 22, strokeColor=INK, strokeWidth=1.5))
    text(d, x, y - 39, name, 8, True, anchor="middle")


def usecase(d, x, y, w, label, fill=PALE):
    d.add(Ellipse(x + w/2, y, w/2, 19, fillColor=fill, strokeColor=PURPLE, strokeWidth=1.1))
    text(d, x + w/2, y - 3, label, 8, anchor="middle")


def use_cases():
    d = Drawing(470, 455)
    d.add(Rect(85, 30, 300, 390, fillColor=None, strokeColor=LINE, strokeWidth=1.2))
    text(d, 235, 429, "ServiceForce system boundary", 10, True, anchor="middle")
    actor(d, 35, 325, "Employee")
    actor(d, 35, 150, "Technician")
    actor(d, 435, 260, "Administrator")
    for y, name in [(365, "Register / sign in"), (320, "Report incident"),
                    (275, "Track / respond"), (230, "Verify / feedback")]:
        usecase(d, 105, y, 120, name, BLUE)
        edge(d, 50, 333, 105, y, arrow=False)
    for y, name in [(170, "Investigate / playbook"), (125, "Resolve incident"),
                    (80, "View workload")]:
        usecase(d, 105, y, 135, name)
        edge(d, 50, 158, 105, y, arrow=False)
    for y, name in [(365, "Manage people / teams"), (320, "Configure services / SLA"),
                    (275, "Assign / escalate"), (230, "Reports / audit"),
                    (125, "Maintain playbooks")]:
        usecase(d, 255, y, 115, name, GOLD)
        edge(d, 420, 270, 370, y, arrow=False)
    return d


def uml(d, x, y, w, h, name, attrs=(), methods=(), fill=PALE, interface=False):
    d.add(Rect(x, y, w, h, fillColor=fill, strokeColor=LINE, strokeWidth=1))
    d.add(Line(x, y+h-27, x+w, y+h-27, strokeColor=LINE, strokeWidth=.7))
    d.add(Line(x, y+h-27-13*len(attrs), x+w, y+h-27-13*len(attrs), strokeColor=LINE, strokeWidth=.7))
    if interface:
        text(d, x+w/2, y+h-12, "<<interface>>", 7, color=MUTED, anchor="middle")
        text(d, x+w/2, y+h-23, name, 8.5, True, anchor="middle")
    else:
        text(d, x+w/2, y+h-18, name, 9, True, anchor="middle")
    for n, value in enumerate(attrs):
        text(d, x+6, y+h-39-n*13, value, 7.2)
    for n, value in enumerate(methods):
        text(d, x+6, y+h-39-13*len(attrs)-n*13, value, 7.2)


def classes():
    d = Drawing(470, 540)
    uml(d, 10, 400, 135, 112, "AssignmentStrategy", ["+ choose(candidates, category)"],
        ["+ ranking(category)"], BLUE, True)
    uml(d, 168, 400, 135, 112, "Desk", ["- database: Database", "- incidents: Incidents"],
        ["+ snapshot(userId)", "+ mutate(userId, action)", "+ monitor()"], PALE)
    uml(d, 326, 400, 135, 112, "RelayServer", ["- sessions: SessionStore", "- desk: Desk"],
        ["+ start()", "- dispatch(request)"], PALE)
    uml(d, 10, 285, 135, 75, "SkillFirst", [], ["+ ranking(category)"], BLUE)
    uml(d, 10, 190, 135, 75, "LeastLoad", [], ["+ ranking(category)"], BLUE)
    uml(d, 168, 255, 135, 115, "Incidents", ["- observers: List<Observer>"],
        ["+ create(...) / act(...) ", "+ snapshot(...) / monitor(...)"], PALE)
    uml(d, 326, 255, 135, 95, "Database", ["- url: String"],
        ["+ transaction(work)", "+ rows(...) / exec(...)"], BLUE)
    uml(d, 168, 120, 135, 95, "Playbooks", [],
        ["+ seed(connection)", "+ copyToIncident(...)"], GOLD)
    uml(d, 326, 120, 135, 95, "MailDelivery", [], ["+ send(to, subject, body)"], GOLD, True)
    uml(d, 168, 5, 135, 82, "IncidentEvent.Observer", [], ["+ record(connection, event)"], BLUE, True)
    uml(d, 10, 5, 135, 65, "AuditLog", [], ["+ record(...)"], PALE)
    uml(d, 326, 5, 135, 65, "NotificationOutbox", [], ["+ record(...)"], PALE)
    edge(d, 76, 360, 76, 400, "implements", dashed=True)
    edge(d, 145, 225, 155, 225, dashed=True, arrow=False)
    edge(d, 155, 225, 155, 455, "implements", dashed=True, arrow=False)
    edge(d, 155, 455, 145, 455, dashed=True)
    edge(d, 326, 457, 303, 457, "uses")
    edge(d, 235, 400, 235, 370, "owns")
    edge(d, 303, 425, 326, 331, "uses")
    edge(d, 235, 255, 235, 215, "copies")
    edge(d, 303, 405, 315, 405, arrow=False)
    edge(d, 315, 405, 315, 177, "sends", arrow=False)
    edge(d, 315, 177, 326, 177)
    edge(d, 235, 255, 235, 87, "emits")
    edge(d, 145, 37, 168, 46, "implements", dashed=True)
    edge(d, 326, 37, 303, 46, "implements", dashed=True)
    return d


def entity(d, x, y, w, h, title, lines, fill=PALE):
    box(d, x, y, w, h, title, lines, fill=fill, title_size=9, body_size=7.3)


def er():
    d = Drawing(470, 540)
    entity(d, 5, 415, 135, 95, "users", ["PK id; username, email, role", "skill, capacity, active"], BLUE)
    entity(d, 168, 415, 135, 95, "categories", ["PK name"], BLUE)
    entity(d, 330, 415, 135, 95, "services", ["PK id; FK category", "criticality"], BLUE)
    entity(d, 5, 280, 135, 90, "policies", ["PK priority", "response/resolution targets"], GOLD)
    entity(d, 168, 280, 135, 105, "incidents", ["PK id; FK reporter_id", "FK service_id, category", "FK technician_id, priority", "status, deadlines"], PALE)
    entity(d, 330, 280, 135, 90, "playbook_templates", ["PK id; FK category", "title, position"], GOLD)
    entity(d, 5, 145, 135, 95, "incident_responders", ["PK/FK incident_id, user_id", "joined_at"], PALE)
    entity(d, 168, 145, 135, 95, "incident_playbook_steps", ["PK id; FK incident_id", "FK actor_id; status, result"], PALE)
    entity(d, 330, 145, 135, 95, "audit", ["PK id; FK incident_id", "actor, action, timestamp"], PALE)
    entity(d, 5, 10, 135, 95, "notifications", ["PK id; FK incident_id", "FK user_id; message"], BLUE)
    entity(d, 168, 10, 135, 95, "email_outbox", ["PK id; FK notification_id", "recipient, status, retry"], GOLD)
    edge(d, 303, 465, 330, 465, "1:N", arrow=False)
    edge(d, 235, 415, 235, 385, "1:N", arrow=False)
    edge(d, 140, 425, 168, 345, "1:N", arrow=False)
    edge(d, 140, 325, 168, 325, "1:N", arrow=False)
    edge(d, 303, 345, 330, 425, "1:N", arrow=False)
    edge(d, 303, 445, 315, 445, arrow=False)
    edge(d, 315, 445, 315, 325, "1:N", arrow=False)
    edge(d, 315, 325, 330, 325, arrow=False)
    edge(d, 200, 280, 100, 240, "1:N", arrow=False)
    edge(d, 235, 280, 235, 240, "1:N", arrow=False)
    edge(d, 270, 280, 365, 240, "1:N", arrow=False)
    edge(d, 168, 300, 152, 300, arrow=False)
    edge(d, 152, 300, 152, 58, "1:N", arrow=False)
    edge(d, 152, 58, 140, 58, arrow=False)
    edge(d, 140, 58, 168, 58, "1:0..1", arrow=False)
    return d


DIAGRAMS = {"ARCHITECTURE": architecture, "USE_CASE": use_cases,
            "CLASS": classes, "ER": er}


def export_svgs(folder: Path):
    folder.mkdir(parents=True, exist_ok=True)
    for name, factory in DIAGRAMS.items():
        renderSVG.drawToFile(factory(), str(folder / (name.lower().replace("_", "-") + ".svg")))

