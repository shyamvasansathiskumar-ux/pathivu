package pathivu.dao;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import pathivu.model.AuditEvent;
import pathivu.security.Sha256;

/**
 * The hash-chained audit trail.
 *
 * Each event stores the hash of the event before it. Change or remove one old
 * row and every hash after it stops matching, so verify() finds the exact
 * point where history was altered.
 *
 * Concurrency: append() locks the single chain-head row with FOR UPDATE,
 * so two people saving at the same moment are put in a queue instead of both
 * extending the chain from the same parent (which would fork it).
 */
public class AuditDao {

    /** Must match DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') in trg_audit_bi. */
    private static final DateTimeFormatter HASH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    public static String computeHash(String prevHash, Integer incidentId, int actorId, String action,
                                     String details, Instant createdAt) {
        String time = HASH_TIME.format(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return Sha256.of(String.join("|", prevHash, incidentId == null ? "-" : incidentId.toString(),
                                     Integer.toString(actorId), action, details, time));
    }

    /** Call inside the same transaction as the change it describes: both are saved, or neither. */
    public void append(Connection c, Integer incidentId, int actorId, String action, String details) throws SQLException {
        if (details.length() > 500) details = details.substring(0, 497) + "...";
        String prev;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE")) {
            rs.next();
            prev = rs.getString(1);
        }
        Instant now;   // the database clock, not the PC clock, so a wrong laptop time cannot fake history
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT UTC_TIMESTAMP(3)")) {
            rs.next();
            now = rs.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC);
        }
        String hash = computeHash(prev, incidentId, actorId, action, details, now);
        String sql = "INSERT INTO audit_event (incident_id, actor_id, action, details, created_at, prev_hash, hash) "
                   + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setIntOrNull(ps, 1, incidentId);
            ps.setInt(2, actorId); ps.setString(3, action); ps.setString(4, details);
            ps.setObject(5, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            ps.setString(6, prev); ps.setString(7, hash);
            ps.executeUpdate();
        }
        // trg_audit_ai moves the chain head to this event.
    }

    public List<AuditEvent> list(Connection c, Integer incidentId) throws SQLException {
        List<AuditEvent> out = new ArrayList<>();
        String sql = "SELECT * FROM audit_event" + (incidentId == null ? "" : " WHERE incident_id = ?") + " ORDER BY event_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (incidentId != null) ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new AuditEvent(rs.getLong("event_id"), Jdbc.intOrNull(rs, "incident_id"),
                    rs.getInt("actor_id"), rs.getString("action"), rs.getString("details"),
                    rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getString("prev_hash"), rs.getString("hash")));
            }
        }
        return out;
    }

    /**
     * An anchor is the newest event id and hash, written down somewhere the database
     * cannot reach (printed, emailed, committed to git). verify() alone cannot catch
     * someone with root access who rewrites history AND recomputes every later hash;
     * checking against an anchor saved earlier does.
     */
    public String anchor(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_event_id, last_hash, UTC_TIMESTAMP() FROM audit_chain_head WHERE id = 1")) {
            rs.next();
            return "pathivu-anchor event=" + rs.getLong(1) + " hash=" + rs.getString(2) + " utc=" + rs.getString(3).replace(' ', 'T');
        }
    }

    public VerifyResult verifyAnchor(Connection c, String anchor) throws SQLException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("event=(\\d+)\\s+hash=([0-9a-f]{64})").matcher(anchor);
        if (!m.find()) return new VerifyResult(false, 0, null, "That is not a Pathivu anchor.");
        long id = Long.parseLong(m.group(1));
        VerifyResult chain = verify(c);
        if (!chain.intact()) return chain;
        for (AuditEvent e : list(c, null)) {
            if (e.id() == id) {
                return e.hash().equals(m.group(2))
                    ? new VerifyResult(true, chain.checked(), null, "Anchor matches: history up to event " + id + " is exactly as it was when the anchor was saved.")
                    : new VerifyResult(false, chain.checked(), id, "Anchor does NOT match event " + id + ": history before it was rewritten and re-hashed.");
            }
        }
        return new VerifyResult(false, chain.checked(), id, "Event " + id + " from the anchor no longer exists.");
    }

    public record VerifyResult(boolean intact, int checked, Long brokenAt, String message) { }

    /** Walks the whole chain from the first event and recomputes every hash. */
    public VerifyResult verify(Connection c) throws SQLException {
        String expectedPrev = "0".repeat(64);
        int n = 0;
        for (AuditEvent e : list(c, null)) {
            if (!e.prevHash().equals(expectedPrev))
                return new VerifyResult(false, n, e.id(), "Event " + e.id() + " does not link to the event before it (a row was removed or inserted).");
            String recomputed = computeHash(e.prevHash(), e.incidentId(), e.actorId(), e.action(), e.details(), e.createdAt());
            if (!recomputed.equals(e.hash()))
                return new VerifyResult(false, n, e.id(), "Event " + e.id() + " was changed after it was written.");
            expectedPrev = e.hash();
            n++;
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_hash FROM audit_chain_head WHERE id = 1")) {
            rs.next();
            if (!rs.getString(1).equals(expectedPrev))
                return new VerifyResult(false, n, null, "The chain head does not match the last event (the newest events were removed).");
        }
        return new VerifyResult(true, n, null, "All " + n + " events verified. The audit trail is intact.");
    }
}
