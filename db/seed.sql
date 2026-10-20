-- =====================================================================
--  Pathivu seed data: frameworks, legal rules, demo organisation.
--  Users are created by the app (passwords must be hashed in Java):
--      java ... pathivu.tools.Setup
--  Run after schema.sql:  mysql -u root -p pathivu < db/seed.sql
-- =====================================================================
USE pathivu;

-- ---------------------------------------------------------------------
-- Demo organisation (fictional, Indian, so DPDP applies)
-- ---------------------------------------------------------------------
INSERT INTO organization (name, sector, state) VALUES
 ('Saarathi Rides Pvt Ltd', 'Mobility', 'Tamil Nadu'),
 ('Kuyil Health Labs',      'Healthcare', 'Karnataka');

INSERT INTO ai_system (org_id, name, system_type, deployer, purpose) VALUES
 (1, 'DevPilot coding agent', 'CODING_AGENT',     'Platform engineering', 'Fixes configuration and deploy issues with shell and cloud API access'),
 (1, 'Ride support bot',      'CHAT_ASSISTANT',   'Customer support',     'Answers rider questions about bookings and refunds'),
 (2, 'Triage score model',    'PREDICTIVE_MODEL', 'Clinical operations',  'Ranks lab samples by urgency');

-- ---------------------------------------------------------------------
-- Frameworks (versioned)
-- ---------------------------------------------------------------------
INSERT INTO taxonomy_version (version_id, framework, label, released_on, is_current) VALUES
 (1, 'OWASP_AGENTIC', 'OWASP Top 10 for Agentic Applications 2026', '2025-12-09', TRUE),
 (2, 'OWASP_LLM',     'OWASP Top 10 for LLM Applications 2025',    '2024-11-18', FALSE),
 (3, 'OWASP_LLM',     'OWASP Top 10 for LLM Applications 2026',    '2026-08-03', TRUE),
 (4, 'MITRE_ATLAS',   'MITRE ATLAS (seeded Oct 2026)',             '2026-10-01', TRUE),
 (5, 'NIST_AI_RMF',   'NIST AI RMF 1.0',                           '2023-01-26', TRUE);

INSERT INTO taxonomy_node (version_id, code, name) VALUES
 (1,'ASI01','Agent Goal Hijack'),
 (1,'ASI02','Tool Misuse and Exploitation'),
 (1,'ASI03','Identity and Privilege Abuse'),
 (1,'ASI04','Agentic Supply Chain Vulnerabilities'),
 (1,'ASI05','Unexpected Code Execution'),
 (1,'ASI06','Memory and Context Poisoning'),
 (1,'ASI07','Insecure Inter-Agent Communication'),
 (1,'ASI08','Cascading Failures'),
 (1,'ASI09','Human-Agent Trust Exploitation'),
 (1,'ASI10','Rogue Agents'),
 -- 2025 LLM list kept so mappings made last year still resolve
 (2,'LLM01','Prompt Injection'),
 (2,'LLM02','Sensitive Information Disclosure'),
 (2,'LLM06','Excessive Agency'),
 (2,'LLM07','System Prompt Leakage'),
 (2,'LLM09','Misinformation'),
 -- 2026 LLM list
 (3,'LLM01','Prompt Injection'),
 (3,'LLM02','Sensitive Information Disclosure'),
 (3,'LLM03','Excessive Agency'),
 (3,'LLM04','Supply Chain'),
 (3,'LLM05','Data and Model Poisoning'),
 (3,'LLM06','Unbounded Consumption'),
 (3,'LLM07','Misinformation'),
 (3,'LLM08','Hidden Context Exposure'),
 (3,'LLM09','Vector and Embedding Weaknesses'),
 (3,'LLM10','Improper Output Handling'),
 -- ATLAS: adversary techniques, plus an explicit "no applicable technique"
 (4,'NONE','No applicable technique (no adversary involved)'),
 (4,'AML.T0051','LLM Prompt Injection'),
 (4,'AML.T0054','LLM Jailbreak'),
 (4,'AML.T0057','LLM Data Leakage'),
 (4,'AML.T0053','LLM Plugin Compromise'),
 (4,'AML.T0020','Poison Training Data'),
 (4,'AML.T0043','Craft Adversarial Data'),
 (4,'AML.T0024','Exfiltration via ML Inference API'),
 (4,'AML.T0048','External Harms'),
 -- NIST AI RMF core functions
 (5,'GOVERN','Govern: policies, roles and accountability'),
 (5,'MAP','Map: context and risks identified'),
 (5,'MEASURE','Measure: risks analysed and tracked'),
 (5,'MANAGE','Manage: risks prioritised and acted on');

-- ---------------------------------------------------------------------
-- Legal rules. effective_from is stored in UTC:
--   CERT-In Directions (28 Apr 2022) in force 28 Jun 2022 00:00 IST
--   DPDP Rules 2025 (G.S.R. 846(E), 13 Nov 2025) Rule 7 in force 13 May 2027 00:00 IST
-- ---------------------------------------------------------------------
INSERT INTO rule_version (code, regime, name, recipient, effective_from, window_minutes, trigger_on, citation, extendable) VALUES
 ('CERTIN_6H',            'CERT_IN', 'Report the cyber security incident',            'CERT-In',
   '2022-06-27 18:30:00', 360,  'CYBER_INCIDENT',
   'CERT-In Directions under s.70B(6) IT Act, 28 Apr 2022, para (ii): report within 6 hours of noticing', FALSE),
 ('DPDP_R7_PRINCIPALS',   'DPDP',    'Tell each affected Data Principal',              'Affected persons',
   '2027-05-12 18:30:00', NULL, 'PERSONAL_DATA',
   'DPDP Rules 2025, Rule 7(1): intimate each affected Data Principal without delay', FALSE),
 ('DPDP_R7_BOARD_INITIAL','DPDP',    'First intimation to the Board',                  'Data Protection Board of India',
   '2027-05-12 18:30:00', NULL, 'PERSONAL_DATA',
   'DPDP Rules 2025, Rule 7(2)(a): intimate the Board without delay', FALSE),
 ('DPDP_R7_BOARD_72H',    'DPDP',    'Detailed report to the Board',                   'Data Protection Board of India',
   '2027-05-12 18:30:00', 4320, 'PERSONAL_DATA',
   'DPDP Rules 2025, Rule 7(2)(b): within 72 hours of becoming aware, or longer if the Board allows in writing', TRUE);
