# Pathivu: status, analysis and loopholes

19 Oct 2026 (second pass: 6 more loopholes closed, website added)

## 1. Status

| Part | State |
|---|---|
| Database: 21 tables, 28 triggers, 2 views, restricted app account | Done, loaded on MySQL 8.0 |
| Java core: model, rules, mappers, workflow, security | Done |
| JDBC layer: 13 DAOs, transactions, hash-chained audit with anchors | Done |
| Services: report, harms, evidence, amendments, mappings, workflow, clocks, notices, report export | Done |
| Swing UI: login, register, deadlines, audit trail, report dialog, incident screen with 6 tabs | Done, screenshot-tested |
| Tests | 66 of 66 pass (19 logic, 16 service, 31 attacks), about 10 s per run, stable over repeated runs |
| Demo data | 4 incidents, one each: running, overdue, met late, closed |
| Website | shyamvasansathiskumar-ux.github.io/pathivu: the engine ported to JavaScript, 240/240 cases matching the Java output, real MySQL refusals, a tamper lab on the real audit chain |

Size: about 3,600 lines of Java across 82 files, 790 lines of SQL and 750 lines of tests. The only dependency is the MySQL JDBC driver.

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

## 8. Four-member split (matches the packages)

| Member | Owns | Files |
|---|---|---|
| 1 · Incident core | Incident hierarchy, IncidentDao, workflow, register and report screens | model/Incident*, dao/IncidentDao, workflow/*, ui/ReportIncidentDialog, ui/MainFrame |
| 2 · Frameworks | Mappers, taxonomy versions, mapping review | mapping/*, dao/TaxonomyDao, dao/MappingDao, db/seed.sql (taxonomy part) |
| 3 · Obligations | Rules, clocks, notices, IST handling, rule preview | rules/*, dao/ClockDao, dao/NotificationDao, dao/RuleDao, service/ObligationService, util/Times |
| 4 · Accountability | Audit chain and anchors, evidence hashing, passwords and lockout, triggers, grants, tests, website proof | security/*, dao/AuditDao, dao/EvidenceDao, dao/LoginDao, db/schema.sql triggers, db/grants.sql, test/*, docs/ (site) |

Each member should be able to explain every line of their part and the schema as a whole.
