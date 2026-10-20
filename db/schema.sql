-- =====================================================================
--  Pathivu (பதிவு) : AI Incident Register with Regulatory Obligation Tracking
--  MySQL 8.0 schema
--
--  Conventions
--  * Every DATETIME column stores UTC. The app converts to IST only for display.
--  * Nothing is ever deleted. Incidents are closed, clocks are ruled out,
--    mappings are rejected. The audit trail is append-only.
--  * Rules that matter legally are enforced HERE (constraints + triggers),
--    not only in Java, so a bug in the app or a manual SQL session cannot
--    break them.
--
--  Run:  mysql -u root -p < db/schema.sql
-- =====================================================================

DROP DATABASE IF EXISTS pathivu;
CREATE DATABASE pathivu CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE pathivu;

-- ---------------------------------------------------------------------
-- 1. Context: who deploys which AI system
-- ---------------------------------------------------------------------
CREATE TABLE organization (
    org_id      INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    sector      VARCHAR(60)  NOT NULL,          -- e.g. 'Mobility', 'Banking' (TEC 57090 sector field)
    state       VARCHAR(60)  NOT NULL DEFAULT 'Tamil Nadu'
);

CREATE TABLE ai_system (
    ai_system_id INT AUTO_INCREMENT PRIMARY KEY,
    org_id       INT NOT NULL,
    name         VARCHAR(120) NOT NULL,
    system_type  ENUM('CODING_AGENT','CHAT_ASSISTANT','PREDICTIVE_MODEL','RECOMMENDER','OTHER') NOT NULL,
    deployer     VARCHAR(120) NOT NULL,         -- team that runs it
    purpose      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_ai_system_org FOREIGN KEY (org_id) REFERENCES organization(org_id),
    CONSTRAINT uq_ai_system UNIQUE (org_id, name)
);

-- ---------------------------------------------------------------------
-- 2. People and roles
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    full_name     VARCHAR(80)  NOT NULL,
    role          ENUM('REPORTER','REVIEWER','AUDITOR') NOT NULL,
    password_hash CHAR(64)     NOT NULL,        -- PBKDF2-HMAC-SHA256, hex
    salt          CHAR(32)     NOT NULL,        -- 16 random bytes, hex
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT (UTC_TIMESTAMP())
);

-- Every sign-in attempt, kept append-only. Five failures in 15 minutes
-- lock the username for 15 minutes (counted by the app, refused if forged).
CREATE TABLE login_attempt (
    attempt_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(40) NOT NULL,
    success     BOOLEAN     NOT NULL,
    at          DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    INDEX ix_login_user (username, at)
);

-- ---------------------------------------------------------------------
-- 3. The incident (superclass table) and its three subtypes
--    "Class table inheritance": common columns here, type-specific
--    columns in one table per subclass, sharing the same primary key.
-- ---------------------------------------------------------------------
CREATE TABLE incident (
    incident_id    INT AUTO_INCREMENT PRIMARY KEY,
    ref_code       VARCHAR(20) NULL UNIQUE,     -- PTV-2026-0001, set once right after insert
    kind           ENUM('AGENT','MODEL_HARM','DATA') NOT NULL,
    title          VARCHAR(160) NOT NULL,
    description    TEXT NOT NULL,
    ai_system_id   INT NOT NULL,
    reported_by    INT NOT NULL,
    occurred_at    DATETIME NOT NULL,           -- when it happened
    detected_at    DATETIME NOT NULL,           -- when someone noticed
    aware_at       DATETIME NOT NULL,           -- when the ORGANISATION became aware: starts every legal clock
    severity       ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    status         ENUM('REPORTED','TRIAGED','ASSESSED','NOTICES_REVIEWED','CLOSED') NOT NULL DEFAULT 'REPORTED',
    adversary      ENUM('NONE','SUSPECTED','CONFIRMED') NOT NULL,
    cyber_incident ENUM('YES','NO','UNSURE') NOT NULL DEFAULT 'UNSURE',   -- CERT-In question
    aware_gap_reason VARCHAR(400) NULL,         -- required if the organisation became aware > 1 hour after detection
    linked_to      INT NULL,                    -- a closed incident is never reopened; a follow-up links to it
    created_at     DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_incident_system   FOREIGN KEY (ai_system_id) REFERENCES ai_system(ai_system_id),
    CONSTRAINT fk_incident_reporter FOREIGN KEY (reported_by)  REFERENCES app_user(user_id),
    CONSTRAINT fk_incident_link     FOREIGN KEY (linked_to)    REFERENCES incident(incident_id),
    CONSTRAINT ck_incident_order    CHECK (occurred_at <= detected_at AND detected_at <= aware_at),
    -- Delaying "awareness" delays every legal clock, so a gap over an hour must be explained.
    CONSTRAINT ck_aware_gap         CHECK (TIMESTAMPDIFF(MINUTE, detected_at, aware_at) <= 60
                                           OR COALESCE(CHAR_LENGTH(TRIM(aware_gap_reason)), 0) >= 20),
    -- COALESCE matters: with a NULL reason the test would be NULL, and a CHECK only
    -- rejects FALSE. Without it, a missing reason would silently pass (a test caught this).
    INDEX ix_incident_status (status),
    INDEX ix_incident_aware  (aware_at)
);

CREATE TABLE agent_incident (
    incident_id             INT PRIMARY KEY,
    agent_name              VARCHAR(80)  NOT NULL,
    instruction_given       VARCHAR(255) NOT NULL,   -- what the agent was asked to do
    action_taken            VARCHAR(255) NOT NULL,   -- what it actually did
    credential_beyond_task  BOOLEAN NOT NULL,        -- held access wider than the task needed
    had_confirmation_step   BOOLEAN NOT NULL,        -- a human had to approve destructive actions
    CONSTRAINT fk_agent_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id)
);

CREATE TABLE model_harm_incident (
    incident_id     INT PRIMARY KEY,
    output_type     ENUM('MISINFORMATION','BIASED_DECISION','HARMFUL_CONTENT','OTHER') NOT NULL,
    affected_group  VARCHAR(120) NOT NULL,
    CONSTRAINT fk_model_harm_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id)
);

CREATE TABLE data_incident (
    incident_id      INT PRIMARY KEY,
    data_categories  VARCHAR(255) NOT NULL,           -- e.g. 'names, phone numbers, trip history'
    records_affected INT NOT NULL CHECK (records_affected >= 0),
    exfiltrated      ENUM('YES','NO','UNSURE') NOT NULL,
    CONSTRAINT fk_data_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id)
);

-- Tags handle overlap: one incident can be both an agent incident and a data incident.
CREATE TABLE incident_tag (
    incident_id INT NOT NULL,
    tag         VARCHAR(40) NOT NULL,
    PRIMARY KEY (incident_id, tag),
    CONSTRAINT fk_tag_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id)
);

-- Corrections never overwrite. They are recorded next to the original.
CREATE TABLE incident_amendment (
    amendment_id INT AUTO_INCREMENT PRIMARY KEY,
    incident_id  INT NOT NULL,
    field_name   VARCHAR(40)  NOT NULL,
    old_value    VARCHAR(255) NULL,
    new_value    VARCHAR(255) NULL,
    reason       VARCHAR(255) NOT NULL,
    amended_by   INT NOT NULL,
    amended_at   DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_amend_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_amend_user     FOREIGN KEY (amended_by)  REFERENCES app_user(user_id),
    CONSTRAINT ck_amend_reason   CHECK (CHAR_LENGTH(TRIM(reason)) >= 10)
);

-- ---------------------------------------------------------------------
-- 4. Harm and evidence
-- ---------------------------------------------------------------------
CREATE TABLE harm (
    harm_id        INT AUTO_INCREMENT PRIMARY KEY,
    incident_id    INT NOT NULL,
    category       ENUM('DATA_LOSS','DATA_EXPOSURE','SERVICE_OUTAGE','FINANCIAL','DISCRIMINATION','SAFETY','REPUTATION','OTHER') NOT NULL,
    description    VARCHAR(255) NOT NULL,
    affected_count INT NOT NULL DEFAULT 0 CHECK (affected_count >= 0),
    personal_data  ENUM('YES','NO','UNSURE') NOT NULL,   -- DPDP question, asked per harm
    recorded_by    INT NOT NULL,
    recorded_at    DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_harm_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_harm_user     FOREIGN KEY (recorded_by) REFERENCES app_user(user_id)
);

CREATE TABLE evidence (
    evidence_id INT AUTO_INCREMENT PRIMARY KEY,
    incident_id INT NOT NULL,
    label       VARCHAR(120) NOT NULL,         -- 'Agent transcript', 'Cloud audit log'
    file_name   VARCHAR(255) NOT NULL,
    sha256      CHAR(64)     NOT NULL,         -- fingerprint taken when the file was attached
    size_bytes  BIGINT       NOT NULL,
    added_by    INT NOT NULL,
    added_at    DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_evidence_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_evidence_user     FOREIGN KEY (added_by)    REFERENCES app_user(user_id),
    CONSTRAINT ck_sha256_hex        CHECK (sha256 REGEXP '^[0-9a-f]{64}$')
);

-- ---------------------------------------------------------------------
-- 5. Frameworks: versioned, so old mappings never point at a renamed category
-- ---------------------------------------------------------------------
CREATE TABLE taxonomy_version (
    version_id  INT AUTO_INCREMENT PRIMARY KEY,
    framework   ENUM('OWASP_AGENTIC','OWASP_LLM','MITRE_ATLAS','NIST_AI_RMF') NOT NULL,
    label       VARCHAR(60) NOT NULL,           -- 'OWASP Top 10 for LLM Applications 2026'
    released_on DATE NOT NULL,
    is_current  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_taxonomy_version UNIQUE (framework, label)
);

CREATE TABLE taxonomy_node (
    node_id     INT AUTO_INCREMENT PRIMARY KEY,
    version_id  INT NOT NULL,
    code        VARCHAR(20)  NOT NULL,          -- 'ASI03', 'LLM03', 'AML.T0051', 'NONE'
    name        VARCHAR(120) NOT NULL,
    CONSTRAINT fk_node_version FOREIGN KEY (version_id) REFERENCES taxonomy_version(version_id),
    CONSTRAINT uq_node UNIQUE (version_id, code)
);

-- Many-to-many between incidents and framework categories.
CREATE TABLE incident_mapping (
    incident_id  INT NOT NULL,
    node_id      INT NOT NULL,
    state        ENUM('SUGGESTED','CONFIRMED','REJECTED') NOT NULL DEFAULT 'SUGGESTED',
    rationale    VARCHAR(400) NOT NULL,         -- a mapping without a reason is not allowed
    confidence   ENUM('LOW','MEDIUM','HIGH') NOT NULL,
    mapped_by    INT NOT NULL,                  -- reporter, or the suggestion engine on their behalf
    reviewed_by  INT NULL,
    reviewed_at  DATETIME NULL,
    PRIMARY KEY (incident_id, node_id),
    CONSTRAINT fk_map_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_map_node     FOREIGN KEY (node_id)     REFERENCES taxonomy_node(node_id),
    CONSTRAINT fk_map_by       FOREIGN KEY (mapped_by)   REFERENCES app_user(user_id),
    CONSTRAINT fk_map_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_map_rationale CHECK (CHAR_LENGTH(TRIM(rationale)) >= 15),
    CONSTRAINT ck_map_review   CHECK ((state = 'SUGGESTED') = (reviewed_by IS NULL))
);

-- ---------------------------------------------------------------------
-- 6. Law: every rule has a start date, so the same system is right
--    before and after a rule comes into force.
-- ---------------------------------------------------------------------
CREATE TABLE rule_version (
    rule_id        INT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL UNIQUE,    -- 'CERTIN_6H', 'DPDP_R7_BOARD_72H'
    regime         VARCHAR(20)  NOT NULL,           -- 'CERT_IN', 'DPDP'; a new regulator is a new row
    name           VARCHAR(120) NOT NULL,
    recipient      VARCHAR(80)  NOT NULL,           -- 'CERT-In', 'Data Protection Board', 'Affected persons'
    effective_from DATETIME NOT NULL,               -- UTC
    window_minutes INT NULL,                        -- NULL means "without delay"
    trigger_on     ENUM('CYBER_INCIDENT','PERSONAL_DATA') NOT NULL,
    citation       VARCHAR(255) NOT NULL,
    extendable     BOOLEAN NOT NULL DEFAULT FALSE,  -- the regulator may allow more time in writing
    CONSTRAINT ck_window CHECK (window_minutes IS NULL OR window_minutes > 0)
);

CREATE TABLE obligation_clock (
    clock_id         INT AUTO_INCREMENT PRIMARY KEY,
    incident_id      INT NOT NULL,
    rule_id          INT NOT NULL,
    started_at       DATETIME NOT NULL,              -- always = incident.aware_at
    due_at           DATETIME NOT NULL,              -- = started_at for "without delay"
    status           ENUM('RUNNING','MET','RULED_OUT') NOT NULL DEFAULT 'RUNNING',
    met_at           DATETIME NULL,
    ruled_out_by     INT NULL,
    ruled_out_reason VARCHAR(400) NULL,
    ruled_out_at     DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_clock_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_clock_rule     FOREIGN KEY (rule_id)     REFERENCES rule_version(rule_id),
    CONSTRAINT fk_clock_reviewer FOREIGN KEY (ruled_out_by) REFERENCES app_user(user_id),
    CONSTRAINT uq_clock UNIQUE (incident_id, rule_id),   -- one clock per rule per incident
    CONSTRAINT ck_clock_due      CHECK (due_at >= started_at),
    CONSTRAINT ck_clock_met      CHECK ((status = 'MET') = (met_at IS NOT NULL)),
    CONSTRAINT ck_clock_ruledout CHECK ((status = 'RULED_OUT') =
                                        (ruled_out_by IS NOT NULL AND ruled_out_reason IS NOT NULL AND ruled_out_at IS NOT NULL)),
    INDEX ix_clock_due (status, due_at)
);

-- An extension never moves the clock. It is a separate record beside it,
-- naming the regulator's written permission. The live due time is the latest one.
CREATE TABLE clock_extension (
    extension_id    INT AUTO_INCREMENT PRIMARY KEY,
    clock_id        INT NOT NULL,
    new_due_at      DATETIME NOT NULL,
    board_reference VARCHAR(120) NOT NULL,           -- reference of the written permission
    reason          VARCHAR(400) NOT NULL,
    recorded_by     INT NOT NULL,
    recorded_at     DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    CONSTRAINT fk_ext_clock FOREIGN KEY (clock_id)    REFERENCES obligation_clock(clock_id),
    CONSTRAINT fk_ext_user  FOREIGN KEY (recorded_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_ext_text  CHECK (CHAR_LENGTH(TRIM(reason)) >= 20 AND CHAR_LENGTH(TRIM(board_reference)) >= 3)
);

CREATE TABLE notification (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    clock_id        INT NOT NULL,
    recipient       VARCHAR(80) NOT NULL,
    body            TEXT NOT NULL,
    drafted_by      INT NOT NULL,
    drafted_at      DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
    approved_by     INT NULL,
    approved_at     DATETIME NULL,
    sent_at         DATETIME NULL,                   -- set by the database clock, never typed in
    CONSTRAINT fk_notice_clock    FOREIGN KEY (clock_id)    REFERENCES obligation_clock(clock_id),
    CONSTRAINT fk_notice_drafter  FOREIGN KEY (drafted_by)  REFERENCES app_user(user_id),
    CONSTRAINT fk_notice_approver FOREIGN KEY (approved_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_notice_four_eyes CHECK (approved_by IS NULL OR approved_by <> drafted_by),
    CONSTRAINT ck_notice_order    CHECK (sent_at IS NULL OR approved_at IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 7. Accountability: append-only, hash-chained audit trail
-- ---------------------------------------------------------------------
CREATE TABLE audit_event (
    event_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id INT NULL,
    actor_id    INT NOT NULL,
    action      VARCHAR(40)  NOT NULL,           -- 'INCIDENT_CREATED', 'CLOCK_RULED_OUT', ...
    details     VARCHAR(500) NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    prev_hash   CHAR(64) NOT NULL,
    hash        CHAR(64) NOT NULL UNIQUE,         -- SHA-256(prev_hash | event fields)
    CONSTRAINT fk_audit_incident FOREIGN KEY (incident_id) REFERENCES incident(incident_id),
    CONSTRAINT fk_audit_actor    FOREIGN KEY (actor_id)    REFERENCES app_user(user_id),
    INDEX ix_audit_incident (incident_id)
);

-- One row that always holds the newest hash. Writers lock it with
-- SELECT ... FOR UPDATE, so two writers can never extend the chain
-- from the same point (no forks).
CREATE TABLE audit_chain_head (
    id            TINYINT PRIMARY KEY CHECK (id = 1),
    last_event_id BIGINT NULL,
    last_hash     CHAR(64) NOT NULL
);
INSERT INTO audit_chain_head VALUES (1, NULL, REPEAT('0', 64));

-- =====================================================================
-- TRIGGERS: the database itself refuses anything that would rewrite history
-- =====================================================================
DELIMITER //

-- aware_at can never be in the future (5 minutes of clock skew allowed).
CREATE TRIGGER trg_incident_bi BEFORE INSERT ON incident
FOR EACH ROW
BEGIN
    IF NEW.aware_at > UTC_TIMESTAMP() + INTERVAL 5 MINUTE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'aware_at cannot be in the future';
    END IF;
    IF NEW.status <> 'REPORTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a new incident must start in REPORTED';
    END IF;
END//

-- Write-once fields + legal workflow transitions.
CREATE TRIGGER trg_incident_bu BEFORE UPDATE ON incident
FOR EACH ROW
BEGIN
    IF NOT (NEW.aware_at <=> OLD.aware_at) OR NOT (NEW.occurred_at <=> OLD.occurred_at)
       OR NOT (NEW.detected_at <=> OLD.detected_at) OR NOT (NEW.reported_by <=> OLD.reported_by)
       OR NOT (NEW.kind <=> OLD.kind) OR NOT (NEW.created_at <=> OLD.created_at)
       OR NOT (NEW.aware_gap_reason <=> OLD.aware_gap_reason) OR NOT (NEW.linked_to <=> OLD.linked_to) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'timestamps, reporter and kind are write-once; record an amendment instead';
    END IF;
    IF OLD.ref_code IS NOT NULL AND NOT (NEW.ref_code <=> OLD.ref_code) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ref_code is write-once';
    END IF;
    IF OLD.status = 'CLOSED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a closed incident cannot change; open a linked incident instead';
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'REPORTED'         AND NEW.status = 'TRIAGED')
        OR (OLD.status = 'TRIAGED'          AND NEW.status = 'ASSESSED')
        OR (OLD.status = 'ASSESSED'         AND NEW.status IN ('NOTICES_REVIEWED','TRIAGED'))
        OR (OLD.status = 'NOTICES_REVIEWED' AND NEW.status IN ('CLOSED','TRIAGED'))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'illegal status transition';
    END IF;
END//

CREATE TRIGGER trg_incident_bd BEFORE DELETE ON incident
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'incidents are never deleted; close them instead'//

-- Severity and the CERT-In answer drive deadlines, so changing them needs an
-- amendment row (written first, in the same transaction) that says why.
CREATE TRIGGER trg_incident_bu_amend BEFORE UPDATE ON incident
FOR EACH ROW FOLLOWS trg_incident_bu
BEGIN
    IF NEW.severity <> OLD.severity AND NOT EXISTS (
         SELECT 1 FROM incident_amendment a
          WHERE a.incident_id = OLD.incident_id AND a.field_name = 'severity'
            AND a.old_value = OLD.severity AND a.new_value = NEW.severity
            AND a.amended_at >= UTC_TIMESTAMP() - INTERVAL 1 MINUTE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'changing severity needs an amendment with a reason';
    END IF;
    IF NEW.cyber_incident <> OLD.cyber_incident AND NOT EXISTS (
         SELECT 1 FROM incident_amendment a
          WHERE a.incident_id = OLD.incident_id AND a.field_name = 'cyber_incident'
            AND a.old_value = OLD.cyber_incident AND a.new_value = NEW.cyber_incident
            AND a.amended_at >= UTC_TIMESTAMP() - INTERVAL 1 MINUTE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'changing the CERT-In answer needs an amendment with a reason';
    END IF;
END//

-- Nothing can be added to a closed incident.
CREATE TRIGGER trg_harm_bi BEFORE INSERT ON harm
FOR EACH ROW
BEGIN
    IF (SELECT status FROM incident WHERE incident_id = NEW.incident_id) = 'CLOSED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'the incident is closed';
    END IF;
END//
CREATE TRIGGER trg_evidence_bi BEFORE INSERT ON evidence
FOR EACH ROW
BEGIN
    IF (SELECT status FROM incident WHERE incident_id = NEW.incident_id) = 'CLOSED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'the incident is closed';
    END IF;
END//

-- A clock is anchored to aware_at forever, and the DATABASE checks the
-- deadline arithmetic and the rule's start date, not just the app.
CREATE TRIGGER trg_clock_bi BEFORE INSERT ON obligation_clock
FOR EACH ROW
BEGIN
    DECLARE v_aware DATETIME;
    DECLARE v_from  DATETIME;
    DECLARE v_win   INT;
    SELECT aware_at INTO v_aware FROM incident WHERE incident_id = NEW.incident_id;
    SELECT effective_from, window_minutes INTO v_from, v_win FROM rule_version WHERE rule_id = NEW.rule_id;
    IF NEW.started_at <> v_aware THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a clock must start at the incident aware_at';
    END IF;
    IF v_aware < v_from THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'that rule was not in force when the organisation became aware';
    END IF;
    IF NEW.due_at <> v_aware + INTERVAL IFNULL(v_win, 0) MINUTE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'due_at does not match the rule window';
    END IF;
    IF NEW.status <> 'RUNNING' OR NEW.met_at IS NOT NULL OR NEW.ruled_out_by IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a new clock must be RUNNING';
    END IF;
END//

CREATE TRIGGER trg_clock_bu BEFORE UPDATE ON obligation_clock
FOR EACH ROW
BEGIN
    DECLARE v_reporter INT;
    DECLARE v_sent DATETIME;
    IF NEW.started_at <> OLD.started_at OR NEW.due_at <> OLD.due_at
       OR NEW.rule_id <> OLD.rule_id OR NEW.incident_id <> OLD.incident_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a clock cannot be moved or reset';
    END IF;
    IF OLD.status <> 'RUNNING' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a clock that is MET or RULED_OUT is final';
    END IF;
    IF NEW.status = 'MET' THEN
        -- A clock is met only by a notice that was actually sent. met_at is
        -- copied from that notice, so nobody can type in a convenient time.
        SELECT MIN(sent_at) INTO v_sent FROM notification
         WHERE clock_id = OLD.clock_id AND sent_at IS NOT NULL;
        IF v_sent IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a clock is met only by a sent notice';
        END IF;
        SET NEW.met_at = v_sent;
    END IF;
    IF NEW.status = 'RULED_OUT' THEN
        SELECT reported_by INTO v_reporter FROM incident WHERE incident_id = NEW.incident_id;
        IF NEW.ruled_out_by = v_reporter THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'the reporter of an incident cannot rule out its clocks';
        END IF;
        IF (SELECT role FROM app_user WHERE user_id = NEW.ruled_out_by) <> 'REVIEWER' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only a reviewer can rule out a clock';
        END IF;
        IF CHAR_LENGTH(TRIM(NEW.ruled_out_reason)) < 20 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ruling out a clock needs a written reason (20+ characters)';
        END IF;
        SET NEW.ruled_out_at = UTC_TIMESTAMP();
    END IF;
END//

CREATE TRIGGER trg_clock_bd BEFORE DELETE ON obligation_clock
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'clocks are never deleted'//

-- Extensions: only for extendable rules, only by a reviewer, only while the
-- clock is running and not yet overdue, and only ever later.
CREATE TRIGGER trg_ext_bi BEFORE INSERT ON clock_extension
FOR EACH ROW
BEGIN
    DECLARE v_status VARCHAR(10);
    DECLARE v_due DATETIME;
    DECLARE v_ext BOOLEAN;
    SELECT c.status, r.extendable,
           COALESCE((SELECT MAX(e.new_due_at) FROM clock_extension e WHERE e.clock_id = c.clock_id), c.due_at)
      INTO v_status, v_ext, v_due
      FROM obligation_clock c JOIN rule_version r ON r.rule_id = c.rule_id
     WHERE c.clock_id = NEW.clock_id;
    IF NOT v_ext THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'this rule does not allow extensions';
    END IF;
    IF v_status <> 'RUNNING' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'that clock is already met or ruled out';
    END IF;
    IF UTC_TIMESTAMP() > v_due THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'an overdue clock cannot be extended afterwards';
    END IF;
    IF NEW.new_due_at <= v_due THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'an extension must be later than the current due time';
    END IF;
    IF (SELECT role FROM app_user WHERE user_id = NEW.recorded_by) <> 'REVIEWER' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only a reviewer can record an extension';
    END IF;
    SET NEW.recorded_at = UTC_TIMESTAMP();
END//
CREATE TRIGGER trg_ext_bu BEFORE UPDATE ON clock_extension
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'extensions are append-only'//
CREATE TRIGGER trg_ext_bd BEFORE DELETE ON clock_extension
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'extensions are append-only'//

-- Sign-in history cannot be rewritten or backdated (that would undo a lockout).
CREATE TRIGGER trg_login_bi BEFORE INSERT ON login_attempt
FOR EACH ROW SET NEW.at = UTC_TIMESTAMP(3)//
CREATE TRIGGER trg_login_bu BEFORE UPDATE ON login_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'login history is append-only'//
CREATE TRIGGER trg_login_bd BEFORE DELETE ON login_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'login history is append-only'//

-- A notice is born as a draft: nobody can insert one already approved or sent.
CREATE TRIGGER trg_notice_bi BEFORE INSERT ON notification
FOR EACH ROW
BEGIN
    IF NEW.approved_by IS NOT NULL OR NEW.approved_at IS NOT NULL OR NEW.sent_at IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a new notice must be a draft';
    END IF;
    IF (SELECT status FROM obligation_clock WHERE clock_id = NEW.clock_id) <> 'RUNNING' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'that clock is already met or ruled out';
    END IF;
END//

-- Approval and sending: four eyes, server time, in order, once.
CREATE TRIGGER trg_notice_bu BEFORE UPDATE ON notification
FOR EACH ROW
BEGIN
    IF NEW.body <> OLD.body AND OLD.approved_by IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'an approved notice cannot be edited';
    END IF;
    IF OLD.approved_by IS NOT NULL AND NOT (NEW.approved_by <=> OLD.approved_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'approval is final';
    END IF;
    IF OLD.sent_at IS NOT NULL AND NOT (NEW.sent_at <=> OLD.sent_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a sent notice is final';
    END IF;
    IF OLD.approved_by IS NULL AND NEW.approved_by IS NOT NULL THEN
        IF (SELECT role FROM app_user WHERE user_id = NEW.approved_by) <> 'REVIEWER' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only a reviewer can approve a notice';
        END IF;
        SET NEW.approved_at = UTC_TIMESTAMP();
    END IF;
    IF OLD.sent_at IS NULL AND NEW.sent_at IS NOT NULL THEN
        SET NEW.sent_at = UTC_TIMESTAMP();                 -- whatever the app sent, the DB clock wins
    END IF;
END//

CREATE TRIGGER trg_notice_bd BEFORE DELETE ON notification
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'notices are never deleted'//

-- The database re-checks every audit hash the app computes, and moves the
-- chain head itself. A row that does not extend the current head is refused.
CREATE TRIGGER trg_audit_bi BEFORE INSERT ON audit_event
FOR EACH ROW
BEGIN
    DECLARE v_head CHAR(64);
    SELECT last_hash INTO v_head FROM audit_chain_head WHERE id = 1 FOR UPDATE;
    IF NEW.prev_hash <> v_head THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit event does not extend the chain head';
    END IF;
    IF ABS(TIMESTAMPDIFF(SECOND, NEW.created_at, UTC_TIMESTAMP(3))) > 60 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit time must be the database time';
    END IF;
    IF NEW.hash <> SHA2(CONCAT_WS('|', NEW.prev_hash, IFNULL(NEW.incident_id, '-'), NEW.actor_id,
                                  NEW.action, NEW.details,
                                  DATE_FORMAT(NEW.created_at, '%Y-%m-%d %H:%i:%s.%f')), 256) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit hash does not match its contents';
    END IF;
END//
CREATE TRIGGER trg_audit_ai AFTER INSERT ON audit_event
FOR EACH ROW
    UPDATE audit_chain_head SET last_event_id = NEW.event_id, last_hash = NEW.hash WHERE id = 1//

-- Append-only tables.
CREATE TRIGGER trg_audit_bu BEFORE UPDATE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only'//
CREATE TRIGGER trg_audit_bd BEFORE DELETE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only'//
CREATE TRIGGER trg_evidence_bu BEFORE UPDATE ON evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evidence is append-only'//
CREATE TRIGGER trg_evidence_bd BEFORE DELETE ON evidence
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evidence is append-only'//
CREATE TRIGGER trg_amend_bu BEFORE UPDATE ON incident_amendment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'amendments are append-only'//
CREATE TRIGGER trg_amend_bd BEFORE DELETE ON incident_amendment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'amendments are append-only'//

-- Every mapping starts as a suggestion. A reviewed mapping is final.
CREATE TRIGGER trg_mapping_bi BEFORE INSERT ON incident_mapping
FOR EACH ROW
BEGIN
    IF NEW.state <> 'SUGGESTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a new mapping must be SUGGESTED';
    END IF;
    IF (SELECT status FROM incident WHERE incident_id = NEW.incident_id) = 'CLOSED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'the incident is closed';
    END IF;
END//
CREATE TRIGGER trg_mapping_bu BEFORE UPDATE ON incident_mapping
FOR EACH ROW
BEGIN
    IF OLD.state <> 'SUGGESTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a reviewed mapping is final';
    END IF;
    IF NEW.state <> 'SUGGESTED' THEN
        IF (SELECT role FROM app_user WHERE user_id = NEW.reviewed_by) <> 'REVIEWER' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only a reviewer can confirm or reject a mapping';
        END IF;
        SET NEW.reviewed_at = UTC_TIMESTAMP();
    END IF;
END//

DELIMITER ;

-- =====================================================================
-- VIEWS used by the dashboard and the viva demo
-- =====================================================================

-- Live status of every clock. MISSED / MET_LATE are computed when you look,
-- so no background job is needed and nothing can "forget" to mark a miss.
CREATE VIEW v_clock_status AS
SELECT c.clock_id, c.incident_id, i.ref_code, i.title, r.code AS rule_code, r.regime, r.name AS rule_name,
       r.recipient, c.started_at, c.due_at, x.effective_due_at, r.window_minutes,
       CASE
         WHEN c.status = 'RULED_OUT'                              THEN 'RULED_OUT'
         WHEN c.status = 'MET' AND c.met_at <= x.effective_due_at THEN 'MET'
         WHEN c.status = 'MET'                                    THEN 'MET_LATE'
         WHEN UTC_TIMESTAMP() > x.effective_due_at                THEN 'OVERDUE'
         ELSE 'RUNNING'
       END AS live_status,
       TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), x.effective_due_at) AS minutes_left
FROM obligation_clock c
JOIN incident i     ON i.incident_id = c.incident_id
JOIN rule_version r ON r.rule_id = c.rule_id
-- the original due_at never changes; a recorded extension can only make the live one later
JOIN (SELECT c2.clock_id,
             COALESCE((SELECT MAX(e.new_due_at) FROM clock_extension e WHERE e.clock_id = c2.clock_id), c2.due_at)
               AS effective_due_at
        FROM obligation_clock c2) x ON x.clock_id = c.clock_id;

-- One line per incident for the register screen.
CREATE VIEW v_incident_register AS
SELECT i.incident_id, i.ref_code, i.kind, i.title, i.severity, i.status, i.aware_at,
       o.name AS organization, s.name AS ai_system, u.full_name AS reporter,
       (SELECT COUNT(*) FROM harm h WHERE h.incident_id = i.incident_id)                         AS harms,
       (SELECT COUNT(*) FROM incident_mapping m WHERE m.incident_id = i.incident_id
                                                AND m.state = 'CONFIRMED')                         AS confirmed_mappings,
       (SELECT COUNT(*) FROM v_clock_status v WHERE v.incident_id = i.incident_id
                                              AND v.live_status IN ('RUNNING','OVERDUE'))          AS open_clocks
FROM incident i
JOIN ai_system s    ON s.ai_system_id = i.ai_system_id
JOIN organization o ON o.org_id = s.org_id
JOIN app_user u     ON u.user_id = i.reported_by;
