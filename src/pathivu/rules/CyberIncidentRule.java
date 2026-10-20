package pathivu.rules;

import java.util.List;
import pathivu.model.Harm;
import pathivu.model.Incident;
import pathivu.model.RuleVersion;

/** CERT-In: runs unless someone has answered "not a cyber security incident". UNSURE keeps it running. */
public class CyberIncidentRule extends ObligationRule {
    public CyberIncidentRule(RuleVersion v) { super(v); }

    @Override protected boolean triggeredBy(Incident incident, List<Harm> harms) {
        return incident.getCyberIncident().mightApply();
    }

    @Override protected String triggerReason(Incident incident, List<Harm> harms, boolean triggered) {
        return "cyber security incident answer is " + incident.getCyberIncident()
             + (triggered ? " (UNSURE counts as yes until a reviewer rules the clock out)" : "");
    }
}
