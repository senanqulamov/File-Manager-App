package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.api.Model;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * In-window modal dialogs: a small window with its own title strip, content and a footer
 * with buttons, over a dimmed background. Esc cancels, Enter confirms.
 */
public final class Dialogs {
    private Dialogs() { }

    public static final class Handle {
        private final StackPane layer;
        private final StackPane dim;
        private Button primary;
        private String primaryText;

        Handle(StackPane layer, StackPane dim) {
            this.layer = layer;
            this.dim = dim;
        }

        /** Disables the main button while the server works (e.g. "Converting…"). */
        public void busy(String text) {
            if (primary == null) return;
            primary.setDisable(true);
            primary.setText(text);
        }

        public void idle() {
            if (primary == null) return;
            primary.setDisable(false);
            primary.setText(primaryText);
        }

        public void close() {
            Node card = dim.getChildren().get(0);
            FadeTransition f = new FadeTransition(Duration.millis(140), dim);
            f.setToValue(0);
            ScaleTransition s = new ScaleTransition(Duration.millis(140), card);
            s.setToX(0.97);
            s.setToY(0.97);
            ParallelTransition p = new ParallelTransition(f, s);
            p.setOnFinished(e -> layer.getChildren().remove(dim));
            p.play();
        }
    }

    /**
     * Shows a dialog. {@code onConfirm} returns true to close the dialog, false to keep it open
     * (e.g. while saving, or to show a validation message).
     */
    public static Handle show(WindowFrame frame, String title, String subtitle, Node body, String cta,
                              boolean danger, boolean showCancel, double width, java.util.function.Predicate<Handle> onConfirm) {
        StackPane layer = frame.overlay();

        Label strip = new Label("PMIS Docket");
        strip.getStyleClass().add("dialog-strip-text");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Button x = new Button();
        x.setGraphic(Icons.of(Icons.CLOSE, 12, 1.5));
        x.getStyleClass().addAll("win-btn", "win-close");
        x.setAccessibleText("Close");
        HBox top = new HBox(strip, grow, x);
        top.getStyleClass().add("dialog-strip");
        top.setAlignment(Pos.CENTER_LEFT);

        Label h = new Label(title);
        h.getStyleClass().add("dialog-title");
        VBox head = new VBox(3, h);
        if (subtitle != null && !subtitle.isBlank()) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("dialog-subtitle");
            head.getChildren().add(s);
        }
        head.getStyleClass().add("dialog-head");
        VBox content = new VBox(14);
        if (body != null) content.getChildren().add(body);
        content.getStyleClass().add("dialog-body");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button primary = new Button(cta);
        primary.getStyleClass().addAll("btn", danger ? "btn-danger" : "btn-primary");
        primary.setDefaultButton(true);
        HBox footer = new HBox(8);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-outline");
        cancel.setCancelButton(true);
        if (showCancel) footer.getChildren().add(cancel);
        footer.getChildren().add(primary);

        // Title stays in place; only the body scrolls when the window is small.
        VBox card = body != null ? new VBox(top, head, scroll, footer) : new VBox(top, head, footer);
        card.getStyleClass().add("dialog-card");
        card.setMaxWidth(width);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane dim = new StackPane(card);
        dim.getStyleClass().add("dim");
        dim.setPadding(new Insets(24));
        layer.getChildren().add(dim);

        Handle handle = new Handle(layer, dim);
        handle.primary = primary;
        handle.primaryText = cta;
        Runnable cancelAction = () -> {
            primary.setDefaultButton(false);
            cancel.setCancelButton(false);
            handle.close();
        };
        x.setOnAction(e -> cancelAction.run());
        cancel.setOnAction(e -> cancelAction.run());
        dim.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelAction.run(); });
        primary.setOnAction(e -> {
            if (onConfirm.test(handle)) {
                primary.setDefaultButton(false);
                cancel.setCancelButton(false);
                handle.close();
            }
        });

        Anim.fadeIn(dim, 180);
        Anim.pop(card);
        Platform.runLater(() -> {
            Node f = body == null ? null : body.lookup(".text-field");
            if (f != null) f.requestFocus();
            else primary.requestFocus();
        });
        return handle;
    }

    /** Asks for a name (new folder, rename). Calls onOk with the trimmed text. */
    public static void prompt(WindowFrame frame, String title, String subtitle, String label, String initial,
                              String suffix, String cta, Consumer<String> onOk) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        TextField field = new TextField(initial);
        field.getStyleClass().add("field");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox row = new HBox(6, field);
        row.setAlignment(Pos.CENTER_LEFT);
        if (suffix != null && !suffix.isBlank()) {
            Label sx = new Label(suffix);
            sx.getStyleClass().add("field-suffix");
            row.getChildren().add(sx);
        }
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setManaged(false);
        error.setVisible(false);
        VBox body = new VBox(6, l, row, error);
        show(frame, title, subtitle, body, cta, false, true, 480, handle -> {
            String v = field.getText() == null ? "" : field.getText().trim();
            if (v.isEmpty()) {
                error.setText("Please type a name.");
                error.setManaged(true);
                error.setVisible(true);
                Anim.shake(field);
                return false;
            }
            onOk.accept(v);
            return true;
        });
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
    }

    public static void confirm(WindowFrame frame, String title, String subtitle, String message, String cta,
                               boolean danger, Runnable onOk) {
        Label m = new Label(message);
        m.setWrapText(true);
        m.getStyleClass().add(danger ? "callout-danger" : "callout");
        m.setMaxWidth(Double.MAX_VALUE);
        show(frame, title, subtitle, m, cta, danger, true, 480, h -> {
            onOk.run();
            return true;
        });
    }

    public static void message(WindowFrame frame, String title, String message, boolean problem) {
        Label m = new Label(message);
        m.setWrapText(true);
        m.getStyleClass().add(problem ? "callout-danger" : "callout");
        m.setMaxWidth(Double.MAX_VALUE);
        show(frame, title, null, m, "OK", false, false, 460, h -> true);
    }

    /** Properties window: details, who can access, and recent activity. */
    public static void properties(WindowFrame frame, Model.Properties p, List<Model.Activity> activity) {
        Model.NodeInfo n = p.node();
        GridPane grid = new GridPane();
        grid.getStyleClass().add("props-grid");
        int r = 0;
        r = row(grid, r, "Name", n.fullName());
        r = row(grid, r, "Type", n.isFile() ? FileKinds.of(n.ext()).label() : n.isRoot() ? "Location" : "File folder");
        r = row(grid, r, "Location", p.location());
        if (n.isFile()) r = row(grid, r, "Size", Format.size(n.sizeBytes()));
        if (p.contains() != null) r = row(grid, r, "Contains", p.contains());
        r = row(grid, r, "Modified", Format.when(n.modifiedAt()) + (n.modifiedByName() == null ? "" : " by " + n.modifiedByName()));
        r = row(grid, r, "Owner", p.owner());
        row(grid, r, "Your access", n.accessLabel());

        VBox access = new VBox();
        access.getStyleClass().add("table-box");
        for (Model.AccessRow a : p.access()) {
            Label who = new Label(a.who());
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            Label lvl = new Label(a.level());
            lvl.getStyleClass().add("strong");
            HBox line = new HBox(who, g, lvl);
            line.getStyleClass().add("table-row");
            access.getChildren().add(line);
        }

        VBox act = new VBox(2);
        if (activity.isEmpty()) {
            Label none = new Label("No activity recorded yet.");
            none.getStyleClass().add("muted");
            act.getChildren().add(none);
        }
        for (Model.Activity a : activity.subList(0, Math.min(8, activity.size()))) {
            Label when = new Label(Format.when(a.at()));
            when.getStyleClass().add("muted");
            when.setMinWidth(120);
            Label what = new Label(a.user() + " · " + a.action());
            what.setWrapText(true);
            HBox.setHgrow(what, Priority.ALWAYS);
            Label pc = new Label(a.computer() == null ? "" : a.computer());
            pc.getStyleClass().add("muted");
            HBox line = new HBox(10, when, what, pc);
            line.getStyleClass().add("activity-row");
            act.getChildren().add(line);
        }

        Label la = new Label("WHO CAN ACCESS");
        la.getStyleClass().add("section-label");
        Label lb = new Label("RECENT ACTIVITY");
        lb.getStyleClass().add("section-label");
        Label note = new Label(n.isPersonal() ? "Only you can see your files." : "Only IT administrators can change access to company folders.");
        note.getStyleClass().add("muted");
        VBox body = new VBox(12, grid, la, access, note, lb, act);
        show(frame, "Properties", n.fullName(), body, "Close", false, false, 640, h -> true);
    }

    private static int row(GridPane g, int r, String k, String v) {
        Label key = new Label(k);
        key.getStyleClass().add("muted");
        Label val = new Label(v == null ? "—" : v);
        val.getStyleClass().add("strong");
        val.setWrapText(true);
        g.add(key, 0, r);
        g.add(val, 1, r);
        return r + 1;
    }
}
