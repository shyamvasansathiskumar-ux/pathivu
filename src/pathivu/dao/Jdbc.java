package pathivu.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import pathivu.util.Times;

/** Small helpers so every DAO handles UTC times and NULLs the same way. */
final class Jdbc {
    private Jdbc() { }

    static void setInstant(PreparedStatement ps, int i, Instant t) throws SQLException {
        if (t == null) ps.setNull(i, Types.TIMESTAMP);
        else ps.setObject(i, Times.toUtc(t));
    }

    static Instant instant(ResultSet rs, String col) throws SQLException {
        return Times.fromUtc(rs.getObject(col, LocalDateTime.class));
    }

    static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static void setIntOrNull(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    static int generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet k = ps.getGeneratedKeys()) {
            if (!k.next()) throw new SQLException("No id was generated");
            return k.getInt(1);
        }
    }
}
