CREATE TABLE IF NOT EXISTS users (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT COLLATE NOCASE UNIQUE NOT NULL,
 username TEXT UNIQUE,
 password TEXT NOT NULL, role TEXT NOT NULL, skill TEXT NOT NULL DEFAULT '',
 available INTEGER NOT NULL DEFAULT 1, capacity INTEGER NOT NULL DEFAULT 20, active INTEGER NOT NULL DEFAULT 1,
 email_verified INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS email_codes(
 user_id INTEGER NOT NULL REFERENCES users, purpose TEXT NOT NULL, code_hash TEXT NOT NULL,
 sent_at TEXT NOT NULL, expires_at TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,purpose));
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
CREATE TABLE IF NOT EXISTS incident_responders(
 incident_id INTEGER NOT NULL REFERENCES incidents, user_id INTEGER NOT NULL REFERENCES users,
 joined_at TEXT NOT NULL, PRIMARY KEY(incident_id,user_id));
CREATE INDEX IF NOT EXISTS responder_user_incidents ON incident_responders(user_id,incident_id);
CREATE INDEX IF NOT EXISTS assigned_incidents ON incidents(technician_id,status);
CREATE TABLE IF NOT EXISTS audit (
 id INTEGER PRIMARY KEY, incident_id INTEGER REFERENCES incidents, actor TEXT NOT NULL, action TEXT NOT NULL,
 description TEXT NOT NULL, previous_state TEXT, new_state TEXT, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS notifications(
 id INTEGER PRIMARY KEY, incident_id INTEGER REFERENCES incidents, user_id INTEGER REFERENCES users,
 message TEXT NOT NULL, channel TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS email_outbox(
 id INTEGER PRIMARY KEY, notification_id INTEGER NOT NULL UNIQUE REFERENCES notifications,
 recipient TEXT NOT NULL, subject TEXT NOT NULL, body TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'pending', attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sla_events(
 incident_id INTEGER REFERENCES incidents, target TEXT, kind TEXT, PRIMARY KEY(incident_id,target,kind));
CREATE TABLE IF NOT EXISTS attachments(
 id INTEGER PRIMARY KEY, incident_id INTEGER NOT NULL REFERENCES incidents, uploader_id INTEGER NOT NULL REFERENCES users,
 file_name TEXT NOT NULL, media_type TEXT NOT NULL, content BLOB NOT NULL, size INTEGER NOT NULL, created_at TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS incident_attachments ON attachments(incident_id);
CREATE TABLE IF NOT EXISTS knowledge_articles(
 id INTEGER PRIMARY KEY, title TEXT NOT NULL, service_id INTEGER NOT NULL REFERENCES services,
 summary TEXT NOT NULL, steps TEXT NOT NULL, author_id INTEGER NOT NULL REFERENCES users,
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS playbook_templates(
 id INTEGER PRIMARY KEY, category TEXT NOT NULL REFERENCES categories(name),
 title TEXT NOT NULL, position INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS playbook_template_category ON playbook_templates(category,position,id);
CREATE TABLE IF NOT EXISTS incident_playbook_steps(
 id INTEGER PRIMARY KEY, incident_id INTEGER NOT NULL REFERENCES incidents(id),
 title TEXT NOT NULL, position INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending',
 result TEXT NOT NULL DEFAULT '', actor_id INTEGER REFERENCES users(id), updated_at TEXT);
CREATE INDEX IF NOT EXISTS incident_playbook_order ON incident_playbook_steps(incident_id,position,id);
CREATE TABLE IF NOT EXISTS incident_reviews(
 incident_id INTEGER PRIMARY KEY REFERENCES incidents, root_cause TEXT NOT NULL, impact TEXT NOT NULL,
 prevention TEXT NOT NULL, author_id INTEGER NOT NULL REFERENCES users, updated_at TEXT NOT NULL);
