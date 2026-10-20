package pathivu.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import pathivu.dao.DataException;
import pathivu.security.AccessDenied;
import pathivu.workflow.WorkflowException;

/** Shared look and small helpers for every screen. */
final class Ui {
    private Ui() { }

    static final Color INK = new Color(0x1F2A2E);
    static final Color MUTED = new Color(0x5B6B70);
    static final Color PAPER = new Color(0xF6F7F5);
    static final Color ACCENT = new Color(0x0F6E6A);      // deep teal
    static final Color OK = new Color(0x2E7D32);
    static final Color WARN = new Color(0xB26A00);
    static final Color BAD = new Color(0xB3261E);
    static final Color QUIET = new Color(0x78838A);

    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font BOLD = BODY.deriveFont(Font.BOLD);
    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    static void installLook() {
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if (i.getName().equals("Nimbus")) { UIManager.setLookAndFeel(i.getClassName()); break; }
        } catch (Exception ignored) { }
        UIManager.put("nimbusBase", new Color(0x2F4A4F));
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusSelectionBackground", ACCENT);
        UIManager.put("control", PAPER);
        UIManager.put("Table.alternateRowColor", new Color(0xEEF1EF));
        UIManager.put("defaultFont", BODY);
    }

    /** Colour for a status word anywhere in the app. */
    static Color statusColor(String s) {
        if (s == null) return INK;
        return switch (s) {
            case "OVERDUE", "MET_LATE", "CRITICAL" -> BAD;
            case "RUNNING", "HIGH", "SUGGESTED", "DRAFT" -> WARN;
            case "MET", "CONFIRMED", "CLOSED", "SENT" -> OK;
            case "RULED_OUT", "REJECTED", "LOW" -> QUIET;
            default -> INK;
        };
    }

    /** Draws status words in bold colour so what needs attention stands out. */
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, f, r, c);
            l.setFont(BOLD);
            if (!sel) l.setForeground(statusColor(String.valueOf(v)));
            return l;
        }
    }

    static JTable table(javax.swing.table.TableModel m, int... statusCols) {
        JTable t = new JTable(m);
        t.setRowHeight(24);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        for (int c : statusCols) t.getColumnModel().getColumn(c).setCellRenderer(new StatusRenderer());
        return t;
    }

    static JPanel padded(JComponent inner) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 12, 10, 12));
        p.add(inner);
        return p;
    }

    static JPanel buttons(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JComponent c : cs) p.add(c);
        return p;
    }

    static JLabel muted(String s) { JLabel l = new JLabel(s); l.setForeground(MUTED); return l; }

    /** Runs an action and shows a readable message if it fails. Returns true on success. */
    static boolean attempt(Component parent, String what, ThrowingRunnable r) {
        try { r.run(); return true; }
        catch (WorkflowException e) {
            JOptionPane.showMessageDialog(parent, "Not yet. First:\n\n• " + String.join("\n• ", e.blockers()),
                                          what, JOptionPane.WARNING_MESSAGE);
        } catch (AccessDenied | IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), what, JOptionPane.WARNING_MESSAGE);
        } catch (DataException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), what,
                e.isRuleViolation() ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, e.toString(), what, JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }

    /** Asks for a written reason; returns null if cancelled. */
    static String askReason(Component parent, String title, String prompt, int minChars) {
        JTextArea area = new JTextArea(4, 40);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel("<html><body style='width:320px'>" + prompt + " (at least " + minChars + " characters)</body></html>"), BorderLayout.NORTH);
        p.add(new JScrollPane(area));
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            if (area.getText().trim().length() >= minChars) return area.getText().trim();
            JOptionPane.showMessageDialog(parent, "Please write at least " + minChars + " characters.");
        }
    }
}
