package pathivu.dao;

import java.sql.*;

/** Sign-in history. Insert-only: the trigger stamps the time and refuses edits and deletes. */
public class LoginDao {

    public void record(Connection c, String username, boolean success) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO login_attempt (username, success) VALUES (?,?)")) {
            ps.setString(1, cut(username)); ps.setBoolean(2, success); ps.executeUpdate();
        }
    }

    /**
     * Failures for this username in the last `minutes`. Pass 3: a success no longer resets
     * the count. The app account can insert rows, so a forged "success" row would otherwise
     * wipe out an attacker's failures and reopen the account to guessing.
     */
    public int recentFailures(Connection c, String username, int minutes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM login_attempt a WHERE a.username = ? AND NOT a.success "
                   + "AND a.at > UTC_TIMESTAMP(3) - INTERVAL ? MINUTE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cut(username)); ps.setInt(2, minutes);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static String cut(String u) { return u.length() > 40 ? u.substring(0, 40) : u; }
}
