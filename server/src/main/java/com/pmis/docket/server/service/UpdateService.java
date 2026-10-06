package com.pmis.docket.server.service;

import com.pmis.docket.server.model.User;
import com.pmis.docket.server.web.ApiException;
import com.pmis.docket.server.web.Dto.UpdateInfo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Automatic updates: IT uploads a new PMIS-Docket-Setup.exe in the admin console;
 * desktop apps check at sign-in and offer to install it.
 */
@Service
public class UpdateService {
    private final StorageService storage;
    private final AuditService audit;

    public UpdateService(StorageService storage, AuditService audit) {
        this.storage = storage;
        this.audit = audit;
    }

    public UpdateInfo latest(String currentVersion) {
        Properties p = read();
        String v = p.getProperty("version");
        boolean available = v != null && Files.isRegularFile(installer()) && compare(v, currentVersion == null ? "0" : currentVersion) > 0;
        return new UpdateInfo(v, available, p.getProperty("notes", ""));
    }

    public Path installer() { return dir().resolve("PMIS-Docket-Setup.exe"); }

    public UpdateInfo publish(User admin, String computer, String version, String notes, InputStream in) {
        AdminService.requireAdmin(admin);
        if (version == null || !version.matches("\\d+(\\.\\d+){1,3}")) throw ApiException.badRequest("Type a version like 1.1.0.");
        try {
            Path tmp = dir().resolve("upload.part");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, installer(), StandardCopyOption.REPLACE_EXISTING);
            Properties p = new Properties();
            p.setProperty("version", version);
            p.setProperty("notes", notes == null ? "" : notes);
            try (OutputStream out = Files.newOutputStream(dir().resolve("latest.properties"))) {
                p.store(out, "PMIS Docket update");
            }
        } catch (IOException e) {
            throw ApiException.conflict("Could not save the installer on the server.");
        }
        audit.record(admin, computer, "Published app update " + version, "Permissions", null);
        return latest("0");
    }

    private Properties read() {
        Properties p = new Properties();
        Path f = dir().resolve("latest.properties");
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // no update information
            }
        }
        return p;
    }

    private Path dir() {
        Path d = storage.root().resolve("updates");
        try { Files.createDirectories(d); } catch (IOException ignored) { }
        return d;
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; }
    }
}
