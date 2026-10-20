package pathivu.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import pathivu.mapping.MapperRegistry;
import pathivu.model.*;
import pathivu.rules.RuleEngine;
import pathivu.workflow.IncidentWorkflow;
import pathivu.workflow.WorkflowFacts;

/**
 * Generates test vectors from the REAL Java engine, so the website's JavaScript port
 * can prove, in the visitor's browser, that it gives the same answers.
 *   java -cp "out:lib/*" pathivu.tools.ExportVectors site/vectors.json
 */
public class ExportVectors {

    static final List<RuleVersion> RULES = List.of(   // same values as db/seed.sql
        new RuleVersion(1, "CERTIN_6H", "CERT_IN", "Report the cyber security incident", "CERT-In",
            Instant.parse("2022-06-27T18:30:00Z"), 360, RuleTrigger.CYBER_INCIDENT, "CERT-In Directions 2022", false),
        new RuleVersion(2, "DPDP_R7_PRINCIPALS", "DPDP", "Tell each affected Data Principal", "Affected persons",
            Instant.parse("2027-05-12T18:30:00Z"), null, RuleTrigger.PERSONAL_DATA, "DPDP Rules 2025, Rule 7(1)", false),
        new RuleVersion(3, "DPDP_R7_BOARD_INITIAL", "DPDP", "First intimation to the Board", "Data Protection Board of India",
            Instant.parse("2027-05-12T18:30:00Z"), null, RuleTrigger.PERSONAL_DATA, "DPDP Rules 2025, Rule 7(2)(a)", false),
        new RuleVersion(4, "DPDP_R7_BOARD_72H", "DPDP", "Detailed report to the Board", "Data Protection Board of India",
            Instant.parse("2027-05-12T18:30:00Z"), 4320, RuleTrigger.PERSONAL_DATA, "DPDP Rules 2025, Rule 7(2)(b)", true));

    static final String[] AWARE = {"2022-06-27T18:29:00Z", "2022-06-27T18:30:00Z", "2026-10-19T04:00:00Z",
                                   "2027-05-12T18:29:59Z", "2027-05-12T18:30:00Z", "2027-06-01T00:00:00Z"};
    static final String[] TITLES = {"Agent dropped the bookings table", "Support bot shared phone numbers",
        "Model gave wrong dosage advice", "Prompt injection via pasted email", "System prompt shown to a user",
        "Retrieval index was poisoned", "Jailbreak produced harmful text", "Cleanup job deleted backups",
        "Scores skewed against rural clinics", "Agent wiped a staging bucket", "Chatbot destroyed nothing but lied",
        "Refund agent used an expired token"};
    static final String[] TAGS = {"prompt-injection", "jailbreak", "poisoning", "system-prompt", "agent", "Data Loss"};

    public static void main(String[] args) throws Exception {
        Random r = new Random(2026);
        StringBuilder out = new StringBuilder("{\n\"rules\": [");
        for (int i = 0; i < RULES.size(); i++) {
            RuleVersion v = RULES.get(i);
            out.append(i == 0 ? "\n" : ",\n").append(String.format(
                "  {\"id\":%d,\"code\":\"%s\",\"regime\":\"%s\",\"name\":\"%s\",\"recipient\":\"%s\",\"effectiveFrom\":\"%s\",\"windowMinutes\":%s,\"triggerOn\":\"%s\",\"citation\":\"%s\",\"extendable\":%b}",
                v.id(), v.code(), v.regime(), v.name(), v.recipient(), v.effectiveFrom(), v.windowMinutes(), v.triggerOn(), v.citation(), v.extendable()));
        }
        out.append("\n],\n\"cases\": [");
        RuleEngine engine = new RuleEngine(RULES);
        MapperRegistry mappers = new MapperRegistry();
        int n = 240;
        for (int k = 0; k < n; k++) {
            Instant aware = Instant.parse(AWARE[r.nextInt(AWARE.length)]);
            Instant det = aware.minusSeconds(60L * r.nextInt(50)), occ = det.minusSeconds(60L * r.nextInt(300));
            IncidentKind kind = IncidentKind.values()[r.nextInt(3)];
            Adversary adv = Adversary.values()[r.nextInt(3)];
            TriState cyber = TriState.values()[r.nextInt(3)];
            String title = TITLES[r.nextInt(TITLES.length)];
            String desc = TITLES[r.nextInt(TITLES.length)] + " during routine work";
            boolean cred = r.nextBoolean(), conf = r.nextBoolean();
            OutputType ot = OutputType.values()[r.nextInt(4)];
            TriState exf = TriState.values()[r.nextInt(3)];
            Incident inc = switch (kind) {
                case AGENT -> new AgentIncident(title, desc, 1, 1, occ, det, aware, Severity.HIGH, adv, "Bot", "fix the deploy",
                                                r.nextBoolean() ? "deleted the production table" : "edited a config file", cred, conf);
                case MODEL_HARM -> new ModelHarmIncident(title, desc, 1, 1, occ, det, aware, Severity.MEDIUM, adv, ot, "riders");
                case DATA -> new DataIncident(title, desc, 1, 1, occ, det, aware, Severity.LOW, adv, "phone numbers", r.nextInt(500), exf);
            };
            inc.setCyberIncident(cyber);
            List<String> tags = new ArrayList<>();
            for (String t : TAGS) if (r.nextInt(5) == 0) { inc.addTag(t); tags.add(t); }
            List<Harm> harms = new ArrayList<>();
            int hn = r.nextInt(4);
            for (int h = 0; h < hn; h++) harms.add(Harm.draft(HarmCategory.values()[r.nextInt(8)], "harm " + h, r.nextInt(100), TriState.values()[r.nextInt(3)]));

            out.append(k == 0 ? "\n" : ",\n").append("  {\"input\":{");
            out.append(q("kind")).append(':').append(q(kind.name())).append(',')
               .append(q("title")).append(':').append(q(title)).append(',')
               .append(q("description")).append(':').append(q(desc)).append(',')
               .append(q("aware")).append(':').append(q(aware.toString())).append(',')
               .append(q("adversary")).append(':').append(q(adv.name())).append(',')
               .append(q("cyber")).append(':').append(q(cyber.name())).append(',')
               .append(q("tags")).append(':').append(arr(tags)).append(',');
            if (inc instanceof AgentIncident a) out.append(q("agent")).append(String.format(":{\"name\":\"Bot\",\"instruction\":\"fix the deploy\",\"action\":%s,\"credentialBeyondTask\":%b,\"confirmationStep\":%b},",
                q(a.getActionTaken()), cred, conf));
            if (inc instanceof ModelHarmIncident) out.append(q("outputType")).append(':').append(q(ot.name())).append(",\"affectedGroup\":\"riders\",");
            if (inc instanceof DataIncident d) out.append(String.format("\"data\":{\"categories\":\"phone numbers\",\"records\":%d,\"exfiltrated\":\"%s\"},", d.getRecordsAffected(), exf));
            out.append("\"harms\":[");
            for (int h = 0; h < harms.size(); h++) out.append(h == 0 ? "" : ",").append(String.format("{\"category\":\"%s\",\"personalData\":\"%s\"}",
                harms.get(h).category(), harms.get(h).personalData()));
            out.append("]},\"expect\":{\"clocks\":[");
            var app = engine.applicable(inc, harms);
            for (int c = 0; c < app.size(); c++) out.append(c == 0 ? "" : ",").append(String.format("{\"code\":\"%s\",\"due\":\"%s\"}",
                app.get(c).version().code(), app.get(c).dueAt(aware)));
            out.append("],\"suggestions\":[");
            var sg = mappers.suggestAll(inc, harms);
            for (int c = 0; c < sg.size(); c++) out.append(c == 0 ? "" : ",").append(String.format("[\"%s\",\"%s\",\"%s\"]",
                sg.get(c).framework(), sg.get(c).code(), sg.get(c).confidence()));
            // workflow: a random target with random facts and a random user
            IncidentStatus from = IncidentStatus.values()[r.nextInt(5)];
            inc.restoreStatus(from);
            var legal = new ArrayList<>(IncidentWorkflow.nextFrom(from));
            IncidentStatus to = (!legal.isEmpty() && r.nextInt(5) > 0) ? legal.get(r.nextInt(legal.size()))
                                                                     : IncidentStatus.values()[r.nextInt(5)];
            boolean good = r.nextInt(3) == 0;     // a third of cases: facts that satisfy everything
            WorkflowFacts f = good ? new WorkflowFacts(1, 1, 1, 0, 0, 0, 0)
                : new WorkflowFacts(r.nextInt(2), r.nextInt(2), r.nextInt(2), r.nextInt(2), r.nextInt(2), r.nextInt(2), r.nextInt(2));
            AppUser u = new AppUser(r.nextBoolean() ? 1 : 2, "u", "U", good ? Role.REVIEWER : Role.values()[r.nextInt(3)]);
            if (good && inc.getCyberIncident() == TriState.UNSURE) inc.setCyberIncident(TriState.YES);
            var b = IncidentWorkflow.blockers(inc, to, u, f);
            out.append("],\"workflow\":{\"from\":").append(q(from.name())).append(",\"to\":").append(q(to.name()))
               .append(",\"cyber\":").append(q(inc.getCyberIncident().name()))
               .append(String.format(",\"facts\":[%d,%d,%d,%d,%d,%d,%d],\"user\":{\"id\":%d,\"role\":\"%s\"},\"blockers\":%s}}}",
                   f.harms(), f.evidence(), f.confirmedMappings(), f.suggestedMappings(), f.unsureHarms(), f.runningClocks(),
                   f.runningClocksWithoutApprovedNotice(), u.id(), u.role(), arr(b)));
        }
        out.append("\n],\n\"generated\": \"by pathivu.tools.ExportVectors from the Java engine, seed 2026, ").append(n).append(" cases\"\n}\n");
        Files.writeString(Path.of(args.length > 0 ? args[0] : "vectors.json"), out.toString());
        System.out.println("wrote " + n + " cases");
    }

    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    static String arr(List<String> xs) { StringBuilder b = new StringBuilder("["); for (int i = 0; i < xs.size(); i++) b.append(i == 0 ? "" : ",").append(q(xs.get(i))); return b.append("]").toString(); }
}
