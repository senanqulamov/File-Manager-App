package com.pmis.docket.desktop.ui;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private Format() { }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return (v >= 10 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v)) + " " + units[i];
    }

    public static String when(Instant at) {
        if (at == null) return "";
        ZonedDateTime t = at.atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        if (t.toLocalDate().equals(today)) return "Today, " + TIME.format(t);
        if (t.toLocalDate().equals(today.minusDays(1))) return "Yesterday, " + TIME.format(t);
        return DATE.format(t);
    }
}
