package pathivu.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 fingerprints for evidence files and for the audit chain. */
public final class Sha256 {
    private Sha256() { }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String of(String text) {
        return Hex.of(digest().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Reads the file in 8 KB pieces, so a 2 GB log does not need 2 GB of memory. */
    public static String ofFile(Path file) throws IOException {
        MessageDigest md = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return Hex.of(md.digest());
    }
}
