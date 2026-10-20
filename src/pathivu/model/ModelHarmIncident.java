package pathivu.model;

import java.time.Instant;
import java.util.List;

/** A model's output harmed people: wrong information, a biased decision, harmful content. */
public class ModelHarmIncident extends Incident {

    private final OutputType outputType;
    private final String affectedGroup;

    public ModelHarmIncident(String title, String description, int aiSystemId, int reportedBy,
                             Instant occurredAt, Instant detectedAt, Instant awareAt,
                             Severity severity, Adversary adversary,
                             OutputType outputType, String affectedGroup) {
        super(title, description, aiSystemId, reportedBy, occurredAt, detectedAt, awareAt, severity, adversary);
        this.outputType = java.util.Objects.requireNonNull(outputType, "output type");
        this.affectedGroup = requireText(affectedGroup, "affected group");
    }

    @Override public IncidentKind kind() { return IncidentKind.MODEL_HARM; }

    @Override public List<String> requiredEvidence() {
        return List.of("Sample of the harmful output", "Model name and version");
    }

    @Override public String specificsSummary() {
        return outputType.name().toLowerCase().replace('_', ' ') + " affecting " + affectedGroup;
    }

    public OutputType getOutputType() { return outputType; }
    public String getAffectedGroup() { return affectedGroup; }
}
