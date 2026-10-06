package com.pmis.docket.desktop.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Line icons drawn on a 24×24 grid (same set as the design).
 * Colour comes from the CSS looked-up colour "icon-color" of the nearest parent.
 */
public final class Icons {
    public static final String BACK = "M15 5 L8 12 L15 19";
    public static final String FORWARD = "M9 5 L16 12 L9 19";
    public static final String UP = "M12 19 V5 M6 11 L12 5 L18 11";
    public static final String REFRESH = "M20 11 A8 8 0 1 0 18 17 M20 5 V11 H14";
    public static final String SEARCH = circle(11, 11, 7) + " M20 20 L16.5 16.5";
    public static final String PLUS = "M12 5 V19 M5 12 H19";
    public static final String UPLOAD = "M12 16 V4 M7 9 L12 4 L17 9 M4 20 H20";
    public static final String DOWNLOAD = "M12 4 V16 M7 11 L12 16 L17 11 M4 20 H20";
    public static final String PENCIL = "M4 20 H8 L19 9 L15 5 L4 16 Z";
    public static final String TRASH = "M4 7 H20 M9 7 V4 H15 V7 M6 7 L7 20 H17 L18 7";
    public static final String INFO = circle(12, 12, 9) + " M12 11 V17 M12 7.5 V7.6";
    public static final String GRID = rect(4, 4, 7, 7, 1.5) + rect(13, 4, 7, 7, 1.5) + rect(4, 13, 7, 7, 1.5) + rect(13, 13, 7, 7, 1.5);
    public static final String LIST = "M8 6 H20 M8 12 H20 M8 18 H20 M4 6 V6.1 M4 12 V12.1 M4 18 V18.1";
    public static final String SORT = "M7 4 V20 M4 17 L7 20 L10 17 M17 20 V4 M14 7 L17 4 L20 7";
    public static final String FOLDER = "M3 6 H10 L12 8 H21 V19 H3 Z";
    public static final String USER = circle(12, 8, 4) + " M4 21 C5 17 8 15 12 15 S19 17 20 21";
    public static final String SERVER = rect(4, 4, 16, 6, 1.5) + rect(4, 14, 16, 6, 1.5) + " M8 7 V7.1 M8 17 V17.1";
    public static final String LOCK = rect(5, 11, 14, 10, 2) + " M8 11 V7 A4 4 0 0 1 16 7 V11";
    public static final String EYE = "M2 12 C2 12 6 5 12 5 C18 5 22 12 22 12 C22 12 18 19 12 19 C6 19 2 12 2 12 Z" + circle(12, 12, 3);
    public static final String CHEVRON = "M9 6 L15 12 L9 18";
    public static final String CLOSE = "M6 6 L18 18 M18 6 L6 18";
    public static final String CHECK = "M5 12.5 L10 17 L19 7";
    public static final String SIGN_OUT = "M15 4 H19 V20 H15 M10 8 L6 12 L10 16 M6 12 H16";
    public static final String OPEN = "M14 4 H20 V10 M20 4 L11 13 M18 14 V20 H4 V6 H10";
    public static final String HOME = "M3 11 L12 4 L21 11 M5 10 V20 H19 V10";
    public static final String PEOPLE = circle(9, 8, 3.5) + " M2.5 20 C3.3 16.4 5.9 14.5 9 14.5 S14.7 16.4 15.5 20 M16 4.5 A3.5 3.5 0 0 1 16 11.5 M18 14.6 C20 15.3 21.2 17.1 21.6 20";
    public static final String HISTORY = "M3 12 A9 9 0 1 0 6 5.3 M3 4 V9 H8 M12 8 V12 L15 14";
    public static final String SHIELD = "M12 3 L20 6 V12 C20 16.5 16.5 20 12 21 C7.5 20 4 16.5 4 12 V6 Z M9 12 L11 14 L15 10";
    public static final String KEY = circle(8, 15, 4) + " M11 12 L20 3 M16 7 L19 10";
    public static final String INBOX = "M3 13 L6 5 H18 L21 13 V19 H3 Z M3 13 H8 L9 16 H15 L16 13 H21";
    public static final String LOG = "M6 3 H15 L19 7 V21 H6 Z M9 11 H16 M9 15 H16 M9 7 H12";
    public static final String DISK = "M4 6 C4 4.3 7.6 3 12 3 S20 4.3 20 6 S16.4 9 12 9 S4 7.7 4 6 Z M4 6 V18 C4 19.7 7.6 21 12 21 S20 19.7 20 18 V6 M4 12 C4 13.7 7.6 15 12 15 S20 13.7 20 12";
    public static final String CONVERT = "M4 8 H17 L14 5 M20 16 H7 L10 19";
    public static final String SIGN = "M3 17 C6 17 7 9 10 9 S11 17 14 17 17 13 21 13 M3 21 H21";
    public static final String STAMP = "M9 3 H15 L14 11 H19 V15 H5 V11 H10 Z M5 20 H19";
    public static final String CUT = circle(6, 18, 3) + circle(18, 18, 3) + " M8 16 L18 4 M16 16 L6 4";
    public static final String COPY = rect(8, 8, 12, 12, 2) + " M16 8 V6 A2 2 0 0 0 14 4 H6 A2 2 0 0 0 4 6 V14 A2 2 0 0 0 6 16 H8";
    public static final String PASTE = rect(6, 4, 12, 17, 2) + " M9 4 H15 V7 H9 Z";
    public static final String PANEL = rect(3, 4, 18, 16, 2) + " M15 4 V20";
    public static final String FILTER = "M4 5 H20 L14 12 V19 L10 17 V12 Z";
    public static final String MINUS = "M5 12 H19";
    public static final String PLAY = "M7 4 L19 12 L7 20 Z";
    public static final String PAUSE = "M8 5 V19 M16 5 V19";
    public static final String RESTORE = "M3 12 A9 9 0 1 0 6 5.3 M3 4 V9 H8";
    public static final String EDIT_MARK = "M12 20 H20 M16.5 3.5 A2.1 2.1 0 0 1 19.5 6.5 L7 19 L3 20 L4 16 Z";
    public static final String WARN = "M12 3 L22 20 H2 Z M12 10 V14 M12 17 V17.1";

    private Icons() { }

    /** An icon of the given size in pixels. */
    public static Node of(String path, double size) {
        return of(path, size, 2);
    }

    public static Node of(String path, double size, double strokeWidth) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(Color.TRANSPARENT);
        p.setStrokeWidth(strokeWidth);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        p.getStyleClass().add("icon");
        Rectangle box = new Rectangle(24, 24, Color.TRANSPARENT);
        Group inner = new Group(box, p);
        double s = size / 24.0;
        inner.setScaleX(s);
        inner.setScaleY(s);
        Group outer = new Group(inner);
        outer.setMouseTransparent(true);
        return outer;
    }

    static String circle(double cx, double cy, double r) {
        return " M" + (cx - r) + " " + cy + " A" + r + " " + r + " 0 1 0 " + (cx + r) + " " + cy
                + " A" + r + " " + r + " 0 1 0 " + (cx - r) + " " + cy + " ";
    }

    static String rect(double x, double y, double w, double h, double r) {
        return " M" + (x + r) + " " + y + " H" + (x + w - r) + " A" + r + " " + r + " 0 0 1 " + (x + w) + " " + (y + r)
                + " V" + (y + h - r) + " A" + r + " " + r + " 0 0 1 " + (x + w - r) + " " + (y + h)
                + " H" + (x + r) + " A" + r + " " + r + " 0 0 1 " + x + " " + (y + h - r)
                + " V" + (y + r) + " A" + r + " " + r + " 0 0 1 " + (x + r) + " " + y + " Z ";
    }
}
