package pathivu.model;

import java.time.Instant;

/**
 * A legal duty with a start date. windowMinutes == null means "without delay":
 * the deadline is the moment of awareness itself.
 * regime is plain text (CERT_IN, DPDP...), so a new regulator is a new row, not new code.
 * extendable: the regulator may allow more time in writing (DPDP Rule 7(2)(b)).
 */
public record RuleVersion(int id, String code, String regime, String name, String recipient,
                          Instant effectiveFrom, Integer windowMinutes, RuleTrigger triggerOn, String citation,
                          boolean extendable) {
    public boolean withoutDelay() { return windowMinutes == null; }
    public String windowText() {
        if (windowMinutes == null) return "without delay";
        if (windowMinutes % 60 == 0) return (windowMinutes / 60) + " hours";
        return windowMinutes + " minutes";
    }
}
