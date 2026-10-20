package pathivu.workflow;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pathivu.model.*;

/**
 * The investigation as a state machine.
 *
 *   REPORTED → TRIAGED → ASSESSED → NOTICES_REVIEWED → CLOSED
 *                 ↑_________|_____________|   (send back with a reason)
 *
 * Each forward move has conditions. The same transition table exists in the
 * database trigger trg_incident_bu, so even a manual UPDATE cannot skip a step.
 */
public final class IncidentWorkflow {

    private static final Map<IncidentStatus, Set<IncidentStatus>> MOVES = new EnumMap<>(IncidentStatus.class);
    static {
        MOVES.put(IncidentStatus.REPORTED, EnumSet.of(IncidentStatus.TRIAGED));
        MOVES.put(IncidentStatus.TRIAGED, EnumSet.of(IncidentStatus.ASSESSED));
        MOVES.put(IncidentStatus.ASSESSED, EnumSet.of(IncidentStatus.NOTICES_REVIEWED, IncidentStatus.TRIAGED));
        MOVES.put(IncidentStatus.NOTICES_REVIEWED, EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.TRIAGED));
        MOVES.put(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
    }

    private IncidentWorkflow() { }

    public static boolean isLegal(IncidentStatus from, IncidentStatus to) { return MOVES.get(from).contains(to); }

    public static Set<IncidentStatus> nextFrom(IncidentStatus from) {
        Set<IncidentStatus> s = EnumSet.noneOf(IncidentStatus.class);
        s.addAll(MOVES.get(from));
        return s;
    }

    /** Every reason the move is not allowed. Empty list = allowed. */
    public static List<String> blockers(Incident i, IncidentStatus to, AppUser by, WorkflowFacts f) {
        List<String> b = new ArrayList<>();
        IncidentStatus from = i.getStatus();
        if (!isLegal(from, to)) { b.add("Cannot move from " + from + " to " + to + "."); return b; }
        if (by.role() != Role.REVIEWER) b.add("Only a reviewer can move an incident through the workflow.");
        boolean back = to == IncidentStatus.TRIAGED && from != IncidentStatus.REPORTED;
        if (back) return b;   // sending back only needs a reviewer and a reason (checked by the service)
        switch (to) {
            case ASSESSED -> {
                if (f.harms() == 0) b.add("Record at least one harm.");
                if (f.evidence() == 0) b.add("Attach at least one piece of evidence.");
                if (f.confirmedMappings() == 0) b.add("Confirm at least one framework mapping.");
                if (i.getCyberIncident() == TriState.UNSURE) b.add("Answer the CERT-In question: is this a cyber security incident?");
                if (f.unsureHarms() > 0) b.add("Answer the personal-data question for " + f.unsureHarms() + " harm(s).");
            }
            case NOTICES_REVIEWED -> {
                if (f.runningClocksWithoutApprovedNotice() > 0)
                    b.add(f.runningClocksWithoutApprovedNotice() + " running clock(s) have no approved notice. Approve one or rule the clock out.");
            }
            case CLOSED -> {
                if (f.runningClocks() > 0) b.add(f.runningClocks() + " clock(s) are still running. Send the notice or rule the clock out.");
                if (f.suggestedMappings() > 0) b.add(f.suggestedMappings() + " mapping suggestion(s) are not reviewed yet.");
                if (by.id() == i.getReportedBy()) b.add("The person who reported an incident cannot close it.");
            }
            default -> { }
        }
        return b;
    }

    /** Moves the incident, or throws with every blocker. */
    public static void move(Incident i, IncidentStatus to, AppUser by, WorkflowFacts f) {
        List<String> b = blockers(i, to, by, f);
        if (!b.isEmpty()) throw new WorkflowException(b);
        i.changeStatus(to);
    }
}
