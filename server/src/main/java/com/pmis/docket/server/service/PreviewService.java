package com.pmis.docket.server.service;

import com.pmis.docket.server.model.Node;
import com.pmis.docket.server.model.NodeType;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.web.ApiException;
import com.pmis.docket.server.web.Dto.ArchiveEntryDto;
import com.pmis.docket.server.web.Dto.NodeDto;
import com.pmis.docket.server.web.Dto.PreviewInfo;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Previews inside Docket: page images for PDF and Office files (Office goes through LibreOffice first),
 * PNG versions of images Java can read, and archive contents. Results are cached by content.
 */
@Service
public class PreviewService {
    private static final int MAX_ARCHIVE_ENTRIES = 500;
    private final NodeService nodes;
    private final StorageService storage;
    private final ToolService tools;
    private final AuditService audit;
    private final com.pmis.docket.server.repo.NodeRepository nodeRepo;

    public PreviewService(NodeService nodes, StorageService storage, ToolService tools, AuditService audit,
                          com.pmis.docket.server.repo.NodeRepository nodeRepo) {
        this.nodes = nodes;
        this.storage = storage;
        this.tools = tools;
        this.audit = audit;
        this.nodeRepo = nodeRepo;
    }

    @Transactional(readOnly = true)
    public PreviewInfo info(User user, Long id) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        String e = FileTypes.ext(n.ext);
        try {
            if (FileTypes.PDF.contains(e) || FileTypes.office(e) && !e.equals("csv")) {
                if (!FileTypes.PDF.contains(e) && !tools.hasOffice()) {
                    return new PreviewInfo("none", 0, "Previews of Office files need LibreOffice on the server. Use Open in app.");
                }
                try (PDDocument doc = Loader.loadPDF(pdfFor(n).toFile())) {
                    return new PreviewInfo(FileTypes.SLIDES.contains(e) ? "slides" : "page", doc.getNumberOfPages(), null);
                }
            }
            if (FileTypes.IMAGE.contains(e)) {
                if (FileTypes.IMAGE_READABLE.contains(e)) return new PreviewInfo("image", 1, null);
                return new PreviewInfo("none", 0, "Docket can’t show ." + e + " pictures yet. Use Open in app.");
            }
            if (FileTypes.VIDEO.contains(e)) return new PreviewInfo("video", 0, null);
            if (FileTypes.AUDIO.contains(e)) return new PreviewInfo("audio", 0, null);
            if (FileTypes.TEXT.contains(e) || e.equals("csv")) return new PreviewInfo("text", 0, null);
            if (FileTypes.CODE.contains(e)) return new PreviewInfo("code", 0, null);
            if (FileTypes.ARCHIVE.contains(e)) {
                if (e.equals("rar")) return new PreviewInfo("none", 0, "RAR archives can’t be opened inside Docket. Use Open in app.");
                return new PreviewInfo("archive", 0, null);
            }
        } catch (InvalidPasswordException ex) {
            return new PreviewInfo("none", 0, "This file is password-protected. Open it in the desktop app and type the password.");
        } catch (IOException ex) {
            return new PreviewInfo("none", 0, "Docket couldn’t read this file. It may be damaged. Try Open in app.");
        }
        return new PreviewInfo("none", 0, null);
    }

    /** PNG image of one page (1-based). */
    @Transactional(readOnly = true)
    public byte[] page(User user, Long id, int page, int dpi) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        int d = Math.max(48, Math.min(dpi, 200));
        Path cached = cache().resolve(n.blobKey + "-p" + page + "-" + d + ".png");
        try {
            if (Files.isRegularFile(cached)) return Files.readAllBytes(cached);
            try (PDDocument doc = Loader.loadPDF(pdfFor(n).toFile())) {
                if (page < 1 || page > doc.getNumberOfPages()) throw ApiException.badRequest("That page doesn’t exist.");
                BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(page - 1, d);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                Files.write(cached, out.toByteArray());
                return out.toByteArray();
            }
        } catch (InvalidPasswordException e) {
            throw ApiException.conflict("This file is password-protected.");
        } catch (IOException e) {
            throw ApiException.conflict("Docket couldn’t show this page.");
        }
    }

    /** The picture as PNG (for formats JavaFX can’t show directly, like WebP). */
    @Transactional(readOnly = true)
    public byte[] image(User user, Long id) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        try {
            BufferedImage img = ImageIO.read(storage.pathFor(n.blobKey).toFile());
            if (img == null) throw ApiException.conflict("Docket can’t read this picture.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw ApiException.conflict("Docket can’t read this picture.");
        }
    }

    @Transactional(readOnly = true)
    public List<ArchiveEntryDto> entries(User user, Long id) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        List<ArchiveEntryDto> out = new ArrayList<>();
        forEachEntry(n, (name, size, folder, in) -> {
            if (out.size() < MAX_ARCHIVE_ENTRIES) out.add(new ArchiveEntryDto(name, size, folder));
        });
        out.sort(Comparator.comparing(ArchiveEntryDto::name));
        return out;
    }

    /** Unpacks an archive into a new folder next to it. */
    @Transactional
    public NodeDto extract(User user, String computer, Long id) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        Node parent = nodeRepo.findById(n.parentId).orElseThrow();
        nodes.requireWriteInside(user, parent);
        String[] base = {n.name};
        Node folder = new Node();
        folder.parentId = parent.id;
        folder.type = NodeType.FOLDER;
        folder.space = parent.space;
        folder.ownerId = user.id;
        folder.name = nodes.uniqueName(parent.id, base[0], null);
        folder.createdAt = folder.modifiedAt = java.time.Instant.now();
        folder.modifiedBy = user.id;
        nodeRepo.save(folder);
        Map<String, Node> dirs = new HashMap<>();
        dirs.put("", folder);
        long[] total = {0};
        forEachEntry(n, (name, size, isFolder, in) -> {
            String clean = name.replace('\\', '/');
            while (clean.startsWith("/")) clean = clean.substring(1);
            if (clean.isEmpty() || clean.contains("../")) return;
            if (isFolder) {
                dirFor(dirs, clean.endsWith("/") ? clean.substring(0, clean.length() - 1) : clean, user);
                return;
            }
            int slash = clean.lastIndexOf('/');
            Node dir = dirFor(dirs, slash < 0 ? "" : clean.substring(0, slash), user);
            String fileName = slash < 0 ? clean : clean.substring(slash + 1);
            StorageService.Stored stored = storage.store(in);
            total[0] += stored.size();
            String[] split = NodeService.splitName(fileName);
            nodes.addFile(dir, safe(split[0]), split[1], stored, user, "Extracted from “" + n.fullName() + "”");
        });
        nodes.checkQuota(user, parent, 0);
        audit.record(user, computer, "Extracted “" + n.fullName() + "” (" + NodeService.human(total[0]) + ")", "Edited", folder, nodes.pathText(folder.id));
        return nodes.toDto(user, folder);
    }

    /** PDF for a PDF or Office file (Office converted once with LibreOffice and cached). */
    Path pdfFor(Node n) {
        String e = FileTypes.ext(n.ext);
        if (FileTypes.PDF.contains(e)) return storage.pathFor(n.blobKey);
        Path cached = cache().resolve(n.blobKey + ".pdf");
        if (Files.isRegularFile(cached)) return cached;
        try {
            Path work = Files.createTempDirectory(storage.root().resolve("tmp"), "pv");
            Path in = work.resolve("document." + e);
            Files.copy(storage.pathFor(n.blobKey), in);
            Path pdf = tools.office(in, "pdf", null);
            Files.copy(pdf, cached);
            return cached;
        } catch (IOException ex) {
            throw ApiException.conflict("Docket couldn’t prepare a preview of this file.");
        }
    }

    private Path cache() {
        Path c = storage.root().resolve("cache");
        try { Files.createDirectories(c); } catch (IOException ignored) { }
        return c;
    }

    private Node dirFor(Map<String, Node> dirs, String path, User user) {
        if (dirs.containsKey(path)) return dirs.get(path);
        int slash = path.lastIndexOf('/');
        Node parent = dirFor(dirs, slash < 0 ? "" : path.substring(0, slash), user);
        Node d = new Node();
        d.parentId = parent.id;
        d.type = NodeType.FOLDER;
        d.space = parent.space;
        d.ownerId = user.id;
        d.name = nodes.uniqueName(parent.id, safe(slash < 0 ? path : path.substring(slash + 1)), null);
        d.createdAt = d.modifiedAt = java.time.Instant.now();
        d.modifiedBy = user.id;
        nodeRepo.save(d);
        dirs.put(path, d);
        return d;
    }

    private static String safe(String s) {
        String r = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return r.isEmpty() ? "Untitled" : (r.length() > 200 ? r.substring(0, 200) : r);
    }

    interface EntryVisitor { void visit(String name, long size, boolean folder, InputStream in) throws IOException; }

    private void forEachEntry(Node n, EntryVisitor v) {
        String e = FileTypes.ext(n.ext);
        File f = storage.pathFor(n.blobKey).toFile();
        try {
            switch (e) {
                case "zip" -> {
                    try (ZipFile zip = new ZipFile(f)) {
                        Enumeration<? extends ZipEntry> en = zip.entries();
                        while (en.hasMoreElements()) {
                            ZipEntry z = en.nextElement();
                            try (InputStream in = z.isDirectory() ? InputStream.nullInputStream() : zip.getInputStream(z)) {
                                v.visit(z.getName(), Math.max(0, z.getSize()), z.isDirectory(), in);
                            }
                        }
                    }
                }
                case "7z" -> {
                    try (SevenZFile sz = SevenZFile.builder().setFile(f).get()) {
                        SevenZArchiveEntry z;
                        while ((z = sz.getNextEntry()) != null) {
                            v.visit(z.getName(), Math.max(0, z.getSize()), z.isDirectory(), z.isDirectory() ? InputStream.nullInputStream() : sz.getInputStream(z));
                        }
                    }
                }
                case "tar", "gz", "tgz" -> {
                    InputStream raw = new BufferedInputStream(new FileInputStream(f));
                    if (!e.equals("tar")) raw = new GzipCompressorInputStream(raw);
                    try (ArchiveInputStream<?> tin = new TarArchiveInputStream(raw)) {
                        ArchiveEntry z;
                        while ((z = tin.getNextEntry()) != null) {
                            v.visit(z.getName(), Math.max(0, z.getSize()), z.isDirectory(), z.isDirectory() ? InputStream.nullInputStream() : nonClosing(tin));
                        }
                    }
                }
                default -> throw ApiException.badRequest("Docket can’t open ." + e + " archives. Use Open in app.");
            }
        } catch (IOException ex) {
            throw ApiException.conflict("This archive couldn’t be read. It may be damaged or password-protected.");
        }
    }

    private static InputStream nonClosing(InputStream in) {
        return new FilterInputStream(in) {
            @Override
            public void close() { }
        };
    }
}
