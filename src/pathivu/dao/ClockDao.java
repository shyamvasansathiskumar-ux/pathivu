package pathivu.dao;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

public class ClockDao {

    private static final String BASE = "SELECT c.*, r.*, (SELECT MAX(e.new_due_at) FROM clock_extension e WHERE e.clock_id = c.clock_id) AS extended_due_at "
                                     + "FROM obligation_clock c JOIN rule_version r ON r.rule_id = c.rule_id ";

    public int insert(Connection c, int incidentId, RuleVersion rule, Instant startedAt, Instant dueAt) throws SQLException {
        String sql = "INSERT INTO obligation_clock (incident_id, rule_id, started_at, due_at) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, incidentId); ps.setInt(2, rule.id());
            Jdbc.setInstant(ps, 3, startedAt); Jdbc.setInstant(ps, 4, dueAt);
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<ObligationClock> listFor(Connection c, int incidentId) throws SQLException {
        List<ObligationClock> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(BASE + "WHERE c.incident_id = ? ORDER BY c.due_at")) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    public ObligationClock find(Connection c, int clockId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(BASE + "WHERE c.clock_id = ?")) {
            ps.setInt(1, clockId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    /** ruled_out_at is set by the trigger. */
    public void ruleOut(Connection c, int clockId, int reviewer, String reason) throws SQLException {
        String sql = "UPDATE obligation_clock SET status = 'RULED_OUT', ruled_out_by = ?, ruled_out_reason = ?, "
                   + "ruled_out_at = UTC_TIMESTAMP() WHERE clock_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reviewer); ps.setString(2, reason); ps.setInt(3, clockId);
            ps.executeUpdate();
        }
    }

    /** met_at is copied by the trigger from the sent notice; the value written here is overwritten. */
    public void markMet(Connection c, int clockId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE obligation_clock SET status = 'MET', met_at = UTC_TIMESTAMP() WHERE clock_id = ?")) {
            ps.setInt(1, clockId); ps.executeUpdate();
        }
    }

    /** The regulator allowed more time in writing. The trigger checks the rule allows it and the clock is not overdue. */
    public void insertExtension(Connection c, int clockId, Instant newDue, String boardRef, String reason, int by) throws SQLException {
        String sql = "INSERT INTO clock_extension (clock_id, new_due_at, board_reference, reason, recorded_by) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clockId); Jdbc.setInstant(ps, 2, newDue); ps.setString(3, boardRef); ps.setString(4, reason); ps.setInt(5, by);
            ps.executeUpdate();
        }
    }

    /** One row of the deadlines screen, straight from the view. */
    public record LiveRow(int clockId, int incidentId, String ref, String title, String ruleCode, String ruleName,
                          String recipient, Instant startedAt, Instant dueAt, String liveStatus, long minutesLeft) { }

    public List<LiveRow> live(Connection c) throws SQLException {
        List<LiveRow> out = new ArrayList<>();
        String sql = "SELECT * FROM v_clock_status ORDER BY FIELD(live_status,'OVERDUE','RUNNING','MET_LATE','MET','RULED_OUT'), due_at";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(new LiveRow(rs.getInt("clock_id"), rs.getInt("incident_id"), rs.getString("ref_code"),
                rs.getString("title"), rs.getString("rule_code"), rs.getString("rule_name"), rs.getString("recipient"),
                Jdbc.instant(rs, "started_at"), Jdbc.instant(rs, "effective_due_at"), rs.getString("live_status"),
                rs.getLong("minutes_left")));
        }
        return out;
    }

    private ObligationClock map(ResultSet rs) throws SQLException {
        return new ObligationClock(rs.getInt("clock_id"), rs.getInt("incident_id"), RuleDao.map(rs),
            Jdbc.instant(rs, "started_at"), Jdbc.instant(rs, "due_at"), ClockState.valueOf(rs.getString("status")),
            Jdbc.instant(rs, "met_at"), Jdbc.intOrNull(rs, "ruled_out_by"), rs.getString("ruled_out_reason"),
            Jdbc.instant(rs, "extended_due_at"));
    }
}
