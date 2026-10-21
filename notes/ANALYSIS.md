# Pathivu: status, analysis and loopholes

21 Oct 2026 (fourth pass: 5 more loopholes closed, 110 tests; third pass on 20 Oct closed 23 and added the plain-words guide)

## 1. Status

| Part | State |
|---|---|
| Database: 21 tables, 36 triggers, 4 guard procedures, 2 views, restricted app account (pass 4 adds 1 column and 2 CHECKs) | Done, loaded on MySQL 8.0 |
| Java core: model, rules, mappers, workflow, security | Done |
| JDBC layer: 13 DAOs, transactions, hash-chained audit with anchors | Done |
| Services: report, harms, evidence, amendments, mappings, workflow, clocks, notices, report export | Done |
| Swing UI: login, register, deadlines, audit trail, report dialog, incident screen with 6 tabs | Done, screenshot-tested |
| Tests | 110 of 110 pass (23 logic, 22 service, 65 attacks), about 15 s per run, stable over repeated runs |
| Demo data | 4 incidents, one each: running, overdue, met late, closed |
| Website | shyamvasansathiskumar-ux.github.io/pathivu: the engine ported to JavaScript, 240/240 cases matching the Java output, real MySQL refusals, a tamper lab on the real audit chain, and `learn.html`, a plain-words guide for non-technical readers |

Size: about 3,700 lines of Java across 83 files, 920 lines of SQL and 770 lines of tests. The only dependency is the MySQL JDBC driver.

## 2. How it is built (the plan, as it ended up)

```
Swing UI  ──>  Services  ──>  DAOs (JDBC)  ──>  MySQL
                  │                               │
        rules / mapping / workflow       CHECKs, triggers, views,
        (pure Java, no DB, unit-tested)  grants: the same rules again
```

Every write goes through one service method that opens one transaction. The change, its audit event, and anything it triggers (new clocks, new mapping suggestions) are committed together or rolled back together.

The important rules are enforced twice: in Java, so the user gets a clear message, and in MySQL, so a bug, a SQL injection or a manual SQL session still cannot break them. The tests prove the second layer by attacking the database directly.

## 3. How upgraded it is compared with the brief

The brief listed eight loopholes. All eight are handled. On top of that:

| Upgrade | Why it matters |
|---|---|
| The database checks the deadline arithmetic itself (`trg_clock_bi`) | The app cannot insert a clock with a made-up due time or a rule that was not in force on the awareness date |
| A clock can only be MET by a sent notice, and `met_at` is copied from that notice | Nobody can type in a convenient "met" time |
| `sent_at`, `approved_at`, `reviewed_at`, `ruled_out_at` are set by the database clock | A wrong laptop clock or a deliberate backdate cannot fake timing |
| The database recomputes every audit hash with `SHA2()` and refuses mismatches | The app cannot write a forged audit row; a fork from an old point is refused |
| The trigger moves the chain head itself, and the app locks it with `FOR UPDATE` | Eight concurrent writers tested; no fork |
| Changing severity or the CERT-In answer needs an amendment row first (trigger-checked) | Deadline-relevant facts cannot change silently |
| Nothing can be added to a closed incident (harm, evidence, mapping triggers) | Closed really means closed |
| Every mapping is born SUGGESTED and only a REVIEWER can confirm it | Code proposes, a person decides |
| The app account cannot create users | A compromised app cannot make itself a reviewer |
| Separation of duties: the reporter cannot rule out their own clocks or close their own incident; a drafter cannot approve their own notice | Four eyes on every legal decision |
| OVERDUE and MET_LATE are computed in a view, never stored | No background job that can forget to run |
| "What if?" rule preview | Shows DPDP Rule 7 working today, without faking data |
| Plain-text regulator-ready report with legal citations and evidence hashes | Something you can actually hand over |

## 4. How it is different from what exists

AI incident databases (AIID, OECD AIM) are public archives, not response tools. VerifyWise does AI incident management for the EU AI Act and ISO 42001, not Indian law. DPDPA Shield tracks the CERT-In and DPDP deadlines but does not understand how AI systems fail. TEC 57090:2025 and the IIT Madras CeRAI framework are a standard and a proposal, not software.

Pathivu joins the two halves in one record: AI failure analysis on one side, India's effective-dated obligations on the other, connected by a human-reviewed assessment. Claim the combination, never "first".

## 5. Loopholes found in the brief itself, and what was done

| # | Loophole in the brief | What the build does |
|---|---|---|
| 1 | The demo incident is dated 3 Jun 2027, but `aware_at` cannot be in the future. The worked case cannot be entered live. | Demo data uses relative times (always live), and **What if?** previews 2027 rules without saving anything. |
| 2 | The clock status list includes MISSED as a stored state. Stored "missed" needs a scheduler and can be wrong. | OVERDUE and MET_LATE are computed in `v_clock_status` and in `ObligationClock.liveStatus()`. |
| 3 | "Any stage can return to Triaged" conflicts with "Closed is final". | Only ASSESSED and NOTICES_REVIEWED can go back, with a written reason. CLOSED is final (trigger). |
| 4 | `IncidentDao` lists "update, archive". Archive implies removal. | No delete or archive anywhere. Incidents close, clocks are ruled out, mappings are rejected. |
| 5 | Passwords as bcrypt. That needs an extra library. | PBKDF2-HMAC-SHA256, built into the JDK, 600,000 iterations, per-user salt, constant-time compare. |
| 6 | Evidence stored as "uri, sha256". A URI can be pointed at a different file later. | Stores file name, size and SHA-256 at attach time; **Check a file** re-hashes any file and says if it matches. |
| 7 | `window_hours` cannot express "without delay". | `window_minutes`, where NULL means without delay (due = aware). |
| 8 | "Adding RBI is a new row, not new code." Only half true. | **Improved (pass 2).** `regime` is now plain text, so a new regulator with an existing kind of trigger is one row. A new kind of trigger still needs one new `ObligationRule` subclass. Say this honestly. |
| 9 | The DPDP 72 hours can be extended by the Board in writing. The brief does not model it. | **Fixed (pass 2).** `clock_extension`: a separate append-only record naming the Board's written permission. The original `due_at` never changes; the view uses the latest extension. The trigger allows it only for extendable rules, only by a reviewer, only while the clock is running and not yet overdue, and only ever later. |
| 10 | "Latest chain hash exported daily" needs a scheduler that does not exist. | **Fixed (pass 2).** **Save anchor** writes the newest event id and hash for keeping outside the database; **Check an anchor** compares. A test has root rewrite history *and* re-hash everything after it: `verify()` is fooled, the anchor is not. |
| 11 | "Aligned with TEC 57090:2025" overstates it. | Only the sector field and the system/harm structure follow it. Say "informed by", not "aligned with". |
| 12 | Team section says "three mappers" but four frameworks are seeded. | Four mappers. |

## 5b. Loopholes found in my own first build (pass 2)

| # | Loophole | Fix |
|---|---|---|
| 13 | A reporter could delay every clock by entering a late "aware" time. | If aware is more than an hour after detected, a written reason (20+ characters) is required: Java check plus `CHECK ck_aware_gap`. Write-once. |
| 14 | That CHECK first let a missing reason through. With a NULL reason the test is NULL, and MySQL only rejects a CHECK that is FALSE. An attack test caught it. | `COALESCE(CHAR_LENGTH(TRIM(aware_gap_reason)), 0) >= 20`. Good viva point: three-valued logic. |
| 15 | No limit on password guessing. | `login_attempt` (insert-only, DB-stamped time, no edit or delete): 5 failures in 15 minutes lock the username, real or not. Backdating an attempt is overwritten by the trigger. |
| 16 | "Closed is final; open a linked incident" had no link. | `incident.linked_to`, write-once, set at report time by reference code. |
| 17 | `verify()` cannot catch root rewriting and re-hashing the whole chain. | Anchors (see 10). |
| 18 | Regulator extensions had nowhere to go. | `clock_extension` (see 9). |

## 5c. Loopholes found in pass 3 (20 Oct 2026)

Found in two rounds: my own review, then an adversarial review that probed the live database as `pathivu_app` inside rolled-back transactions. Every fix has a test that attacks it.

**Session tricks: any connection can change its own settings, no privilege needed**

| # | Loophole | Fix |
|---|---|---|
| 19 | `SET timestamp = …` moves `NOW()` and `UTC_TIMESTAMP()` for that session. "The database clock" could be set back 3 days, then a notice marked sent "on time", a login failure backdated, an audit event written for yesterday. Tested: the clock really moved 72 hours. | `assert_honest_session()` runs first in every protective trigger: it compares `NOW()` with `SYSDATE()` (which ignores `SET timestamp`) and refuses a gap over 5 seconds. |
| 20 | `SET foreign_key_checks = 0` let the app attach a harm to incident 9999 and evidence to a user who does not exist. | Same guard refuses a session with key checks (or `unique_checks`) off. |
| 21 | `SET sql_mode = ''` turns an unknown ENUM value into `''` and a bad date into `0000-00-00`, with only a warning. A trigger cannot see this: **a trigger runs with the sql_mode it was created with**. | CHECK constraints work whatever the sql_mode: every ENUM column refuses `''`, and `occurred_at >= '2000-01-01'` refuses zero dates. |

**Times the app could still choose**

| # | Loophole | Fix |
|---|---|---|
| 22 | `amended_at` came from the app. An amendment dated a year ahead would "pre-approve" a silent severity change any time that year. | Every insert-time stamp (`created_at`, `recorded_at`, `added_at`, `drafted_at`, `amended_at`) is overwritten by the trigger. |
| 23 | `approved_at` could be rewritten on an already sent notice. | Only the trigger sets it, once, at approval. |
| 24 | An "aware" date years in the past put the incident before CERT-In existed, so no clock started. | `ck_late_report`: aware more than 7 days before the report needs a written reason (Java check, UI field and CHECK). |

**Silent edits**

| # | Loophole | Fix |
|---|---|---|
| 25 | Title and description could be rewritten with a plain UPDATE: "what happened" changed with no trace. | Need an amendment row first (trigger-checked), like severity. |
| 26 | The table collation is case- and accent-insensitive, so `'Résumé' = 'resume'` is TRUE in MySQL. A case-only change slipped past `<>`. | Text is compared `COLLATE utf8mb4_bin` (byte for byte). |
| 27 | The incident could be moved to a different AI system; the adversary answer changed with no reason. | `ai_system_id` write-once; adversary needs an amendment (now offered in the Amend dialog). |
| 28 | Amendment values were `VARCHAR(255)` and Java cut them to 252 characters: amending a long description lost the old text. | `TEXT`, nothing cut. |
| 29 | The app could choose any `ref_code`. | The trigger clears it on insert and accepts only `PTV-<year aware, IST>-<id>`. |
| 30 | A second details row could be added later, or AGENT details attached to a DATA incident. | Details only while `ref_code` is still NULL (the same transaction) and only of the incident's own kind. |
| 31 | A draft notice's text, author or clock could be changed, so a reviewer could rewrite a colleague's draft, or put a colleague's name on their own draft, and then approve it. The four-eyes CHECK never noticed. | Notices are write-once; a new draft replaces an old one. Mapping suggestions are write-once too. |

**Rules that lived only in Java**

| # | Loophole | Fix |
|---|---|---|
| 32 | The database could not tell who changed an incident's status. A reporter could close their own incident by raw SQL. | A status change is accepted only if the newest audit event for that incident says `OLD -> NEW`, comes from an active reviewer, is under a minute old, and (for CLOSED) is not from the reporter. The service writes the audit event first. |
| 33 | Forged announcements could still push an incident from REPORTED to CLOSED with no harm, no evidence, no confirmed mapping and no CERT-In clock. | The trigger repeats `IncidentWorkflow.blockers()`, and checks that every rule that applies has its clock before ASSESSED. |
| 34 | A notice could be "sent" with no approver (only `approved_at` was checked), and that met the clock. | `ck_notice_order` checks `approved_by`; `approved_by` and `approved_at` must be both set or both empty; a clock is met only by an approved, sent notice. |
| 35 | An overdue clock could be ruled out afterwards, so a missed deadline disappeared from the view. | Refused once the (extended) due time has passed or a notice has gone out: send it late (MET_LATE) instead. Same check in Java. |
| 36 | "Closed" was checked only for harm, evidence and mapping inserts. Clocks, notices, mapping reviews, tags, amendments and extensions could still be added to a closed incident. | `assert_open()` in every one of those triggers, reading with `FOR SHARE` so it also waits for a close that is happening at that moment. |
| 37 | The app can UPDATE the audit head (it needs to lock it), so it could wind the head back to an old event and then append a branch. | `trg_head_bu`: the head can only point at the newest event and its hash. |
| 38 | Role checks used `(SELECT role …) <> 'REVIEWER'`, which is NULL (so passes) for a missing user, and ignored deactivated accounts. | `assert_reviewer()` uses `NOT EXISTS (… role = 'REVIEWER' AND active)`. The reporter can no longer record an extension either. |
| 39 | A forged "success" row reset the lockout count. | Failures are counted for the whole 15 minutes; a success no longer resets them. |

**App level**

| # | Loophole | Fix |
|---|---|---|
| 40 | Swing renders any label text starting with `<html>` as HTML, including a typed incident title. One user could change what another sees, or make the app fetch an image from the internet. | `ui/Html`: user text is escaped inside our own HTML; tables and headings that show user text have HTML switched off. Tested. |
| 41 | An unknown username was refused in about 1 ms and a wrong password in about 300 ms (PBKDF2), so a stopwatch revealed which usernames exist. | A dummy PBKDF2 runs for unknown users. Tested by timing. |

## 5d. Loopholes found in pass 4 (21 Oct 2026)

| # | Loophole | Fix |
|---|---|---|
| 42 | A notice could be addressed to anyone ("Internal team") and still meet a CERT-In clock once approved and sent. | `trg_notice_bi`: the recipient must be exactly the one the rule names. |
| 43 | A one-word notice ("x") could meet a legal clock. | `ck_notice_body`: at least 30 characters, plus the same check in Java. |
| 44 | Sending an incident back to TRIAGED needed a reason only in Java. | The trigger requires the announcing audit event to say `… because <10+ characters>`. |
| 45 | A reviewer could type in a mapping and confirm it themselves: four eyes held for code suggestions, not for people's. | `incident_mapping.source` (ENGINE or PERSON); a PERSON's proposal must be confirmed by someone else. |
| 46 | The repo stores the scripts without the executable bit (uploaded through the browser), so `scripts/test.sh` failed with "Permission denied" for anyone who cloned it, including an examiner. | Scripts call each other with `bash`, and every instruction says `bash scripts/…`. |

## 6. Fact check, corrected

- OWASP Top 10 for LLM Applications 2026: published 3 Aug 2026 and formally presented 1–2 Sep 2026. Earlier I told you "6 Aug". That was wrong. Your 5 Sep LinkedIn post saying it came out around 1 Sep is close enough; do not change it to 6 Aug.
- 2026 list as seeded: LLM01 Prompt Injection, LLM02 Sensitive Information Disclosure, LLM03 Excessive Agency, LLM04 Supply Chain, LLM05 Data and Model Poisoning, LLM06 Unbounded Consumption, LLM07 Misinformation, LLM08 Hidden Context Exposure, LLM09 Vector and Embedding Weaknesses, LLM10 Improper Output Handling.
- OWASP Top 10 for Agentic Applications 2026 (ASI01–ASI10): released Dec 2025.
- DPDP Rules 2025: G.S.R. 846(E), 13 Nov 2025. Rule 7 in force 13 May 2027.
- CERT-In 6-hour reporting: in force since 28 Jun 2022. The May 2026 CERT-In guidance on patching known exploited vulnerabilities did not change the 6-hour rule.
- A report around 16 Oct 2026 about AI agents in testing reaching live systems of Indian firms came from a single source. Do not cite it in the viva unless you find a second source.

## 7. Known limits (say these before the examiner does)

- Pathivu does not decide whether the law was broken. It starts clocks conservatively and a named reviewer decides.
- It does not email regulators. A person submits the notice and records that it was sent.
- MySQL admins (root) can still disable triggers. That is why the audit chain exists: `verify()` finds the exact event that was changed, and an anchor saved outside the database catches even a full re-hashed rewrite (both tested). An anchor only helps if someone actually saves it somewhere else.
- Mapping suggestions are keyword and flag based, not AI. They are suggestions only.
- No sector regulators (RBI, SEBI, IRDAI) yet.
- Evidence files are fingerprinted, not stored. Pathivu proves a file is unchanged; it does not keep the file.
- **The database trusts the user id the app writes.** Every `*_by` and `actor_id` column, and so every separation-of-duties check, believes the app about who is acting. A compromised app could write "approved by Kavya". What the database guarantees is that such a claim is permanent and sits in the hash-chained trail. Closing this fully needs one database login per person (or definer-rights procedures), which is beyond a semester project.
- The lockout can be used to annoy: five wrong guesses lock a real user out for 15 minutes. That is the usual trade-off.
- The clock guard allows 5 seconds of difference (a statement can wait that long for a lock). A server started with `--sysdate-is-now` would make the guard blind; do not run it that way.

## 8. Four-member split (matches the packages)

| Member | Owns | Files |
|---|---|---|
| 1 · Incident core | Incident hierarchy, IncidentDao, workflow, register and report screens | model/Incident*, dao/IncidentDao, workflow/*, ui/ReportIncidentDialog, ui/MainFrame |
| 2 · Frameworks | Mappers, taxonomy versions, mapping review | mapping/*, dao/TaxonomyDao, dao/MappingDao, db/seed.sql (taxonomy part) |
| 3 · Obligations | Rules, clocks, notices, IST handling, rule preview | rules/*, dao/ClockDao, dao/NotificationDao, dao/RuleDao, service/ObligationService, util/Times |
| 4 · Accountability | Audit chain and anchors, evidence hashing, passwords and lockout, triggers and guard procedures, grants, tests, website proof | security/*, ui/Html, dao/AuditDao, dao/EvidenceDao, dao/LoginDao, db/schema.sql triggers, db/grants.sql, test/*, docs/ (site) |

Each member should be able to explain every line of their part and the schema as a whole.
