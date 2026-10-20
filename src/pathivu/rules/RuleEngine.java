package pathivu.rules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.Harm;
import pathivu.model.Incident;
import pathivu.model.RuleVersion;

/** Decides which legal clocks an incident needs. Pure logic, no database, so it is easy to test. */
public class RuleEngine {

    private final List<ObligationRule> rules = new ArrayList<>();

    public RuleEngine(List<RuleVersion> versions) {
        for (RuleVersion v : versions) rules.add(from(v));
    }

    /** Factory: the rule's trigger decides which subclass handles it. */
    public static ObligationRule from(RuleVersion v) {
        return switch (v.triggerOn()) {
            case CYBER_INCIDENT -> new CyberIncidentRule(v);
            case PERSONAL_DATA -> new PersonalDataRule(v);
        };
    }

    public List<ObligationRule> rules() { return List.copyOf(rules); }

    /** Rules that need a clock right now. */
    public List<ObligationRule> applicable(Incident incident, List<Harm> harms) {
        return rules.stream().filter(r -> r.appliesTo(incident, harms)).toList();
    }

    /** One line per rule for a hypothetical awareness time: shows effective dating at work. */
    public List<String> preview(Incident incident, List<Harm> harms, Instant hypotheticalAware) {
        List<String> out = new ArrayList<>();
        for (ObligationRule r : rules) {
            String due = r.appliesAt(incident, harms, hypotheticalAware)
                       ? " → due " + pathivu.util.Times.ist(r.dueAt(hypotheticalAware)) : "";
            out.add(r.version().code() + " (" + r.version().windowText() + "): "
                    + r.explain(incident, harms, hypotheticalAware) + due);
        }
        return out;
    }
}
