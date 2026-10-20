-- =====================================================================
--  The app never connects as root. It uses pathivu_app, which has:
--    * SELECT + INSERT everywhere it needs them
--    * UPDATE only on the few tables whose rows legitimately change state
--    * NO DELETE anywhere, NO UPDATE on audit_event / evidence / amendments
--    * NO DROP, ALTER, TRIGGER: it cannot switch the protections off
--    * NO INSERT on app_user: the app cannot create itself a reviewer.
--      Accounts are made by pathivu.tools.Setup with the admin login.
--  Even if someone finds a SQL injection bug, history cannot be erased.
--
--  Run as root: mysql -u root -p < db/grants.sql
--  Change the password before any real use.
-- =====================================================================
DROP USER IF EXISTS 'pathivu_app'@'localhost';
CREATE USER 'pathivu_app'@'localhost' IDENTIFIED BY 'change-me-Pathivu-2026';

GRANT SELECT ON pathivu.* TO 'pathivu_app'@'localhost';

GRANT INSERT ON pathivu.organization        TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.ai_system           TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.incident            TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.agent_incident      TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.model_harm_incident TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.data_incident       TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.incident_tag        TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.incident_amendment  TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.harm                TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.evidence            TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.incident_mapping    TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.obligation_clock    TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.notification        TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.audit_event         TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.clock_extension     TO 'pathivu_app'@'localhost';
GRANT INSERT ON pathivu.login_attempt       TO 'pathivu_app'@'localhost';

-- state changes only
GRANT UPDATE ON pathivu.incident            TO 'pathivu_app'@'localhost';
GRANT UPDATE ON pathivu.incident_mapping    TO 'pathivu_app'@'localhost';
GRANT UPDATE ON pathivu.obligation_clock    TO 'pathivu_app'@'localhost';
GRANT UPDATE ON pathivu.notification        TO 'pathivu_app'@'localhost';
-- needed only so the app can lock the head row (SELECT ... FOR UPDATE);
-- the audit trigger moves the head itself, and verify() catches any forgery
GRANT UPDATE ON pathivu.audit_chain_head    TO 'pathivu_app'@'localhost';

FLUSH PRIVILEGES;
