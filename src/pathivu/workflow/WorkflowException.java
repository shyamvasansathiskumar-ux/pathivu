package pathivu.workflow;

import java.util.List;

/** A move was refused. Carries every reason, so the screen can show them all at once. */
public class WorkflowException extends RuntimeException {
    private final List<String> blockers;
    public WorkflowException(List<String> blockers) {
        super(String.join("\n", blockers));
        this.blockers = List.copyOf(blockers);
    }
    public List<String> blockers() { return blockers; }
}
