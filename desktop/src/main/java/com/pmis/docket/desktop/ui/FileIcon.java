package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.api.Model.NodeInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.Locale;

/** Large (tile) and small (list) icons for folders and every file type. */
public final class FileIcon {
    static final String FOLDER_BACK = "M2 8 A6 6 0 0 1 8 2 H22 L28 8 H56 A6 6 0 0 1 62 14 V44 A6 6 0 0 1 56 50 H8 A6 6 0 0 1 2 44 Z";
    static final String FOLDER_FRONT = "M2 18 A6 6 0 0 1 8 12 H56 A6 6 0 0 1 62 18 V44 A6 6 0 0 1 56 50 H8 A6 6 0 0 1 2 44 Z";

    private FileIcon() { }

    public static Node big(NodeInfo n) {
        StackPane box = new StackPane();
        box.setMinSize(74, 64);
        box.setPrefSize(74, 64);
        box.setMaxSize(74, 64);
        if (!n.isFile()) {
            box.getChildren().add(folder(66, n.noAccess()));
        } else {
            FileKinds.Info info = FileKinds.of(n.ext());
            box.getChildren().add(fileTile(info, n.ext()));
        }
        HBox badges = badges(n, 20);
        StackPane.setAlignment(badges, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(badges, new Insets(0, -4, -4, 0));
        box.getChildren().add(badges);
        return box;
    }

    public static Node small(NodeInfo n) {
        StackPane box = new StackPane();
        box.setMinSize(26, 24);
        box.setPrefSize(26, 24);
        if (!n.isFile()) {
            box.getChildren().add(folder(24, n.noAccess()));
        } else {
            FileKinds.Info info = FileKinds.of(n.ext());
            Label ext = new Label(shortExt(n.ext()));
            ext.getStyleClass().add("ext-mini");
            ext.setStyle("-fx-background-color: " + info.color() + ";");
            box.getChildren().add(ext);
        }
        HBox badges = badges(n, 14);
        StackPane.setAlignment(badges, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(badges, new Insets(0, -6, -4, 0));
        box.getChildren().add(badges);
        return box;
    }

    public static Node folder(double width, boolean noAccess) {
        SVGPath back = new SVGPath();
        back.setContent(FOLDER_BACK);
        back.setFill(Color.web(noAccess ? "#B9BECA" : "#6F86FF"));
        SVGPath front = new SVGPath();
        front.setContent(FOLDER_FRONT);
        front.setFill(Color.web(noAccess ? "#D6DAE2" : "#9DB0FF"));
        Group inner = new Group(back, front);
        double s = width / 64.0;
        inner.setScaleX(s);
        inner.setScaleY(s);
        return new Group(inner);
    }

    private static Node fileTile(FileKinds.Info info, String ext) {
        return switch (info.kind()) {
            case IMAGE -> picture(68, 52, "#F6B26B", "#8E7CC3", false);
            case VIDEO -> picture(70, 46, "#6FA8DC", "#3D85C6", true);
            case AUDIO -> audio();
            case ARCHIVE -> archive(ext);
            case APP -> app(ext);
            case SLIDES -> slides(ext, info.color());
            case CODE -> page(ext, info.color(), "#0F172A", "#38BDF8", true);
            case TEXT -> page(ext, info.color(), "#FFFDF3", "#D9D3BF", false);
            case SHEET -> sheet(ext, info.color());
            case OTHER -> other(ext, info.color());
            default -> page(ext, info.color(), "#FFFFFF", "#E1E4EB", false);
        };
    }

    private static VBox paper(String bg, String border) {
        VBox p = new VBox(3);
        p.setPadding(new Insets(6));
        p.setMinSize(48, 60);
        p.setPrefSize(48, 60);
        p.setMaxSize(48, 60);
        p.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 6; -fx-border-color: " + border
                + "; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(26,28,36,0.10), 10, 0, 0, 4);");
        return p;
    }

    private static Region line(double widthPct, String color) {
        Region r = new Region();
        r.setMinHeight(3);
        r.setPrefHeight(3);
        r.setMaxHeight(3);
        r.setMaxWidth(36 * widthPct);
        r.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
        return r;
    }

    private static Label extBadge(String ext, String color) {
        Label l = new Label(shortExt(ext));
        l.getStyleClass().add("ext-badge");
        l.setStyle("-fx-background-color: " + color + ";");
        return l;
    }

    private static Node page(String ext, String color, String bg, String lineColor, boolean code) {
        VBox p = paper(bg, code ? bg : "#E1E4EB");
        if (code) {
            p.getChildren().addAll(line(0.4, "#38BDF8"), line(0.7, "#F472B6"), line(0.55, "#FBBF24"), line(0.3, "#38BDF8"));
        } else {
            p.getChildren().addAll(line(0.7, lineColor), line(0.9, lineColor), line(0.8, lineColor), line(0.85, lineColor));
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        p.getChildren().addAll(spacer, extBadge(ext, color));
        return p;
    }

    private static Node sheet(String ext, String color) {
        VBox p = paper("#FFFFFF", "#E1E4EB");
        Region head = new Region();
        head.setMinHeight(4);
        head.setStyle("-fx-background-color: #1F8A4C; -fx-background-radius: 1;");
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                Region cell = new Region();
                cell.setMinSize(10, 3);
                cell.setStyle("-fx-background-color: #E1E4EB;");
                grid.add(cell, c, r);
            }
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        p.getChildren().addAll(head, grid, spacer, extBadge(ext, color));
        return p;
    }

    private static Node slides(String ext, String color) {
        VBox p = new VBox(3);
        p.setPadding(new Insets(6));
        p.setMinSize(66, 46);
        p.setMaxSize(66, 46);
        p.setStyle("-fx-background-color: white; -fx-background-radius: 5; -fx-border-color: #E1E4EB; -fx-border-radius: 5;"
                + " -fx-effect: dropshadow(gaussian, rgba(26,28,36,0.10), 10, 0, 0, 4);");
        Region bar = line(0.5, "#D9480F");
        bar.setMinHeight(4);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        p.getChildren().addAll(bar, line(0.8, "#E1E4EB"), spacer, extBadge(ext, color));
        return p;
    }

    private static Node picture(double w, double h, String c1, String c2, boolean video) {
        StackPane p = new StackPane();
        p.setMinSize(w, h);
        p.setMaxSize(w, h);
        Rectangle bg = new Rectangle(w, h);
        bg.setArcWidth(16);
        bg.setArcHeight(16);
        bg.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(c1)), new Stop(1, Color.web(c2))));
        SVGPath hills = new SVGPath();
        hills.setContent("M0 " + h + " L" + (w * 0.3) + " " + (h * 0.5) + " L" + (w * 0.47) + " " + (h * 0.73)
                + " L" + (w * 0.68) + " " + (h * 0.42) + " L" + w + " " + h + " Z");
        hills.setFill(Color.rgb(255, 255, 255, 0.55));
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        Pane art = new Pane(hills);
        art.setClip(clip);
        p.getChildren().addAll(bg, art);
        if (video) {
            Rectangle shade = new Rectangle(w, h, Color.rgb(15, 17, 24, 0.45));
            shade.setArcWidth(16);
            shade.setArcHeight(16);
            Circle play = new Circle(11, Color.rgb(255, 255, 255, 0.92));
            SVGPath tri = new SVGPath();
            tri.setContent("M0 0 L8 5 L0 10 Z");
            tri.setFill(Color.web("#1A1C24"));
            tri.setTranslateX(1);
            p.getChildren().addAll(shade, play, tri);
        } else {
            Circle sun = new Circle(6, Color.rgb(255, 255, 255, 0.85));
            StackPane.setAlignment(sun, Pos.TOP_RIGHT);
            StackPane.setMargin(sun, new Insets(8, 12, 0, 0));
            p.getChildren().add(sun);
        }
        return p;
    }

    private static Node audio() {
        HBox bars = new HBox(2);
        bars.setAlignment(Pos.CENTER);
        for (int h : new int[]{6, 12, 20, 10, 16, 22, 8, 14}) {
            Region b = new Region();
            b.setMinSize(3, h);
            b.setMaxSize(3, h);
            b.setStyle("-fx-background-color: #DB2777; -fx-background-radius: 2;");
            bars.getChildren().add(b);
        }
        StackPane p = new StackPane(bars);
        p.setMinSize(56, 56);
        p.setMaxSize(56, 56);
        p.setStyle("-fx-background-color: #FDE7F1; -fx-background-radius: 16;");
        return p;
    }

    private static Node archive(String ext) {
        StackPane p = new StackPane();
        p.setMinSize(50, 58);
        p.setMaxSize(50, 58);
        p.setStyle("-fx-background-color: #E9D9A8; -fx-background-radius: 6; -fx-effect: dropshadow(gaussian, rgba(26,28,36,0.10), 10, 0, 0, 4);");
        VBox zip = new VBox(3);
        zip.setAlignment(Pos.TOP_CENTER);
        zip.setPadding(new Insets(4, 0, 0, 0));
        for (int i = 0; i < 5; i++) {
            Region z = new Region();
            z.setMinSize(8, 3);
            z.setMaxSize(8, 3);
            z.setStyle("-fx-background-color: #8A6D1F;");
            zip.getChildren().add(z);
        }
        Label l = extBadge(ext, "#8A6D1F");
        StackPane.setAlignment(l, Pos.BOTTOM_LEFT);
        StackPane.setMargin(l, new Insets(0, 0, 5, 5));
        p.getChildren().addAll(zip, l);
        return p;
    }

    private static Node app(String ext) {
        StackPane p = new StackPane();
        p.setMinSize(54, 54);
        p.setMaxSize(54, 54);
        p.setStyle("-fx-background-color: #334155; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(26,28,36,0.18), 10, 0, 0, 4);");
        SVGPath win = new SVGPath();
        win.setContent(Icons.rect(2, 2, 24, 20, 3) + " M2 8 H26");
        win.setFill(Color.TRANSPARENT);
        win.setStroke(Color.WHITE);
        win.setStrokeWidth(2);
        Label l = extBadge(ext, "#1A1C24");
        StackPane.setAlignment(l, Pos.BOTTOM_LEFT);
        StackPane.setMargin(l, new Insets(0, 0, -4, -4));
        p.getChildren().addAll(win, l);
        return p;
    }

    private static Node other(String ext, String color) {
        VBox p = paper("#FFFFFF", "#E1E4EB");
        p.setAlignment(Pos.CENTER);
        Label l = new Label("." + (ext == null ? "" : ext.toLowerCase(Locale.ROOT)));
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + color + ";");
        p.getChildren().add(l);
        return p;
    }

    private static HBox badges(NodeInfo n, double size) {
        HBox b = new HBox(2);
        b.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        b.setMouseTransparent(true);
        if (n.noAccess()) b.getChildren().add(badge(Icons.LOCK, "#B42318", size));
        else if (!n.isFile() && n.readOnly() && !n.isPersonal()) b.getChildren().add(badge(Icons.EYE, "#5A5F6E", size));
        if (n.locked()) b.getChildren().add(badge(Icons.LOCK, "#1A1C24", size));
        if (n.signed()) b.getChildren().add(badge(Icons.SIGN, "#1F8A4C", size));
        if (n.checkedOutByName() != null) b.getChildren().add(badge(Icons.PENCIL, n.checkedOutByMe() ? "#3B5BFF" : "#C2410C", size));
        if (n.stamp() != null) b.getChildren().add(badge(Icons.STAMP, "#B45309", size));
        if (n.sharedCount() > 0) b.getChildren().add(badge(Icons.PEOPLE, "#6D28D9", size));
        return b;
    }

    private static Node badge(String path, String color, double size) {
        StackPane s = new StackPane(Icons.of(path, size - 8, 2.5));
        s.setMinSize(size, size);
        s.setMaxSize(size, size);
        s.setStyle("-fx-background-color: " + color + "; -fx-background-radius: " + size
                + "; -fx-border-color: white; -fx-border-width: 2; -fx-border-radius: " + size + "; icon-color: white;");
        return s;
    }

    private static String shortExt(String ext) {
        if (ext == null) return "";
        String e = ext.toUpperCase(Locale.ROOT);
        return e.length() > 4 ? e.substring(0, 4) : e;
    }
}
