package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

public class RuleDao {
    public List<RuleVersion> listAll(Connection c) throws SQLException {
        List<RuleVersion> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rule_version ORDER BY rule_id")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    static RuleVersion map(ResultSet rs) throws SQLException {
        return new RuleVersion(rs.getInt("rule_id"), rs.getString("code"), rs.getString("regime"),
            rs.getString("name"), rs.getString("recipient"), Jdbc.instant(rs, "effective_from"),
            Jdbc.intOrNull(rs, "window_minutes"), RuleTrigger.valueOf(rs.getString("trigger_on")), rs.getString("citation"),
            rs.getBoolean("extendable"));
    }
}
