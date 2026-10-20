package pathivu.model;

/** A saved link between an incident and a framework category. */
public record Mapping(int incidentId, TaxonomyNode node, MappingState state, String rationale,
                      Confidence confidence, int mappedBy, Integer reviewedBy) { }
