package pathivu.ui;

import javax.swing.JComponent;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Swing quietly renders any label text that starts with "&lt;html&gt;" as HTML, including
 * text a user typed (an incident title, a system name, a reason). That would let one
 * user change what another user sees, or make the app fetch an image from the internet.
 * Pass 3: user text is escaped before it goes into our own HTML, and labels and table
 * cells that show user text have HTML switched off.
 */
public final class Html {
    private Html() { }

    /** Escape text so it shows as typed inside an HTML label. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Switch off HTML rendering for this component (Swing's "html.disable" property). */
    public static <C extends JComponent> C noHtml(C c) {
        c.putClientProperty("html.disable", Boolean.TRUE);
        return c;
    }

    /** A table cell renderer that always shows text as plain text. */
    public static DefaultTableCellRenderer plainRenderer() {
        return noHtml(new DefaultTableCellRenderer());
    }
}
