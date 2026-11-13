CREATE TABLE IF NOT EXISTS users (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT COLLATE NOCASE UNIQUE NOT NULL,
 password TEXT NOT NULL, role TEXT NOT NULL, skill TEXT NOT NULL DEFAULT '',
 available INTEGER NOT NULL DEFAULT 1, capacity INTEGER NOT NULL DEFAULT 20);
CREATE TABLE IF NOT EXISTS categories(name TEXT PRIMARY KEY COLLATE NOCASE);
CREATE TABLE IF NOT EXISTS services (
 id INTEGER PRIMARY KEY, name TEXT UNIQUE COLLATE NOCASE NOT NULL, category TEXT REFERENCES categories,
 criticality INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS policies(priority TEXT PRIMARY KEY, response_minutes INTEGER NOT NULL,
 resolution_minutes INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS settings(name TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS incidents (
 id INTEGER PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, reporter_id INTEGER REFERENCES users,
 category TEXT REFERENCES categories, service_id INTEGER REFERENCES services, impact INTEGER NOT NULL,
 urgency INTEGER NOT NULL, priority TEXT REFERENCES policies, status TEXT NOT NULL, technician_id INTEGER REFERENCES users,
 created_at TEXT NOT NULL, response_due TEXT NOT NULL, resolution_due TEXT NOT NULL, acknowledged_at TEXT,
 resolved_at TEXT, closed_at TEXT, resolution TEXT DEFAULT '', feedback TEXT DEFAULT '',
 related_id INTEGER REFERENCES incidents, suggestion_source_id INTEGER REFERENCES incidents, suggested_resolution TEXT DEFAULT '');
CREATE INDEX IF NOT EXISTS assigned_incidents ON incidents(technician_id,status);
CREATE TABLE IF NOT EXISTS audit (
 id INTEGER PRIMARY KEY, incident_id INTEGER REFERENCES incidents, actor TEXT NOT NULL, action TEXT NOT NULL,
 description TEXT NOT NULL, previous_state TEXT, new_state TEXT, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS notifications(
 id INTEGER PRIMARY KEY, incident_id INTEGER REFERENCES incidents, user_id INTEGER REFERENCES users,
 message TEXT NOT NULL, channel TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sla_events(
 incident_id INTEGER REFERENCES incidents, target TEXT, kind TEXT, PRIMARY KEY(incident_id,target,kind));
