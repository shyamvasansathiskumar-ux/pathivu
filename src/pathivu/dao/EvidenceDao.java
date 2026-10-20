package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.Evidence;

public class EvidenceDao {
    public int insert(Connection c, int incidentId, String label, String fileName, String sha256,
                      long size, int addedBy) throws SQLException {
        String sql = "INSERT INTO evidence (incident_id, label, file_name, sha256, size_bytes, added_by) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, incidentId); ps.setString(2, label); ps.setString(3, fileName);
            ps.setString(4, sha256); ps.setLong(5, size); ps.setInt(6, addedBy);
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<Evidence> listFor(Connection c, int incidentId) throws SQLException {
        List<Evidence> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM evidence WHERE incident_id = ? ORDER BY evidence_id")) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Evidence(rs.getInt("evidence_id"), incidentId, rs.getString("label"),
                    rs.getString("file_name"), rs.getString("sha256"), rs.getLong("size_bytes"),
                    rs.getInt("added_by"), Jdbc.instant(rs, "added_at")));
            }
        }
        return out;
    }
}
