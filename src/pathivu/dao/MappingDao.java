package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

public class MappingDao {

    public boolean exists(Connection c, int incidentId, int nodeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM incident_mapping WHERE incident_id = ? AND node_id = ?")) {
            ps.setInt(1, incidentId); ps.setInt(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** byEngine: true when the suggestion engine proposed it, false when a person typed it in. */
    public void insertSuggestion(Connection c, int incidentId, int nodeId, String rationale,
                                 Confidence conf, int mappedBy, boolean byEngine) throws SQLException {
        String sql = "INSERT INTO incident_mapping (incident_id, node_id, rationale, confidence, mapped_by, source) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, incidentId); ps.setInt(2, nodeId); ps.setString(3, rationale);
            ps.setString(4, conf.name()); ps.setInt(5, mappedBy); ps.setString(6, byEngine ? "ENGINE" : "PERSON");
            ps.executeUpdate();
        }
    }

    /** reviewed_at is set by the trigger from the database clock. */
    public void review(Connection c, int incidentId, int nodeId, MappingState state, int reviewer) throws SQLException {
        String sql = "UPDATE incident_mapping SET state = ?, reviewed_by = ? WHERE incident_id = ? AND node_id = ? AND state = 'SUGGESTED'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, state.name()); ps.setInt(2, reviewer); ps.setInt(3, incidentId); ps.setInt(4, nodeId);
            if (ps.executeUpdate() != 1) throw new SQLException("That mapping is not waiting for review");
        }
    }

    public List<Mapping> listFor(Connection c, int incidentId) throws SQLException {
        List<Mapping> out = new ArrayList<>();
        String sql = "SELECT m.*, n.node_id, n.version_id, v.framework, v.label, n.code, n.name FROM incident_mapping m "
                   + "JOIN taxonomy_node n ON n.node_id = m.node_id JOIN taxonomy_version v ON v.version_id = n.version_id "
                   + "WHERE m.incident_id = ? ORDER BY v.framework, n.code";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Mapping(incidentId, TaxonomyDao.map(rs),
                    MappingState.valueOf(rs.getString("state")), rs.getString("rationale"),
                    Confidence.valueOf(rs.getString("confidence")), rs.getInt("mapped_by"),
                    Jdbc.intOrNull(rs, "reviewed_by")));
            }
        }
        return out;
    }
}
