package pathivu.rules;

import java.util.List;
import pathivu.model.Harm;
import pathivu.model.Incident;
import pathivu.model.IncidentKind;
import pathivu.model.RuleVersion;

/**
 * DPDP Rule 7: runs when any harm involves (or might involve) personal data.
 * A DATA incident with no harms recorded yet is treated as "might": the
 * question has not been answered, and an unanswered question must not
 * silently switch a legal duty off.
 */
public class PersonalDataRule extends ObligationRule {
    public PersonalDataRule(RuleVersion v) { super(v); }

    @Override protected boolean triggeredBy(Incident incident, List<Harm> harms) {
        if (harms.isEmpty()) return incident.kind() == IncidentKind.DATA;
        return harms.stream().anyMatch(h -> h.personalData().mightApply());
    }

    @Override protected String triggerReason(Incident incident, List<Harm> harms, boolean triggered) {
        if (harms.isEmpty()) return triggered ? "data incident with no harms recorded yet" : "no harms recorded";
        long n = harms.stream().filter(h -> h.personalData().mightApply()).count();
        return n + " of " + harms.size() + " harms involve or may involve personal data";
    }
}
