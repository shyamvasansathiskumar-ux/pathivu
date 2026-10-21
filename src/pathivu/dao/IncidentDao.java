package pathivu.dao;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;
import pathivu.util.Times;

/**
 * Saves and loads incidents. One Java class hierarchy maps to four tables
 * (class-table inheritance): the common part goes to `incident`, the
 * kind-specific part to agent_incident / model_harm_incident / data_incident,
 * all sharing the same incident_id.
 */
public class IncidentDao {

    public int insert(Connection c, Incident i) throws SQLException {
        i.validateForSave();
        String sql = "INSERT INTO incident (kind, title, description, ai_system_id, reported_by, occurred_at, "
                   + "detected_at, aware_at, severity, adversary, cyber_incident, aware_gap_reason, linked_to, late_report_reason) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int id;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, i.kind().name());
            ps.setString(2, i.getTitle());
            ps.setString(3, i.getDescription());
            ps.setInt(4, i.getAiSystemId());
            ps.setInt(5, i.getReportedBy());
            Jdbc.setInstant(ps, 6, i.getOccurredAt());
            Jdbc.setInstant(ps, 7, i.getDetectedAt());
            Jdbc.setInstant(ps, 8, i.getAwareAt());
            ps.setString(9, i.getSeverity().name());
            ps.setString(10, i.getAdversary().name());
            ps.setString(11, i.getCyberIncident().name());
            ps.setString(12, i.getAwareGapReason());
            Jdbc.setIntOrNull(ps, 13, i.getLinkedTo());
            ps.setString(14, i.getLateReportReason());
            ps.executeUpdate();
            id = Jdbc.generatedId(ps);
        }
        insertSubtype(c, id, i);
        for (String tag : i.getTags()) addTag(c, id, tag);

        // Reference code uses the year of awareness in IST: PTV-2026-0007
        String ref = String.format("PTV-%d-%04d", i.getAwareAt().atZone(Times.IST).getYear(), id);
        try (PreparedStatement ps = c.prepareStatement("UPDATE incident SET ref_code = ? WHERE incident_id = ?")) {
            ps.setString(1, ref); ps.setInt(2, id); ps.executeUpdate();
        }
        i.assignIdentity(id, ref);
        return id;
    }

    /** Polymorphism in the DAO: the Java subclass decides which table gets the details. */
    private void insertSubtype(Connection c, int id, Incident i) throws SQLException {
        if (i instanceof AgentIncident a) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO agent_incident VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, id); ps.setString(2, a.getAgentName()); ps.setString(3, a.getInstructionGiven());
                ps.setString(4, a.getActionTaken()); ps.setBoolean(5, a.isCredentialBeyondTask());
                ps.setBoolean(6, a.isHadConfirmationStep());
                ps.executeUpdate();
            }
        } else if (i instanceof ModelHarmIncident m) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO model_harm_incident VALUES (?,?,?)")) {
                ps.setInt(1, id); ps.setString(2, m.getOutputType().name()); ps.setString(3, m.getAffectedGroup());
                ps.executeUpdate();
            }
        } else if (i instanceof DataIncident d) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO data_incident VALUES (?,?,?,?)")) {
                ps.setInt(1, id); ps.setString(2, d.getDataCategories()); ps.setInt(3, d.getRecordsAffected());
                ps.setString(4, d.getExfiltrated().name());
                ps.executeUpdate();
            }
        } else {
            throw new IllegalArgumentException("Unknown incident type " + i.getClass());
        }
    }

    public void addTag(Connection c, int incidentId, String tag) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO incident_tag VALUES (?,?)")) {
            ps.setInt(1, incidentId); ps.setString(2, tag); ps.executeUpdate();
        }
    }

    /** One query with LEFT JOINs to all three subtype tables; the kind column says which one is filled. */
    public Incident find(Connection c, int id) throws SQLException {
        String sql = "SELECT i.*, a.agent_name, a.instruction_given, a.action_taken, a.credential_beyond_task, "
                   + "a.had_confirmation_step, m.output_type, m.affected_group, d.data_categories, "
                   + "d.records_affected, d.exfiltrated "
                   + "FROM incident i "
                   + "LEFT JOIN agent_incident a      ON a.incident_id = i.incident_id "
                   + "LEFT JOIN model_harm_incident m ON m.incident_id = i.incident_id "
                   + "LEFT JOIN data_incident d       ON d.incident_id = i.incident_id "
                   + "WHERE i.incident_id = ?";
        Incident inc;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                inc = map(rs);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT tag FROM incident_tag WHERE incident_id = ? ORDER BY tag")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) inc.addTag(rs.getString(1)); }
        }
        return inc;
    }

    /** Locks the incident row for the rest of the transaction, so two reviewers cannot move it at once. */
    /** Finds an incident id from its reference code, e.g. PTV-2026-0003. */
    public Integer idForRef(Connection c, String ref) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT incident_id FROM incident WHERE ref_code = ?")) {
            ps.setString(1, ref.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : null; }
        }
    }

    public void lock(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT incident_id FROM incident WHERE incident_id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("No incident " + id); }
        }
    }

    private Incident map(ResultSet rs) throws SQLException {
        String title = rs.getString("title"), desc = rs.getString("description");
        int sys = rs.getInt("ai_system_id"), by = rs.getInt("reported_by");
        Instant occ = Jdbc.instant(rs, "occurred_at"), det = Jdbc.instant(rs, "detected_at"), aw = Jdbc.instant(rs, "aware_at");
        Severity sev = Severity.valueOf(rs.getString("severity"));
        Adversary adv = Adversary.valueOf(rs.getString("adversary"));
        Incident i = switch (IncidentKind.valueOf(rs.getString("kind"))) {
            case AGENT -> new AgentIncident(title, desc, sys, by, occ, det, aw, sev, adv,
                    rs.getString("agent_name"), rs.getString("instruction_given"), rs.getString("action_taken"),
                    rs.getBoolean("credential_beyond_task"), rs.getBoolean("had_confirmation_step"));
            case MODEL_HARM -> new ModelHarmIncident(title, desc, sys, by, occ, det, aw, sev, adv,
                    OutputType.valueOf(rs.getString("output_type")), rs.getString("affected_group"));
            case DATA -> new DataIncident(title, desc, sys, by, occ, det, aw, sev, adv,
                    rs.getString("data_categories"), rs.getInt("records_affected"),
                    TriState.valueOf(rs.getString("exfiltrated")));
        };
        i.assignIdentity(rs.getInt("incident_id"), rs.getString("ref_code"));
        i.restoreStatus(IncidentStatus.valueOf(rs.getString("status")));
        i.setCyberIncident(TriState.valueOf(rs.getString("cyber_incident")));
        i.restoreExtras(rs.getString("aware_gap_reason"), rs.getString("late_report_reason"), Jdbc.intOrNull(rs, "linked_to"));
        return i;
    }

    public void updateStatus(Connection c, int id, IncidentStatus status) throws SQLException {
        update(c, "UPDATE incident SET status = ? WHERE incident_id = ?", status.name(), id);
    }

    /** Only for fields the database allows to change. The trigger demands an amendment row first. */
    public void updateField(Connection c, int id, String column, String value) throws SQLException {
        if (!List.of("title", "description", "severity", "cyber_incident", "adversary").contains(column))
            throw new IllegalArgumentException(column + " cannot be changed");
        update(c, "UPDATE incident SET " + column + " = ? WHERE incident_id = ?", value, id);   // column is whitelisted above
    }

    private void update(Connection c, String sql, String v, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, v); ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new SQLException("Incident " + id + " not found");
        }
    }

    /** One row of the register screen (from the view v_incident_register). */
    public record RegisterRow(int id, String ref, String kind, String title, String severity, String status,
                              Instant awareAt, String organization, String aiSystem, String reporter,
                              int harms, int confirmedMappings, int openClocks) { }

    public List<RegisterRow> register(Connection c) throws SQLException {
        List<RegisterRow> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM v_incident_register ORDER BY aware_at DESC")) {
            while (rs.next()) out.add(new RegisterRow(rs.getInt("incident_id"), rs.getString("ref_code"),
                rs.getString("kind"), rs.getString("title"), rs.getString("severity"), rs.getString("status"),
                Jdbc.instant(rs, "aware_at"), rs.getString("organization"), rs.getString("ai_system"),
                rs.getString("reporter"), rs.getInt("harms"), rs.getInt("confirmed_mappings"), rs.getInt("open_clocks")));
        }
        return out;
    }
}
