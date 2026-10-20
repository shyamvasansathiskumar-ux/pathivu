package pathivu.model;

import java.time.Instant;
import java.util.List;

/** An AI agent did something it should not have: deleted data, used the wrong tool, went beyond its task. */
public class AgentIncident extends Incident {

    private final String agentName;
    private final String instructionGiven;     // what it was asked to do
    private final String actionTaken;          // what it actually did
    private final boolean credentialBeyondTask; // it held more access than the task needed
    private final boolean hadConfirmationStep;  // a human had to approve destructive actions

    public AgentIncident(String title, String description, int aiSystemId, int reportedBy,
                         Instant occurredAt, Instant detectedAt, Instant awareAt,
                         Severity severity, Adversary adversary,
                         String agentName, String instructionGiven, String actionTaken,
                         boolean credentialBeyondTask, boolean hadConfirmationStep) {
        super(title, description, aiSystemId, reportedBy, occurredAt, detectedAt, awareAt, severity, adversary);
        this.agentName = requireText(agentName, "agent name");
        this.instructionGiven = requireText(instructionGiven, "instruction given");
        this.actionTaken = requireText(actionTaken, "action taken");
        this.credentialBeyondTask = credentialBeyondTask;
        this.hadConfirmationStep = hadConfirmationStep;
    }

    @Override public IncidentKind kind() { return IncidentKind.AGENT; }

    @Override public List<String> requiredEvidence() {
        return List.of("Agent transcript", "Tool / API call log", "Credential scope at the time");
    }

    @Override public String specificsSummary() {
        return agentName + " was asked to \"" + instructionGiven + "\" and instead " + actionTaken
             + (credentialBeyondTask ? "; it held access beyond the task" : "")
             + (hadConfirmationStep ? "" : "; no human confirmation step");
    }

    public String getAgentName() { return agentName; }
    public String getInstructionGiven() { return instructionGiven; }
    public String getActionTaken() { return actionTaken; }
    public boolean isCredentialBeyondTask() { return credentialBeyondTask; }
    public boolean isHadConfirmationStep() { return hadConfirmationStep; }
}
