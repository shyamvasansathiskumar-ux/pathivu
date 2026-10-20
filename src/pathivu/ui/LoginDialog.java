package pathivu.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import pathivu.dao.Db;
import pathivu.model.AppUser;
import pathivu.service.AuthService;

class LoginDialog {
    static AppUser show(Db db) {
        AuthService auth = new AuthService(db);
        JTextField user = new JTextField("meera", 16);
        JPasswordField pass = new JPasswordField(16);
        JLabel title = new JLabel("பதிவு  Pathivu");
        title.setFont(Ui.TITLE.deriveFont(22f));
        title.setForeground(Ui.ACCENT);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 4, 4, 4));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; form.add(title, g);
        g.gridy = 1; form.add(Ui.muted("AI incident register with legal deadline tracking"), g);
        g.gridwidth = 1; g.gridy = 2; form.add(new JLabel("Username"), g); g.gridx = 1; form.add(user, g);
        g.gridx = 0; g.gridy = 3; form.add(new JLabel("Password"), g); g.gridx = 1; form.add(pass, g);
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
        form.add(Ui.muted("<html>Demo: meera (reporter), arjun / kavya (reviewers), ravi (auditor)</html>"), g);
        SwingUtilities.invokeLater(pass::requestFocusInWindow);
        while (true) {
            int r = JOptionPane.showConfirmDialog(null, form, "Sign in to Pathivu", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            AppUser[] out = new AppUser[1];
            if (Ui.attempt(null, "Sign in", () -> out[0] = auth.login(user.getText(), pass.getPassword()))) return out[0];
            pass.setText("");
        }
    }
}
