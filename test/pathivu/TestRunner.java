package pathivu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import pathivu.dao.*;
import pathivu.mapping.MapperRegistry;
import pathivu.model.*;
import pathivu.rules.RuleEngine;
import pathivu.security.*;
import pathivu.service.*;
import pathivu.workflow.*;

/**
 * Plain-Java test runner (no JUnit needed).
 *
 * Part 1 tests the logic with no database.
 * Part 2 tests against MySQL: the services, and then ATTACKS. Each attack
 * connects as the app's own account and tries to break a rule with raw SQL,
 * the way a bug or a SQL-injection would. Every one must be refused by the
 * database itself.
 *
 * Needs a fresh database: run scripts/reset-db.sh first.
 */
public class TestRunner {

    static int passed = 0, failed = 0;
    static final List<String> failures = new ArrayList<>();

    interface Body { void run() throws Exception; }

    static void test(String name, Body b) {
        current = name;
        try { b.run(); passed++; System.out.println("  PASS  " + name); }
        catch (Throwable t) { failed++; failures.add(name + " -> " + t); System.out.println("  FAIL  " + name + "\n        " + t); }
    }

    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    /** The SQL must fail, and the error message must contain the hint. */
    static void refused(Connection c, String sql, String hint) { refusedAs(c, sql, hint, sql); }

    /** Same, but the log (and so the website) shows `shown`, e.g. with the SET that came first. */
    static void refusedAs(Connection c, String sql, String hint, String shown) {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
            throw new AssertionError("Database ACCEPTED: " + sql);
        } catch (SQLException e) {
            String m = String.valueOf(e.getMessage()).toLowerCase();
            check(m.contains(hint.toLowerCase()), "refused, but for another reason: " + e.getMessage());
            log(shown, e);
        }
    }

    /** With -Dpathivu.log=file, every refusal is written down (the website shows these exact messages). */
    static void log(String sql, SQLException e) {
        String f = System.getProperty("pathivu.log");
        if (f == null) return;
        String msg = String.valueOf(e.getMessage()).replaceAll("^\\([^)]*\\)\\s*", "").replaceAll("^\\(conn=\\d+\\)\\s*", "");
        String line = "{\"test\":" + js(current) + ",\"sql\":" + js(sql) + ",\"code\":" + e.getErrorCode()
                    + ",\"state\":" + js(String.valueOf(e.getSQLState())) + ",\"message\":" + js(msg) + "}\n";
        try { Files.writeString(Path.of(f), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
        catch (java.io.IOException io) { throw new RuntimeException(io); }
    }

    static String current = "";
    static String js(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""; }

    static <T extends Throwable> T throwsA(Class<T> type, Body b) {
        try { b.run(); } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    static Instant utc(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant();
    }

    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("\nPart 1: logic (no database)");
        unitTests();
        Path props = Path.of("db.properties");
        if (Files.exists(props)) {
            System.out.println("\nPart 2: services against MySQL");
            Db app = Db.fromFile(props, "db.");
            Db admin = Db.fromFile(props, "admin.");
            serviceTests(app);
            lockoutTest(app);
            pass3ServiceTests(app);
            System.out.println("\nPart 3: attacks (raw SQL as the app account)");
            attackTests(app, admin);
        } else {
            System.out.println("\n(no db.properties: database tests skipped)");
        }
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        failures.forEach(f -> System.out.println("  - " + f));
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Part 1

    static final RuleVersion CERTIN = new RuleVersion(1, "CERTIN_6H", "CERT_IN", "Report", "CERT-In",
        utc(2022, 6, 27, 18, 30), 360, RuleTrigger.CYBER_INCIDENT, "c", false);
    static final RuleVersion DPDP72 = new RuleVersion(4, "DPDP_R7_BOARD_72H", "DPDP", "Board", "Board",
        utc(2027, 5, 12, 18, 30), 4320, RuleTrigger.PERSONAL_DATA, "c", true);
    static final RuleVersion DPDP_NOW = new RuleVersion(2, "DPDP_R7_PRINCIPALS", "DPDP", "Tell", "People",
        utc(2027, 5, 12, 18, 30), null, RuleTrigger.PERSONAL_DATA, "c", false);

    static AgentIncident agent(Instant aware, Adversary adv, boolean cred, boolean confirm) {
        return new AgentIncident("Agent deleted db", "The agent dropped a table", 1, 1,
            aware.minusSeconds(600), aware.minusSeconds(300), aware, Severity.HIGH, adv,
            "Bot", "fix deploy", "dropped the production table", cred, confirm);
    }

    static void unitTests() {
        Instant aware = utc(2026, 10, 19, 4, 0);

        test("times must be in order occurred <= detected <= aware", () ->
            throwsA(IllegalArgumentException.class, () -> new AgentIncident("t", "d", 1, 1,
                aware, aware.minusSeconds(60), aware, Severity.LOW, Adversary.NONE, "a", "b", "c", false, false)));

        test("CERT-In: 6-hour deadline is aware_at + 360 minutes", () -> {
            var r = RuleEngine.from(CERTIN);
            check(r.dueAt(aware).equals(aware.plus(Duration.ofHours(6))), "wrong due time");
        });

        test("CERT-In: UNSURE keeps the clock, NO does not", () -> {
            var e = new RuleEngine(List.of(CERTIN));
            var i = agent(aware, Adversary.NONE, false, false);
            check(e.applicable(i, List.of()).size() == 1, "UNSURE should apply");
            i.setCyberIncident(TriState.NO);
            check(e.applicable(i, List.of()).isEmpty(), "NO should not apply");
        });

        test("CERT-In: an incident from before 28 Jun 2022 IST gets no clock", () -> {
            var e = new RuleEngine(List.of(CERTIN));
            check(e.applicable(agent(utc(2022, 6, 27, 18, 29), Adversary.NONE, false, false), List.of()).isEmpty(), "too early");
            check(e.applicable(agent(utc(2022, 6, 27, 18, 30), Adversary.NONE, false, false), List.of()).size() == 1, "exact start");
        });

        test("DPDP: not in force in Oct 2026, in force for awareness after 13 May 2027 IST", () -> {
            var e = new RuleEngine(List.of(DPDP72, DPDP_NOW));
            var harm = List.of(Harm.draft(HarmCategory.DATA_EXPOSURE, "phones shown", 5, TriState.YES));
            check(e.applicable(agent(aware, Adversary.NONE, false, false), harm).isEmpty(), "should not apply yet");
            var later = agent(utc(2027, 6, 1, 0, 0), Adversary.NONE, false, false);
            check(e.applicable(later, harm).size() == 2, "both DPDP rules should apply");
        });

        test("DPDP: 'without delay' means due = aware; 72h means +4320 minutes", () -> {
            Instant t = utc(2027, 6, 1, 0, 0);
            check(RuleEngine.from(DPDP_NOW).dueAt(t).equals(t), "without delay");
            check(RuleEngine.from(DPDP72).dueAt(t).equals(t.plus(Duration.ofHours(72))), "72h");
        });

        test("DPDP: a harm with personal data NO does not trigger, UNSURE does", () -> {
            var e = new RuleEngine(List.of(DPDP72));
            var i = agent(utc(2027, 6, 1, 0, 0), Adversary.NONE, false, false);
            check(e.applicable(i, List.of(Harm.draft(HarmCategory.FINANCIAL, "x", 1, TriState.NO))).isEmpty(), "NO");
            check(e.applicable(i, List.of(Harm.draft(HarmCategory.FINANCIAL, "x", 1, TriState.UNSURE))).size() == 1, "UNSURE");
        });

        test("Mapper: no adversary -> explicit ATLAS NONE", () -> {
            var s = new MapperRegistry().suggestAll(agent(aware, Adversary.NONE, true, false), List.of());
            check(s.stream().anyMatch(m -> m.framework() == Framework.MITRE_ATLAS && m.code().equals("NONE")), "NONE missing");
        });

        test("Mapper: wide credentials -> ASI03 + LLM03; no confirmation + delete -> ASI02", () -> {
            var codes = new MapperRegistry().suggestAll(agent(aware, Adversary.NONE, true, false), List.of())
                                            .stream().map(MappingSuggestion::code).toList();
            check(codes.containsAll(List.of("ASI03", "LLM03", "ASI02")), "got " + codes);
        });

        test("Mapper: prompt-injection tag + suspected attacker -> LLM01, ASI01, AML.T0051", () -> {
            var i = agent(aware, Adversary.SUSPECTED, false, true);
            i.addTag("prompt-injection");
            var codes = new MapperRegistry().suggestAll(i, List.of()).stream().map(MappingSuggestion::code).toList();
            check(codes.containsAll(List.of("LLM01", "ASI01", "AML.T0051")) && !codes.contains("NONE"), "got " + codes);
        });

        test("Mapping rationale shorter than 15 characters is refused", () ->
            throwsA(IllegalArgumentException.class, () -> new MappingSuggestion(Framework.OWASP_LLM, "LLM01", "short", Confidence.LOW)));

        test("Workflow: cannot skip from REPORTED to CLOSED", () ->
            check(!IncidentWorkflow.isLegal(IncidentStatus.REPORTED, IncidentStatus.CLOSED), "skip allowed"));

        test("Workflow: ASSESSED lists every missing thing", () -> {
            var i = agent(aware, Adversary.NONE, false, false);
            i.changeStatus(IncidentStatus.TRIAGED);
            var rev = new AppUser(9, "r", "R", Role.REVIEWER);
            var b = IncidentWorkflow.blockers(i, IncidentStatus.ASSESSED, rev, new WorkflowFacts(0, 0, 0, 0, 0, 1, 1));
            check(b.size() == 4, "expected 4 blockers, got " + b);
        });

        test("Workflow: reporter cannot close their own incident", () -> {
            var i = agent(aware, Adversary.NONE, false, false);
            for (var s : List.of(IncidentStatus.TRIAGED, IncidentStatus.ASSESSED, IncidentStatus.NOTICES_REVIEWED)) i.changeStatus(s);
            var reporterWhoIsReviewer = new AppUser(1, "k", "K", Role.REVIEWER);
            var b = IncidentWorkflow.blockers(i, IncidentStatus.CLOSED, reporterWhoIsReviewer, new WorkflowFacts(1, 1, 1, 0, 0, 0, 0));
            check(b.size() == 1 && b.get(0).contains("reported"), "got " + b);
        });

        test("Clock live status: OVERDUE and MET_LATE are computed, not stored", () -> {
            var k = new ObligationClock(1, 1, CERTIN, aware, aware.plusSeconds(3600), ClockState.RUNNING, null, null, null, null);
            check(k.liveStatus(aware.plusSeconds(60)) == LiveStatus.RUNNING, "running");
            check(k.liveStatus(aware.plusSeconds(7200)) == LiveStatus.OVERDUE, "overdue");
            var late = new ObligationClock(1, 1, CERTIN, aware, aware.plusSeconds(3600), ClockState.MET, aware.plusSeconds(4000), null, null, null);
            check(late.liveStatus(aware) == LiveStatus.MET_LATE, "met late");
        });

        test("Extension: the original due stays, the later one counts", () -> {
            var k = new ObligationClock(1, 1, DPDP72, aware, aware.plusSeconds(3600), ClockState.MET, aware.plusSeconds(5000), null, null,
                                        aware.plusSeconds(7200));
            check(k.dueAt().equals(aware.plusSeconds(3600)), "original changed");
            check(k.liveStatus(aware) == LiveStatus.MET, "met within the extension should not be late");
        });

        test("aware more than an hour after detection needs a written reason", () -> {
            var i = new AgentIncident("t", "d", 1, 1, aware.minusSeconds(9000), aware.minusSeconds(7200), aware,
                                      Severity.LOW, Adversary.NONE, "a", "b", "c", false, false);
            throwsA(IllegalArgumentException.class, i::validateForSave);
            i.setAwareGapReason("Detected by a contractor who told us only at shift change");
            i.validateForSave();
        });

        test("PBKDF2: right password verifies, wrong does not, salts differ", () -> {
            byte[] s1 = PasswordHasher.newSalt(), s2 = PasswordHasher.newSalt();
            String h1 = PasswordHasher.hash("secret".toCharArray(), s1);
            check(PasswordHasher.verify("secret".toCharArray(), Hex.of(s1), h1), "right password failed");
            check(!PasswordHasher.verify("Secret".toCharArray(), Hex.of(s1), h1), "wrong password passed");
            check(!h1.equals(PasswordHasher.hash("secret".toCharArray(), s2)), "same hash with different salt");
        });

        test("Audit hash: same input same hash, any change gives a different hash", () -> {
            Instant t = Instant.parse("2026-10-19T04:00:00.123Z");
            String a = AuditDao.computeHash("0".repeat(64), 1, 2, "X", "d", t);
            check(a.equals(AuditDao.computeHash("0".repeat(64), 1, 2, "X", "d", t)), "not deterministic");
            check(!a.equals(AuditDao.computeHash("0".repeat(64), 1, 2, "X", "e", t)), "details change not detected");
        });

        // ---- pass 3
        String evil = "<html><img src='http://attacker.example/pixel.png'><b>All clear</b>";
        test("Swing would render a typed title as HTML (the risk is real)", () -> {
            javax.swing.JLabel l = new javax.swing.JLabel(evil);
            check(l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null, "plain JLabel did not parse HTML");
        });
        test("labels that show user text keep it as plain text", () -> {
            javax.swing.JLabel l = pathivu.ui.Html.noHtml(new javax.swing.JLabel());
            l.setText(evil);
            check(l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) == null, "HTML was rendered");
        });
        test("table cells show user text as plain text", () -> {
            var r = pathivu.ui.Html.plainRenderer();
            var comp = (javax.swing.JComponent) r.getTableCellRendererComponent(new javax.swing.JTable(1, 1), evil, false, false, 0, 0);
            check(comp.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) == null, "HTML was rendered in a cell");
        });
        test("user text inside our own HTML is escaped", () -> {
            check(pathivu.ui.Html.esc("<b>A & 'B'</b>").equals("&lt;b&gt;A &amp; &#39;B&#39;&lt;/b&gt;"), pathivu.ui.Html.esc("<b>A & 'B'</b>"));
        });
    }

    // ------------------------------------------------------------------ Part 2

    static AppUser meera, arjun, kavya, ravi;

    static void serviceTests(Db app) throws Exception {
        AuthService auth = new AuthService(app);
        IncidentService inc = new IncidentService(app);
        ObligationService obl = new ObligationService(app);

        test("login: right password works, wrong password gives one generic message", () -> {
            meera = auth.login("meera", "Pathivu@2026".toCharArray());
            arjun = auth.login("arjun", "Pathivu@2026".toCharArray());
            kavya = auth.login("kavya", "Pathivu@2026".toCharArray());
            ravi = auth.login("ravi", "Pathivu@2026".toCharArray());
            var e = throwsA(AccessDenied.class, () -> auth.login("meera", "wrong".toCharArray()));
            var e2 = throwsA(AccessDenied.class, () -> auth.login("nobody", "wrong".toCharArray()));
            check(e.getMessage().equals(e2.getMessage()), "messages differ, which reveals valid usernames");
        });

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        int[] id = new int[1];

        test("report: incident, subtype row, ref code, CERT-In clock and suggestions in one transaction", () -> {
            var a = agent(now.minusSeconds(3600), Adversary.NONE, true, false);
            var mine = new AgentIncident(a.getTitle(), a.getDescription(), 1, meera.id(), a.getOccurredAt(),
                a.getDetectedAt(), a.getAwareAt(), Severity.HIGH, Adversary.NONE, "Bot", "fix", "dropped the table", true, false);
            id[0] = inc.report(meera, mine);
            var f = inc.load(id[0]);
            check(f.incident() instanceof AgentIncident, "subtype lost");
            check(f.incident().getRefCode().startsWith("PTV-2026-"), "ref " + f.incident().getRefCode());
            check(f.clocks().size() == 1 && f.clocks().get(0).rule().code().equals("CERTIN_6H"), "clock missing");
            check(f.clocks().get(0).dueAt().equals(mine.getAwareAt().plus(Duration.ofHours(6))), "due wrong");
            check(f.mappings().size() >= 4, "suggestions missing");
        });

        test("a failed step rolls back the whole report (no half-saved incident)", () -> {
            int before = inc.register().size();
            var bad = new AgentIncident("x", "y", 999, meera.id(), now.minusSeconds(60), now.minusSeconds(60),
                now.minusSeconds(60), Severity.LOW, Adversary.NONE, "a", "b", "c", false, false);
            throwsA(DataException.class, () -> inc.report(meera, bad));   // ai_system 999 does not exist
            check(inc.register().size() == before, "a partial incident was saved");
        });

        test("auditor cannot report; reporter cannot report as someone else", () -> {
            var asArjun = new AgentIncident("t", "d", 1, arjun.id(), now.minusSeconds(60), now.minusSeconds(60),
                now.minusSeconds(60), Severity.LOW, Adversary.NONE, "a", "b", "c", false, false);
            throwsA(AccessDenied.class, () -> inc.report(ravi, asArjun));
            throwsA(AccessDenied.class, () -> inc.report(meera, asArjun));   // says arjun reported it, but meera is logged in
        });

        test("workflow refuses ASSESSED with the reasons listed", () -> {
            inc.move(arjun, id[0], IncidentStatus.TRIAGED, null);
            var e = throwsA(WorkflowException.class, () -> inc.move(arjun, id[0], IncidentStatus.ASSESSED, null));
            check(e.blockers().size() >= 3, "blockers " + e.blockers());
        });

        test("reporter cannot confirm mappings or rule out clocks", () -> {
            var f = inc.load(id[0]);
            throwsA(AccessDenied.class, () -> inc.reviewMapping(meera, id[0], f.mappings().get(0).node().id(), true));
            throwsA(AccessDenied.class, () -> obl.ruleOut(meera, f.clocks().get(0).id(), "not relevant at all, really not"));
        });

        test("four eyes: the drafter of a notice cannot approve it", () -> {
            int clock = inc.load(id[0]).clocks().get(0).id();
            int n = obl.draftNotice(arjun, clock, "Report to CERT-In: the agent dropped the production table.");
            throwsA(AccessDenied.class, () -> obl.approveNotice(arjun, n));
            obl.approveNotice(kavya, n);
        });

        test("sending a notice marks the clock MET with the database's time", () -> {
            var f = inc.load(id[0]);
            int n = f.notices().get(0).id();
            obl.markSent(kavya, n);
            var k = inc.load(id[0]).clocks().get(0);
            check(k.state() == ClockState.MET && k.liveStatus(Instant.now()) == LiveStatus.MET, "state " + k.state());
            check(Duration.between(k.metAt(), Instant.now()).abs().toMinutes() < 2, "met_at not near now");
        });

        test("aware_at correction is an annotation; the clock does not move", () -> {
            Instant before = inc.load(id[0]).clocks().get(0).dueAt();
            inc.amend(meera, id[0], "aware_at", "2026-10-19 08:00 IST", "The on-call lead saw the alert 20 minutes earlier");
            var f = inc.load(id[0]);
            check(f.clocks().get(0).dueAt().equals(before), "clock moved");
            check(f.amendments().stream().anyMatch(a -> a.field().equals("aware_at")), "no annotation");
        });

        test("evidence: fingerprint detects a changed file", () -> {
            Path tmp = Files.createTempFile("ev", ".log");
            Files.writeString(tmp, "original log");
            inc.addEvidence(meera, id[0], "Tool / API call log", tmp);
            var e = inc.load(id[0]).evidence().get(0);
            check(inc.evidenceMatches(e, tmp), "same file should match");
            Files.writeString(tmp, "edited log");
            check(!inc.evidenceMatches(e, tmp), "edited file should not match");
        });

        test("audit trail verifies as intact", () -> {
            var r = new AuditService(app).verify();
            check(r.intact(), r.message());
        });

        test("a regulator extension is refused for CERT-In (not extendable)", () -> {
            int clock = inc.load(1).clocks().get(0).id();
            var e = throwsA(DataException.class, () -> obl.recordExtension(arjun, clock, Instant.now().plusSeconds(86400),
                "BOARD/2026/17", "The Board allowed more time for the detailed report"));
            check(e.getMessage().contains("does not allow"), e.getMessage());
        });

        test("report: a late 'aware' needs a reason; a follow-up links to a closed incident", () -> {
            var late = new DataIncident("Follow-up on rural triage", "Second batch found during audit", 3, meera.id(),
                now.minusSeconds(14400), now.minusSeconds(10800), now.minusSeconds(3600), Severity.LOW, Adversary.NONE,
                "sample ids", 4, TriState.NO);
            throwsA(IllegalArgumentException.class, () -> inc.report(meera, late));
            var ok = new DataIncident("Follow-up on rural triage", "Second batch found during audit", 3, meera.id(),
                now.minusSeconds(14400), now.minusSeconds(10800), now.minusSeconds(3600), Severity.LOW, Adversary.NONE,
                "sample ids", 4, TriState.NO);
            ok.setAwareGapReason("Lab manager was on leave; escalation reached the DPO two hours later");
            ok.setLinkedTo(3);
            int nid = inc.report(meera, ok);
            var f = inc.load(nid).incident();
            check(Integer.valueOf(3).equals(f.getLinkedTo()) && f.getAwareGapReason() != null, "link or reason lost");
        });

        test("an anchor saved now verifies later", () -> {
            var as = new AuditService(app);
            anchor = as.anchor();
            inc.addTag(meera, id[0], "after-anchor");
            var r = as.verifyAnchor(anchor);
            check(r.intact(), r.message());
        });

        test("8 people writing at the same moment do not fork the audit chain", () -> {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                final int n = t;
                fs.add(pool.submit(() -> { inc.addTag(meera, id[0], "load-" + n); return null; }));
            }
            for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
            pool.shutdown();
            var r = new AuditService(app).verify();
            check(r.intact(), r.message());
        });
    }

    static String anchor;

    static void lockoutTest(Db app) {
        AuthService auth = new AuthService(app);
        test("5 wrong passwords lock the account, even against the right password", () -> {
            for (int n = 0; n < 5; n++) throwsA(AccessDenied.class, () -> auth.login("ravi", "guess".toCharArray()));
            var e = throwsA(AccessDenied.class, () -> auth.login("ravi", "Pathivu@2026".toCharArray()));
            check(e.getMessage().contains("locked"), e.getMessage());
            var u = throwsA(AccessDenied.class, () -> { for (int n = 0; n < 6; n++) auth.login("nobody-" + 1, "x".toCharArray()); });
            check(u.getMessage().contains("locked") || u.getMessage().contains("wrong"), u.getMessage());
        });
    }

    static void pass3ServiceTests(Db app) {
        IncidentService inc = new IncidentService(app);
        AuthService auth = new AuthService(app);
        // ---- pass 4
        test("a reviewer cannot confirm a mapping they proposed by hand; a colleague can", () -> {
            int node;
            try (Connection c = app.open(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MIN(node_id) FROM taxonomy_node WHERE node_id NOT IN "
                                              + "(SELECT node_id FROM incident_mapping WHERE incident_id = 2)")) { rs.next(); node = rs.getInt(1); }
            inc.proposeMapping(arjun, 2, node, "Arjun thinks this category fits the leak", Confidence.MEDIUM);
            var e = throwsA(DataException.class, () -> inc.reviewMapping(arjun, 2, node, true));
            check(e.getMessage().contains("proposed themselves"), e.getMessage());
            inc.reviewMapping(kavya, 2, node, true);
        });
        test("sending an incident back needs a reason, in the database too", () -> {
            var a = agent(Instant.now().minusSeconds(1800), Adversary.NONE, true, false);
            var mine = new AgentIncident(a.getTitle(), a.getDescription(), 1, meera.id(), a.getOccurredAt(), a.getDetectedAt(),
                a.getAwareAt(), Severity.HIGH, Adversary.NONE, "Bot", "fix", "dropped the table", true, false);
            mine.setCyberIncident(TriState.YES);
            int id = inc.report(meera, mine);
            inc.addHarm(meera, id, Harm.draft(HarmCategory.DATA_LOSS, "bookings table lost", 120, TriState.NO));
            Path tmp = Files.createTempFile("ev", ".log"); Files.writeString(tmp, "DROP TABLE bookings;");
            inc.addEvidence(meera, id, "Agent transcript", tmp);
            var m = inc.load(id).mappings().get(0);
            inc.reviewMapping(arjun, id, m.node().id(), true);
            inc.move(arjun, id, IncidentStatus.TRIAGED, null);
            inc.move(arjun, id, IncidentStatus.ASSESSED, null);
            try (Connection c = app.open()) {
                new AuditDao().append(c, id, arjun.id(), "STATUS_CHANGED", "ASSESSED -> TRIAGED");
                refused(c, "UPDATE incident SET status = 'TRIAGED' WHERE incident_id = " + id, "needs a reason");
            }
            inc.move(arjun, id, IncidentStatus.TRIAGED, "the harm count was wrong, recount first");
            check(inc.load(id).incident().getStatus() == IncidentStatus.TRIAGED, "not sent back");
        });
        test("a long description can be amended (the old value is over 255 characters)", () -> {
            String longer = "The agent ran a cleanup job with production credentials. ".repeat(12).trim();
            inc.amend(arjun, 1, "description", longer, "full account from the on-call engineer");
            String back = "Back to the short version: " + "x".repeat(300);
            inc.amend(arjun, 1, "description", back, "shortened after review, long text kept in amendment");
            check(inc.load(1).incident().getDescription().equals(back), "description not changed");
            check(inc.load(1).amendments().stream().anyMatch(a -> longer.equals(a.oldValue()) || longer.equals(a.newValue())),
                  "the long value was not kept in the amendment history");
        });
        test("reporting more than 7 days after awareness needs a reason", () -> {
            Instant old = Instant.now().minusSeconds(10 * 86400L);
            var late = agent(old, Adversary.NONE, false, true);
            var e = throwsA(IllegalArgumentException.class, () -> inc.report(meera, late));
            check(e.getMessage().contains("days ago"), e.getMessage());
            var late2 = agent(old, Adversary.NONE, false, true);
            late2.setLateReportReason("found during the quarterly log review, nobody raised it at the time");
            int lid = inc.report(meera, late2);
            check(inc.load(lid).incident().getLateReportReason() != null, "reason lost");
        });
        test("the adversary answer can change, with a reason", () -> {
            inc.amend(arjun, 1, "adversary", "SUSPECTED", "new logs show an outside prompt in the ticket");
            check(inc.load(1).incident().getAdversary() == Adversary.SUSPECTED, "adversary not changed");
        });
        test("an unknown username takes as long to refuse as a wrong password", () -> {
            long t0 = System.nanoTime();
            for (int n = 0; n < 2; n++) throwsA(AccessDenied.class, () -> auth.login("kavya", "wrong".toCharArray()));
            long wrong = System.nanoTime() - t0;
            t0 = System.nanoTime();
            for (int n = 0; n < 2; n++) throwsA(AccessDenied.class, () -> auth.login("ghost-user", "wrong".toCharArray()));
            long unknown = System.nanoTime() - t0;
            check(unknown > wrong / 2, "unknown user answered in " + unknown / 1_000_000 + " ms vs " + wrong / 1_000_000 + " ms");
        });
    }

    /** A second app connection that has changed one of its own session settings. */
    static void sessionRefused(Db app, String set, String sql, String hint) throws Exception {
        try (Connection d = app.open(); Statement st = d.createStatement()) {
            st.execute(set);
            refusedAs(d, sql, hint, set + "; " + sql);
        }
    }

    // ------------------------------------------------------------------ Part 3

    static void attackTests(Db app, Db admin) throws Exception {
        try (Connection c = app.open()) {
            test("cannot change aware_at", () -> refused(c, "UPDATE incident SET aware_at = aware_at - INTERVAL 1 HOUR WHERE incident_id = 1", "write-once"));
            test("cannot report with aware_at in the future", () -> refused(c,
                "INSERT INTO incident (kind,title,description,ai_system_id,reported_by,occurred_at,detected_at,aware_at,severity,adversary) "
              + "VALUES ('AGENT','t','d',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP()+INTERVAL 1 DAY,UTC_TIMESTAMP()+INTERVAL 1 DAY,'LOW','NONE')", "future"));
            test("cannot skip workflow steps", () -> refused(c, "UPDATE incident SET status = 'CLOSED' WHERE incident_id = 2", "illegal status"));
            test("cannot change a closed incident", () -> refused(c, "UPDATE incident SET title = 'x' WHERE incident_id = 3", "closed"));
            test("cannot add a harm to a closed incident", () -> refused(c,
                "INSERT INTO harm (incident_id,category,description,personal_data,recorded_by) VALUES (3,'OTHER','x','NO',1)", "closed"));
            test("cannot change severity without an amendment", () -> refused(c, "UPDATE incident SET severity = 'LOW' WHERE incident_id = 1", "amendment"));
            test("cannot delete an incident (no DELETE permission)", () -> refused(c, "DELETE FROM incident WHERE incident_id = 2", "denied"));
            test("cannot delete audit events", () -> refused(c, "DELETE FROM audit_event", "denied"));
            test("cannot edit audit events", () -> refused(c, "UPDATE audit_event SET details = 'nothing happened'", "denied"));
            test("cannot create a user (no INSERT on app_user)", () -> refused(c,
                "INSERT INTO app_user (username,full_name,role,password_hash,salt) VALUES ('evil','E','REVIEWER',REPEAT('a',64),REPEAT('b',32))", "denied"));
            test("cannot drop the triggers", () -> refused(c, "DROP TRIGGER trg_clock_bu", "denied"));
            test("cannot move a clock's deadline", () -> refused(c, "UPDATE obligation_clock SET due_at = due_at + INTERVAL 1 DAY WHERE clock_id = 2", "moved"));
            test("cannot start a clock with a made-up deadline", () -> refused(c,
                "INSERT INTO obligation_clock (incident_id, rule_id, started_at, due_at) "
              + "SELECT incident_id, 1, aware_at, aware_at + INTERVAL 30 DAY FROM incident WHERE incident_id = 2", "rule window"));
            test("cannot start a DPDP clock before Rule 7 is in force", () -> refused(c,
                "INSERT INTO obligation_clock (incident_id, rule_id, started_at, due_at) "
              + "SELECT incident_id, 4, aware_at, aware_at + INTERVAL 4320 MINUTE FROM incident WHERE incident_id = 2", "not in force"));
            test("cannot mark a clock met without a sent notice", () -> refused(c,
                "UPDATE obligation_clock SET status='MET', met_at=UTC_TIMESTAMP() WHERE clock_id = 2", "sent notice"));
            test("cannot reopen a met clock", () -> refused(c, "UPDATE obligation_clock SET status='RUNNING', met_at=NULL WHERE clock_id = 4", "final"));
            test("reporter cannot rule out their own incident's clock", () -> refused(c,
                "UPDATE obligation_clock SET status='RULED_OUT', ruled_out_by=1, ruled_out_reason='this one is not needed at all', "
              + "ruled_out_at=UTC_TIMESTAMP() WHERE clock_id = 2", "reporter"));
            test("cannot insert a notice that is already approved", () -> refused(c,
                "INSERT INTO notification (clock_id,recipient,body,drafted_by,approved_by) VALUES (2,'CERT-In','x',1,2)", "draft"));
            test("a reporter cannot approve a notice", () -> refused(c,
                "UPDATE notification SET approved_by = 1 WHERE notification_id = 1", "only a reviewer"));
            test("a reviewer cannot approve a notice they drafted (CHECK constraint)", () -> {
                try (Statement st = c.createStatement()) {   // arjun (user 2) drafts a notice for clock 1
                    st.executeUpdate("INSERT INTO notification (clock_id, recipient, body, drafted_by) VALUES (1, 'CERT-In', 'Draft report: agent dropped the bookings table.', 2)");
                }
                refused(c, "UPDATE notification SET approved_by = 2 WHERE drafted_by = 2 AND approved_by IS NULL", "four_eyes");
            });
            test("cannot backdate a sent time: the trigger uses the DB clock", () -> {
                try (Statement st = c.createStatement()) {
                    // notice 1 (incident 2) is a draft by meera; approve as arjun via SQL, then send with a fake time
                    st.executeUpdate("UPDATE notification SET approved_by = 2 WHERE notification_id = 1");
                    st.executeUpdate("UPDATE notification SET sent_at = '2026-01-01 00:00:00' WHERE notification_id = 1");
                    try (ResultSet rs = st.executeQuery("SELECT TIMESTAMPDIFF(MINUTE, sent_at, UTC_TIMESTAMP()) FROM notification WHERE notification_id = 1")) {
                        rs.next();
                        check(Math.abs(rs.getInt(1)) < 2, "sent_at kept the fake value");
                    }
                }
            });
            test("cannot insert a mapping as already CONFIRMED", () -> refused(c,
                "INSERT INTO incident_mapping (incident_id,node_id,state,rationale,confidence,mapped_by,reviewed_by) "
              + "VALUES (2,1,'CONFIRMED','looks right to me honestly','LOW',1,2)", "suggested"));
            test("cannot extend a CERT-In clock", () -> refused(c,
                "INSERT INTO clock_extension (clock_id,new_due_at,board_reference,reason,recorded_by) "
              + "VALUES (1, UTC_TIMESTAMP() + INTERVAL 1 DAY, 'REF-1', 'we would like more time please', 2)", "does not allow"));
            test("cannot report a late 'aware' without a reason (CHECK)", () -> refused(c,
                "INSERT INTO incident (kind,title,description,ai_system_id,reported_by,occurred_at,detected_at,aware_at,severity,adversary) "
              + "VALUES ('AGENT','t','d',1,1,UTC_TIMESTAMP()-INTERVAL 5 HOUR,UTC_TIMESTAMP()-INTERVAL 5 HOUR,UTC_TIMESTAMP(),'LOW','NONE')", "ck_aware_gap"));
            test("cannot change which incident a report follows up", () -> refused(c, "UPDATE incident SET linked_to = 1 WHERE incident_id = 2", "write-once"));
            test("cannot erase or edit sign-in history", () -> {
                refused(c, "DELETE FROM login_attempt", "denied");
                refused(c, "UPDATE login_attempt SET success = TRUE", "denied");
            });
            test("cannot backdate a sign-in attempt to dodge the lockout", () -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO login_attempt (username, success, at) VALUES ('probe', FALSE, '2020-01-01')");
                    try (ResultSet rs = st.executeQuery("SELECT MAX(at) > UTC_TIMESTAMP() - INTERVAL 1 MINUTE FROM login_attempt WHERE username = 'probe'")) {
                        rs.next(); check(rs.getBoolean(1), "backdated time was kept");
                    }
                }
            });
            test("cannot add an audit event with a forged hash", () -> {
                try (Statement st = c.createStatement()) { st.execute("SET @head = (SELECT last_hash FROM audit_chain_head WHERE id = 1)"); }
                refused(c, "INSERT INTO audit_event (incident_id,actor_id,action,details,created_at,prev_hash,hash) "
                         + "VALUES (NULL,1,'X','forged',UTC_TIMESTAMP(3),@head,REPEAT('f',64))", "hash does not match");
            });
            test("cannot add an audit event that branches off an old point", () -> refused(c,
                "INSERT INTO audit_event (incident_id,actor_id,action,details,created_at,prev_hash,hash) "
              + "VALUES (NULL,1,'X','fork',UTC_TIMESTAMP(3),REPEAT('0',64),REPEAT('e',64))", "chain head"));

            // ---------------- pass 3
            test("SET timestamp really moves the session's clock back 3 days (the risk is real)", () -> {
                try (Connection d = app.open(); Statement st = d.createStatement()) {
                    st.execute("SET timestamp = UNIX_TIMESTAMP() - 3*86400");
                    try (ResultSet rs = st.executeQuery("SELECT TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(), SYSDATE()) - TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(), NOW())")) {
                        rs.next(); check(rs.getInt(1) == 72, "clock did not move: " + rs.getInt(1));
                    }
                }
            });
            test("a session with a faked clock cannot send a notice or sign in", () -> {
                sessionRefused(app, "SET timestamp = UNIX_TIMESTAMP() - 3*86400",
                    "UPDATE notification SET approved_by = 3 WHERE notification_id = 4", "changed its clock");
                sessionRefused(app, "SET timestamp = UNIX_TIMESTAMP() - 3*86400",
                    "INSERT INTO login_attempt (username, success) VALUES ('clock', FALSE)", "changed its clock");
            });
            test("a session with a faked clock cannot write a backdated audit event", () -> {
                try (Connection d = app.open(); Statement st = d.createStatement()) {
                    st.execute("SET timestamp = UNIX_TIMESTAMP() - 86400");
                    st.execute("SET @head = (SELECT last_hash FROM audit_chain_head WHERE id = 1), @t = UTC_TIMESTAMP(3)");
                    st.execute("SET @h = SHA2(CONCAT_WS('|', @head, '-', 1, 'X', 'yesterday', DATE_FORMAT(@t, '%Y-%m-%d %H:%i:%s.%f')), 256)");
                    String ins = "INSERT INTO audit_event (incident_id,actor_id,action,details,created_at,prev_hash,hash) "
                               + "VALUES (NULL,1,'X','yesterday',@t,@head,@h)";
                    refusedAs(d, ins, "changed its clock", "SET timestamp = UNIX_TIMESTAMP() - 86400; /* hash computed for 'yesterday' */ " + ins);
                }
            });
            test("cannot switch off foreign keys to attach a harm to nothing", () -> sessionRefused(app,
                "SET foreign_key_checks = 0",
                "INSERT INTO harm (incident_id,category,description,personal_data,recorded_by) VALUES (9999,'OTHER','orphan','NO',77)", "key checks"));
            test("cannot switch off foreign keys to add evidence from a user who does not exist", () -> sessionRefused(app,
                "SET foreign_key_checks = 0",
                "INSERT INTO evidence (incident_id,label,file_name,size_bytes,sha256,added_by) VALUES (2,'x','x',1,REPEAT('a',64),77)", "key checks"));
            test("cannot switch off unique checks", () -> sessionRefused(app,
                "SET unique_checks = 0", "INSERT INTO incident_tag (incident_id, tag) VALUES (2, 'dup-probe')", "key checks"));
            test("cannot switch off strict mode to squeeze in bad values", () -> sessionRefused(app,
                "SET sql_mode = ''",
                "INSERT INTO harm (incident_id,category,description,personal_data,recorded_by) VALUES (2,'MADE_UP','x','NO',1)", "ck_harm_category_valid"));
            test("cannot future-date an amendment to pre-approve a later change", () -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO incident_amendment (incident_id,field_name,old_value,new_value,reason,amended_by,amended_at) "
                                   + "VALUES (2,'severity','HIGH','LOW','prepared well in advance',1, UTC_TIMESTAMP() + INTERVAL 1 YEAR)");
                    try (ResultSet rs = st.executeQuery("SELECT ABS(TIMESTAMPDIFF(SECOND, MAX(amended_at), UTC_TIMESTAMP())) FROM incident_amendment WHERE incident_id = 2")) {
                        rs.next(); check(rs.getInt(1) < 60, "future date was kept");
                    }
                }
            });
            test("cannot backdate when a harm was recorded", () -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO harm (incident_id,category,description,personal_data,recorded_by,recorded_at) "
                                   + "VALUES (2,'OTHER','dated probe','NO',1,'2020-01-01')");
                    try (ResultSet rs = st.executeQuery("SELECT MAX(recorded_at) > UTC_TIMESTAMP() - INTERVAL 1 MINUTE FROM harm WHERE description = 'dated probe'")) {
                        rs.next(); check(rs.getBoolean(1), "backdated time was kept");
                    }
                }
            });
            test("cannot silently rewrite what happened (title)", () -> refused(c,
                "UPDATE incident SET title = 'Nothing happened' WHERE incident_id = 2", "needs an amendment"));
            test("cannot silently rewrite what happened (description)", () -> refused(c,
                "UPDATE incident SET description = 'All fine.' WHERE incident_id = 2", "needs an amendment"));
            test("a change of case only is still a change (byte-for-byte comparison)", () -> refused(c,
                "UPDATE incident SET title = UPPER(title) WHERE incident_id = 2", "needs an amendment"));
            test("cannot move an incident to a different AI system", () -> refused(c,
                "UPDATE incident SET ai_system_id = IF(ai_system_id = 1, 2, 1) WHERE incident_id = 2", "write-once"));
            test("cannot change the adversary answer without an amendment", () -> refused(c,
                "UPDATE incident SET adversary = IF(adversary = 'NONE', 'CONFIRMED', 'NONE') WHERE incident_id = 2", "adversary"));
            test("cannot wind the audit head back to start a branch", () -> refused(c,
                "UPDATE audit_chain_head SET last_event_id = 3, last_hash = (SELECT hash FROM audit_event WHERE event_id = 3) WHERE id = 1",
                "newest audit event"));
            test("cannot change status without announcing it in the audit trail first", () -> refused(c,
                "UPDATE incident SET status = 'TRIAGED' WHERE incident_id = 2", "audit trail first"));
            test("a status change announced by a reporter is refused", () -> {
                new AuditDao().append(c, 2, 1, "STATUS_CHANGED", "REPORTED -> TRIAGED");   // meera, a reporter
                refused(c, "UPDATE incident SET status = 'TRIAGED' WHERE incident_id = 2", "only a reviewer");
            });
            test("cannot hand your own draft to a colleague's name and then approve it", () -> refused(c,
                "UPDATE notification SET drafted_by = 3 WHERE notification_id = 4", "write-once"));
            test("cannot rewrite someone else's draft before approving it", () -> refused(c,
                "UPDATE notification SET body = 'Nothing to report.' WHERE notification_id = 4", "write-once"));
            test("cannot swap what a suggested mapping points at", () -> refused(c,
                "UPDATE incident_mapping SET rationale = 'swapped after the fact, trust me' WHERE state = 'SUGGESTED' LIMIT 1", "write-once"));
            // ---------------- pass 3b (found by an adversarial review of pass 3)
            test("cannot send a notice that nobody approved", () -> {
                refused(c, "UPDATE notification SET sent_at = UTC_TIMESTAMP() WHERE notification_id = 4", "ck_notice_order");
                refused(c, "UPDATE notification SET approved_at = UTC_TIMESTAMP(), sent_at = UTC_TIMESTAMP() WHERE notification_id = 4", "approved_at");
            });
            test("cannot rewrite when a notice was approved", () -> refused(c,
                "UPDATE notification SET approved_at = '2026-10-19 00:00:00' WHERE notification_id = 1", "approved_at"));
            test("closed means closed: no tags, amendments or clocks on a closed incident", () -> {
                refused(c, "INSERT INTO incident_tag (incident_id, tag) VALUES (3, 'late-tag')", "closed");
                refused(c, "INSERT INTO incident_amendment (incident_id,field_name,old_value,new_value,reason,amended_by) "
                         + "VALUES (3,'aware_at','(original kept)','2026-01-01','a quiet correction after closing',1)", "closed");
                refused(c, "INSERT INTO obligation_clock (incident_id, rule_id, started_at, due_at) "
                         + "SELECT incident_id, 1, aware_at, aware_at + INTERVAL 360 MINUTE FROM incident WHERE incident_id = 3", "closed");
            });
            test("the workflow's conditions are enforced by the database too", () -> {
                new AuditDao().append(c, 1, 2, "STATUS_CHANGED", "TRIAGED -> ASSESSED");   // arjun announces it
                refused(c, "UPDATE incident SET status = 'ASSESSED' WHERE incident_id = 1", "ASSESSED needs");
            });
            test("an overdue clock cannot be ruled out afterwards", () -> refused(c,
                "UPDATE obligation_clock SET status = 'RULED_OUT', ruled_out_by = 3, "
              + "ruled_out_reason = 'on reflection this was never reportable' WHERE clock_id = 2", "overdue"));
            test("a zero date cannot sneak in with strict mode off", () -> sessionRefused(app, "SET sql_mode = ''",
                "INSERT INTO incident (kind,title,description,ai_system_id,reported_by,occurred_at,detected_at,aware_at,severity,adversary) "
              + "VALUES ('AGENT','t','d',1,1,'garbage','garbage','garbage','LOW','NONE')", "ck_incident_dates"));
            test("cannot report an old 'aware' date without saying why", () -> refused(c,
                "INSERT INTO incident (kind,title,description,ai_system_id,reported_by,occurred_at,detected_at,aware_at,severity,adversary) "
              + "VALUES ('AGENT','t','d',1,1,'2021-01-01','2021-01-01','2021-01-01','LOW','NONE')", "ck_late_report"));
            test("the app cannot choose a reference code", () -> {
                refused(c, "UPDATE incident SET ref_code = 'PTV-1999-0001' WHERE incident_id = 2", "write-once");
                try (Connection d = app.open(); Statement st = d.createStatement()) {
                    d.setAutoCommit(false);
                    try {
                        st.executeUpdate("INSERT INTO incident (kind,title,description,ai_system_id,reported_by,occurred_at,detected_at,aware_at,severity,adversary,ref_code) "
                                       + "VALUES ('AGENT','t','d',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP(),'LOW','NONE','PTV-1999-0001')");
                        try (ResultSet rs = st.executeQuery("SELECT ref_code FROM incident WHERE incident_id = LAST_INSERT_ID()")) {
                            rs.next(); check(rs.getString(1) == null, "a chosen code was kept: " + rs.getString(1));
                        }
                        refused(d, "UPDATE incident SET ref_code = 'PTV-1999-0001' WHERE incident_id = LAST_INSERT_ID()", "must be PTV");
                    } finally { d.rollback(); }
                }
            });
            test("details rows only at birth, and of the incident's own kind", () -> {
                refused(c, "INSERT INTO data_incident VALUES (1, 'names', 10, 'NO')", "details can only");
                refused(c, "INSERT INTO agent_incident (incident_id, agent_name, instruction_given, action_taken, credential_beyond_task, had_confirmation_step) "
                         + "SELECT 3, 'bot', 'x', 'y', FALSE, FALSE", "details can only");
            });
            test("a forged 'success' row does not reset the lockout", () -> {
                try (Statement st = c.createStatement()) {
                    for (int n = 0; n < 5; n++) st.executeUpdate("INSERT INTO login_attempt (username, success) VALUES ('lock-probe', FALSE)");
                    st.executeUpdate("INSERT INTO login_attempt (username, success) VALUES ('lock-probe', TRUE)");
                }
                check(new LoginDao().recentFailures(c, "lock-probe", 15) >= 5, "the forged success reset the count");
            });
            test("a deactivated reviewer cannot approve", () -> {
                try (Connection a = admin.open(); Statement st = a.createStatement()) {
                    st.executeUpdate("UPDATE app_user SET active = FALSE WHERE user_id = 3");
                    try { refused(c, "UPDATE notification SET approved_by = 3 WHERE notification_id = 4", "only a reviewer"); }
                    finally { st.executeUpdate("UPDATE app_user SET active = TRUE WHERE user_id = 3"); }
                }
            });
            test("a notice to the wrong recipient cannot meet a legal clock", () -> refused(c,
                "INSERT INTO notification (clock_id, recipient, body, drafted_by) "
              + "VALUES (1, 'Internal team', 'FYI: the agent dropped a table, all handled.', 1)", "recipient the rule names"));
            test("a one-word notice is not a report", () -> refused(c,
                "INSERT INTO notification (clock_id, recipient, body, drafted_by) VALUES (1, 'CERT-In', 'x', 1)", "ck_notice_body"));
            test("cannot approve a notice whose clock was ruled out", () -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO notification (clock_id, recipient, body, drafted_by) VALUES (6, 'CERT-In', 'Draft report: support bot showed phone numbers.', 1)");
                    st.executeUpdate("UPDATE obligation_clock SET status = 'RULED_OUT', ruled_out_by = 2, "
                                   + "ruled_out_reason = 'not a cyber security incident after review' WHERE clock_id = 6");
                }
                refused(c, "UPDATE notification SET approved_by = 3 WHERE clock_id = 6 AND approved_by IS NULL", "ruled out");
            });
        }

        // Root rewrites one event AND recomputes every later hash and the head.
        // verify() is fooled (that is the honest limit of a hash chain); the anchor is not.
        test("a full re-hashed rewrite by root fools verify() but not a saved anchor", () -> {
            try (Connection a = admin.open(); Statement st = a.createStatement()) {
                List<AuditEvent> before = new AuditDao().list(a, null);
                st.execute("DROP TRIGGER trg_audit_bu");
                try {
                    String prev = before.get(23).hash();                       // event 25 is index 24
                    for (int k = 24; k < before.size(); k++) {
                        AuditEvent e = before.get(k);
                        String details = k == 24 ? "CERTIN_6H: never mind" : e.details();
                        String h = AuditDao.computeHash(prev, e.incidentId(), e.actorId(), e.action(), details, e.createdAt());
                        try (PreparedStatement ps = a.prepareStatement("UPDATE audit_event SET details=?, prev_hash=?, hash=? WHERE event_id=?")) {
                            ps.setString(1, details); ps.setString(2, prev); ps.setString(3, h); ps.setLong(4, e.id()); ps.executeUpdate();
                        }
                        prev = h;
                    }
                    st.executeUpdate("UPDATE audit_chain_head SET last_hash = '" + prev + "' WHERE id = 1");
                    var as = new AuditService(app);
                    check(as.verify().intact(), "expected verify() to be fooled by a full rewrite");
                    var r = as.verifyAnchor(anchor);
                    check(!r.intact(), "anchor did not catch the rewrite: " + r.message());
                } finally {
                    for (int k = before.size() - 1; k >= 24; k--) {      // put history back exactly
                        AuditEvent e = before.get(k);
                        try (PreparedStatement ps = a.prepareStatement("UPDATE audit_event SET details=?, prev_hash=?, hash=? WHERE event_id=?")) {
                            ps.setString(1, e.details()); ps.setString(2, e.prevHash()); ps.setString(3, e.hash()); ps.setLong(4, e.id()); ps.executeUpdate();
                        }
                    }
                    st.executeUpdate("UPDATE audit_chain_head SET last_hash = '" + before.get(before.size() - 1).hash() + "' WHERE id = 1");
                    st.execute("CREATE TRIGGER trg_audit_bu BEFORE UPDATE ON audit_event FOR EACH ROW "
                             + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only'");
                }
                check(new AuditService(app).verifyAnchor(anchor).intact(), "restore failed");
            }
        });

        // Someone with full admin rights drops the protection and edits history.
        test("tampering by an admin is detected by verify()", () -> {
            try (Connection a = admin.open(); Statement st = a.createStatement()) {
                String original;
                try (ResultSet rs = st.executeQuery("SELECT details FROM audit_event WHERE event_id = 25")) { rs.next(); original = rs.getString(1); }
                st.execute("DROP TRIGGER trg_audit_bu");
                try {
                    try (PreparedStatement ps = a.prepareStatement("UPDATE audit_event SET details = ? WHERE event_id = 25")) {
                        ps.setString(1, "CERTIN_6H: never mind"); ps.executeUpdate();
                    }
                    var r = new AuditService(app).verify();
                    check(!r.intact() && Long.valueOf(25).equals(r.brokenAt()), "not detected: " + r.message());
                    try (PreparedStatement ps = a.prepareStatement("UPDATE audit_event SET details = ? WHERE event_id = 25")) {
                        ps.setString(1, original); ps.executeUpdate();
                    }
                    check(new AuditService(app).verify().intact(), "restoring did not verify");
                } finally {
                    st.execute("CREATE TRIGGER trg_audit_bu BEFORE UPDATE ON audit_event FOR EACH ROW "
                             + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only'");
                }
            }
        });
    }
}
