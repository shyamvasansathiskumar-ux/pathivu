package pathivu.ui;

import java.awt.*;
import javax.swing.*;

/** Label-on-the-left form builder. */
class Form extends JPanel {
    private final GridBagConstraints g = new GridBagConstraints();
    private int row = 0;
    Form() {
        super(new GridBagLayout());
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;
    }
    Form add(String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        super.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        super.add(field, g);
        row++;
        return this;
    }
    Form note(String text) {
        g.gridx = 1; g.gridy = row++; g.fill = GridBagConstraints.HORIZONTAL;
        super.add(Ui.muted("<html>" + text + "</html>"), g);
        return this;
    }
}
