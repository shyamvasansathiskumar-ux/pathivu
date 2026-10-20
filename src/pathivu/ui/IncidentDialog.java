package pathivu.ui;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import pathivu.model.*;
import pathivu.service.IncidentFile;
import pathivu.service.ReportService;
import pathivu.util.Times;
import pathivu.workflow.IncidentWorkflow;

/** One incident, everything about it, and every action a user's role allows. */
class IncidentDialog extends JDialog {

    private final MainFrame main;
    private final int id;
    private IncidentFile file;
    private final boolean reviewer, writer;

    private final JLabel heading = new JLabel();
    private final JLabel statusLine = new JLabel();
    private final JPanel moves = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JTextArea overview = new JTextArea();
    private final RowsModel<Harm> harms = new RowsModel<>(new String[]{"Category", "What", "Affected", "Personal data"},
        h -> h.category(), h -> h.description(), h -> h.affectedCount(), h -> h.personalData());
    private final RowsModel<Evidence> evidence;
    private final RowsModel<Mapping> mappings = new RowsModel<>(new String[]{"Framework", "Code", "Category", "State", "Confidence", "Why"},
        m -> m.node().framework().label(), m -> m.node().code(), m -> m.node().name(), m -> m.state(), m -> m.confidence(), m -> m.rationale());
    private final RowsModel<ObligationClock> clocks = new RowsModel<>(new String[]{"Status", "Time", "Duty", "To", "Window", "Due (IST)"},
        k -> k.liveStatus(Instant.now()), k -> k.state() == ClockState.RUNNING ? Times.left(k.timeLeft(Instant.now())) : "",
        k -> k.rule().code(), k -> k.rule().recipient(), k -> k.rule().windowText(),
        k -> Times.ist(k.effectiveDue()) + (k.extendedDueAt() != null ? "  (extended)" : ""));
    private final RowsModel<Notification> notices;
    private final RowsModel<AuditEvent> history;
    private final JTable harmT, evT, mapT, clockT, noticeT;

    IncidentDialog(MainFrame main, int id) {
        super(main, true);
        this.main = main; this.id = id;
        reviewer = main.user.role() == Role.REVIEWER;
        writer = main.user.role() != Role.AUDITOR;
        evidence = new RowsModel<>(new String[]{"Label", "File", "SHA-256", "Added by", "When"},
            e -> e.label(), e -> e.fileName(), e -> e.sha256(), e -> main.names.getOrDefault(e.addedBy(), "?"), e -> Times.ist(e.addedAt()));
        notices = new RowsModel<>(new String[]{"#", "Duty", "To", "Stage", "Drafted by", "Approved by", "Sent (IST)"},
            n -> n.id(), n -> clockCode(n.clockId()), n -> n.recipient(), n -> n.stage(), n -> main.names.getOrDefault(n.draftedBy(), "?"),
            n -> n.approvedBy() == null ? "" : main.names.getOrDefault(n.approvedBy(), "?"), n -> Times.ist(n.sentAt()));
        history = new RowsModel<>(new String[]{"When (IST)", "Who", "Action", "Details"},
            a -> Times.ist(a.createdAt()), a -> main.names.getOrDefault(a.actorId(), "?"), a -> a.action(), a -> a.details());

        heading.setFont(Ui.TITLE);
        overview.setEditable(false); overview.setFont(Ui.MONO); overview.setLineWrap(true); overview.setWrapStyleWord(true);
        harmT = Ui.table(harms, 3); evT = Ui.table(evidence); mapT = Ui.table(mappings, 3);
        clockT = Ui.table(clocks, 0); noticeT = Ui.table(notices, 3);
        mapT.getColumnModel().getColumn(5).setPreferredWidth(420);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setBorder(new EmptyBorder(12, 14, 6, 14));
        top.add(heading, BorderLayout.NORTH);
        top.add(statusLine);
        top.add(moves, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", overviewTab());
        tabs.addTab("Harm", tableTab(harmT, button("Record harm", writer, this::addHarm)));
        tabs.addTab("Evidence", tableTab(evT, button("Attach file", writer, this::addEvidence), button("Check a file", true, this::checkEvidence)));
        tabs.addTab("Mappings", tableTab(mapT, button("Confirm", reviewer, () -> review(true)), button("Reject", reviewer, () -> review(false)),
                                         button("Propose another", writer, this::propose)));
        tabs.addTab("Deadlines & notices", deadlinesTab());
        tabs.addTab("History", tableTab(Ui.table(history)));
        add(top, BorderLayout.NORTH);
        add(tabs);
        setSize(1060, 680);
        setLocationRelativeTo(main);
        reload();
    }

    // ------------------------------------------------------------------ layout

    private final List<JButton> writeButtons = new java.util.ArrayList<>();

    /** allowed = the role may do it. Write buttons are also switched off once the incident is closed. */
    private JButton button(String text, boolean allowed, Runnable r) {
        JButton b = new JButton(text);
        b.setEnabled(allowed);
        b.putClientProperty("allowed", allowed);
        boolean readOnly = text.startsWith("Check") || text.startsWith("What if") || text.startsWith("Export")
                        || text.startsWith("Show") || text.startsWith("Read");
        if (!readOnly) writeButtons.add(b);
        b.addActionListener(e -> r.run());
        return b;
    }

    private boolean closed() { return file != null && file.incident().getStatus() == IncidentStatus.CLOSED; }

    private JPanel tableTab(JTable t, JButton... bs) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(t));
        if (bs.length > 0) p.add(Ui.buttons(bs), BorderLayout.SOUTH);
        return Ui.padded(p);
    }

    private JPanel overviewTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(overview));
        p.add(Ui.buttons(button("Amend…", writer, this::amend), button("Add tag", writer, this::addTag),
                         button("What if? (rule preview)", true, this::preview), button("Export report", true, this::export)),
              BorderLayout.SOUTH);
        return Ui.padded(p);
    }

    private JPanel deadlinesTab() {
        JPanel upper = new JPanel(new BorderLayout());
        upper.add(new JLabel("Legal clocks (anchored to aware_at; never deleted)"), BorderLayout.NORTH);
        upper.add(new JScrollPane(clockT));
        upper.add(Ui.buttons(button("Draft notice", writer, this::draftNotice), button("Rule out…", reviewer, this::ruleOut), button("Record extension…", reviewer, this::extend),
                             button("Show legal basis", true, this::citation)), BorderLayout.SOUTH);
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(new JLabel("Notices (draft → approved by a different reviewer → sent)"), BorderLayout.NORTH);
        lower.add(new JScrollPane(noticeT));
        lower.add(Ui.buttons(button("Read", true, this::readNotice), button("Approve", reviewer, this::approve),
                             button("Mark sent", reviewer, this::markSent)), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, lower);
        split.setResizeWeight(0.5);
        return Ui.padded(split);
    }

    // ------------------------------------------------------------------ data

    private void reload() {
        Ui.attempt(this, "Load", () -> {
            file = main.incidents.load(id);
            Incident i = file.incident();
            setTitle(i.getRefCode() + " — " + i.getTitle());
            heading.setText(i.getRefCode() + "   " + i.getTitle());
            statusLine.setText("<html>Status <b style='color:" + hex(Ui.statusColor(i.getStatus().name())) + "'>" + i.getStatus()
                + "</b> · Severity <b style='color:" + hex(Ui.statusColor(i.getSeverity().name())) + "'>" + i.getSeverity()
                + "</b> · " + i.kind() + " · " + file.aiSystem() + "</html>");
            overview.setText(overviewText());
            overview.setCaretPosition(0);
            harms.setRows(file.harms()); evidence.setRows(file.evidence()); mappings.setRows(file.mappings());
            clocks.setRows(file.clocks()); notices.setRows(file.notices()); history.setRows(file.history());
            moves.removeAll();
            for (IncidentStatus to : IncidentWorkflow.nextFrom(i.getStatus())) {
                boolean back = to == IncidentStatus.TRIAGED && i.getStatus() != IncidentStatus.REPORTED;
                JButton b = new JButton(back ? "Send back to TRIAGED" : "Move to " + to);
                b.setEnabled(reviewer);
                b.addActionListener(e -> move(to, back));
                moves.add(b);
            }
            if (!reviewer && i.getStatus() != IncidentStatus.CLOSED) moves.add(Ui.muted("A reviewer moves incidents forward."));
            for (JButton b : writeButtons) b.setEnabled((Boolean) b.getClientProperty("allowed") && !closed());
            if (closed()) moves.add(Ui.muted("Closed. Nothing can be changed; history is kept as it was."));
            moves.revalidate(); moves.repaint();
        });
    }

    private String overviewText() {
        Incident i = file.incident();
        StringBuilder b = new StringBuilder();
        b.append(i.getDescription()).append("\n\n").append(i.specificsSummary()).append("\n\n");
        b.append(String.format("%-14s %s%n", "Occurred", Times.ist(i.getOccurredAt())));
        b.append(String.format("%-14s %s%n", "Detected", Times.ist(i.getDetectedAt())));
        b.append(String.format("%-14s %s   <- every clock starts here%n", "Aware", Times.ist(i.getAwareAt())));
        for (Amendment a : file.amendments())
            b.append(String.format("%-14s %s: %s -> %s (%s, %s)%n", "Amendment", a.field(), a.oldValue(), a.newValue(), a.reason(),
                     main.names.getOrDefault(a.amendedBy(), "?")));
        if (i.getAwareGapReason() != null) b.append(String.format("%-14s %s%n", "Gap reason", i.getAwareGapReason()));
        if (i.getLinkedTo() != null) b.append(String.format("%-14s follows up incident #%d%n", "Linked", i.getLinkedTo()));
        b.append(String.format("%n%-14s %s%n", "Adversary", i.getAdversary()));
        b.append(String.format("%-14s %s%n", "Cyber incident", i.getCyberIncident()));
        b.append(String.format("%-14s %s%n", "Reported by", main.names.getOrDefault(i.getReportedBy(), "?")));
        b.append(String.format("%-14s %s%n", "Tags", i.getTags().isEmpty() ? "-" : String.join(", ", i.getTags())));
        b.append("\nEvidence expected for this kind:\n");
        for (String e : i.requiredEvidence()) {
            boolean has = file.evidence().stream().anyMatch(x -> x.label().equalsIgnoreCase(e));
            b.append(has ? "  [x] " : "  [ ] ").append(e).append('\n');
        }
        return b.toString();
    }

    private String clockCode(int clockId) {
        return file == null ? "" : file.clocks().stream().filter(k -> k.id() == clockId).map(k -> k.rule().code()).findFirst().orElse("");
    }

    private static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    private <T> T selected(JTable t, RowsModel<T> m, String what) {
        if (t.getSelectedRow() < 0) { JOptionPane.showMessageDialog(this, "Select " + what + " first."); return null; }
        return m.row(t.convertRowIndexToModel(t.getSelectedRow()));
    }

    private void after(boolean ok) { if (ok) reload(); }

    // ------------------------------------------------------------------ actions

    private void move(IncidentStatus to, boolean back) {
        String reason = null;
        if (back) {
            reason = Ui.askReason(this, "Send back", "Why does this incident need another look?", 10);
            if (reason == null) return;
        }
        String r = reason;
        after(Ui.attempt(this, "Move to " + to, () -> main.incidents.move(main.user, id, to, r)));
    }

    private void addHarm() {
        JComboBox<HarmCategory> cat = new JComboBox<>(HarmCategory.values());
        JTextField what = new JTextField(30);
        JSpinner n = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        JComboBox<TriState> pd = new JComboBox<>(TriState.values());
        pd.setSelectedItem(TriState.UNSURE);
        Form f = new Form().add("Category", cat).add("What happened", what).add("People affected", n)
            .add("Personal data involved?", pd).note("DPDP question. YES or UNSURE can start DPDP clocks (once Rule 7 is in force).");
        if (JOptionPane.showConfirmDialog(this, f, "Record harm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        after(Ui.attempt(this, "Record harm", () -> main.incidents.addHarm(main.user, id,
            Harm.draft((HarmCategory) cat.getSelectedItem(), what.getText(), (Integer) n.getValue(), (TriState) pd.getSelectedItem()))));
    }

    private void addEvidence() {
        JFileChooser ch = new JFileChooser();
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Object label = JOptionPane.showInputDialog(this, "What is this file?", "Evidence label", JOptionPane.PLAIN_MESSAGE, null,
            file.incident().requiredEvidence().toArray(), file.incident().requiredEvidence().get(0));
        if (label == null) return;
        after(Ui.attempt(this, "Attach evidence", () -> {
            String sha = main.incidents.addEvidence(main.user, id, label.toString(), ch.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "Fingerprint recorded:\n" + sha);
        }));
    }

    private void checkEvidence() {
        Evidence e = selected(evT, evidence, "an evidence row");
        if (e == null) return;
        JFileChooser ch = new JFileChooser();
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Ui.attempt(this, "Check evidence", () -> {
            boolean same = main.incidents.evidenceMatches(e, ch.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, same ? "Same file. It has not changed since it was attached."
                                                     : "DIFFERENT. This file does not match the fingerprint recorded on " + Times.ist(e.addedAt()) + ".",
                "Evidence check", same ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        });
    }

    private void review(boolean confirm) {
        Mapping m = selected(mapT, mappings, "a mapping");
        if (m == null) return;
        after(Ui.attempt(this, confirm ? "Confirm" : "Reject", () -> main.incidents.reviewMapping(main.user, id, m.node().id(), confirm)));
    }

    private void propose() {
        JComboBox<TaxonomyNode> node = new JComboBox<>(main.incidents.currentNodes().toArray(new TaxonomyNode[0]));
        node.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                TaxonomyNode n = (TaxonomyNode) v;
                return super.getListCellRendererComponent(l, n == null ? "" : n.framework().label() + " · " + n, i, s, f);
            }
        });
        JTextField why = new JTextField(36);
        JComboBox<Confidence> conf = new JComboBox<>(Confidence.values());
        Form f = new Form().add("Category", node).add("Why (15+ characters)", why).add("Confidence", conf);
        if (JOptionPane.showConfirmDialog(this, f, "Propose a mapping", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        after(Ui.attempt(this, "Propose", () -> main.incidents.proposeMapping(main.user, id,
            ((TaxonomyNode) node.getSelectedItem()).id(), why.getText(), (Confidence) conf.getSelectedItem())));
    }

    private void amend() {
        String[] fields = {"severity", "cyber_incident", "title", "description", "aware_at", "detected_at", "occurred_at"};
        JComboBox<String> field = new JComboBox<>(fields);
        JTextField value = new JTextField(30);
        Form f = new Form().add("Field", field).add("New value", value)
            .note("severity: LOW / MEDIUM / HIGH / CRITICAL · cyber_incident: YES / NO / UNSURE<br>"
                + "Times are never overwritten: a correction is recorded beside the original and the clocks stay where they are.");
        if (JOptionPane.showConfirmDialog(this, f, "Amend", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String reason = Ui.askReason(this, "Reason", "Why is this being corrected?", 10);
        if (reason == null) return;
        String fld = (String) field.getSelectedItem();
        String v = value.getText().trim();
        after(Ui.attempt(this, "Amend", () -> {
            if (fld.equals("severity")) Severity.valueOf(v.toUpperCase());
            if (fld.equals("cyber_incident")) TriState.valueOf(v.toUpperCase());
            boolean upper = fld.equals("severity") || fld.equals("cyber_incident");
            main.incidents.amend(main.user, id, fld, upper ? v.toUpperCase() : v, reason);
        }));
    }

    private void addTag() {
        String t = JOptionPane.showInputDialog(this, "Tag (e.g. prompt-injection, jailbreak, system-prompt)");
        if (t != null && !t.isBlank()) after(Ui.attempt(this, "Tag", () -> main.incidents.addTag(main.user, id, t)));
    }

    private void preview() {
        String when = JOptionPane.showInputDialog(this,
            "Which clocks WOULD run if the organisation had become aware at (IST, yyyy-MM-dd HH:mm)?\nTry 2027-06-01 10:00 to see DPDP Rule 7.",
            "2027-06-01 10:00");
        if (when == null) return;
        Ui.attempt(this, "Preview", () -> {
            List<String> lines = main.obligations.preview(id, Times.parseIst(when));
            JTextArea a = new JTextArea(String.join("\n\n", lines), 14, 70);
            a.setLineWrap(true); a.setWrapStyleWord(true); a.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(a), "Rule preview (nothing is saved)", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void export() {
        JFileChooser ch = new JFileChooser();
        ch.setSelectedFile(new java.io.File(file.incident().getRefCode() + "-report.txt"));
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Ui.attempt(this, "Export", () -> {
            Path out = Files.writeString(ch.getSelectedFile().toPath(), new ReportService().build(file, main.names));
            JOptionPane.showMessageDialog(this, "Saved " + out.getFileName());
        });
    }

    private void draftNotice() {
        ObligationClock k = selected(clockT, clocks, "a clock");
        if (k == null) return;
        JTextArea body = new JTextArea(main.obligations.template(file, k), 22, 70);
        body.setLineWrap(true); body.setWrapStyleWord(true);
        if (JOptionPane.showConfirmDialog(this, new JScrollPane(body), "Draft notice to " + k.rule().recipient(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        after(Ui.attempt(this, "Draft notice", () -> main.obligations.draftNotice(main.user, k.id(), body.getText())));
    }

    private void ruleOut() {
        ObligationClock k = selected(clockT, clocks, "a clock");
        if (k == null) return;
        String reason = Ui.askReason(this, "Rule out " + k.rule().code(), "Why does this legal duty not apply to this incident?", 20);
        if (reason != null) after(Ui.attempt(this, "Rule out", () -> main.obligations.ruleOut(main.user, k.id(), reason)));
    }

    private void extend() {
        ObligationClock k = selected(clockT, clocks, "a clock");
        if (k == null) return;
        if (!k.rule().extendable()) {
            JOptionPane.showMessageDialog(this, k.rule().code() + " does not allow extensions. Only the DPDP 72-hour Board report does, and only with the Board's written permission.");
            return;
        }
        JTextField due = new JTextField(Times.inputIst(k.effectiveDue().plus(java.time.Duration.ofHours(48))), 16);
        JTextField ref = new JTextField(24);
        JTextField why = new JTextField(36);
        Form f = new Form().add("New due time (IST)", due).add("Board's written permission (ref)", ref).add("Reason (20+ characters)", why)
            .note("The original deadline stays on record. An extension cannot be recorded once the clock is overdue.");
        if (JOptionPane.showConfirmDialog(this, f, "Record extension for " + k.rule().code(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        after(Ui.attempt(this, "Record extension", () ->
            main.obligations.recordExtension(main.user, k.id(), Times.parseIst(due.getText()), ref.getText(), why.getText())));
    }

    private void citation() {
        ObligationClock k = selected(clockT, clocks, "a clock");
        if (k == null) return;
        JOptionPane.showMessageDialog(this, "<html><body style='width:380px'><b>" + k.rule().name() + "</b><br><br>" + k.rule().citation()
            + "<br><br>In force from " + Times.ist(k.rule().effectiveFrom()) + "<br>Window: " + k.rule().windowText()
            + (k.ruledOutReason() == null ? "" : "<br><br><b>Ruled out:</b> " + k.ruledOutReason()) + "</body></html>");
    }

    private void readNotice() {
        Notification n = selected(noticeT, notices, "a notice");
        if (n == null) return;
        JTextArea a = new JTextArea(n.body(), 20, 70);
        a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(a), "Notice #" + n.id() + " (" + n.stage() + ")", JOptionPane.PLAIN_MESSAGE);
    }

    private void approve() {
        Notification n = selected(noticeT, notices, "a notice");
        if (n != null) after(Ui.attempt(this, "Approve", () -> main.obligations.approveNotice(main.user, n.id())));
    }

    private void markSent() {
        Notification n = selected(noticeT, notices, "a notice");
        if (n == null) return;
        if (JOptionPane.showConfirmDialog(this, "Confirm that this notice has been submitted to " + n.recipient()
                + ".\nThe time recorded will be the database's time, now.", "Mark sent", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        after(Ui.attempt(this, "Mark sent", () -> main.obligations.markSent(main.user, n.id())));
    }
}
