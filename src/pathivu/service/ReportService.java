package pathivu.service;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import pathivu.model.*;
import pathivu.util.Times;

/** A plain-text incident report someone can attach to an email or print for a review meeting. */
public class ReportService {

    public String build(IncidentFile f, Map<Integer, String> userNames) {
        Incident i = f.incident();
        Instant now = Instant.now();
        StringBuilder b = new StringBuilder();
        line(b, "PATHIVU INCIDENT REPORT  " + i.getRefCode());
        b.append("Generated ").append(Times.ist(now)).append("\n\n");
        b.append(i.getTitle()).append("\n");
        b.append("Kind: ").append(i.kind()).append("   Severity: ").append(i.getSeverity())
         .append("   Status: ").append(i.getStatus()).append("\n");
        b.append("System: ").append(f.aiSystem()).append("\n");
        b.append("Reported by: ").append(userNames.getOrDefault(i.getReportedBy(), "?")).append("\n");
        b.append("Adversary: ").append(i.getAdversary()).append("   Cyber security incident: ").append(i.getCyberIncident()).append("\n");
        if (!i.getTags().isEmpty()) b.append("Tags: ").append(String.join(", ", i.getTags())).append("\n");
        b.append("\nOccurred  ").append(Times.ist(i.getOccurredAt()))
         .append("\nDetected  ").append(Times.ist(i.getDetectedAt()))
         .append("\nAware     ").append(Times.ist(i.getAwareAt())).append("   (all legal clocks start here)\n");
        if (i.getAwareGapReason() != null) b.append("  Why aware came later than detection: ").append(i.getAwareGapReason()).append("\n");
        if (i.getLinkedTo() != null) b.append("  Follows up incident #").append(i.getLinkedTo()).append("\n");
        f.amendments().stream().filter(a -> a.field().endsWith("_at")).forEach(a ->
            b.append("  Note: ").append(a.field()).append(" correction recorded: ").append(a.newValue())
             .append(" because ").append(a.reason()).append(" (original kept; clocks unchanged)\n"));
        b.append("\n").append(i.getDescription()).append("\n").append(i.specificsSummary()).append("\n");

        section(b, "HARM");
        if (f.harms().isEmpty()) b.append("None recorded.\n");
        for (Harm h : f.harms()) b.append("- ").append(h.category()).append(": ").append(h.description())
            .append(" | affected ").append(h.affectedCount()).append(" | personal data ").append(h.personalData()).append("\n");

        section(b, "FRAMEWORK MAPPINGS (confirmed)");
        Map<Framework, String> byFw = f.mappings().stream().filter(m -> m.state() == MappingState.CONFIRMED)
            .collect(Collectors.groupingBy(m -> m.node().framework(),
                Collectors.mapping(m -> m.node().code() + " " + m.node().name(), Collectors.joining("; "))));
        for (Framework fw : Framework.values())
            b.append(String.format("%-22s %s%n", fw.label(), byFw.getOrDefault(fw, "(not confirmed)")));

        section(b, "LEGAL OBLIGATIONS");
        if (f.clocks().isEmpty()) b.append("No clocks.\n");
        for (ObligationClock k : f.clocks()) {
            b.append(k.rule().code()).append(" → ").append(k.rule().recipient()).append(", ").append(k.rule().windowText())
             .append("\n  due ").append(Times.ist(k.dueAt())).append("  status ").append(k.liveStatus(now));
            if (k.metAt() != null) b.append("  (met ").append(Times.ist(k.metAt())).append(")");
            if (k.ruledOutReason() != null) b.append("\n  ruled out by ").append(userNames.getOrDefault(k.ruledOutBy(), "?"))
                                                .append(": ").append(k.ruledOutReason());
            b.append("\n  ").append(k.rule().citation()).append("\n");
        }

        section(b, "EVIDENCE (SHA-256 at time of attachment)");
        if (f.evidence().isEmpty()) b.append("None.\n");
        for (Evidence e : f.evidence()) b.append("- ").append(e.label()).append(": ").append(e.fileName())
            .append("\n  ").append(e.sha256()).append("\n");
        b.append("Expected for this kind: ").append(String.join(", ", i.requiredEvidence())).append("\n");

        section(b, "HISTORY");
        for (AuditEvent e : f.history()) b.append(Times.ist(e.createdAt())).append("  ")
            .append(userNames.getOrDefault(e.actorId(), "?")).append("  ").append(e.action()).append("  ").append(e.details()).append("\n");
        b.append("\nThis report is generated from the register. It is not legal advice.\n");
        return b.toString();
    }

    private static void line(StringBuilder b, String s) { b.append(s).append("\n").append("=".repeat(s.length())).append("\n"); }
    private static void section(StringBuilder b, String s) { b.append("\n").append(s).append("\n").append("-".repeat(s.length())).append("\n"); }
}
