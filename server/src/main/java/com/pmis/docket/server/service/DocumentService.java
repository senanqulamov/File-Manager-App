package com.pmis.docket.server.service;

import com.pmis.docket.server.model.Node;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.repo.NodeRepository;
import com.pmis.docket.server.web.ApiException;
import com.pmis.docket.server.web.Dto.ActionResult;
import com.pmis.docket.server.web.Dto.ConversionsDto;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Convert, Sign, Lock and Stamp. Results are saved on the server as new files or new versions. */
@Service
public class DocumentService {
    private static final Map<String, Color> STAMP_COLORS = Map.of(
            "APPROVED", new Color(0x15803D), "CONFIDENTIAL", new Color(0xC2410C), "DRAFT", new Color(0x6B7280),
            "COPY", new Color(0x2563EB), "PAID", new Color(0x15803D), "REJECTED", new Color(0xB42318));

    private final NodeService nodes;
    private final NodeRepository nodeRepo;
    private final StorageService storage;
    private final ToolService tools;
    private final SigningService signing;
    private final PreviewService preview;
    private final AuditService audit;

    public DocumentService(NodeService nodes, NodeRepository nodeRepo, StorageService storage, ToolService tools,
                           SigningService signing, PreviewService preview, AuditService audit) {
        this.nodes = nodes;
        this.nodeRepo = nodeRepo;
        this.storage = storage;
        this.tools = tools;
        this.signing = signing;
        this.preview = preview;
        this.audit = audit;
    }

    // ================================================================== convert

    /** Formats this file can be converted to on this server right now. */
    @Transactional(readOnly = true)
    public ConversionsDto conversions(User user, Long id) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        String e = FileTypes.ext(n.ext);
        boolean office = tools.hasOffice();
        List<String> f = new ArrayList<>();
        String note = null;
        if (FileTypes.PDF.contains(e)) {
            f.add("Images");
            f.add("Text");
            if (office) f.add("Word");
        } else if (FileTypes.DOC.contains(e)) {
            if (office) f.addAll(List.of("PDF", "Word", "Text"));
        } else if (FileTypes.SHEET.contains(e)) {
            if (office) f.addAll(e.equals("csv") ? List.of("PDF", "Excel") : List.of("PDF", "CSV", "Excel"));
        } else if (FileTypes.SLIDES.contains(e)) {
            if (office) f.addAll(List.of("PDF", "Images", "PowerPoint"));
        } else if (FileTypes.IMAGE_READABLE.contains(e)) {
            f.addAll(List.of("PDF", "PNG", "JPG"));
        } else if (FileTypes.TEXT.contains(e) || FileTypes.CODE.contains(e)) {
            f.add("PDF");
            if (office) f.add("Word");
        } else if (FileTypes.VIDEO.contains(e) || FileTypes.AUDIO.contains(e)) {
            if (tools.hasFfmpeg()) f.addAll(FileTypes.VIDEO.contains(e) ? List.of("MP4", "MP3 audio", "GIF") : List.of("MP3", "WAV"));
            else note = "Video and audio conversion needs FFmpeg on the server.";
        }
        if (f.isEmpty() && note == null && (FileTypes.office(e)) && !office) note = "Office conversions need LibreOffice on the server.";
        f.removeIf(x -> targetExt(x).equals(e) && !x.equals("Images"));
        if (f.isEmpty() && note == null) note = "This type of file can’t be converted.";
        return new ConversionsDto(f, note);
    }

    @Transactional
    public ActionResult convert(User user, String computer, Long id, String format) {
        Node n = nodes.requireFile(id);
        nodes.requireRead(user, n);
        if (!conversions(user, id).formats().contains(format)) throw ApiException.badRequest(n.fullName() + " can’t be converted to " + format + ".");
        String e = FileTypes.ext(n.ext);
        Path src = storage.pathFor(n.blobKey);
        String outExt = targetExt(format);
        byte[] result;
        try {
            Path work = Files.createTempDirectory(storage.root().resolve("tmp"), "cv");
            Path in = work.resolve("source." + (e.isEmpty() ? "bin" : e));
            Files.copy(src, in);
            result = switch (format) {
                case "Images" -> pagesToZip(FileTypes.PDF.contains(e) ? in : tools.office(in, "pdf", null));
                case "Text" -> FileTypes.PDF.contains(e) ? pdfText(in) : Files.readAllBytes(tools.office(in, "txt:Text", null));
                case "PDF" -> {
                    if (FileTypes.IMAGE_READABLE.contains(e)) yield imageToPdf(in);
                    if (FileTypes.TEXT.contains(e) || FileTypes.CODE.contains(e)) yield textToPdf(in, n.fullName());
                    yield Files.readAllBytes(tools.office(in, "pdf", null));
                }
                case "Word" -> Files.readAllBytes(tools.office(FileTypes.CODE.contains(e) ? renamed(in, "txt") : in, "docx",
                        FileTypes.PDF.contains(e) ? "writer_pdf_import" : null));
                case "Excel" -> Files.readAllBytes(tools.office(in, "xlsx", null));
                case "CSV" -> Files.readAllBytes(tools.office(in, "csv", null));
                case "PowerPoint" -> Files.readAllBytes(tools.office(in, "pptx", null));
                case "PNG", "JPG" -> imageTo(in, format.equals("PNG") ? "png" : "jpg");
                case "MP4" -> media(in, "mp4", List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", "-movflags", "+faststart"));
                case "MP3 audio", "MP3" -> media(in, "mp3", List.of("-vn", "-c:a", "libmp3lame", "-q:a", "2"));
                case "WAV" -> media(in, "wav", List.of("-vn"));
                case "GIF" -> media(in, "gif", List.of("-t", "15", "-vf", "fps=10,scale=640:-1:flags=lanczos"));
                default -> throw ApiException.badRequest("Unknown format.");
            };
        } catch (IOException ex) {
            throw ApiException.conflict("The conversion failed. The file may be damaged or password-protected.");
        }
        Node target = writableTargetFor(user, n);
        String baseName = n.name + (format.equals("Images") ? " (pages)" : "");
        Node created = nodes.addFile(target, baseName, outExt, storage.store(result), user, "Converted from " + n.fullName());
        audit.record(user, computer, "Converted to " + format + " → “" + created.fullName() + "”", "Edited", n, nodes.pathText(n.id));
        String where = target.id.equals(n.parentId) ? "the same folder" : "My files";
        return new ActionResult(nodes.toDto(user, created), "Converted. " + created.fullName() + " was saved to " + where + ".");
    }

    // ================================================================== sign

    /**
     * PDF: visible signature + digital signature, saved as a new version.
     * Word/Excel/PowerPoint: converted to PDF, then signed; the signed PDF is saved next to the original.
     */
    @Transactional
    public ActionResult sign(User user, String computer, Long id, String mode, String imagePngBase64, String placement) {
        Node n = nodes.requireFile(id);
        String e = FileTypes.ext(n.ext);
        boolean pdf = FileTypes.PDF.contains(e);
        if (!pdf && !FileTypes.office(e)) throw ApiException.badRequest("Only PDF and Office documents can be signed.");
        if (n.locked) throw ApiException.conflict("This file is locked with a password. Signing must be done before locking.");
        if (pdf) nodes.requireModify(user, n);
        else {
            nodes.requireRead(user, n);
            nodes.requireWriteInside(user, nodeRepo.findById(n.parentId).orElseThrow());
        }
        try {
            byte[] source = pdf ? Files.readAllBytes(storage.pathFor(n.blobKey)) : Files.readAllBytes(preview.pdfFor(n));
            BufferedImage mark = signatureImage(user, mode, imagePngBase64);
            byte[] visible = addVisibleSignature(source, mark, user, placement == null ? "last" : placement);
            byte[] signed = signing.signPdf(visible, user, "Signed in PMIS Docket");
            Node result;
            String msg;
            if (pdf) {
                result = nodes.addVersion(n, storage.store(signed), "pdf", user, "Signed by " + user.displayName);
                result.signed = true;
                nodeRepo.save(result);
                msg = "Signed. " + n.fullName() + " is now version " + result.version + ".";
            } else {
                Node parent = nodeRepo.findById(n.parentId).orElseThrow();
                result = nodes.addFile(parent, n.name + " (signed)", "pdf", storage.store(signed), user, "Signed copy of " + n.fullName());
                result.signed = true;
                nodeRepo.save(result);
                msg = "Signed. A signed PDF copy was saved as " + result.fullName() + ".";
            }
            audit.record(user, computer, "Signed (" + (mode == null ? "draw" : mode.toLowerCase(Locale.ROOT)) + ", certificate " + signing.certificateSubject(user) + ")", "Edited", n, nodes.pathText(n.id));
            return new ActionResult(nodes.toDto(user, result), msg);
        } catch (InvalidPasswordException ex) {
            throw ApiException.conflict("This PDF is password-protected and can’t be signed.");
        } catch (IOException ex) {
            throw ApiException.conflict("Signing failed. The file may be damaged.");
        }
    }

    // ================================================================== lock

    /**
     * PDF: AES-256 password with print/copy permissions. Word/Excel/PowerPoint (.docx/.xlsx/.pptx):
     * Office's own AES encryption. Anything else: replaced by an AES-256 encrypted ZIP.
     * The password is never stored by Docket.
     */
    @Transactional
    public ActionResult lock(User user, String computer, Long id, String password, boolean allowPrint, boolean allowCopy) {
        Node n = nodes.requireFile(id);
        nodes.requireModify(user, n);
        if (password == null || password.length() < 6) throw ApiException.badRequest("Use a password with at least 6 characters.");
        if (n.locked) throw ApiException.conflict("This file is already locked.");
        String e = FileTypes.ext(n.ext);
        Path src = storage.pathFor(n.blobKey);
        try {
            if (FileTypes.PDF.contains(e)) {
                byte[] out;
                try (PDDocument doc = Loader.loadPDF(src.toFile())) {
                    AccessPermission ap = new AccessPermission();
                    ap.setCanPrint(allowPrint);
                    ap.setCanPrintFaithful(allowPrint);
                    ap.setCanExtractContent(allowCopy);
                    ap.setCanExtractForAccessibility(true);
                    ap.setCanModify(false);
                    ap.setCanModifyAnnotations(false);
                    StandardProtectionPolicy p = new StandardProtectionPolicy(UUID.randomUUID().toString(), password, ap);
                    p.setEncryptionKeyLength(256);
                    doc.protect(p);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    doc.save(bos);
                    out = bos.toByteArray();
                }
                return lockedVersion(user, computer, n, out, "pdf", "Locked with password (AES-256" + (allowPrint ? "" : ", no printing") + (allowCopy ? "" : ", no copying") + ")");
            }
            if (FileTypes.OOXML.contains(e)) {
                POIFSFileSystem fs = new POIFSFileSystem();
                EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
                Encryptor enc = info.getEncryptor();
                enc.confirmPassword(password);
                try (InputStream in = Files.newInputStream(src)) {
                    OPCPackage opc = OPCPackage.open(in);
                    try (OutputStream os = enc.getDataStream(fs)) {
                        opc.save(os);
                    } finally {
                        opc.revert();
                    }
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                fs.writeFilesystem(bos);
                fs.close();
                return lockedVersion(user, computer, n, bos.toByteArray(), e, "Locked with password (Office AES encryption)");
            }
            // everything else: encrypted ZIP replaces the original
            Path work = Files.createTempDirectory(storage.root().resolve("tmp"), "lk");
            Path zipPath = work.resolve("locked.zip");
            ZipParameters zp = new ZipParameters();
            zp.setEncryptFiles(true);
            zp.setEncryptionMethod(EncryptionMethod.AES);
            zp.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            zp.setFileNameInZip(n.fullName());
            try (ZipFile zip = new ZipFile(zipPath.toFile(), password.toCharArray()); InputStream in = Files.newInputStream(src)) {
                zip.addStream(in, zp);
            }
            Node parent = nodeRepo.findById(n.parentId).orElseThrow();
            Node created;
            try (InputStream in = Files.newInputStream(zipPath)) {
                created = nodes.addFile(parent, n.fullName(), "zip", storage.store(in), user, "Encrypted ZIP of " + n.fullName());
            }
            created.locked = true;
            nodeRepo.save(created);
            n.deleted = true;
            n.deletedAt = java.time.Instant.now();
            n.deletedBy = user.id;
            n.deleteRoot = true;
            nodeRepo.save(n);
            audit.record(user, computer, "Locked in encrypted ZIP “" + created.fullName() + "” (original moved to Recycle Bin)", "Edited", created, nodes.pathText(created.id));
            return new ActionResult(nodes.toDto(user, created), "Locked. " + n.fullName() + " is now inside " + created.fullName() + " and needs the password to open.");
        } catch (InvalidPasswordException ex) {
            throw ApiException.conflict("This file is already password-protected.");
        } catch (Exception ex) {
            if (ex instanceof ApiException a) throw a;
            throw ApiException.conflict("Locking failed. The file may be damaged or already protected.");
        }
    }

    private ActionResult lockedVersion(User user, String computer, Node n, byte[] bytes, String ext, String note) {
        Node r = nodes.addVersion(n, storage.store(bytes), ext, user, note);
        r.locked = true;
        nodeRepo.save(r);
        audit.record(user, computer, note, "Edited", n, nodes.pathText(n.id));
        return new ActionResult(nodes.toDto(user, r), "Locked. " + n.fullName() + " now needs a password to open (version " + r.version + ").");
    }

    // ================================================================== stamp

    /** PDF and pictures: stamped in place as a new version. Word/PowerPoint: a stamped PDF copy. */
    @Transactional
    public ActionResult stamp(User user, String computer, Long id, String text, String colorHex) {
        Node n = nodes.requireFile(id);
        String e = FileTypes.ext(n.ext);
        String label = text == null || text.isBlank() ? "APPROVED" : text.trim().toUpperCase(Locale.ROOT);
        if (label.length() > 40) label = label.substring(0, 40);
        Color color = colorHex != null && colorHex.matches("#[0-9A-Fa-f]{6}") ? Color.decode(colorHex) : STAMP_COLORS.getOrDefault(label, new Color(0xC2410C));
        if (n.locked) throw ApiException.conflict("This file is locked with a password and can’t be stamped.");
        try {
            if (FileTypes.PDF.contains(e)) {
                nodes.requireModify(user, n);
                byte[] out = stampPdf(Files.readAllBytes(storage.pathFor(n.blobKey)), label, color);
                Node r = nodes.addVersion(n, storage.store(out), "pdf", user, "Stamped “" + label + "”");
                r.stamp = label;
                nodeRepo.save(r);
                audit.record(user, computer, "Stamped “" + label + "”", "Edited", n, nodes.pathText(n.id));
                return new ActionResult(nodes.toDto(user, r), "Stamped “" + label + "” on " + n.fullName() + ".");
            }
            if (FileTypes.IMAGE_READABLE.contains(e)) {
                nodes.requireModify(user, n);
                BufferedImage img = ImageIO.read(storage.pathFor(n.blobKey).toFile());
                if (img == null) throw ApiException.conflict("This picture can’t be read.");
                String outExt = e.equals("jpg") || e.equals("jpeg") ? "jpg" : "png";
                BufferedImage canvas = new BufferedImage(img.getWidth(), img.getHeight(), outExt.equals("jpg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                g.drawImage(img, 0, 0, null);
                drawStamp(g, label, color, img.getWidth(), img.getHeight());
                g.dispose();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(canvas, outExt, bos);
                Node r = nodes.addVersion(n, storage.store(bos.toByteArray()), outExt.equals(e) ? null : outExt, user, "Stamped “" + label + "”");
                r.stamp = label;
                nodeRepo.save(r);
                audit.record(user, computer, "Stamped “" + label + "”", "Edited", n, nodes.pathText(n.id));
                return new ActionResult(nodes.toDto(user, r), "Stamped “" + label + "” on " + n.fullName() + ".");
            }
            if (FileTypes.DOC.contains(e) || FileTypes.SLIDES.contains(e)) {
                nodes.requireRead(user, n);
                Node parent = nodeRepo.findById(n.parentId).orElseThrow();
                nodes.requireWriteInside(user, parent);
                byte[] out = stampPdf(Files.readAllBytes(preview.pdfFor(n)), label, color);
                Node r = nodes.addFile(parent, n.name + " (" + label.toLowerCase(Locale.ROOT) + ")", "pdf", storage.store(out), user, "Stamped copy of " + n.fullName());
                r.stamp = label;
                nodeRepo.save(r);
                audit.record(user, computer, "Stamped “" + label + "” → “" + r.fullName() + "”", "Edited", n, nodes.pathText(n.id));
                return new ActionResult(nodes.toDto(user, r), "Stamped. A PDF copy was saved as " + r.fullName() + ".");
            }
            throw ApiException.badRequest("This type of file can’t be stamped.");
        } catch (IOException ex) {
            throw ApiException.conflict("Stamping failed. The file may be damaged or password-protected.");
        }
    }

    // ================================================================== helpers

    private Node writableTargetFor(User user, Node n) {
        Node parent = nodeRepo.findById(n.parentId).orElseThrow();
        if (nodes.canWriteInside(user, parent)) return parent;
        return nodes.personalRootOf(user);
    }

    static String targetExt(String format) {
        return switch (format) {
            case "PDF" -> "pdf";
            case "Word" -> "docx";
            case "Excel" -> "xlsx";
            case "PowerPoint" -> "pptx";
            case "CSV" -> "csv";
            case "Text" -> "txt";
            case "Images" -> "zip";
            case "PNG" -> "png";
            case "JPG" -> "jpg";
            case "MP4" -> "mp4";
            case "MP3", "MP3 audio" -> "mp3";
            case "WAV" -> "wav";
            case "GIF" -> "gif";
            default -> "bin";
        };
    }

    private Path renamed(Path in, String ext) throws IOException {
        Path out = in.resolveSibling("source." + ext);
        Files.copy(in, out);
        return out;
    }

    private byte[] media(Path in, String ext, List<String> args) throws IOException {
        Path out = in.resolveSibling("result." + ext);
        tools.ffmpeg(in, out, args);
        return Files.readAllBytes(out);
    }

    private byte[] pdfText(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(doc).getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] pagesToZip(Path pdf) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile()); ZipOutputStream zip = new ZipOutputStream(bos)) {
            PDFRenderer r = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = r.renderImageWithDPI(i, 150);
                zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT, "page-%03d.png", i + 1)));
                ImageIO.write(img, "png", zip);
                zip.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private byte[] imageTo(Path in, String fmt) throws IOException {
        BufferedImage img = ImageIO.read(in.toFile());
        if (img == null) throw ApiException.conflict("This picture can’t be read.");
        BufferedImage out = img;
        if (fmt.equals("jpg") && img.getColorModel().hasAlpha()) {
            out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, fmt, bos);
        return bos.toByteArray();
    }

    private byte[] imageToPdf(Path in) throws IOException {
        BufferedImage img = ImageIO.read(in.toFile());
        if (img == null) throw ApiException.conflict("This picture can’t be read.");
        try (PDDocument doc = new PDDocument()) {
            PDRectangle a4 = PDRectangle.A4;
            boolean landscape = img.getWidth() > img.getHeight();
            PDRectangle box = landscape ? new PDRectangle(a4.getHeight(), a4.getWidth()) : a4;
            PDPage page = new PDPage(box);
            doc.addPage(page);
            PDImageXObject x = img.getColorModel().hasAlpha() ? LosslessFactory.createFromImage(doc, img) : JPEGFactory.createFromImage(doc, img, 0.9f);
            float margin = 24;
            float scale = Math.min((box.getWidth() - 2 * margin) / img.getWidth(), (box.getHeight() - 2 * margin) / img.getHeight());
            float w = img.getWidth() * scale, h = img.getHeight() * scale;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(x, (box.getWidth() - w) / 2, (box.getHeight() - h) / 2, w, h);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private byte[] textToPdf(Path in, String title) throws IOException {
        List<String> lines = Files.readAllLines(in, StandardCharsets.UTF_8);
        try (PDDocument doc = new PDDocument()) {
            PDFont font = unicodeFont(doc);
            float size = 10.5f, leading = 14, margin = 56;
            PDRectangle box = PDRectangle.A4;
            float maxWidth = box.getWidth() - 2 * margin;
            List<String> wrapped = new ArrayList<>();
            for (String raw : lines) wrapped.addAll(wrap(sanitize(raw, font), font, size, maxWidth));
            int perPage = (int) ((box.getHeight() - 2 * margin) / leading);
            for (int start = 0; start < Math.max(1, wrapped.size()); start += perPage) {
                PDPage page = new PDPage(box);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, size);
                    cs.setLeading(leading);
                    cs.newLineAtOffset(margin, box.getHeight() - margin);
                    for (int i = start; i < Math.min(wrapped.size(), start + perPage); i++) {
                        cs.showText(wrapped.get(i));
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private PDFont unicodeFont(PDDocument doc) {
        for (String p : new String[]{"C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf"}) {
            File f = new File(p);
            if (f.isFile()) {
                try {
                    return PDType0Font.load(doc, f);
                } catch (IOException ignored) {
                    // try the next font
                }
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String sanitize(String s, PDFont font) {
        String t = s.replace("\t", "    ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                b.append(ch);
            } catch (Exception e) {
                b.append('?');
            }
            i += Character.charCount(cp);
        }
        return b.toString();
    }

    private List<String> wrap(String line, PDFont font, float size, float maxWidth) throws IOException {
        List<String> out = new ArrayList<>();
        if (line.isEmpty()) {
            out.add("");
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : line.split(" ", -1)) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (font.getStringWidth(next) / 1000 * size <= maxWidth) {
                cur = new StringBuilder(next);
            } else {
                if (cur.length() > 0) out.add(cur.toString());
                cur = new StringBuilder(word);
                while (font.getStringWidth(cur.toString()) / 1000 * size > maxWidth && cur.length() > 1) {
                    int cut = Math.max(1, (int) (cur.length() * maxWidth / (font.getStringWidth(cur.toString()) / 1000 * size)));
                    out.add(cur.substring(0, cut));
                    cur = new StringBuilder(cur.substring(cut));
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    private BufferedImage signatureImage(User user, String mode, String base64) throws IOException {
        if ("DRAW".equalsIgnoreCase(mode) && base64 != null && !base64.isBlank()) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            if (img != null) return img;
        }
        BufferedImage img = new BufferedImage(600, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x1F2E8A));
        Font f = new Font("Segoe Script", Font.PLAIN, 64);
        if (!f.getFamily().equals("Segoe Script")) f = new Font(Font.SERIF, Font.ITALIC, 64);
        g.setFont(f);
        g.drawString(user.displayName, 10, 110);
        g.dispose();
        return img;
    }

    private byte[] addVisibleSignature(byte[] pdf, BufferedImage mark, User user, String placement) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDImageXObject x = LosslessFactory.createFromImage(doc, mark);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<Integer> pages = new ArrayList<>();
            int count = doc.getNumberOfPages();
            switch (placement.toLowerCase(Locale.ROOT)) {
                case "first" -> pages.add(0);
                case "every" -> { for (int i = 0; i < count; i++) pages.add(i); }
                default -> pages.add(count - 1);
            }
            String line = sanitizeAscii("Digitally signed by " + user.displayName + " · " + java.time.LocalDate.now());
            for (int i : pages) {
                PDPage page = doc.getPage(i);
                PDRectangle box = page.getMediaBox();
                float w = 170, h = w * mark.getHeight() / Math.max(1, mark.getWidth());
                float px = box.getWidth() - w - 48, py = 64;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.drawImage(x, px, py, w, h);
                    cs.setStrokingColor(new Color(0xC9CDD5));
                    cs.moveTo(px, py - 2);
                    cs.lineTo(px + w, py - 2);
                    cs.stroke();
                    cs.beginText();
                    cs.setFont(font, 7);
                    cs.setNonStrokingColor(new Color(0x7A7E89));
                    cs.newLineAtOffset(px, py - 12);
                    cs.showText(line);
                    cs.endText();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private byte[] stampPdf(byte[] pdf, String label, Color color) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            String text = sanitizeAscii(label);
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                float size = 26;
                float tw = font.getStringWidth(text) / 1000 * size;
                float pad = 10, bw = tw + 2 * pad, bh = size + 2 * pad;
                float cx = box.getWidth() - bw / 2 - 60, cy = box.getHeight() - bh / 2 - 80;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(0.85f);
                    gs.setStrokingAlphaConstant(0.85f);
                    cs.setGraphicsStateParameters(gs);
                    cs.saveGraphicsState();
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(12), cx, cy));
                    cs.setStrokingColor(color);
                    cs.setNonStrokingColor(color);
                    cs.setLineWidth(3);
                    cs.addRect(-bw / 2, -bh / 2, bw, bh);
                    cs.stroke();
                    cs.beginText();
                    cs.setFont(font, size);
                    cs.newLineAtOffset(-tw / 2, -size / 2 + 5);
                    cs.showText(text);
                    cs.endText();
                    cs.restoreGraphicsState();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private void drawStamp(Graphics2D g, String label, Color color, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = Math.max(18, Math.min(w, h) / 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(label), pad = size / 3;
        int bw = tw + 2 * pad, bh = fm.getHeight() + pad;
        int cx = w - bw / 2 - w / 20, cy = bh / 2 + h / 20;
        java.awt.geom.AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(-8), cx, cy);
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, pad, pad);
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(3, size / 8f)));
        g.drawRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, pad, pad);
        g.drawString(label, cx - tw / 2, cy + fm.getAscent() / 2 - 2);
        g.setTransform(old);
    }

    private static String sanitizeAscii(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c < 32 || c > 255 ? (c == '·' ? '-' : '?') : c);
        return b.toString().replace('·', '-');
    }
}
