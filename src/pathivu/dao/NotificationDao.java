package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.Notification;

public class NotificationDao {

    public int insertDraft(Connection c, int clockId, String recipient, String body, int draftedBy) throws SQLException {
        String sql = "INSERT INTO notification (clock_id, recipient, body, drafted_by) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clockId); ps.setString(2, recipient); ps.setString(3, body); ps.setInt(4, draftedBy);
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    /** approved_at is set by the trigger; the CHECK constraint refuses approver = drafter. */
    public void approve(Connection c, int noticeId, int reviewer) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE notification SET approved_by = ? WHERE notification_id = ? AND approved_by IS NULL")) {
            ps.setInt(1, reviewer); ps.setInt(2, noticeId);
            if (ps.executeUpdate() != 1) throw new SQLException("That notice is already approved");
        }
    }

    /** Whatever time we pass, the trigger replaces it with the database clock. */
    public void markSent(Connection c, int noticeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE notification SET sent_at = UTC_TIMESTAMP() WHERE notification_id = ? AND sent_at IS NULL")) {
            ps.setInt(1, noticeId);
            if (ps.executeUpdate() != 1) throw new SQLException("That notice was already sent");
        }
    }

    public Notification find(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM notification WHERE notification_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public List<Notification> listForIncident(Connection c, int incidentId) throws SQLException {
        List<Notification> out = new ArrayList<>();
        String sql = "SELECT n.* FROM notification n JOIN obligation_clock c ON c.clock_id = n.clock_id "
                   + "WHERE c.incident_id = ? ORDER BY n.notification_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    private Notification map(ResultSet rs) throws SQLException {
        return new Notification(rs.getInt("notification_id"), rs.getInt("clock_id"), rs.getString("recipient"),
            rs.getString("body"), rs.getInt("drafted_by"), Jdbc.instant(rs, "drafted_at"),
            Jdbc.intOrNull(rs, "approved_by"), Jdbc.instant(rs, "approved_at"), Jdbc.instant(rs, "sent_at"));
    }
}
