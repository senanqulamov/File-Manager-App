package com.pmis.docket.desktop.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small notifications in the bottom-right corner, like Windows toasts. */
public final class Toast {
    private static HBox current;

    private Toast() { }

    public static void show(WindowFrame frame, String message) {
        show(frame, message, null, null);
    }

    /** Same as show, with a red "!" so problems don't look like success. */
    public static void error(WindowFrame frame, String message) {
        show(frame, message, null, null, true);
    }

    public static void show(WindowFrame frame, String message, String actionLabel, Runnable action) {
        show(frame, message, actionLabel, action, false);
    }

    private static void show(WindowFrame frame, String message, String actionLabel, Runnable action, boolean error) {
        StackPane layer = frame.overlay();
        if (current != null) layer.getChildren().remove(current);

        Label check = new Label(error ? "!" : "✓");
        check.getStyleClass().add("toast-check");
        if (error) check.getStyleClass().add("toast-error");
        Label text = new Label(message);
        text.setWrapText(true);
        text.getStyleClass().add("toast-text");
        HBox box = new HBox(12, check, text);
        box.getStyleClass().add("toast");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(440);
        box.setMaxHeight(HBox.USE_PREF_SIZE);
        if (actionLabel != null) {
            Button b = new Button(actionLabel);
            b.getStyleClass().add("toast-action");
            b.setOnAction(e -> {
                layer.getChildren().remove(box);
                action.run();
            });
            box.getChildren().add(b);
        }
        place(layer, box);
        current = box;
        Anim.slideUp(box);
        PauseTransition wait = new PauseTransition(Duration.seconds(actionLabel == null ? 3.6 : 6));
        wait.setOnFinished(e -> Anim.fadeOut(box, 200, () -> {
            layer.getChildren().remove(box);
            if (current == box) current = null;
        }));
        wait.play();
    }

    /** A progress card for uploads and downloads. */
    public static Progress progress(WindowFrame frame, String message) {
        StackPane layer = frame.overlay();
        Label text = new Label(message);
        text.getStyleClass().add("progress-text");
        ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("progress-line");
        bar.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(10, text, bar);
        box.getStyleClass().add("progress-card");
        box.setMaxSize(340, VBox.USE_PREF_SIZE);
        place(layer, box);
        Anim.slideUp(box);
        return new Progress(layer, box, text, bar);
    }

    private static void place(StackPane layer, javafx.scene.Node n) {
        StackPane.setAlignment(n, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(n, new Insets(0, 18, 46, 0));
        layer.getChildren().add(n);
    }

    public record Progress(StackPane layer, VBox box, Label text, ProgressBar bar) {
        public void update(String message, double fraction) {
            text.setText(message);
            bar.setProgress(fraction);
        }

        public void close() {
            Anim.fadeOut(box, 200, () -> layer.getChildren().remove(box));
        }
    }
}
