package pathivu.ui;

import java.nio.file.Path;
import javax.swing.*;
import pathivu.dao.Db;
import pathivu.model.AppUser;

/** Starts Pathivu: read settings, sign in, open the main window. */
public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Ui.installLook();
            Db db;
            try {
                db = Db.fromFile(Path.of(args.length > 0 ? args[0] : "db.properties"), "db.");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Could not read db.properties:\n" + e.getMessage(), "Pathivu", JOptionPane.ERROR_MESSAGE);
                return;
            }
            AppUser user = LoginDialog.show(db);
            if (user == null) System.exit(0);
            new MainFrame(db, user).setVisible(true);
        });
    }
}
