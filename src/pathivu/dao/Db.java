package pathivu.dao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens connections and runs transactions.
 *
 * tx(...) is the one place commit/rollback happens: either every statement in
 * the block is saved, or none is. Reporting an incident writes to up to six
 * tables; a crash halfway must not leave an incident without its clocks.
 */
public class Db {

    private final String url, user, password;

    public Db(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }

    /** Reads db.properties (url, user, password). prefix "db." for the app, "admin." for Setup. */
    public static Db fromFile(Path file, String prefix) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        String url = p.getProperty(prefix + "url");
        if (url == null) throw new IOException("Missing " + prefix + "url in " + file);
        return new Db(url, p.getProperty(prefix + "user"), p.getProperty(prefix + "password", ""));
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @FunctionalInterface
    public interface Work<T> { T run(Connection c) throws SQLException; }

    /** Runs the work inside one transaction. Any exception rolls everything back. */
    public <T> T tx(Work<T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw DataException.from(e);
        }
    }

    /** Read-only work, no transaction needed. */
    public <T> T read(Work<T> work) {
        try (Connection c = open()) {
            return work.run(c);
        } catch (SQLException e) {
            throw DataException.from(e);
        }
    }
}
