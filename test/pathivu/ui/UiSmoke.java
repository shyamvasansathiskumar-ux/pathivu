package pathivu.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import pathivu.dao.Db;
import pathivu.service.AuthService;

/** Opens the real screens and saves screenshots. Run under a display (xvfb-run on Linux). */
public class UiSmoke {
    public static void main(String[] args) throws Exception {
        Db db = Db.fromFile(Path.of("db.properties"), "db.");
        var user = new AuthService(db).login(args.length > 0 ? args[0] : "arjun", "Pathivu@2026".toCharArray());
        File dir = new File("screenshots"); dir.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            Ui.installLook();
            MainFrame f = new MainFrame(db, user);
            f.setVisible(true);
            shot(f, new File(dir, "1-register.png"));
            JTabbedPane tabs = find(f.getContentPane(), JTabbedPane.class);
            tabs.setSelectedIndex(1); shot(f, new File(dir, "2-deadlines.png"));
            tabs.setSelectedIndex(2); shot(f, new File(dir, "3-audit.png"));
        });
        for (int[] spec : new int[][]{{1, 0, 4}, {1, 3, 5}, {2, 4, 6}, {3, 0, 7}}) {
            SwingUtilities.invokeLater(() -> {
                MainFrame f = (MainFrame) Frame.getFrames()[0];
                IncidentDialog d = new IncidentDialog(f, spec[0]);
                new Timer(700, e -> {
                    ((Timer) e.getSource()).stop();
                    find(d.getContentPane(), JTabbedPane.class).setSelectedIndex(spec[1]);
                    new Timer(300, e2 -> { ((Timer) e2.getSource()).stop();
                        shot(d, new File(dir, spec[2] + "-incident" + spec[0] + "-tab" + spec[1] + ".png")); d.dispose(); }).start();
                }).start();
                d.setVisible(true);
            });
            Thread.sleep(2000);
        }
        System.out.println("screenshots saved");
        System.exit(0);
    }

    static void shot(Window w, File out) {
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
        w.paint(img.getGraphics());
        try { ImageIO.write(img, "png", out); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static <T> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return type.cast(k);
            if (k instanceof Container cc) { T t = find(cc, type); if (t != null) return t; }
        }
        return null;
    }
}
