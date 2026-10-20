package pathivu.model;

import java.time.Instant;

/** A notice to a regulator or to affected people, tied to one clock. */
public record Notification(int id, int clockId, String recipient, String body, int draftedBy, Instant draftedAt,
                           Integer approvedBy, Instant approvedAt, Instant sentAt) {
    public enum Stage { DRAFT, APPROVED, SENT }
    public Stage stage() {
        if (sentAt != null) return Stage.SENT;
        if (approvedBy != null) return Stage.APPROVED;
        return Stage.DRAFT;
    }
}
