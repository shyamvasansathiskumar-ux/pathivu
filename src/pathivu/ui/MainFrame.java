package pathivu.ui;

import java.awt.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import pathivu.dao.*;
import pathivu.model.*;
import pathivu.service.*;
import pathivu.util.Times;

/** The main window: register, deadlines, audit trail. */
class MainFrame extends JFrame {

    final Db db;
    final AppUser user;
    final IncidentService incidents;
    final ObligationService obligations;
    final AuditService audit;
    final AuthService auth;
    private final RowsModel<IncidentDao.RegisterRow> register;
    private final RowsModel<ClockDao.LiveRow> clocks;
    private final RowsModel<AuditEvent> events;
    private final JLabel auditState = new JLabel(" ");
    private final JLabel summary = new JLabel(" ");
    Map<Integer, String> names;

    MainFrame(Db db, AppUser user) {
        super("Pathivu — " + user.fullName() + " (" + user.role() + ")");
        this.db = db; this.user = user;
        incidents = new IncidentService(db);
        obligations = new ObligationService(db);
        audit = new AuditService(db);
        auth = new AuthService(db);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 720);
        setLocationRelativeTo(null);

        // header
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(new EmptyBorder(12, 16, 8, 16));
        JLabel name = new JLabel("பதிவு Pathivu");
        name.setFont(Ui.TITLE); name.setForeground(Ui.ACCENT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.add(name); left.add(summary);
        head.add(left, BorderLayout.WEST);
        JButton report = new JButton("Report incident");
        report.setEnabled(user.role() != Role.AUDITOR);
        report.addActionListener(e -> { if (new ReportIncidentDialog(this).open()) refresh(); });
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        head.add(Ui.buttons(Ui.muted(user.fullName() + " · " + user.role()), refresh, report), BorderLayout.EAST);

        // register
        register = new RowsModel<>(new String[]{"Ref", "Kind", "Title", "Severity", "Status", "Aware (IST)", "System", "Harms", "Confirmed maps", "Open clocks"},
            r -> r.ref(), r -> r.kind(), r -> r.title(), r -> r.severity(), r -> r.status(), r -> Times.ist(r.awareAt()),
            r -> r.aiSystem(), r -> r.harms(), r -> r.confirmedMappings(), r -> r.openClocks());
        JTable regTable = Ui.table(register, 3, 4);
        int[] widths = {110, 95, 330, 80, 130, 165, 190, 50, 70, 70};
        for (int c = 0; c < widths.length; c++) regTable.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        regTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && regTable.getSelectedRow() >= 0)
                    openIncident(register.row(regTable.convertRowIndexToModel(regTable.getSelectedRow())).id());
            }
        });
        JPanel regPanel = new JPanel(new BorderLayout());
        regPanel.add(new JScrollPane(regTable));
        regPanel.add(Ui.muted("Double-click an incident to open it."), BorderLayout.SOUTH);

        // deadlines
        clocks = new RowsModel<>(new String[]{"Status", "Time", "Ref", "Incident", "Duty", "To", "Due (IST)"},
            k -> k.liveStatus(), k -> timeText(k), k -> k.ref(), k -> k.title(), k -> k.ruleCode(), k -> k.recipient(), k -> Times.ist(k.dueAt()));
        JTable clockTable = Ui.table(clocks, 0);
        int[] cw = {110, 120, 110, 330, 170, 170, 170};
        for (int c = 0; c < cw.length; c++) clockTable.getColumnModel().getColumn(c).setPreferredWidth(cw[c]);
        clockTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && clockTable.getSelectedRow() >= 0)
                    openIncident(clocks.row(clockTable.convertRowIndexToModel(clockTable.getSelectedRow())).incidentId());
            }
        });
        JPanel clockPanel = new JPanel(new BorderLayout());
        clockPanel.add(new JScrollPane(clockTable));
        clockPanel.add(Ui.muted("Worked out live from the view v_clock_status; refreshes every 30 seconds. Every clock starts at aware_at."), BorderLayout.SOUTH);
        new Timer(30_000, e -> loadClocks()).start();

        // audit
        events = new RowsModel<>(new String[]{"#", "When (IST)", "Who", "Action", "Details", "Hash"},
            a -> a.id(), a -> Times.ist(a.createdAt()), a -> names.getOrDefault(a.actorId(), "?"), a -> a.action(),
            a -> a.details(), a -> a.hash().substring(0, 12) + "…");
        JTable auditTable = Ui.table(events);
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(460);
        JButton verify = new JButton("Verify chain");
        verify.addActionListener(e -> verify());
        JButton saveAnchor = new JButton("Save anchor");
        saveAnchor.addActionListener(e -> saveAnchor());
        JButton checkAnchor = new JButton("Check an anchor");
        checkAnchor.addActionListener(e -> checkAnchor());
        JPanel auditPanel = new JPanel(new BorderLayout());
        auditPanel.add(Ui.buttons(verify, saveAnchor, checkAnchor, auditState), BorderLayout.NORTH);
        auditPanel.add(new JScrollPane(auditTable));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Register", Ui.padded(regPanel));
        tabs.addTab("Deadlines", Ui.padded(clockPanel));
        tabs.addTab("Audit trail", Ui.padded(auditPanel));
        add(head, BorderLayout.NORTH);
        add(tabs);
        refresh();
    }

    private static String timeText(ClockDao.LiveRow k) {
        return switch (k.liveStatus()) {
            case "RUNNING", "OVERDUE" -> Times.left(java.time.Duration.ofMinutes(k.minutesLeft()));
            default -> "";
        };
    }

    void refresh() {
        Ui.attempt(this, "Refresh", () -> {
            names = auth.names();
            var rows = incidents.register();
            register.setRows(rows);
            events.setRows(audit.all());
            loadClocks();
        });
    }

    private void loadClocks() {
        Ui.attempt(this, "Deadlines", () -> {
            var live = obligations.liveClocks();
            clocks.setRows(live);
            long overdue = live.stream().filter(k -> k.liveStatus().equals("OVERDUE")).count();
            long running = live.stream().filter(k -> k.liveStatus().equals("RUNNING")).count();
            summary.setText("<html><span style='color:#B3261E'><b>" + overdue + " overdue</b></span> · "
                          + "<span style='color:#B26A00'>" + running + " running</span> · " + register.getRowCount() + " incidents</html>");
        });
    }

    private void verify() {
        Ui.attempt(this, "Verify", () -> {
            var r = audit.verify();
            auditState.setText(r.message());
            auditState.setForeground(r.intact() ? Ui.OK : Ui.BAD);
            auditState.setFont(Ui.BOLD);
        });
    }

    /** Write the newest hash down outside the database, so even a full rewrite by root is caught later. */
    private void saveAnchor() {
        Ui.attempt(this, "Save anchor", () -> {
            String a = audit.anchor();
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(a), null);
            JTextArea t = new JTextArea(a, 3, 60);
            t.setLineWrap(true); t.setEditable(false);
            JOptionPane.showMessageDialog(this, new Object[]{"Copied. Keep it somewhere the database cannot reach (email it to yourself, print it):", t},
                "Audit anchor", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void checkAnchor() {
        String a = JOptionPane.showInputDialog(this, "Paste an anchor you saved earlier:");
        if (a == null || a.isBlank()) return;
        Ui.attempt(this, "Check anchor", () -> {
            var r = audit.verifyAnchor(a);
            auditState.setText(r.message());
            auditState.setForeground(r.intact() ? Ui.OK : Ui.BAD);
            auditState.setFont(Ui.BOLD);
        });
    }

    void openIncident(int id) {
        new IncidentDialog(this, id).setVisible(true);
        refresh();
    }
}
