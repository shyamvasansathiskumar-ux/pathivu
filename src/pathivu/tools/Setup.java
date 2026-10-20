package pathivu.tools;

import java.nio.file.Path;
import pathivu.dao.AuditDao;
import pathivu.dao.Db;
import pathivu.dao.UserDao;
import pathivu.model.Role;
import pathivu.security.Hex;
import pathivu.security.PasswordHasher;

/**
 * Creates the demo accounts. Runs with the ADMIN login from db.properties,
 * because the app's own account is not allowed to create users.
 *
 *   java -cp "out:lib/*" pathivu.tools.Setup [password]
 */
public class Setup {

    record Person(String username, String name, Role role) { }

    static final Person[] PEOPLE = {
        new Person("meera", "Meera Iyer", Role.REPORTER),
        new Person("arjun", "Arjun Rao", Role.REVIEWER),
        new Person("kavya", "Kavya Nair", Role.REVIEWER),
        new Person("ravi", "Ravi Shankar", Role.AUDITOR),
    };

    public static void main(String[] args) throws Exception {
        String password = args.length > 0 ? args[0] : "Pathivu@2026";
        Db admin = Db.fromFile(Path.of("db.properties"), "admin.");
        UserDao users = new UserDao();
        for (Person p : PEOPLE) {
            boolean exists = admin.read(c -> users.findCredentials(c, p.username()) != null);
            if (exists) { System.out.println("exists  " + p.username()); continue; }
            byte[] salt = PasswordHasher.newSalt();
            String hash = PasswordHasher.hash(password.toCharArray(), salt);
            admin.tx(c -> {
                int id = users.insert(c, p.username(), p.name(), p.role(), hash, Hex.of(salt));
                new AuditDao().append(c, null, id, "USER_CREATED", p.username() + " as " + p.role());
                return null;
            });
            System.out.println("created " + p.username() + " (" + p.role() + ")");
        }
        System.out.println("Password for all demo users: " + password + "   (change it before any real use)");
    }
}
