package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.Amendment;

public class AmendmentDao {
    public void insert(Connection c, int incidentId, String field, String oldV, String newV,
                       String reason, int by) throws SQLException {
        String sql = "INSERT INTO incident_amendment (incident_id, field_name, old_value, new_value, reason, amended_by) "
                   + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, incidentId); ps.setString(2, field); ps.setString(3, cut(oldV)); ps.setString(4, cut(newV));
            ps.setString(5, reason); ps.setInt(6, by);
            ps.executeUpdate();
        }
    }

    private static String cut(String s) { return s == null || s.length() <= 255 ? s : s.substring(0, 252) + "..."; }

    public List<Amendment> listFor(Connection c, int incidentId) throws SQLException {
        List<Amendment> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM incident_amendment WHERE incident_id = ? ORDER BY amendment_id")) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Amendment(rs.getInt("amendment_id"), incidentId, rs.getString("field_name"),
                    rs.getString("old_value"), rs.getString("new_value"), rs.getString("reason"),
                    rs.getInt("amended_by"), Jdbc.instant(rs, "amended_at")));
            }
        }
        return out;
    }
}
