package pathivu.security;

import java.util.HexFormat;

/** Lower-case hex, matching what MySQL's SHA2() returns. */
public final class Hex {
    private static final HexFormat HEX = HexFormat.of();
    private Hex() { }
    public static String of(byte[] bytes) { return HEX.formatHex(bytes); }
    public static byte[] parse(String hex) { return HEX.parseHex(hex); }
}
