package pathivu.model;

import java.util.Objects;

/**
 * One harm caused by an incident. An incident can have many.
 * personalData is asked per harm because that is what decides DPDP duties.
 */
public record Harm(int id, int incidentId, HarmCategory category, String description,
                   int affectedCount, TriState personalData) {
    public Harm {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(personalData, "personal data answer");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("harm description is required");
        if (affectedCount < 0) throw new IllegalArgumentException("affected count cannot be negative");
        description = description.trim();
    }
    /** A harm that has not been saved yet. */
    public static Harm draft(HarmCategory c, String description, int affected, TriState personalData) {
        return new Harm(0, 0, c, description, affected, personalData);
    }
}
