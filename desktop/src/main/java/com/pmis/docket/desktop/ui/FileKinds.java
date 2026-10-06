package com.pmis.docket.desktop.ui;

import java.util.Locale;
import java.util.Map;

/** File type registry: what each extension is called, which family it belongs to, and its colour. */
public final class FileKinds {
    public enum Kind { PDF, DOC, SHEET, SLIDES, IMAGE, VIDEO, AUDIO, TEXT, CODE, ARCHIVE, APP, CAD, EMAIL, OTHER }

    public record Info(String label, Kind kind, String color) { }

    private static final Map<String, Info> TYPES = Map.ofEntries(
            Map.entry("pdf", new Info("PDF document", Kind.PDF, "#E5484D")),
            Map.entry("docx", new Info("Word document", Kind.DOC, "#3B5BFF")),
            Map.entry("doc", new Info("Word 97–2003 document", Kind.DOC, "#3B5BFF")),
            Map.entry("odt", new Info("OpenDocument text", Kind.DOC, "#3B5BFF")),
            Map.entry("rtf", new Info("Rich text document", Kind.DOC, "#3B5BFF")),
            Map.entry("xlsx", new Info("Excel workbook", Kind.SHEET, "#1F8A4C")),
            Map.entry("xls", new Info("Excel 97–2003 workbook", Kind.SHEET, "#1F8A4C")),
            Map.entry("csv", new Info("CSV data", Kind.SHEET, "#1F8A4C")),
            Map.entry("pptx", new Info("PowerPoint deck", Kind.SLIDES, "#D9480F")),
            Map.entry("ppt", new Info("PowerPoint 97–2003 deck", Kind.SLIDES, "#D9480F")),
            Map.entry("jpg", new Info("JPEG image", Kind.IMAGE, "#B45309")),
            Map.entry("jpeg", new Info("JPEG image", Kind.IMAGE, "#B45309")),
            Map.entry("png", new Info("PNG image", Kind.IMAGE, "#B45309")),
            Map.entry("gif", new Info("GIF image", Kind.IMAGE, "#B45309")),
            Map.entry("webp", new Info("WebP image", Kind.IMAGE, "#B45309")),
            Map.entry("heic", new Info("HEIC photo", Kind.IMAGE, "#B45309")),
            Map.entry("svg", new Info("SVG image", Kind.IMAGE, "#B45309")),
            Map.entry("bmp", new Info("Bitmap image", Kind.IMAGE, "#B45309")),
            Map.entry("mp4", new Info("MP4 video", Kind.VIDEO, "#7C3AED")),
            Map.entry("mov", new Info("QuickTime video", Kind.VIDEO, "#7C3AED")),
            Map.entry("avi", new Info("AVI video", Kind.VIDEO, "#7C3AED")),
            Map.entry("mkv", new Info("MKV video", Kind.VIDEO, "#7C3AED")),
            Map.entry("mp3", new Info("MP3 audio", Kind.AUDIO, "#DB2777")),
            Map.entry("m4a", new Info("Voice memo", Kind.AUDIO, "#DB2777")),
            Map.entry("wav", new Info("WAV audio", Kind.AUDIO, "#DB2777")),
            Map.entry("txt", new Info("Text note", Kind.TEXT, "#475569")),
            Map.entry("md", new Info("Markdown note", Kind.TEXT, "#475569")),
            Map.entry("json", new Info("JSON file", Kind.CODE, "#0E7490")),
            Map.entry("xml", new Info("XML file", Kind.CODE, "#0E7490")),
            Map.entry("log", new Info("Log file", Kind.CODE, "#0E7490")),
            Map.entry("zip", new Info("ZIP archive", Kind.ARCHIVE, "#8A6D1F")),
            Map.entry("rar", new Info("RAR archive", Kind.ARCHIVE, "#8A6D1F")),
            Map.entry("7z", new Info("7-Zip archive", Kind.ARCHIVE, "#8A6D1F")),
            Map.entry("exe", new Info("Application", Kind.APP, "#334155")),
            Map.entry("msi", new Info("Windows installer", Kind.APP, "#334155")),
            Map.entry("iso", new Info("Disc image", Kind.APP, "#334155")),
            Map.entry("dwg", new Info("AutoCAD drawing", Kind.CAD, "#0F766E")),
            Map.entry("eml", new Info("Email message", Kind.EMAIL, "#2563EB")),
            Map.entry("msg", new Info("Outlook message", Kind.EMAIL, "#2563EB"))
    );

    public static final String[] GROUPS = {"All", "Documents", "Images", "Videos", "Audio", "Text & code", "Archives", "Apps & other"};
    private static final java.util.Set<String> STAMPABLE_IMAGES = java.util.Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");

    private FileKinds() { }

    public static String group(String ext) {
        return switch (of(ext).kind()) {
            case PDF, DOC, SHEET, SLIDES, EMAIL, CAD -> "Documents";
            case IMAGE -> "Images";
            case VIDEO -> "Videos";
            case AUDIO -> "Audio";
            case TEXT, CODE -> "Text & code";
            case ARCHIVE -> "Archives";
            default -> "Apps & other";
        };
    }

    /** PDF and Office documents can be signed (Office ones become a signed PDF copy). */
    public static boolean canSign(String ext) {
        Kind k = of(ext).kind();
        return k == Kind.PDF || k == Kind.DOC || k == Kind.SHEET || k == Kind.SLIDES;
    }

    /** PDF, Word, PowerPoint and common pictures can be stamped. */
    public static boolean canStamp(String ext) {
        Kind k = of(ext).kind();
        if (k == Kind.IMAGE) return ext != null && STAMPABLE_IMAGES.contains(ext.toLowerCase(Locale.ROOT));
        return k == Kind.PDF || k == Kind.DOC || k == Kind.SLIDES;
    }

    /** Lock keeps the type for PDF and modern Office files; other files become an encrypted ZIP. */
    public static boolean locksInPlace(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return e.equals("pdf") || e.equals("docx") || e.equals("xlsx") || e.equals("pptx");
    }

    public static String plural(String ext) {
        String l = of(ext).label();
        return l.replaceAll(" (file|document|message)$", "") + " files";
    }

    public static Info of(String ext) {
        if (ext == null || ext.isBlank()) return new Info("File", Kind.OTHER, "#5A5F6E");
        Info i = TYPES.get(ext.toLowerCase(Locale.ROOT));
        return i != null ? i : new Info(ext.toUpperCase(Locale.ROOT) + " file", Kind.OTHER, "#5A5F6E");
    }
}
