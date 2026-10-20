package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.Framework;
import pathivu.model.TaxonomyNode;

public class TaxonomyDao {
    private static final String BASE = "SELECT n.node_id, n.version_id, v.framework, v.label, n.code, n.name "
                                     + "FROM taxonomy_node n JOIN taxonomy_version v ON v.version_id = n.version_id ";

    /** The node with this code in the CURRENT version of the framework (LLM03 means Excessive Agency in 2026). */
    public TaxonomyNode findCurrent(Connection c, Framework f, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(BASE + "WHERE v.framework = ? AND v.is_current AND n.code = ?")) {
            ps.setString(1, f.name()); ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public List<TaxonomyNode> listCurrent(Connection c) throws SQLException {
        List<TaxonomyNode> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(BASE + "WHERE v.is_current ORDER BY v.framework, n.node_id")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    static TaxonomyNode map(ResultSet rs) throws SQLException {
        return new TaxonomyNode(rs.getInt("node_id"), rs.getInt("version_id"), Framework.valueOf(rs.getString("framework")),
                                rs.getString("label"), rs.getString("code"), rs.getString("name"));
    }
}
