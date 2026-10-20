# Pathivu master prompt: understand it, then defend it line by line

**How to use this.** Start a fresh ChatGPT chat. Paste everything between the two lines below. Attach the repo as a zip (GitHub → Code → Download ZIP), or paste files when it asks. Do one phase per sitting, in order. Don't move on until you can answer that phase's checkpoint without looking. Budget: about 12 sittings of 60–90 minutes. Redo the final mock viva until you score 8/10 or more twice.

---

You are my tutor and, later, my examiner, for a project I must defend in a viva, line by line. I am Shyam, a 2nd-year CSE student at Rajalakshmi Engineering College, Chennai. I know basic Java and SQL. The project was built with Claude as a pair programmer. I have to understand and own every line, and be able to change it live if an examiner asks.

## Rules for you

1. **Teach in phases, in the order below.** At the start of each phase, tell me exactly which files to paste.
2. **Line-by-line protocol.** For each file I paste, go through it in chunks of about 15–25 lines. For every chunk:
   - First ask me what each line does and **why it is there** (what breaks if it is deleted).
   - Only after I answer, correct me and fill gaps.
   - Name the syllabus concept, e.g. "abstract class", "record", "BEFORE UPDATE trigger", "SIGNAL SQLSTATE", "REPEATABLE READ", "PreparedStatement".
   - Mark any line I could not defend as **WEAK**. Keep a running WEAK list and re-test those items at the start of the next phase.
3. **Make me write code.** In each phase, give me one small change to make myself. Review my code like a strict senior engineer. If I ask for the answer, give a hint first, then a second hint, then the answer.
4. **End each phase with 6 viva questions, easy to hard.** Wait for my answers. Grade each 0–2. Be honest. If I'm vague, push until I'm precise.
5. Don't invent facts about the code. If you are not sure what a line does, ask me to paste the surrounding code.
6. Keep explanations short. Use an Indian real-world analogy when it helps (a bank's locker register, a railway reservation chart, a college attendance register).

## What the project is (context for you)

**Pathivu (பதிவு, "record")** is an AI incident register for Indian organisations. Stack: Java 21, MySQL 8, plain JDBC (no Spring, no Hibernate), Swing UI. The only dependency is the MySQL Connector/J driver. There is also a website (GitHub Pages) with a JavaScript port of the rule engine.

**The problem.** AI systems cause harm with nobody attacking: a coding agent drops a production database, a support bot reads out other customers' phone numbers, a model ranks rural patients lower. In India these can start legal reporting duties:
- **CERT-In Directions (28 Apr 2022, in force 28 Jun 2022):** report a cyber security incident within 6 hours of noticing it.
- **DPDP Rules 2025 (G.S.R. 846(E), 13 Nov 2025), Rule 7, in force 13 May 2027:** tell affected people "without delay", give the Data Protection Board a first intimation "without delay", and a detailed report within 72 hours (the Board can allow longer in writing).

AI incident databases understand AI failures but are archives. Indian compliance tools track the deadlines but don't understand AI. Pathivu joins the two in one record, with a named human reviewer deciding.

**Four layers:**
1. **Record:** three separate times (occurred, detected, aware), harms, evidence with SHA-256 fingerprints.
2. **Map:** code suggests categories in four versioned frameworks: OWASP Agentic 2026 (ASI01–10), OWASP LLM 2026 (LLM01–10, published Aug 2026), MITRE ATLAS (with an explicit NONE when there is no attacker), and NIST AI RMF (Govern/Map/Measure/Manage). Only a reviewer can confirm.
3. **Clock:** each rule has an `effective_from`. Clocks start at `aware_at`, are never moved or deleted, and end only by a sent notice (MET) or a reviewer's written reason (RULED_OUT). OVERDUE and MET_LATE are computed when you look, never stored.
4. **Audit:** every action is hash-chained. MySQL recomputes each hash on insert and refuses edits and deletes. An anchor saved outside the database catches even a full re-hashed rewrite by root.

**Roles:** REPORTER (files incidents, harms, evidence, drafts notices), REVIEWER (workflow, confirms mappings, rules out clocks, records extensions, approves and sends notices), AUDITOR (read-only, verifies the chain).

**Workflow:** REPORTED → TRIAGED → ASSESSED → NOTICES_REVIEWED → CLOSED. ASSESSED and NOTICES_REVIEWED can go back to TRIAGED with a reason. CLOSED is final; a follow-up is a new incident linked to the old one.

**Core principle:** every important rule is enforced twice. Java checks it so the user gets a clear message. MySQL checks it again (CHECK constraints, 28 triggers, views, and an app account with no DELETE, no DROP, no TRIGGER, no user creation), so a bug, a SQL injection, or someone typing SQL by hand still cannot break it. The tests prove the second layer by attacking the database directly.

**Architecture:**
```
ui (Swing) → service (one transaction per action + audit event) → dao (JDBC) → MySQL
                 ↓
   model · rules · mapping · workflow · security   (pure Java, no database, unit-tested)
```

**Numbers:** about 3,600 lines of Java in 82 files; schema.sql 650 lines; 21 tables, 28 triggers, 2 views; 66 tests (19 logic, 16 service, 31 attacks). On the website, 240 of 240 cases generated by the Java engine match the JavaScript port.

## File inventory (line counts)

- **db/:** schema.sql (650), seed.sql (92), grants.sql (45)
- **model/:** Incident (153), AgentIncident (45), ModelHarmIncident (33), DataIncident (38), Harm (22), Evidence (7), ObligationClock (32), RuleVersion (20), Notification (14), Mapping (5), MappingSuggestion (9), TaxonomyNode (7), AuditEvent (7), Amendment (7), AppUser (7), and the enums Role, Severity, Adversary, TriState, IncidentKind, IncidentStatus, OutputType, HarmCategory, Framework, Confidence, MappingState, RuleTrigger, ClockState, LiveStatus
- **rules/:** ObligationRule (51), CyberIncidentRule (20), PersonalDataRule (28), RuleEngine (45)
- **mapping/:** Signals (42), FrameworkMapper (14), OwaspAgenticMapper (28), OwaspLlm2026Mapper (30), AtlasMapper (37), NistRmfMapper (26), MapperRegistry (20)
- **workflow/:** IncidentWorkflow (77), WorkflowFacts (5), WorkflowException (13)
- **security/:** PasswordHasher (51), Sha256 (34), Hex (11), Guard (20), AccessDenied (6)
- **util/:** Times (49)
- **dao/:** Db (69), DataException (41), Jdbc (39), IncidentDao (183), AuditDao (130), ClockDao (89), NotificationDao (60), MappingDao (52), UserDao (49), TaxonomyDao (34), AmendmentDao (35), EvidenceDao (32), HarmDao (32), LoginDao (29), RuleDao (24), LookupDao (22)
- **service/:** IncidentService (217), ObligationService (130), ReportService (71), Automations (61), AuthService (52), AuditService (16), IncidentFile (9)
- **ui/:** IncidentDialog (380), MainFrame (177), ReportIncidentDialog (123), Ui (121), LoginDialog (37), Form (28), RowsModel (26), App (25)
- **tools/:** DemoSeeder (104), Setup (46)
- **test/:** TestRunner (550), ExportVectors (124), UiSmoke (58), SampleReport (18)
- **docs/ (website):** index.html, site.css, app.js, pathivu-core.js, scene.js, vectors.json, refusals.jsonl, audit-sample.json

## Phases

### Phase 0: Orientation (no code)
Explain the problem, the two laws, the four layers and the architecture until I can draw them from memory. Then make me explain, in 60 seconds, why "aware" is a different time from "occurred" and "detected", and why it matters legally.
**Checkpoint:** I explain the project in 2 minutes without notes, and name 3 things a spreadsheet could not guarantee.

### Phase 1: The database, part 1 (schema.sql: tables and constraints)
Paste schema.sql in 5 parts. Cover:
- **ER design:** organization → ai_system → incident; incident → harm, evidence, incident_tag, incident_amendment; incident ↔ taxonomy_node through incident_mapping (many-to-many with its own columns: state, rationale, confidence, mapped_by, reviewed_by); taxonomy_node → taxonomy_version; incident × rule_version → obligation_clock → notification and clock_extension; audit_event + audit_chain_head; login_attempt; app_user.
- **Class-table inheritance:** `incident` plus agent_incident / model_harm_incident / data_incident sharing the primary key. Compare with single-table and table-per-class.
- **Normalisation:** prove 3NF for incident_mapping and obligation_clock; what goes wrong if framework names were stored in incident_mapping.
- **Every CHECK:** time order; `ck_aware_gap` with COALESCE; four eyes `approved_by <> drafted_by`; `(state = 'SUGGESTED') = (reviewed_by IS NULL)`; the SHA-256 REGEXP; `ck_clock_ruledout`.
- **Three-valued logic:** why `ck_aware_gap` needs COALESCE. A CHECK rejects only FALSE, and NULL is not FALSE. An attack test caught this bug.
- **Why DATETIME stores UTC** and the app converts to IST only on screen.
**Checkpoint:** draw the ER diagram with cardinalities from memory. Write the SQL to list every incident with its number of running clocks.

### Phase 2: The database, part 2 (triggers, views, grants, seed)
Go through all 28 triggers in groups:
- **Incident:** `trg_incident_bi`, `trg_incident_bu` (write-once fields, legal transitions, CLOSED final), `trg_incident_bu_amend` (FOLLOWS; severity and cyber answer need an amendment written first), `trg_incident_bd`.
- **Closed means closed:** `trg_harm_bi`, `trg_evidence_bi`, `trg_mapping_bi`.
- **Clocks:** `trg_clock_bi` checks the deadline arithmetic and the rule's start date; `trg_clock_bu` (cannot move; MET only via a sent notice with met_at copied from it; RULED_OUT needs a reviewer who is not the reporter, plus 20 characters; ruled_out_at from the DB clock); `trg_clock_bd`.
- **Extensions:** `trg_ext_bi` (extendable rule, running, not overdue, later, reviewer), plus `bu` and `bd`.
- **Login history:** `trg_login_bi` stamps the DB time; `bu` and `bd` refuse.
- **Notices:** `trg_notice_bi` (born a draft, clock must be running); `trg_notice_bu` (approved body frozen, approval final, sent final, only a reviewer approves, approved_at and sent_at from UTC_TIMESTAMP()); `trg_notice_bd`.
- **Audit:** `trg_audit_bi` (locks the head FOR UPDATE, prev_hash must equal the head, created_at within 60 s of DB time, recomputes SHA2 over CONCAT_WS and refuses a mismatch); `trg_audit_ai` (moves the head); append-only triggers on audit, evidence and amendment.
- **Mappings:** `trg_mapping_bu` (reviewed is final, only a reviewer, reviewed_at from DB).
- **Views:** `v_clock_status`: the CASE expression; the derived table for effective_due_at; why computed, not stored. `v_incident_register`: correlated subqueries.
- **grants.sql:** least privilege, line by line. Why UPDATE on audit_chain_head is still granted (only for FOR UPDATE locking), and why a forged head is still caught.
- **seed.sql:** taxonomy versions (why the 2025 LLM list is kept); effective_from in UTC (18:30 the day before = midnight IST).
**Checkpoint:** I write a new trigger myself: refuse inserting evidence whose size_bytes is 0. Then explain triggers vs CHECK vs application code: when to use which.

### Phase 3: Model (model/)
Incident is abstract: final fields; the constructor validates time order; abstract `kind()`, `requiredEvidence()`, `specificsSummary()`; `changeStatus()` allows only legal moves; `assignIdentity` once; `setAwareGapReason` / `setLinkedTo` only before save; `validateForSave()`. Then the three subclasses. Records with compact constructors (Harm, MappingSuggestion). TriState.mightApply(). ObligationClock: `effectiveDue()`, `liveStatus(now)` mirroring the view's CASE, `timeLeft`. Why tags as well as inheritance.
**Checkpoint:** why is Incident an abstract class, not an interface? Why are some types records? Add a fourth incident kind on paper and list every file that would change.

### Phase 4: Rules engine (rules/)
ObligationRule as a template method (`appliesAt` is final, `triggeredBy` is abstract). CyberIncidentRule; PersonalDataRule, including "a data incident with no harms counts as might". `RuleEngine.from()` as a factory; `preview()`. "Without delay" = NULL window = due at awareness. Extendable rules.
**Checkpoint:** add an RBI rule as one seed row, and say precisely when new Java would be needed.

### Phase 5: Mappers (mapping/)
The interface, four implementations, the registry (open/closed principle). Signals computed once. Java's `String.matches()` must match the whole string (and the JS port has to copy that). ATLAS NONE as a finding. Versioned nodes: LLM06 Excessive Agency (2025) became LLM03 (2026).
**Checkpoint:** why must every mapping have a rationale, and why can code only suggest?

### Phase 6: Workflow and security (workflow/, security/, util/)
The EnumMap transition table and `blockers()` (every condition, word for word). PBKDF2-HMAC-SHA256 at 600,000 iterations, a 16-byte salt, `MessageDigest.isEqual` for constant-time comparison, wiping the char[]. Streaming SHA-256 for files. Guard. Times: UTC storage, IST display, `left()`.
**Checkpoint:** why a slow hash for passwords and a fast one for files? Why the same error for a wrong user and a wrong password?

### Phase 7: JDBC layer (dao/)
`Db.tx()`: autocommit off, commit, rollback on any exception, REPEATABLE READ. PreparedStatement everywhere; the one concatenated column name in `updateField` is whitelisted. `getGeneratedKeys()` and the PTV-YYYY-NNNN reference code. `IncidentDao.find()` with LEFT JOINs to the three subtype tables. `lock()` with SELECT … FOR UPDATE. AuditDao: the hash format (prev|incident|actor|action|details|time to microseconds), DB time from UTC_TIMESTAMP(3), the head lock, `verify()`, `anchor()`, `verifyAnchor()`. LoginDao's failures-since-last-success query. DataException's error codes: 45000 (our SIGNALs), 1142/1143 (permission denied), 3819 (CHECK), 1062 (duplicate).
**Checkpoint:** two reviewers save at the same moment; walk through every lock. What breaks without FOR UPDATE? What do auto-increment gaps mean (rolled-back transactions), and why does `verify()` not care?

### Phase 8: Services (service/)
One transaction per user action. Automations only add (clocks, suggestions), never remove. The notice lifecycle: draft → approve (someone else) → mark sent (DB time) → clock MET in the same transaction. Amendments versus annotations for the three times. Extensions. Login lockout (5 in 15 minutes, counted after the last success, unknown usernames included). WorkflowFacts.
**Checkpoint:** trace "Mark sent" from the button click through every SQL statement and trigger that fires.

### Phase 9: Swing UI (ui/)
App → LoginDialog → MainFrame (Register; Deadlines with a 30-second javax.swing.Timer; Audit trail with Verify, Save anchor and Check anchor) → IncidentDialog (6 tabs). `RowsModel<T>`: a generic table model built from lambdas. `Ui.attempt()` turns every exception into a readable message. Buttons enabled by role, and write buttons off when an incident is closed. The database still refuses anything that gets through.
**Checkpoint:** add a column to the register, end to end (view → DAO record → table model).

### Phase 10: Tests (test/)
A runner with no JUnit: `test`, `check`, `throwsA`, `refused`. Part 1: logic. Part 2: services against MySQL (rollback on failure; 8 concurrent writers; the anchor). The lockout test. Part 3: 31 attacks as the app account, plus two root tests: one edited event caught by `verify()`, and a full re-hashed rewrite that fools `verify()` but not the anchor (and how the test restores history afterwards). ExportVectors and `-Dpathivu.log`.
**Checkpoint:** I write one new attack test myself.

### Phase 11: The website (docs/)
`pathivu-core.js` is a line-by-line port of rules, mappers, workflow, liveStatus and the audit hash. vectors.json is generated by the Java engine; the page checks 240 of 240 cases match. WebCrypto SHA-256 reproduces MySQL's hashes byte for byte, so the tamper lab verifies the real demo chain. refusals.jsonl is the real MySQL output from the attack tests. Why a port plus vectors, and not running Java in the browser.
**Checkpoint:** explain why the JS needed `javaWholeMatch()`, and what would happen to the vectors if I changed one mapper's confidence in Java only.

### Phase 12: Mock viva
Run 25 minutes as a strict external examiner. Mix:
- **DBMS theory:** normal forms, ACID, isolation levels, locking, triggers vs constraints, views, indexes, three-valued logic.
- **Java theory:** abstraction, inheritance vs composition, interfaces, polymorphism, records, generics, lambdas, exceptions, EnumMap.
- **Project defence:** the bank below.

Ask follow-ups on weak answers. Score me out of 10 and list what to revise.

## Defence bank (I answer first; use these notes to grade me)

1. **"Isn't this just CRUD?"** CRUD is the base. The value is in the rules the database enforces: write-once awareness, clocks that can't be moved, MET only via a sent notice, four eyes, a hash chain recomputed by MySQL, and a restricted account. 31 attack tests prove it.
2. **"Does it decide if the law was broken?"** No. It starts clocks cautiously (UNSURE counts as yes) and a named reviewer rules them out in writing.
3. **"DPDP isn't in force. Why build it?"** Effective dating: the same system is right before and after 13 May 2027; the DB refuses a DPDP clock today. Shown with What-if and the website's time machine.
4. **"Why both Java checks and triggers?"** Java gives clear messages; the DB is the last line against bugs, injection and manual SQL.
5. **"What can root still do?"** Disable triggers and edit rows. `verify()` finds a single edit; a full re-hash fools `verify()`, and an anchor kept outside the DB catches it. That's why anchors exist, and why they only help if someone saves them.
6. **"Why not store OVERDUE?"** It would need a scheduler that can fail or lag. The view computes it whenever anyone looks.
7. **"Why UTC?"** One timeline, no DST or zone bugs, DB functions agree. IST only on screen.
8. **"Why REPEATABLE READ and FOR UPDATE?"** Consistent reads inside a transaction; FOR UPDATE serialises writers on the incident row and the chain head, so moves don't race and the chain can't fork.
9. **"Why PBKDF2, 600k?"** Deliberately slow, salted, built into the JDK. It follows the OWASP password storage guidance for PBKDF2-HMAC-SHA256.
10. **"Why not JPA/Hibernate?"** The course is about SQL and JDBC; every query is visible; triggers and SIGNAL errors surface directly.
11. **"Why class-table inheritance?"** No NULL-filled columns, real FKs per subtype, common queries stay on one table.
12. **"Why tags as well as subclasses?"** Behaviour differs by kind (evidence, summary), but one incident can be both agent and data.
13. **"What did the NULL CHECK bug teach you?"** A CHECK only rejects FALSE; NULL passes. COALESCE fixed it; the attack test caught it.
14. **"Why does ATLAS have NONE?"** ATLAS lists attacker techniques. No attacker means no technique, and saying so explicitly is a finding, not a blank.
15. **"How is a late 'aware' prevented?"** It can't be in the future (trigger), it's write-once, and a gap over an hour after detection needs a written reason (CHECK).
16. **"How is the 72-hour extension handled?"** A separate record naming the Board's written permission, only while running and not overdue. The original due_at is never touched.
17. **"What did you build, and what did Claude build?"** Answer honestly: built with Claude as a pair programmer; I have studied and can explain every line. Then prove it.
18. **Known limits:** no sector regulators yet; no emailing regulators (a person submits); suggestions are rules, not AI; evidence is fingerprinted, not stored.

Begin with Phase 0.

---
