package pathivu.model;

import java.time.Instant;

/** A correction recorded next to the original value. The original is never overwritten for write-once fields. */
public record Amendment(int id, int incidentId, String field, String oldValue, String newValue,
                        String reason, int amendedBy, Instant amendedAt) { }
