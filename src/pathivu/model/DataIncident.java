package pathivu.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Data was exposed, leaked or lost through an AI system. */
public class DataIncident extends Incident {

    private final String dataCategories;   // e.g. "names, phone numbers, trip history"
    private final int recordsAffected;
    private final TriState exfiltrated;    // did it leave the organisation?

    public DataIncident(String title, String description, int aiSystemId, int reportedBy,
                        Instant occurredAt, Instant detectedAt, Instant awareAt,
                        Severity severity, Adversary adversary,
                        String dataCategories, int recordsAffected, TriState exfiltrated) {
        super(title, description, aiSystemId, reportedBy, occurredAt, detectedAt, awareAt, severity, adversary);
        if (recordsAffected < 0) throw new IllegalArgumentException("records affected cannot be negative");
        this.dataCategories = requireText(dataCategories, "data categories");
        this.recordsAffected = recordsAffected;
        this.exfiltrated = Objects.requireNonNull(exfiltrated, "exfiltrated");
    }

    @Override public IncidentKind kind() { return IncidentKind.DATA; }

    @Override public List<String> requiredEvidence() {
        return List.of("Access log", "List of affected records (or a count method)");
    }

    @Override public String specificsSummary() {
        return recordsAffected + " records (" + dataCategories + "), exfiltrated: " + exfiltrated;
    }

    public String getDataCategories() { return dataCategories; }
    public int getRecordsAffected() { return recordsAffected; }
    public TriState getExfiltrated() { return exfiltrated; }
}
