package com.pmis.docket.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Working copies of checked-out files on this PC:
 * %LOCALAPPDATA%\PMIS Docket\checked-out\<id>\<file name>, plus the time it was downloaded,
 * so check-in knows whether the user actually changed it.
 */
public final class CheckoutStore {
    private static final Path ROOT = AppConfig.dataDir().resolve("checked-out");

    private CheckoutStore() { }

    public static Path pathFor(long id, String fileName) { return ROOT.resolve(String.valueOf(id)).resolve(fileName); }

    public static void remember(long id, Path file) {
        Properties p = new Properties();
        try {
            p.setProperty("file", file.getFileName().toString());
            p.setProperty("modified", String.valueOf(Files.getLastModifiedTime(file).toMillis()));
            p.setProperty("size", String.valueOf(Files.size(file)));
            try (OutputStream out = Files.newOutputStream(ROOT.resolve(String.valueOf(id)).resolve(".docket"))) {
                p.store(out, "PMIS Docket check-out");
            }
        } catch (IOException ignored) {
            // Without the marker, check-in simply uploads the file.
        }
    }

    /** The working copy, or null if there is none on this PC. */
    public static Path workingCopy(long id) {
        Properties p = read(id);
        String name = p.getProperty("file");
        if (name == null) return null;
        Path f = ROOT.resolve(String.valueOf(id)).resolve(name);
        return Files.isRegularFile(f) ? f : null;
    }

    public static boolean changed(long id) {
        Path f = workingCopy(id);
        if (f == null) return false;
        Properties p = read(id);
        try {
            long m = Long.parseLong(p.getProperty("modified", "0"));
            long s = Long.parseLong(p.getProperty("size", "-1"));
            return Files.getLastModifiedTime(f).toMillis() != m || Files.size(f) != s;
        } catch (IOException | NumberFormatException e) {
            return true;
        }
    }

    public static void forget(long id) {
        Path dir = ROOT.resolve(String.valueOf(id));
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(x -> {
                try { Files.deleteIfExists(x); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // Leftover files are harmless.
        }
    }

    private static Properties read(long id) {
        Properties p = new Properties();
        Path f = ROOT.resolve(String.valueOf(id)).resolve(".docket");
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // treat as unknown
            }
        }
        return p;
    }
}
