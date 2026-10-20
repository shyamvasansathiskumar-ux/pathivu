package pathivu.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Time handling. Rule: store UTC, show IST.
 * Everything inside the program is an Instant (a point on the UTC timeline).
 * Only the screen and the reports convert to Asia/Kolkata.
 */
public final class Times {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'");
    public static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Times() { }

    public static String ist(Instant t) { return t == null ? "" : SHOW.format(t.atZone(IST)); }

    public static String inputIst(Instant t) { return INPUT.format(t.atZone(IST)); }

    /** Parse "2026-10-19 14:30" typed by a person in India. */
    public static Instant parseIst(String text) {
        try {
            return LocalDateTime.parse(text.trim(), INPUT).atZone(IST).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Use the format yyyy-MM-dd HH:mm, for example 2026-10-19 14:30");
        }
    }

    /** Instant to the UTC wall-clock value stored in a DATETIME column. */
    public static LocalDateTime toUtc(Instant t) { return t == null ? null : LocalDateTime.ofInstant(t, ZoneOffset.UTC); }

    public static Instant fromUtc(LocalDateTime t) { return t == null ? null : t.toInstant(ZoneOffset.UTC); }

    /** "4h 12m left" or "2h 05m overdue". */
    public static String left(Duration d) {
        boolean late = d.isNegative();
        long mins = Math.abs(d.toMinutes());
        String body = mins >= 1440 ? (mins / 1440) + "d " + (mins % 1440) / 60 + "h"
                                   : (mins / 60) + "h " + String.format("%02dm", mins % 60);
        return body + (late ? " overdue" : " left");
    }
}
