package pathivu.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import pathivu.dao.*;
import pathivu.model.*;
import pathivu.security.AccessDenied;
import pathivu.security.Guard;
import pathivu.security.Sha256;
import pathivu.workflow.IncidentWorkflow;
import pathivu.workflow.WorkflowFacts;

/**
 * Every write is one transaction: the change, its audit event, and anything
 * it triggers (new clocks, new suggestions) are saved together or not at all.
 */
public class IncidentService {

    private final Db db;
    private final IncidentDao incidents = new IncidentDao();
    private final HarmDao harms = new HarmDao();
    private final EvidenceDao evidence = new EvidenceDao();
    private final MappingDao mappings = new MappingDao();
    private final TaxonomyDao taxonomy = new TaxonomyDao();
    private final ClockDao clocks = new ClockDao();
    private final NotificationDao notices = new NotificationDao();
    private final AmendmentDao amendments = new AmendmentDao();
    private final AuditDao audit = new AuditDao();
    private final LookupDao lookups = new LookupDao();
    private final Automations auto = new Automations();

    public IncidentService(Db db) { this.db = db; }

    // ------------------------------------------------------------------ report

    public int report(AppUser by, Incident draft) {
        Guard.requireWriter(by, "report incidents");
        if (draft.getReportedBy() != by.id()) throw new AccessDenied("You can only report incidents as yourself.");
        return db.tx(c -> {
            int id = incidents.insert(c, draft);
            audit.append(c, id, by.id(), "INCIDENT_REPORTED",
                draft.getRefCode() + " " + draft.kind() + " \"" + draft.getTitle() + "\" aware " + pathivu.util.Times.ist(draft.getAwareAt()));
            auto.syncClocks(c, id, by.id());
            auto.suggestMappings(c, id, by.id());
            return id;
        });
    }

    // ------------------------------------------------------------------ read

    public Integer idForRef(String ref) { return db.read(c -> incidents.idForRef(c, ref)); }

    public List<IncidentDao.RegisterRow> register() { return db.read(incidents::register); }

    public List<LookupDao.AiSystemRow> aiSystems() { return db.read(lookups::aiSystems); }

    public List<TaxonomyNode> currentNodes() { return db.read(taxonomy::listCurrent); }

    public IncidentFile load(int id) {
        return db.read(c -> {
            Incident i = incidents.find(c, id);
            if (i == null) throw new IllegalArgumentException("No incident " + id);
            String sys = lookups.aiSystems(c).stream().filter(s -> s.id() == i.getAiSystemId())
                                .map(Object::toString).findFirst().orElse("?");
            return new IncidentFile(i, sys, harms.listFor(c, id), evidence.listFor(c, id), mappings.listFor(c, id),
                clocks.listFor(c, id), notices.listForIncident(c, id), amendments.listFor(c, id), audit.list(c, id));
        });
    }

    // ------------------------------------------------------------------ harms & evidence

    public void addHarm(AppUser by, int incidentId, Harm h) {
        Guard.requireWriter(by, "record harms");
        db.tx(c -> {
            incidents.lock(c, incidentId);
            harms.insert(c, incidentId, h, by.id());
            audit.append(c, incidentId, by.id(), "HARM_RECORDED",
                h.category() + ": " + h.description() + " (personal data: " + h.personalData() + ")");
            auto.syncClocks(c, incidentId, by.id());
            auto.suggestMappings(c, incidentId, by.id());
            return null;
        });
    }

    /** Hashes the file first (outside the transaction, it may be large), then records the fingerprint. */
    public String addEvidence(AppUser by, int incidentId, String label, Path file) throws IOException {
        Guard.requireWriter(by, "attach evidence");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Give the evidence a label");
        String sha = Sha256.ofFile(file);
        long size = Files.size(file);
        db.tx(c -> {
            evidence.insert(c, incidentId, label.trim(), file.getFileName().toString(), sha, size, by.id());
            audit.append(c, incidentId, by.id(), "EVIDENCE_ADDED", label + ": " + file.getFileName() + " sha256=" + sha);
            return null;
        });
        return sha;
    }

    /** Re-hash a file and say whether it is the same one that was attached. */
    public boolean evidenceMatches(Evidence e, Path file) throws IOException {
        return Sha256.ofFile(file).equals(e.sha256());
    }

    // ------------------------------------------------------------------ amendments

    /**
     * Corrections. Severity and the CERT-In answer may change (with a reason
     * recorded first; the trigger checks). The three timestamps never change:
     * a correction to them is recorded as an annotation beside the original,
     * and every clock stays anchored to the original aware_at.
     */
    public void amend(AppUser by, int incidentId, String field, String newValue, String reason) {
        Guard.requireWriter(by, "amend incidents");
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("Give a reason of at least 10 characters");
        db.tx(c -> {
            incidents.lock(c, incidentId);
            Incident i = incidents.find(c, incidentId);
            String old = switch (field) {
                case "title" -> i.getTitle();
                case "description" -> i.getDescription();
                case "severity" -> i.getSeverity().name();
                case "cyber_incident" -> i.getCyberIncident().name();
                case "occurred_at", "detected_at", "aware_at" -> null;   // annotation only
                default -> throw new IllegalArgumentException("Unknown field " + field);
            };
            boolean annotation = old == null;
            amendments.insert(c, incidentId, field, annotation ? "(original kept)" : old, newValue, reason.trim(), by.id());
            if (!annotation) incidents.updateField(c, incidentId, field, newValue);
            audit.append(c, incidentId, by.id(), annotation ? "TIME_ANNOTATED" : "INCIDENT_AMENDED",
                field + (annotation ? " correction noted: " : ": " + old + " -> ") + newValue + " because " + reason.trim());
            if (field.equals("cyber_incident")) auto.syncClocks(c, incidentId, by.id());
            return null;
        });
    }

    public void addTag(AppUser by, int incidentId, String tag) {
        Guard.requireWriter(by, "tag incidents");
        db.tx(c -> {
            String t = tag.toLowerCase().trim().replaceAll("[^a-z0-9-]", "-");
            incidents.addTag(c, incidentId, t);
            audit.append(c, incidentId, by.id(), "TAG_ADDED", t);
            auto.suggestMappings(c, incidentId, by.id());
            return null;
        });
    }

    // ------------------------------------------------------------------ mappings

    /** Anyone who can write may propose a mapping by hand; it still needs a reviewer. */
    public void proposeMapping(AppUser by, int incidentId, int nodeId, String rationale, Confidence conf) {
        Guard.requireWriter(by, "propose mappings");
        db.tx(c -> {
            mappings.insertSuggestion(c, incidentId, nodeId, rationale, conf, by.id());
            audit.append(c, incidentId, by.id(), "MAPPING_PROPOSED", nodeLabel(c, nodeId) + ": " + rationale);
            return null;
        });
    }

    public void reviewMapping(AppUser by, int incidentId, int nodeId, boolean confirm) {
        Guard.require(by, Role.REVIEWER, "confirm or reject mappings");
        db.tx(c -> {
            mappings.review(c, incidentId, nodeId, confirm ? MappingState.CONFIRMED : MappingState.REJECTED, by.id());
            audit.append(c, incidentId, by.id(), confirm ? "MAPPING_CONFIRMED" : "MAPPING_REJECTED", nodeLabel(c, nodeId));
            return null;
        });
    }

    // ------------------------------------------------------------------ workflow

    public void move(AppUser by, int incidentId, IncidentStatus to, String reason) {
        db.tx(c -> {
            incidents.lock(c, incidentId);           // nobody else can change it until we commit
            Incident i = incidents.find(c, incidentId);
            IncidentStatus from = i.getStatus();
            boolean back = to == IncidentStatus.TRIAGED && from != IncidentStatus.REPORTED;
            if (back && (reason == null || reason.trim().length() < 10))
                throw new IllegalArgumentException("Sending an incident back needs a reason of at least 10 characters");
            IncidentWorkflow.move(i, to, by, facts(c, incidentId));
            incidents.updateStatus(c, incidentId, to);
            audit.append(c, incidentId, by.id(), "STATUS_CHANGED",
                from + " -> " + to + (reason == null || reason.isBlank() ? "" : " because " + reason.trim()));
            return null;
        });
    }

    public List<String> blockers(AppUser by, int incidentId, IncidentStatus to) {
        return db.read(c -> IncidentWorkflow.blockers(incidents.find(c, incidentId), to, by, facts(c, incidentId)));
    }

    private String nodeLabel(Connection c, int nodeId) throws SQLException {
        return taxonomy.listCurrent(c).stream().filter(n -> n.id() == nodeId)
                       .map(n -> n.framework().label() + " " + n.code()).findFirst().orElse("node " + nodeId);
    }

    private WorkflowFacts facts(Connection c, int id) throws SQLException {
        List<Harm> hs = harms.listFor(c, id);
        List<Mapping> ms = mappings.listFor(c, id);
        List<ObligationClock> ks = clocks.listFor(c, id);
        List<Notification> ns = notices.listForIncident(c, id);
        int running = 0, runningWithout = 0;
        for (ObligationClock k : ks) {
            if (k.state() != ClockState.RUNNING) continue;
            running++;
            boolean approved = ns.stream().anyMatch(n -> n.clockId() == k.id() && n.approvedBy() != null);
            if (!approved) runningWithout++;
        }
        return new WorkflowFacts(hs.size(), evidence.listFor(c, id).size(),
            (int) ms.stream().filter(m -> m.state() == MappingState.CONFIRMED).count(),
            (int) ms.stream().filter(m -> m.state() == MappingState.SUGGESTED).count(),
            (int) hs.stream().filter(h -> h.personalData() == TriState.UNSURE).count(),
            running, runningWithout);
    }
}
