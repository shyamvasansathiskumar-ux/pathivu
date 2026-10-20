package pathivu.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pathivu.dao.*;
import pathivu.mapping.MapperRegistry;
import pathivu.model.*;
import pathivu.rules.ObligationRule;
import pathivu.rules.RuleEngine;

/**
 * The two things Pathivu does automatically whenever an incident's facts change.
 * Both only ADD, never remove:
 *   1. start any legal clock that now applies (an existing clock is never deleted,
 *      even if the facts change; a reviewer must rule it out with a reason)
 *   2. add mapping suggestions that are not there yet (a reviewer decides on them)
 */
final class Automations {
    private final IncidentDao incidents = new IncidentDao();
    private final HarmDao harms = new HarmDao();
    private final RuleDao rules = new RuleDao();
    private final ClockDao clocks = new ClockDao();
    private final TaxonomyDao taxonomy = new TaxonomyDao();
    private final MappingDao mappings = new MappingDao();
    private final AuditDao audit = new AuditDao();
    private final MapperRegistry mappers = new MapperRegistry();

    /** Returns how many new clocks were started. */
    int syncClocks(Connection c, int incidentId, int actorId) throws SQLException {
        Incident i = incidents.find(c, incidentId);
        List<Harm> hs = harms.listFor(c, incidentId);
        Set<String> existing = new HashSet<>();
        for (ObligationClock k : clocks.listFor(c, incidentId)) existing.add(k.rule().code());
        int started = 0;
        for (ObligationRule r : new RuleEngine(rules.listAll(c)).applicable(i, hs)) {
            if (existing.contains(r.version().code())) continue;
            clocks.insert(c, incidentId, r.version(), i.getAwareAt(), r.dueAt(i.getAwareAt()));
            audit.append(c, incidentId, actorId, "CLOCK_STARTED",
                r.version().code() + " (" + r.version().windowText() + ") due " + pathivu.util.Times.ist(r.dueAt(i.getAwareAt())));
            started++;
        }
        return started;
    }

    /** Returns how many new suggestions were added. */
    int suggestMappings(Connection c, int incidentId, int actorId) throws SQLException {
        Incident i = incidents.find(c, incidentId);
        int added = 0;
        for (MappingSuggestion s : mappers.suggestAll(i, harms.listFor(c, incidentId))) {
            TaxonomyNode node = taxonomy.findCurrent(c, s.framework(), s.code());
            if (node == null || mappings.exists(c, incidentId, node.id())) continue;
            mappings.insertSuggestion(c, incidentId, node.id(), s.rationale(), s.confidence(), actorId);
            added++;
        }
        if (added > 0) audit.append(c, incidentId, actorId, "MAPPINGS_SUGGESTED", added + " suggestion(s) waiting for a reviewer");
        return added;
    }
}
