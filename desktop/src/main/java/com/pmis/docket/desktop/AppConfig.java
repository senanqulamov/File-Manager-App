package com.pmis.docket.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings file. The installer writes the company server address to
 * %ProgramData%\PMIS Docket\docket.properties (everyone) or
 * %LOCALAPPDATA%\PMIS Docket\docket.properties (only me). The user file wins.
 */
public final class AppConfig {
    public static final String DEFAULT_SERVER = "http://localhost:8080";
    /** Version of this app. Bump it for every installer you publish. */
    public static final String APP_VERSION = "1.0.0";

    private static final Path USER_DIR = Path.of(env("LOCALAPPDATA", System.getProperty("user.home")), "PMIS Docket");
    private static final Path USER_FILE = USER_DIR.resolve("docket.properties");
    private static final Path MACHINE_FILE = Path.of(env("ProgramData", "C:\\ProgramData"), "PMIS Docket", "docket.properties");

    private final Properties props = new Properties();

    private AppConfig() { }

    public static AppConfig load() {
        AppConfig c = new AppConfig();
        c.read(MACHINE_FILE);
        c.read(USER_FILE);
        return c;
    }

    public String serverUrl() { return props.getProperty("server.url", DEFAULT_SERVER).trim(); }

    public void setServerUrl(String url) {
        props.setProperty("server.url", url.trim());
        save();
    }

    public String lastLogin() { return props.getProperty("last.login", ""); }

    public void setLastLogin(String login) {
        props.setProperty("last.login", login);
        save();
    }

    public static String computerName() {
        String n = System.getenv("COMPUTERNAME");
        if (n != null && !n.isBlank()) return n;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "Unknown PC";
        }
    }

    public static Path tempDir() { return USER_DIR.resolve("temp"); }

    public static Path dataDir() { return USER_DIR; }

    public boolean previewPane() { return Boolean.parseBoolean(props.getProperty("preview.pane", "false")); }

    public void setPreviewPane(boolean on) {
        props.setProperty("preview.pane", String.valueOf(on));
        save();
    }

    public boolean listView() { return Boolean.parseBoolean(props.getProperty("view.list", "false")); }

    public void setListView(boolean on) {
        props.setProperty("view.list", String.valueOf(on));
        save();
    }

    private void read(Path file) {
        if (!Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            // A broken settings file should never stop the app from starting.
        }
    }

    private void save() {
        try {
            Files.createDirectories(USER_DIR);
            try (OutputStream out = Files.newOutputStream(USER_FILE)) {
                props.store(out, "PMIS Docket settings");
            }
        } catch (IOException ignored) {
            // Not critical: settings just won't be remembered.
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
