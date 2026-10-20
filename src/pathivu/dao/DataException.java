package pathivu.dao;

import java.sql.SQLException;

/**
 * A database error turned into something a person can read.
 * SQLSTATE 45000 is what our triggers raise with SIGNAL, so its message is
 * already written for people ("a clock cannot be moved or reset").
 */
public class DataException extends RuntimeException {

    private final boolean ruleViolation;

    private DataException(String message, boolean ruleViolation, Throwable cause) {
        super(message, cause);
        this.ruleViolation = ruleViolation;
    }

    /** True when the database refused because of one of Pathivu's own rules. */
    public boolean isRuleViolation() { return ruleViolation; }

    public static DataException from(SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if ("45000".equals(state)) return new DataException("Refused by the database: " + clean(msg), true, e);
        if (e.getErrorCode() == 1142 || e.getErrorCode() == 1143)
            return new DataException("Refused: the app's database account has no permission for that ("
                                     + clean(msg) + ")", true, e);
        if (e.getErrorCode() == 3819) return new DataException("Refused by a CHECK constraint: " + clean(msg), true, e);
        if (e.getErrorCode() == 1062) return new DataException("That already exists: " + clean(msg), true, e);
        if (state.startsWith("08")) return new DataException("Cannot reach the database. Is MySQL running? " + clean(msg), false, e);
        return new DataException("Database error: " + clean(msg), false, e);
    }

    /** Drivers prefix messages with their own tags; keep only the useful part. */
    private static String clean(String m) {
        int i = m.lastIndexOf(") ");
        String s = (m.startsWith("(") && i > 0) ? m.substring(i + 2) : m;
        return s.replaceFirst("^\\[[^]]*]", "").trim();
    }
}
