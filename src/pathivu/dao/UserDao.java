package pathivu.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.AppUser;
import pathivu.model.Role;

public class UserDao {

    /** A user plus their stored hash, used only during login. */
    public record Credentials(AppUser user, String hash, String salt, boolean active) { }

    public Credentials findCredentials(Connection c, String username) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, password_hash, salt, active FROM app_user WHERE username = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Credentials(map(rs), rs.getString("password_hash"), rs.getString("salt"), rs.getBoolean("active"));
            }
        }
    }

    /** Only the admin connection (Setup) can do this; the app account has no INSERT on app_user. */
    public int insert(Connection c, String username, String fullName, Role role, String hash, String salt) throws SQLException {
        String sql = "INSERT INTO app_user (username, full_name, role, password_hash, salt) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username); ps.setString(2, fullName); ps.setString(3, role.name());
            ps.setString(4, hash); ps.setString(5, salt);
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<AppUser> listAll(Connection c) throws SQLException {
        List<AppUser> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, username, full_name, role FROM app_user ORDER BY user_id")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private AppUser map(ResultSet rs) throws SQLException {
        return new AppUser(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                           Role.valueOf(rs.getString("role")));
    }
}
