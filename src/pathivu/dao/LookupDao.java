package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Reference data for drop-downs. */
public class LookupDao {
    public record AiSystemRow(int id, String name, String organization, String type) {
        @Override public String toString() { return name + " (" + organization + ")"; }
    }

    public List<AiSystemRow> aiSystems(Connection c) throws SQLException {
        List<AiSystemRow> out = new ArrayList<>();
        String sql = "SELECT s.ai_system_id, s.name, o.name AS org, s.system_type FROM ai_system s "
                   + "JOIN organization o ON o.org_id = s.org_id ORDER BY o.name, s.name";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(new AiSystemRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        }
        return out;
    }
}
