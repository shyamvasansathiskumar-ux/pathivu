# Pathivu (பதிவு)

**When an AI system breaks something, a legal clock may already be running.** Pathivu is an AI incident register for Indian organisations, built with Java 21, MySQL 8 and plain JDBC (Swing UI).

**Live site, with the rule engine running in your browser:** https://shyamvasansathiskumar-ux.github.io/pathivu/

When an AI system causes harm (an agent deletes a database, a chatbot leaks phone numbers, a model makes biased decisions), Pathivu:

1. **records** the incident: three separate times (occurred, detected, aware), harms, and evidence fingerprinted with SHA-256,
2. **maps** it to OWASP Agentic 2026, OWASP LLM 2026, MITRE ATLAS and NIST AI RMF (code suggests, a reviewer confirms),
3. **runs India's reporting clocks** from the moment the organisation became aware: CERT-In 6 hours today, and DPDP Rule 7 once it is in force on 13 May 2027,
4. **keeps a tamper-evident audit trail** that MySQL itself checks, plus anchors that catch even a full rewrite by root.

The important rules are enforced twice: in Java, so the user gets a clear message, and in MySQL (constraints, 28 triggers, a restricted account), so a bug, a SQL injection or a manual SQL session still cannot break them.

## What you need

- JDK 21 (`java -version`)
- MySQL 8.0 server
- MySQL Connector/J jar (`mysql-connector-j-8.x.x.jar` from dev.mysql.com/downloads/connector/j, "Platform Independent"). Put it in `lib/`.

## Set up (once)

```bash
# 1. database, demo data, and the restricted app account
mysql -u root -p < db/schema.sql
mysql -u root -p < db/seed.sql
mysql -u root -p < db/grants.sql

# 2. settings: copy db.properties.example to db.properties, then edit
#    db.*    = the app account (pathivu_app), leave as is
#    admin.* = root and YOUR root password (only Setup uses it)

# 3. build
scripts/build.sh            # Windows: see "Windows" below

# 4. create the four demo users, then load four demo incidents
java -cp "out:lib/*" pathivu.tools.Setup
java -cp "out:lib/*" pathivu.tools.DemoSeeder
```

On Linux with MySQL root on socket login, `scripts/reset-db.sh` does steps 1, 3 and 4 in one go.

## Run

```bash
scripts/run.sh              # or: java -cp "out:lib/*" pathivu.ui.App
```

Demo logins (password `Pathivu@2026`):

| User  | Role     | Can do |
|-------|----------|--------|
| meera | REPORTER | report incidents, record harms, attach evidence, draft notices |
| arjun | REVIEWER | everything above + move workflow, confirm mappings, rule out clocks, record extensions, approve and send notices |
| kavya | REVIEWER | second reviewer, for four-eyes approval |
| ravi  | AUDITOR  | read everything, verify the audit chain, check anchors |

Five wrong passwords in 15 minutes lock a username for 15 minutes.

## Test

```bash
scripts/test.sh
```

Resets the database and runs **66 tests**: 19 on pure logic, 16 on services against MySQL, and 31 attacks where the app's own database account tries to break the rules with raw SQL (plus root tampering with the audit trail). Every attack must be refused by MySQL itself.

## Windows

Use `;` instead of `:` in the classpath:

```
javac -encoding UTF-8 -d out -cp "lib/*" (list all .java files, or use an IDE)
java -cp "out;lib/*" pathivu.ui.App
```

Easiest is IntelliJ: open the folder, mark `src` and `test` as source roots, add the jar in `lib/` as a library, and run `pathivu.ui.App`. A `pom.xml` is included if you prefer Maven.

## Demo script (5 minutes)

1. Log in as **arjun**. The register shows 4 incidents; the header shows 1 overdue and 1 running.
2. **Deadlines** tab: PTV-0002 is OVERDUE (leaked phone numbers, 9 hours ago), PTV-0001 has about 4 hours left, PTV-0004 was met late, PTV-0003 was ruled out with a written reason.
3. Open **PTV-0001** (agent dropped the database) → Mappings: confirm ASI03, LLM03 and ATLAS NONE. Explain why NONE is an explicit finding.
4. Try **Move to ASSESSED**: it lists exactly what is missing.
5. Overview → **What if?** → `2027-06-01 10:00`. The three DPDP Rule 7 clocks appear, because the rule would be in force then. Nothing is saved.
6. Deadlines & notices → **Draft notice** as arjun, then try **Approve** as arjun: refused (four eyes). Log in as kavya to approve.
7. **Audit trail** → **Save anchor**, then **Verify chain**. Then show `scripts/test.sh`, where root rewrites history and re-hashes everything: `verify()` is fooled, the anchor is not.

## Project layout

```
db/        schema.sql (21 tables, CHECKs, 28 triggers, 2 views), seed.sql, grants.sql
src/pathivu/
  model/     Incident (abstract) + 3 subclasses, records for everything else
  rules/     ObligationRule (abstract) + CyberIncidentRule, PersonalDataRule, RuleEngine
  mapping/   FrameworkMapper (interface) + 4 mappers, Signals, MapperRegistry
  workflow/  IncidentWorkflow state machine
  security/  PBKDF2 passwords, SHA-256, role guard
  dao/       JDBC: Db (transactions), one DAO per table group, AuditDao (hash chain + anchors), LoginDao
  service/   use cases; every write = one transaction + audit event
  ui/        Swing screens
  tools/     Setup (users), DemoSeeder
test/      TestRunner (no JUnit needed), UiSmoke (screenshots), ExportVectors, SampleReport
docs/      the website (GitHub Pages): a JavaScript port of the engine, checked against the Java output
notes/     ANALYSIS.md, MASTER-PROMPT.md, a sample report
```

## The website

`docs/` is the project site. `docs/pathivu-core.js` is a line-by-line port of `rules/`, `mapping/` and `workflow/`. `docs/vectors.json` is produced by the Java code (`pathivu.tools.ExportVectors`), and the page checks that the port gives the same answer on all 240 cases. `docs/refusals.jsonl` is the real output of the attack tests (`-Dpathivu.log=`), and `docs/audit-sample.json` is the real audit chain of the demo database. Shared styles and libraries (`base.css`, `motion.js`, `world.js`, GSAP, Three.js, Lenis, fonts) come from the design system used across my project sites.

Regenerate the data after changing the engine:

```bash
java -cp "out:lib/*" pathivu.tools.ExportVectors docs/vectors.json
scripts/reset-db.sh && rm -f docs/refusals.jsonl && java -Dpathivu.log=docs/refusals.jsonl -cp "out:lib/*" pathivu.TestRunner
```

## Credits

Shyam Kumar, CSE, Rajalakshmi Engineering College, 2026. OOP + DBMS semester project. Built with Claude as a pair programmer. MIT licence.

Legal points are research summaries, not legal advice.
