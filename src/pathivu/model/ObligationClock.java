package pathivu.model;

import java.time.Duration;
import java.time.Instant;

/**
 * A legal deadline attached to an incident. There are no setters for startedAt
 * or dueAt: a clock cannot be moved. Its outcome changes only through
 * ObligationService (sent notice → MET, reviewer with reason → RULED_OUT).
 */
public record ObligationClock(int id, int incidentId, RuleVersion rule, Instant startedAt, Instant dueAt,
                              ClockState state, Instant metAt, Integer ruledOutBy, String ruledOutReason,
                              Instant extendedDueAt) {

    /** dueAt never changes. A regulator's written extension is stored beside it; the later one counts. */
    public Instant effectiveDue() {
        return extendedDueAt != null && extendedDueAt.isAfter(dueAt) ? extendedDueAt : dueAt;
    }

    /** The same CASE expression as v_clock_status, in Java. */
    public LiveStatus liveStatus(Instant now) {
        Instant due = effectiveDue();
        return switch (state) {
            case RULED_OUT -> LiveStatus.RULED_OUT;
            case MET -> metAt.isAfter(due) ? LiveStatus.MET_LATE : LiveStatus.MET;
            case RUNNING -> now.isAfter(due) ? LiveStatus.OVERDUE : LiveStatus.RUNNING;
        };
    }

    /** Negative when overdue. */
    public Duration timeLeft(Instant now) { return Duration.between(now, effectiveDue()); }
}
