package pathivu.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An AI incident. Abstract because every real incident is one of three kinds,
 * and each kind needs different details and different evidence.
 *
 * Three timestamps are kept apart on purpose:
 *   occurredAt - when it happened
 *   detectedAt - when someone first noticed
 *   awareAt    - when the organisation became aware; every legal clock starts here
 * awareAt is final: once set it can never change (the database enforces this too).
 */
public abstract class Incident {

    private int id;                       // 0 until saved
    private String refCode;               // PTV-2026-0001, assigned when saved
    private String title;
    private String description;
    private final int aiSystemId;
    private final int reportedBy;
    private final Instant occurredAt;
    private final Instant detectedAt;
    private final Instant awareAt;        // FINAL: anchors every obligation clock
    private Severity severity;
    private IncidentStatus status = IncidentStatus.REPORTED;
    private final Adversary adversary;
    private TriState cyberIncident = TriState.UNSURE;
    private final Set<String> tags = new LinkedHashSet<>();
    private String awareGapReason;        // needed when aware is more than an hour after detection
    private Integer linkedTo;             // an earlier incident this one follows up
    private String lateReportReason;      // needed when awareness was more than 7 days before the report

    /** Delaying awareness delays every legal clock, so a gap over this needs a written reason. */
    public static final long MAX_UNEXPLAINED_GAP_MINUTES = 60;
    /** Pass 3: reporting more than 7 days after awareness needs a reason (CHECK ck_late_report). */
    public static final long MAX_UNEXPLAINED_LATE_DAYS = 7;

    protected Incident(String title, String description, int aiSystemId, int reportedBy,
                       Instant occurredAt, Instant detectedAt, Instant awareAt,
                       Severity severity, Adversary adversary) {
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.aiSystemId = aiSystemId;
        this.reportedBy = reportedBy;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        this.awareAt = Objects.requireNonNull(awareAt, "awareAt");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.adversary = Objects.requireNonNull(adversary, "adversary");
        if (occurredAt.isAfter(detectedAt) || detectedAt.isAfter(awareAt)) {
            throw new IllegalArgumentException(
                "Times must be in order: occurred <= detected <= aware");
        }
    }

    /** Which subclass this is. */
    public abstract IncidentKind kind();

    /** Evidence this kind of incident should have before it can be assessed. */
    public abstract List<String> requiredEvidence();

    /** One line about the kind-specific details, for lists and reports. */
    public abstract String specificsSummary();

    // ---- getters ----
    public int getId() { return id; }
    public String getRefCode() { return refCode; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getAiSystemId() { return aiSystemId; }
    public int getReportedBy() { return reportedBy; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getAwareAt() { return awareAt; }
    public Severity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public Adversary getAdversary() { return adversary; }
    public TriState getCyberIncident() { return cyberIncident; }
    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }

    public String getAwareGapReason() { return awareGapReason; }
    public Integer getLinkedTo() { return linkedTo; }
    public String getLateReportReason() { return lateReportReason; }

    /** An "aware" date far in the past could put the incident before a rule was in force. */
    public boolean needsLateReason(java.time.Instant reportedAt) {
        return java.time.Duration.between(awareAt, reportedAt).toDays() > MAX_UNEXPLAINED_LATE_DAYS;
    }
    public void setLateReportReason(String reason) {
        if (id != 0) throw new IllegalStateException("Already saved; record an amendment instead");
        this.lateReportReason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public boolean needsGapReason() {
        return java.time.Duration.between(detectedAt, awareAt).toMinutes() > MAX_UNEXPLAINED_GAP_MINUTES;
    }

    /** Both are write-once: they can only be set before the incident is saved. */
    public void setAwareGapReason(String reason) {
        if (id != 0) throw new IllegalStateException("Already saved; record an amendment instead");
        this.awareGapReason = reason == null || reason.isBlank() ? null : reason.trim();
    }
    public void setLinkedTo(Integer incidentId) {
        if (id != 0) throw new IllegalStateException("Already saved");
        this.linkedTo = incidentId;
    }

    /** Called before saving. The database CHECK ck_aware_gap repeats this. */
    public void validateForSave() {
        if (needsGapReason() && (awareGapReason == null || awareGapReason.length() < 20))
            throw new IllegalArgumentException("The organisation became aware more than an hour after detection. "
                + "Explain the gap (at least 20 characters): every legal clock starts at awareness.");
        if (needsLateReason(java.time.Instant.now()) && (lateReportReason == null || lateReportReason.length() < 20))
            throw new IllegalArgumentException("The organisation became aware more than " + MAX_UNEXPLAINED_LATE_DAYS
                + " days ago. Explain why it is being reported only now (at least 20 characters).");
    }

    /** Used by the DAO when loading. */
    public void restoreExtras(String gapReason, String lateReason, Integer link) {
        this.awareGapReason = gapReason; this.lateReportReason = lateReason; this.linkedTo = link;
    }

    // ---- setters only for what is allowed to change ----

    /** Called once by the DAO after INSERT. A second call is a bug. */
    public void assignIdentity(int id, String refCode) {
        if (this.id != 0) throw new IllegalStateException("Incident already has an id");
        this.id = id;
        this.refCode = refCode;
    }

    /**
     * Changes status, but only along a legal edge of the workflow. The
     * conditions for a move (harms recorded, clocks handled...) are checked by
     * IncidentWorkflow before it calls this.
     */
    public void changeStatus(IncidentStatus to) {
        if (!pathivu.workflow.IncidentWorkflow.isLegal(status, to))
            throw new IllegalStateException("Illegal move " + status + " -> " + to);
        this.status = to;
    }

    /** Used by the DAO when loading from the database. */
    public void restoreStatus(IncidentStatus status) { this.status = status; }

    public void setSeverity(Severity severity) { this.severity = Objects.requireNonNull(severity); }
    public void setCyberIncident(TriState v) { this.cyberIncident = Objects.requireNonNull(v); }
    public void setTitle(String title) { this.title = requireText(title, "title"); }
    public void setDescription(String d) { this.description = requireText(d, "description"); }

    public void addTag(String tag) {
        String t = requireText(tag, "tag").toLowerCase().replaceAll("[^a-z0-9-]", "-");
        tags.add(t);
    }

    protected static String requireText(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " is required");
        return s.trim();
    }

    @Override public String toString() {
        return (refCode == null ? "(unsaved)" : refCode) + " " + title;
    }
}
