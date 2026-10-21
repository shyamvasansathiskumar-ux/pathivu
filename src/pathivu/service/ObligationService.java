package pathivu.service;

import java.time.Instant;
import java.util.List;
import pathivu.dao.*;
import pathivu.model.*;
import pathivu.rules.RuleEngine;
import pathivu.security.Guard;
import pathivu.util.Times;

/** Legal clocks and the notices that satisfy them. */
public class ObligationService {

    private final Db db;
    private final ClockDao clocks = new ClockDao();
    private final NotificationDao notices = new NotificationDao();
    private final IncidentDao incidents = new IncidentDao();
    private final HarmDao harms = new HarmDao();
    private final RuleDao rules = new RuleDao();
    private final AuditDao audit = new AuditDao();

    public ObligationService(Db db) { this.db = db; }

    public List<ClockDao.LiveRow> liveClocks() { return db.read(clocks::live); }

    /** The only way to stop a clock without sending a notice. The trigger repeats every check. */
    public void ruleOut(AppUser by, int clockId, String reason) {
        Guard.require(by, Role.REVIEWER, "rule out a clock");
        if (reason == null || reason.trim().length() < 20)
            throw new IllegalArgumentException("Write why this duty does not apply (at least 20 characters).");
        db.tx(c -> {
            ObligationClock k = clocks.find(c, clockId);
            if (java.time.Instant.now().isAfter(k.effectiveDue()))   // the trigger repeats this with the DB clock
                throw new IllegalArgumentException("This deadline has already passed. It cannot be ruled out afterwards; "
                    + "send the notice late instead (it will show as MET_LATE).");
            clocks.ruleOut(c, clockId, by.id(), reason.trim());
            audit.append(c, k.incidentId(), by.id(), "CLOCK_RULED_OUT", k.rule().code() + ": " + reason.trim());
            return null;
        });
    }

    public int draftNotice(AppUser by, int clockId, String body) {
        Guard.requireWriter(by, "draft notices");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("The notice is empty");
        return db.tx(c -> {
            ObligationClock k = clocks.find(c, clockId);
            int id = notices.insertDraft(c, clockId, k.rule().recipient(), body.trim(), by.id());
            audit.append(c, k.incidentId(), by.id(), "NOTICE_DRAFTED", "#" + id + " to " + k.rule().recipient());
            return id;
        });
    }

    /** Four eyes: the database CHECK refuses an approver who is also the drafter. */
    public void approveNotice(AppUser by, int noticeId) {
        Guard.require(by, Role.REVIEWER, "approve notices");
        db.tx(c -> {
            Notification n = notices.find(c, noticeId);
            if (n.draftedBy() == by.id()) throw new pathivu.security.AccessDenied("You drafted this notice, so someone else must approve it.");
            notices.approve(c, noticeId, by.id());
            audit.append(c, incidentOf(c, n), by.id(), "NOTICE_APPROVED", "#" + noticeId);
            return null;
        });
    }

    /**
     * Records that the approved notice went out. Pathivu does not email the
     * regulator itself: a person submits it (CERT-In takes reports by email,
     * phone or its portal) and then presses this. The time is the database's.
     * The clock becomes MET in the same transaction.
     */
    public void markSent(AppUser by, int noticeId) {
        Guard.require(by, Role.REVIEWER, "record that a notice was sent");
        db.tx(c -> {
            Notification n = notices.find(c, noticeId);
            if (n.approvedBy() == null) throw new IllegalStateException("Approve the notice before sending it.");
            notices.markSent(c, noticeId);
            clocks.markMet(c, n.clockId());
            ObligationClock k = clocks.find(c, n.clockId());
            audit.append(c, k.incidentId(), by.id(), "NOTICE_SENT",
                "#" + noticeId + " to " + n.recipient() + "; " + k.rule().code() + " " + k.liveStatus(Instant.now()));
            return null;
        });
    }

    /**
     * Records a regulator's written permission for more time (DPDP Rule 7(2)(b) lets the Board
     * allow longer than 72 hours). The original deadline stays on record; the trigger refuses
     * rules that are not extendable, overdue clocks, and anything that is not later.
     */
    public void recordExtension(AppUser by, int clockId, Instant newDue, String boardReference, String reason) {
        Guard.require(by, Role.REVIEWER, "record an extension");
        if (reason == null || reason.trim().length() < 20) throw new IllegalArgumentException("Write the reason (at least 20 characters).");
        if (boardReference == null || boardReference.trim().length() < 3) throw new IllegalArgumentException("Give the reference of the written permission.");
        db.tx(c -> {
            ObligationClock k = clocks.find(c, clockId);
            clocks.insertExtension(c, clockId, newDue, boardReference.trim(), reason.trim(), by.id());
            audit.append(c, k.incidentId(), by.id(), "CLOCK_EXTENDED",
                k.rule().code() + " due " + Times.ist(k.effectiveDue()) + " -> " + Times.ist(newDue) + " per " + boardReference.trim());
            return null;
        });
    }

    private int incidentOf(java.sql.Connection c, Notification n) throws java.sql.SQLException {
        return clocks.find(c, n.clockId()).incidentId();
    }

    /** "What if the organisation had become aware at another time?" Nothing is saved. */
    public List<String> preview(int incidentId, Instant hypotheticalAware) {
        return db.read(c -> new RuleEngine(rules.listAll(c))
            .preview(incidents.find(c, incidentId), harms.listFor(c, incidentId), hypotheticalAware));
    }

    /** A starting draft. A person edits it before it goes anywhere. */
    public String template(IncidentFile f, ObligationClock k) {
        Incident i = f.incident();
        StringBuilder b = new StringBuilder();
        b.append("To: ").append(k.rule().recipient()).append('\n');
        b.append("Subject: ").append("CERT_IN".equals(k.rule().regime()) ? "Cyber security incident report" : "Personal data breach intimation")
         .append(" (").append(i.getRefCode()).append(")\n\n");
        b.append("Legal basis: ").append(k.rule().citation()).append("\n\n");
        b.append("Organisation became aware: ").append(Times.ist(i.getAwareAt())).append('\n');
        b.append("Incident occurred: ").append(Times.ist(i.getOccurredAt())).append('\n');
        b.append("System affected: ").append(f.aiSystem()).append('\n');
        b.append("What happened: ").append(i.getDescription()).append('\n');
        b.append("Details: ").append(i.specificsSummary()).append('\n');
        if (!f.harms().isEmpty()) {
            b.append("Harm:\n");
            for (Harm h : f.harms()) b.append(" - ").append(h.description()).append(" (").append(h.affectedCount()).append(" affected)\n");
        }
        b.append("\nActions taken so far: [fill in]\nContact person: [name, phone, email]\n");
        return b.toString();
    }
}
