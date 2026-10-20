package pathivu.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256, built into the JDK (no library).
 * 600,000 iterations follows the OWASP Password Storage Cheat Sheet figure
 * for PBKDF2-HMAC-SHA256. Each user gets a random 16-byte salt, so two users
 * with the same password get different hashes.
 */
public final class PasswordHasher {

    public static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static byte[] newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static String hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Hex.of(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JDK", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Constant-time comparison, so the time taken does not leak how many characters matched. */
    public static boolean verify(char[] password, String saltHex, String expectedHashHex) {
        String actual = hash(password, Hex.parse(saltHex));
        return MessageDigest.isEqual(actual.getBytes(), expectedHashHex.getBytes());
    }

    /** Wipe a password from memory once it has been used. */
    public static void wipe(char[] password) { if (password != null) Arrays.fill(password, '\0'); }
}
