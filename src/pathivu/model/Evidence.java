package pathivu.model;

import java.time.Instant;

/** A file attached to an incident. Only its SHA-256 fingerprint is stored, so later tampering is detectable. */
public record Evidence(int id, int incidentId, String label, String fileName, String sha256,
                       long sizeBytes, int addedBy, Instant addedAt) { }
