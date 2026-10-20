package pathivu.workflow;

/** What the workflow needs to know about an incident before allowing a move. Loaded by the service. */
public record WorkflowFacts(int harms, int evidence, int confirmedMappings, int suggestedMappings,
                            int unsureHarms, int runningClocks, int runningClocksWithoutApprovedNotice) { }
