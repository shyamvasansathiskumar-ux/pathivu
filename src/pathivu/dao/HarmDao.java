package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

public class HarmDao {
    public int insert(Connection c, int incidentId, Harm h, int recordedBy) throws SQLException {
        String sql = "INSERT INTO harm (incident_id, category, description, affected_count, personal_data, recorded_by) "
                   + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, incidentId); ps.setString(2, h.category().name()); ps.setString(3, h.description());
            ps.setInt(4, h.affectedCount()); ps.setString(5, h.personalData().name()); ps.setInt(6, recordedBy);
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<Harm> listFor(Connection c, int incidentId) throws SQLException {
        List<Harm> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM harm WHERE incident_id = ? ORDER BY harm_id")) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Harm(rs.getInt("harm_id"), incidentId,
                    HarmCategory.valueOf(rs.getString("category")), rs.getString("description"),
                    rs.getInt("affected_count"), TriState.valueOf(rs.getString("personal_data"))));
            }
        }
        return out;
    }
}
