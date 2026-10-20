package pathivu.service;

import java.util.List;
import pathivu.model.*;

/** Everything about one incident, loaded together for the detail screen and the report. */
public record IncidentFile(Incident incident, String aiSystem, List<Harm> harms, List<Evidence> evidence,
                           List<Mapping> mappings, List<ObligationClock> clocks, List<Notification> notices,
                           List<Amendment> amendments, List<AuditEvent> history) { }
