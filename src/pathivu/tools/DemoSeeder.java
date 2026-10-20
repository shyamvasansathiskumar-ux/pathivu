package pathivu.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import pathivu.dao.Db;
import pathivu.dao.UserDao;
import pathivu.model.*;
import pathivu.service.*;

/**
 * Fills the register with four realistic incidents, each at a different point
 * in its life, by calling the real services (so the audit trail is real too).
 * Times are relative to now, so the clocks are always live in a demo.
 *
 *   java -cp "out:lib/*" pathivu.tools.DemoSeeder
 */
public class DemoSeeder {

    public static void main(String[] args) throws Exception {
        Db db = Db.fromFile(Path.of("db.properties"), "db.");
        UserDao ud = new UserDao();
        AppUser meera = db.read(c -> ud.findCredentials(c, "meera")).user();
        AppUser arjun = db.read(c -> ud.findCredentials(c, "arjun")).user();
        AppUser kavya = db.read(c -> ud.findCredentials(c, "kavya")).user();
        IncidentService inc = new IncidentService(db);
        ObligationService obl = new ObligationService(db);
        Path dir = Files.createDirectories(Path.of("demo-evidence"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        // 1. Live: agent deleted production data 2 hours ago. CERT-In clock running.
        AgentIncident a = new AgentIncident(
            "DevPilot agent dropped the production bookings database",
            "Asked to fix a failing staging deploy, the coding agent ran a cleanup script against production and dropped the bookings database. Restored from a 6-hour-old backup.",
            1, meera.id(), ago(now, 160), ago(now, 140), ago(now, 120), Severity.CRITICAL, Adversary.NONE,
            "DevPilot", "Fix the failing staging deploy", "dropped the production bookings database", true, false);
        a.setCyberIncident(TriState.YES);
        a.addTag("agent"); a.addTag("data-loss");
        int id1 = inc.report(meera, a);
        inc.addHarm(meera, id1, Harm.draft(HarmCategory.DATA_LOSS, "Six hours of bookings lost (names, phone numbers, pickup points)", 12400, TriState.YES));
        inc.addHarm(meera, id1, Harm.draft(HarmCategory.SERVICE_OUTAGE, "Booking app down for 3 hours in Chennai and Coimbatore", 0, TriState.NO));
        inc.addEvidence(meera, id1, "Agent transcript", file(dir, "devpilot-transcript.txt",
            "[10:02] user: staging deploy fails, please fix\n[10:09] agent: running cleanup.sh --env=prod\n[10:09] agent: DROP DATABASE bookings;\n"));
        inc.move(arjun, id1, IncidentStatus.TRIAGED, null);

        // 2. Overdue: support bot leaked phone numbers through prompt injection 9 hours ago.
        DataIncident d = new DataIncident(
            "Support bot revealed other riders' phone numbers",
            "A rider pasted text containing injected instructions (prompt injection); the support bot then listed phone numbers from other riders' recent trips.",
            2, meera.id(), ago(now, 600), ago(now, 560), ago(now, 540), Severity.HIGH, Adversary.SUSPECTED,
            "phone numbers, first names", 37, TriState.YES);
        d.setCyberIncident(TriState.YES);
        d.addTag("prompt-injection");
        int id2 = inc.report(meera, d);
        inc.addHarm(meera, id2, Harm.draft(HarmCategory.DATA_EXPOSURE, "Phone numbers of 37 riders shown to another user", 37, TriState.YES));
        IncidentFile f2 = inc.load(id2);
        obl.draftNotice(meera, f2.clocks().get(0).id(), obl.template(f2, f2.clocks().get(0)));

        // 3. Closed: biased triage scores. Not a cyber incident, so the CERT-In clock was ruled out.
        ModelHarmIncident m = new ModelHarmIncident(
            "Triage model ranked samples from rural clinics lower",
            "Weekly review found that samples sent from rural partner clinics were scored as less urgent than identical urban samples.",
            3, meera.id(), ago(now, 60 * 24 * 5), ago(now, 60 * 24 * 3 + 90), ago(now, 60 * 24 * 3), Severity.MEDIUM, Adversary.NONE,
            OutputType.BIASED_DECISION, "patients of rural partner clinics");
        m.setAwareGapReason("Found by the night-shift lab technician; the clinical lead was told when the morning shift began");
        int id3 = inc.report(meera, m);
        inc.addHarm(meera, id3, Harm.draft(HarmCategory.DISCRIMINATION, "Urgent samples from rural clinics processed up to a day late", 212, TriState.NO));
        inc.addEvidence(meera, id3, "Sample of the harmful output", file(dir, "triage-scores.csv",
            "sample,clinic_type,score\nS-1041,urban,0.91\nS-1042,rural,0.62\n"));
        inc.move(arjun, id3, IncidentStatus.TRIAGED, null);
        inc.amend(arjun, id3, "cyber_incident", "NO", "No system was compromised; this is a model quality and fairness failure.");
        int clock3 = inc.load(id3).clocks().get(0).id();
        obl.ruleOut(arjun, clock3, "Not a cyber security incident under the CERT-In Directions: no unauthorised access or compromise.");
        for (Mapping mp : inc.load(id3).mappings()) inc.reviewMapping(arjun, id3, mp.node().id(), true);
        inc.move(arjun, id3, IncidentStatus.ASSESSED, null);
        inc.move(arjun, id3, IncidentStatus.NOTICES_REVIEWED, null);
        inc.move(kavya, id3, IncidentStatus.CLOSED, null);

        // 4. Reported late: agent used a leaked key 30 hours ago; notice went out after the 6 hours.
        AgentIncident a2 = new AgentIncident(
            "Pricing agent pushed surge prices using an expired approval",
            "The pricing agent reused a cached approval token after it expired and applied 3x surge pricing in Madurai for 40 minutes.",
            1, meera.id(), ago(now, 60 * 31), ago(now, 60 * 30 + 20), ago(now, 60 * 30), Severity.HIGH, Adversary.NONE,
            "PriceBot", "Recommend surge levels for review", "applied surge pricing without a valid approval", true, true);
        a2.setCyberIncident(TriState.YES);
        int id4 = inc.report(meera, a2);
        inc.addHarm(meera, id4, Harm.draft(HarmCategory.FINANCIAL, "Riders overcharged; refunds issued", 830, TriState.NO));
        IncidentFile f4 = inc.load(id4);
        int notice = obl.draftNotice(meera, f4.clocks().get(0).id(), obl.template(f4, f4.clocks().get(0)));
        obl.approveNotice(arjun, notice);
        obl.markSent(arjun, notice);

        System.out.println("Demo data loaded: " + List.of(id1, id2, id3, id4).size() + " incidents.");
    }

    private static Instant ago(Instant now, long minutes) { return now.minus(Duration.ofMinutes(minutes)); }

    private static Path file(Path dir, String name, String content) throws Exception {
        return Files.writeString(dir.resolve(name), content);
    }
}
