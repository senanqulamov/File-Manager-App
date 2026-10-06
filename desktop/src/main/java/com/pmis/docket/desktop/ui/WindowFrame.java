package com.pmis.docket.desktop.ui;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * The custom window: rounded corners, our own title bar with minimise / maximise / close,
 * dragging, double-click to maximise, and resizing from the edges.
 * Also hosts the overlay layer used for dialogs and toasts.
 */
public class WindowFrame {
    private static final double EDGE = 6;
    private static final double RADIUS = 10;

    private final Stage stage;
    private final StackPane shell = new StackPane();
    private final BorderPane window = new BorderPane();
    private final StackPane contentHolder = new StackPane();
    private final StackPane overlay = new StackPane();
    private final HBox titleCenter = new HBox();
    private final HBox titleRight = new HBox(8);
    private final Rectangle clip = new Rectangle();

    private boolean maximized;
    private Rectangle2D restoreBounds;
    private double dragDX, dragDY;

    private Cursor resizeCursor = Cursor.DEFAULT;
    private double startX, startY, startW, startH, startSX, startSY;
    private boolean resizing;

    public WindowFrame(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);

        window.getStyleClass().add("window");
        window.setTop(buildTitleBar());
        window.setCenter(contentHolder);
        contentHolder.getStyleClass().add("content-holder");

        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        clip.widthProperty().bind(window.widthProperty());
        clip.heightProperty().bind(window.heightProperty());
        window.setClip(clip);

        overlay.setPickOnBounds(false);
        overlay.getStyleClass().add("overlay-layer");

        shell.getChildren().addAll(window, overlay);
        shell.setStyle("-fx-background-color: transparent;");
    }

    public Scene createScene(double width, double height) {
        Scene scene = new Scene(shell, width, height, Color.TRANSPARENT);
        installResize(scene);
        return scene;
    }

    public Stage stage() { return stage; }

    public StackPane overlay() { return overlay; }

    public void setContent(Node node) {
        Node old = contentHolder.getChildren().isEmpty() ? null : contentHolder.getChildren().get(0);
        if (old != null) {
            old.setMouseTransparent(true);
            Anim.fadeOut(old, 150, () -> contentHolder.getChildren().remove(old));
        }
        contentHolder.getChildren().add(node);
        Anim.fadeUp(node, old == null ? 0 : 80);
    }

    public void setTitleCenter(Node node) {
        titleCenter.getChildren().setAll(node == null ? new Node[0] : new Node[]{node});
        if (node != null) Anim.fadeIn(node, 250);
    }

    public void setTitleRight(Node node) {
        titleRight.getChildren().setAll(node == null ? new Node[0] : new Node[]{node});
        if (node != null) Anim.fadeIn(node, 250);
    }

    // ------------------------------------------------------------------ title bar

    private Node buildTitleBar() {
        Label logo = new Label("D");
        logo.getStyleClass().add("logo-mark");
        Label name = new Label("Docket");
        name.getStyleClass().add("app-name");
        HBox left = new HBox(10, logo, name);
        left.setAlignment(Pos.CENTER_LEFT);

        titleCenter.setAlignment(Pos.CENTER);
        titleRight.setAlignment(Pos.CENTER_RIGHT);

        Button min = windowButton("M0 5 H10", "Minimize");
        min.setOnAction(e -> stage.setIconified(true));
        Button max = windowButton("M0.5 1.5 A1 1 0 0 1 1.5 0.5 H8.5 A1 1 0 0 1 9.5 1.5 V8.5 A1 1 0 0 1 8.5 9.5 H1.5 A1 1 0 0 1 0.5 8.5 Z", "Maximize");
        max.setOnAction(e -> toggleMaximize());
        Button close = windowButton("M0 0 L10 10 M10 0 L0 10", "Close");
        close.getStyleClass().add("win-close");
        close.setOnAction(e -> stage.close());
        HBox winButtons = new HBox(min, max, close);

        Region l = new Region();
        Region r = new Region();
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox.setHgrow(r, Priority.ALWAYS);
        HBox leftWrap = new HBox(left, l);
        leftWrap.setAlignment(Pos.CENTER_LEFT);
        HBox rightWrap = new HBox(r, titleRight, winButtons);
        rightWrap.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(leftWrap, Priority.ALWAYS);
        HBox.setHgrow(rightWrap, Priority.ALWAYS);
        leftWrap.setMinWidth(0);
        rightWrap.setMinWidth(0);
        leftWrap.setPrefWidth(0);
        rightWrap.setPrefWidth(0);

        HBox bar = new HBox(12, leftWrap, titleCenter, rightWrap);
        bar.getStyleClass().add("titlebar");
        bar.setAlignment(Pos.CENTER_LEFT);

        bar.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY || onButton(e)) return;
            dragDX = e.getScreenX() - stage.getX();
            dragDY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || onButton(e) || resizing) return;
            if (maximized) {
                double ratio = dragDX / stage.getWidth();
                toggleMaximize();
                dragDX = stage.getWidth() * ratio;
            }
            stage.setX(e.getScreenX() - dragDX);
            stage.setY(Math.max(e.getScreenY() - dragDY, screenOf().getVisualBounds().getMinY()));
        });
        bar.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !onButton(e)) toggleMaximize();
        });
        return bar;
    }

    private Button windowButton(String path, String label) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(Color.TRANSPARENT);
        p.setStrokeWidth(1);
        p.getStyleClass().add("icon");
        Button b = new Button();
        b.setGraphic(p);
        b.getStyleClass().add("win-btn");
        b.setAccessibleText(label);
        b.setFocusTraversable(false);
        return b;
    }

    private boolean onButton(MouseEvent e) {
        Node n = e.getPickResult().getIntersectedNode();
        while (n != null) {
            if (n instanceof Button || n instanceof javafx.scene.control.TextField) return true;
            n = n.getParent();
        }
        return false;
    }

    public void toggleMaximize() {
        if (maximized) {
            if (restoreBounds != null) {
                stage.setX(restoreBounds.getMinX());
                stage.setY(restoreBounds.getMinY());
                stage.setWidth(restoreBounds.getWidth());
                stage.setHeight(restoreBounds.getHeight());
            }
            maximized = false;
            window.getStyleClass().remove("maximized");
            clip.setArcWidth(RADIUS * 2);
            clip.setArcHeight(RADIUS * 2);
        } else {
            restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Rectangle2D vb = screenOf().getVisualBounds();
            stage.setX(vb.getMinX());
            stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth());
            stage.setHeight(vb.getHeight());
            maximized = true;
            window.getStyleClass().add("maximized");
            clip.setArcWidth(0);
            clip.setArcHeight(0);
        }
    }

    private Screen screenOf() {
        return Screen.getScreensForRectangle(stage.getX(), stage.getY(), Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream().findFirst().orElse(Screen.getPrimary());
    }

    // ------------------------------------------------------------------ resizing from the edges

    private void installResize(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (maximized) {
                setCursor(scene, Cursor.DEFAULT);
                return;
            }
            setCursor(scene, cursorFor(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight()));
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, e -> {
            if (!resizing) setCursor(scene, Cursor.DEFAULT);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (maximized || resizeCursor == Cursor.DEFAULT || e.getButton() != MouseButton.PRIMARY) return;
            resizing = true;
            startX = stage.getX();
            startY = stage.getY();
            startW = stage.getWidth();
            startH = stage.getHeight();
            startSX = e.getScreenX();
            startSY = e.getScreenY();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing) return;
            double dx = e.getScreenX() - startSX, dy = e.getScreenY() - startSY;
            double minW = stage.getMinWidth(), minH = stage.getMinHeight();
            Cursor c = resizeCursor;
            if (c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE) stage.setWidth(Math.max(minW, startW + dx));
            if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE) stage.setHeight(Math.max(minH, startH + dy));
            if (c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) {
                double w = Math.max(minW, startW - dx);
                stage.setX(startX + (startW - w));
                stage.setWidth(w);
            }
            if (c == Cursor.N_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.NE_RESIZE) {
                double h = Math.max(minH, startH - dy);
                stage.setY(startY + (startH - h));
                stage.setHeight(h);
            }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizing) {
                resizing = false;
                e.consume();
            }
        });
    }

    private Cursor cursorFor(double x, double y, double w, double h) {
        boolean l = x < EDGE, r = x > w - EDGE, t = y < EDGE, b = y > h - EDGE;
        if (t && l) return Cursor.NW_RESIZE;
        if (t && r) return Cursor.NE_RESIZE;
        if (b && l) return Cursor.SW_RESIZE;
        if (b && r) return Cursor.SE_RESIZE;
        if (l) return Cursor.W_RESIZE;
        if (r) return Cursor.E_RESIZE;
        if (t) return Cursor.N_RESIZE;
        if (b) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }

    private void setCursor(Scene scene, Cursor c) {
        resizeCursor = c;
        scene.setCursor(c);
    }

    public Parent root() { return shell; }
}
