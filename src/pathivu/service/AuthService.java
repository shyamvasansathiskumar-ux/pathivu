package pathivu.service;

import pathivu.dao.Db;
import pathivu.dao.UserDao;
import pathivu.model.AppUser;
import pathivu.security.AccessDenied;
import pathivu.security.PasswordHasher;

public class AuthService {
    private final Db db;
    private final UserDao users = new UserDao();

    public AuthService(Db db) { this.db = db; }

    /** user_id -> full name, for showing who did what. */
    public java.util.Map<Integer, String> names() {
        java.util.Map<Integer, String> m = new java.util.HashMap<>();
        for (AppUser u : db.read(users::listAll)) m.put(u.id(), u.fullName());
        return m;
    }

    public java.util.List<AppUser> all() { return db.read(users::listAll); }

    public static final int MAX_FAILURES = 5;
    private static final String DUMMY_SALT = pathivu.security.Hex.of(PasswordHasher.newSalt());
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password".toCharArray(),
                                                                 pathivu.security.Hex.parse(DUMMY_SALT));
    public static final int LOCK_MINUTES = 15;
    private final pathivu.dao.LoginDao logins = new pathivu.dao.LoginDao();

    /**
     * Same message for "no such user" and "wrong password", so the screen does not reveal which
     * usernames exist. Five failures in 15 minutes lock the username (real or not) for 15 minutes,
     * which stops password guessing. Every attempt is recorded, append-only.
     */
    public AppUser login(String username, char[] password) {
        String name = username == null ? "" : username.trim();
        try {
            if (db.read(c -> logins.recentFailures(c, name, LOCK_MINUTES)) >= MAX_FAILURES) {
                db.tx(c -> { logins.record(c, name, false); return null; });
                throw new AccessDenied("Too many failed attempts. This account is locked for " + LOCK_MINUTES + " minutes.");
            }
            UserDao.Credentials cr = db.read(c -> users.findCredentials(c, name));
            // Pass 3: always do the slow PBKDF2 work, even for a username that does not
            // exist. Otherwise "no such user" answers in 1 ms and "wrong password" in
            // about 300 ms, and the stopwatch tells an attacker which usernames are real.
            boolean ok;
            if (cr == null) { PasswordHasher.verify(password, DUMMY_SALT, DUMMY_HASH); ok = false; }
            else ok = PasswordHasher.verify(password, cr.salt(), cr.hash()) && cr.active();
            if (!ok) {
                db.tx(c -> { logins.record(c, name, false); return null; });
                throw new AccessDenied("Username or password is wrong.");
            }
            db.tx(c -> { logins.record(c, name, true); return null; });
            db.tx(c -> { new pathivu.dao.AuditDao().append(c, null, cr.user().id(), "LOGIN", cr.user().username() + " signed in"); return null; });
            return cr.user();
        } finally {
            PasswordHasher.wipe(password);
        }
    }
}
