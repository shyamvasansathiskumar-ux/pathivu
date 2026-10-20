package pathivu.model;

import java.time.Instant;

/** One row of the hash-chained audit trail. */
public record AuditEvent(long id, Integer incidentId, int actorId, String action, String details,
                         Instant createdAt, String prevHash, String hash) { }
