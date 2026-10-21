package pathivu.ui;

import java.awt.*;
import java.time.Instant;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import pathivu.dao.LookupDao;
import pathivu.model.*;
import pathivu.util.Times;

/** Report a new incident. The kind picks which extra fields are shown (one subclass per kind). */
class ReportIncidentDialog extends JDialog {

    private final MainFrame main;
    private boolean saved = false;

    private final JComboBox<IncidentKind> kind = new JComboBox<>(IncidentKind.values());
    private final JTextField title = new JTextField(36);
    private final JTextArea desc = new JTextArea(4, 36);
    private final JComboBox<LookupDao.AiSystemRow> system;
    private final JTextField occurred = new JTextField(14), detected = new JTextField(14), aware = new JTextField(14);
    private final JComboBox<Severity> severity = new JComboBox<>(Severity.values());
    private final JComboBox<Adversary> adversary = new JComboBox<>(Adversary.values());
    private final JComboBox<TriState> cyber = new JComboBox<>(TriState.values());
    private final JTextField tags = new JTextField(24);
    private final JTextField gapReason = new JTextField(36);
    private final JTextField lateReason = new JTextField(36);
    private final JTextField linkedRef = new JTextField(14);
    // agent
    private final JTextField agentName = new JTextField(20), instruction = new JTextField(30), action = new JTextField(30);
    private final JCheckBox credential = new JCheckBox("It held access beyond what the task needed");
    private final JCheckBox confirm = new JCheckBox("A human had to approve destructive actions");
    // model harm
    private final JComboBox<OutputType> outputType = new JComboBox<>(OutputType.values());
    private final JTextField group = new JTextField(30);
    // data
    private final JTextField categories = new JTextField(30);
    private final JSpinner records = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JComboBox<TriState> exfil = new JComboBox<>(TriState.values());

    ReportIncidentDialog(MainFrame main) {
        super(main, "Report an incident", true);
        this.main = main;
        system = new JComboBox<>(main.incidents.aiSystems().toArray(new LookupDao.AiSystemRow[0]));
        String now = Times.inputIst(Instant.now());
        occurred.setText(now); detected.setText(now); aware.setText(now);
        severity.setSelectedItem(Severity.MEDIUM);
        cyber.setSelectedItem(TriState.UNSURE);
        exfil.setSelectedItem(TriState.UNSURE);
        desc.setLineWrap(true); desc.setWrapStyleWord(true);

        Form common = new Form()
            .add("Kind", kind).add("Title", title).add("What happened", new JScrollPane(desc)).add("AI system", system)
            .add("Occurred (IST)", occurred).add("Detected (IST)", detected).add("Organisation aware (IST)", aware)
            .note("Every legal clock starts at <b>aware</b>. It can never be changed later, only annotated.")
            .add("If aware > 1 h after detected, why?", gapReason)
            .note("Required when there is a gap of more than an hour: a late 'aware' time would delay every clock.")
            .add("If aware > 7 days ago, why report now?", lateReason)
            .note("Required for old incidents: an old 'aware' date could fall before a rule was in force.")
            .add("Severity", severity).add("Was anyone attacking?", adversary)
            .add("Cyber security incident?", cyber).note("CERT-In question. UNSURE keeps the 6-hour clock running until a reviewer rules it out.")
            .add("Follows up incident (ref)", linkedRef).note("Optional, e.g. PTV-2026-0003. Closed incidents are never reopened; link a new one instead.")
            .add("Tags (comma separated)", tags).note("e.g. prompt-injection, jailbreak, system-prompt: these feed the mapping suggestions.");

        CardLayout cards = new CardLayout();
        JPanel specific = new JPanel(cards);
        specific.setBorder(BorderFactory.createTitledBorder("Details for this kind"));
        specific.add(new Form().add("Agent name", agentName).add("It was asked to", instruction).add("It actually", action)
                               .add("", credential).add("", confirm), IncidentKind.AGENT.name());
        specific.add(new Form().add("Output type", outputType).add("Who was affected", group), IncidentKind.MODEL_HARM.name());
        specific.add(new Form().add("Data categories", categories).add("Records affected", records).add("Left the organisation?", exfil),
                     IncidentKind.DATA.name());
        kind.addActionListener(e -> cards.show(specific, kind.getSelectedItem().toString()));

        JButton save = new JButton("Report");
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(new EmptyBorder(12, 12, 12, 12));
        body.add(common, BorderLayout.NORTH);
        body.add(specific);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel); south.add(save);
        add(new JScrollPane(body)); add(south, BorderLayout.SOUTH);
        setSize(720, 760);
        setLocationRelativeTo(main);
    }

    boolean open() { setVisible(true); return saved; }

    private void save() {
        Ui.attempt(this, "Report incident", () -> {
            Incident i = build();
            i.setCyberIncident((TriState) cyber.getSelectedItem());
            for (String t : tags.getText().split(",")) if (!t.isBlank()) i.addTag(t);
            i.setAwareGapReason(gapReason.getText());
            i.setLateReportReason(lateReason.getText());
            if (!linkedRef.getText().isBlank()) {
                Integer link = main.incidents.idForRef(linkedRef.getText());
                if (link == null) throw new IllegalArgumentException("No incident with reference " + linkedRef.getText().trim());
                i.setLinkedTo(link);
            }
            main.incidents.report(main.user, i);
            saved = true;
            JOptionPane.showMessageDialog(this, i.getRefCode() + " saved.\nClocks and mapping suggestions were created automatically.");
            dispose();
        });
    }

    private Incident build() {
        int sys = ((LookupDao.AiSystemRow) system.getSelectedItem()).id();
        Instant o = Times.parseIst(occurred.getText()), d = Times.parseIst(detected.getText()), a = Times.parseIst(aware.getText());
        Severity s = (Severity) severity.getSelectedItem();
        Adversary adv = (Adversary) adversary.getSelectedItem();
        int by = main.user.id();
        return switch ((IncidentKind) kind.getSelectedItem()) {
            case AGENT -> new AgentIncident(title.getText(), desc.getText(), sys, by, o, d, a, s, adv,
                agentName.getText(), instruction.getText(), action.getText(), credential.isSelected(), confirm.isSelected());
            case MODEL_HARM -> new ModelHarmIncident(title.getText(), desc.getText(), sys, by, o, d, a, s, adv,
                (OutputType) outputType.getSelectedItem(), group.getText());
            case DATA -> new DataIncident(title.getText(), desc.getText(), sys, by, o, d, a, s, adv,
                categories.getText(), (Integer) records.getValue(), (TriState) exfil.getSelectedItem());
        };
    }
}
