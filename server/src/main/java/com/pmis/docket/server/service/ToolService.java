package com.pmis.docket.server.service;

import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the two outside programs Docket uses on the server:
 * LibreOffice (Word/Excel/PowerPoint conversions and previews) and FFmpeg (video/audio).
 * If a program is missing, the features that need it say so instead of failing silently.
 */
@Service
public class ToolService {
    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final DocketProperties props;
    private final StorageService storage;
    private final Object officeLock = new Object();

    public ToolService(DocketProperties props, StorageService storage) {
        this.props = props;
        this.storage = storage;
    }

    public String soffice() {
        List<String> candidates = new ArrayList<>();
        if (!props.getSofficePath().isBlank()) candidates.add(props.getSofficePath());
        candidates.add("C:\\Program Files\\LibreOffice\\program\\soffice.exe");
        candidates.add("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe");
        candidates.add("/usr/bin/soffice");
        candidates.add("/usr/bin/libreoffice");
        candidates.add("/opt/libreoffice/program/soffice");
        candidates.add("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        for (String c : candidates) if (new File(c).isFile()) return c;
        return null;
    }

    public boolean hasOffice() { return soffice() != null; }

    public String ffmpeg() {
        String p = props.getFfmpegPath().isBlank() ? "ffmpeg" : props.getFfmpegPath();
        try {
            Process proc = new ProcessBuilder(p, "-version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            return proc.waitFor(10, TimeUnit.SECONDS) && proc.exitValue() == 0 ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasFfmpeg() { return ffmpeg() != null; }

    /** Converts {@code input} with LibreOffice. {@code target} is e.g. "pdf", "docx", "txt:Text", "csv". */
    public Path office(Path input, String target, String inFilter) {
        String exe = soffice();
        if (exe == null) throw ApiException.conflict("This needs LibreOffice on the server. Ask IT to install it.");
        synchronized (officeLock) {
            try {
                Path outDir = Files.createTempDirectory(storage.root().resolve("tmp"), "lo-out");
                Path profile = storage.root().resolve("tmp").resolve("lo-profile");
                List<String> cmd = new ArrayList<>(List.of(exe, "-env:UserInstallation=" + profile.toUri(),
                        "--headless", "--norestore", "--nolockcheck", "--nodefault"));
                if (inFilter != null) cmd.add("--infilter=" + inFilter);
                cmd.addAll(List.of("--convert-to", target, "--outdir", outDir.toString(), input.toString()));
                run(cmd, 180);
                String ext = target.contains(":") ? target.substring(0, target.indexOf(':')) : target;
                String base = input.getFileName().toString();
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                Path out = outDir.resolve(base + "." + ext);
                if (!Files.isRegularFile(out)) throw ApiException.conflict("LibreOffice couldn’t convert this file.");
                return out;
            } catch (IOException e) {
                throw ApiException.conflict("LibreOffice couldn’t convert this file.");
            }
        }
    }

    /** Runs FFmpeg: input -> output with extra arguments placed before the output. */
    public void ffmpeg(Path input, Path output, List<String> args) {
        String exe = ffmpeg();
        if (exe == null) throw ApiException.conflict("Video and audio conversion needs FFmpeg on the server. Ask IT to install it.");
        List<String> cmd = new ArrayList<>(List.of(exe, "-y", "-hide_banner", "-loglevel", "error", "-i", input.toString()));
        cmd.addAll(args);
        cmd.add(output.toString());
        run(cmd, 1800);
        if (!Files.isRegularFile(output)) throw ApiException.conflict("FFmpeg couldn’t convert this file.");
    }

    private void run(List<String> cmd, int timeoutSeconds) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            Thread drain = new Thread(() -> {
                try { p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); } catch (IOException ignored) { }
            });
            drain.setDaemon(true);
            drain.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw ApiException.conflict("The conversion took too long and was stopped.");
            }
        } catch (IOException e) {
            log.warn("Tool failed: {}", cmd, e);
            throw ApiException.conflict("The conversion tool on the server didn’t start.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("The conversion was interrupted.");
        }
    }
}
