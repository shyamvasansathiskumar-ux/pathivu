package pathivu.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import pathivu.model.Harm;
import pathivu.model.Incident;
import pathivu.model.RuleVersion;

/**
 * A legal duty that may start a clock. Abstract class + two subclasses:
 * the start-date check and the deadline arithmetic are the same for every
 * rule, but WHAT triggers the rule differs (cyber incident vs personal data).
 */
public abstract class ObligationRule {

    protected final RuleVersion version;

    protected ObligationRule(RuleVersion version) { this.version = version; }

    public RuleVersion version() { return version; }

    /** Was this rule law at that moment? Decided by when the organisation became aware. */
    public boolean inForceAt(Instant awareAt) { return !awareAt.isBefore(version.effectiveFrom()); }

    /** Does the incident have the facts that trigger this duty? */
    protected abstract boolean triggeredBy(Incident incident, List<Harm> harms);

    /** Plain words for why it does or does not apply. */
    protected abstract String triggerReason(Incident incident, List<Harm> harms, boolean triggered);

    public final boolean appliesTo(Incident incident, List<Harm> harms) {
        return appliesAt(incident, harms, incident.getAwareAt());
    }

    /** Same question, but as if awareness happened at another time. Used for the "what if" preview. */
    public final boolean appliesAt(Incident incident, List<Harm> harms, Instant awareAt) {
        return inForceAt(awareAt) && triggeredBy(incident, harms);
    }

    public final Instant dueAt(Instant start) {
        Integer w = version.windowMinutes();
        return w == null ? start : start.plus(Duration.ofMinutes(w));
    }

    public final String explain(Incident incident, List<Harm> harms, Instant awareAt) {
        if (!inForceAt(awareAt)) return "not in force yet (starts " + pathivu.util.Times.ist(version.effectiveFrom()) + ")";
        boolean t = triggeredBy(incident, harms);
        return (t ? "applies: " : "does not apply: ") + triggerReason(incident, harms, t);
    }
}
