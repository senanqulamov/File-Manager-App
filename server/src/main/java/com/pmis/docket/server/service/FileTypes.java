package com.pmis.docket.server.service;

import java.util.Locale;
import java.util.Set;

/** File families, the same as in the desktop app. */
public final class FileTypes {
    public static final Set<String> PDF = Set.of("pdf");
    public static final Set<String> DOC = Set.of("docx", "doc", "odt", "rtf");
    public static final Set<String> SHEET = Set.of("xlsx", "xls", "ods", "csv");
    public static final Set<String> SLIDES = Set.of("pptx", "ppt", "odp");
    public static final Set<String> IMAGE = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "svg");
    public static final Set<String> IMAGE_READABLE = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    public static final Set<String> VIDEO = Set.of("mp4", "mov", "avi", "mkv", "webm", "wmv");
    public static final Set<String> AUDIO = Set.of("mp3", "m4a", "wav", "aac", "ogg", "wma");
    public static final Set<String> TEXT = Set.of("txt", "md", "log");
    public static final Set<String> CODE = Set.of("json", "xml", "yml", "yaml", "html", "css", "js", "sql", "java", "py", "ini", "cfg");
    public static final Set<String> ARCHIVE = Set.of("zip", "7z", "tar", "gz", "tgz", "rar");
    public static final Set<String> OOXML = Set.of("docx", "xlsx", "pptx");

    private FileTypes() { }

    public static String ext(String e) { return e == null ? "" : e.toLowerCase(Locale.ROOT); }

    public static boolean office(String e) { return DOC.contains(ext(e)) || SHEET.contains(ext(e)) || SLIDES.contains(ext(e)); }
}
