package com.pmis.docket.desktop.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

import java.util.function.Consumer;

/**
 * A toolbar button that stays clickable when not allowed, so it can explain why
 * (as in the design: greyed out + a short message instead of doing nothing).
 */
public class ToolButton extends Button {
    private String whyNot;
    private final boolean labelled;
    private final Runnable action;
    private final Consumer<String> explain;

    public ToolButton(String iconPath, String label, String tooltip, Runnable action, Consumer<String> explain) {
        super(label == null ? "" : label, Icons.of(iconPath, 16));
        this.action = action;
        this.explain = explain;
        this.labelled = label != null;
        getStyleClass().add("tb");
        setMinWidth(USE_PREF_SIZE);
        if (label == null) {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            getStyleClass().add("tb-icon");
        }
        setTooltip(new Tooltip(tooltip));
        setFocusTraversable(false);
        setOnAction(e -> {
            if (whyNot != null) explain.accept(whyNot);
            else action.run();
        });
    }

    /** null = allowed; otherwise the reason shown when clicked. */
    public void setWhyNot(String reason) {
        this.whyNot = reason;
        if (reason == null) getStyleClass().remove("off");
        else if (!getStyleClass().contains("off")) getStyleClass().add("off");
    }

    public boolean allowed() { return whyNot == null; }

    /** Icon only (tooltip still shows the name) when the window is too narrow for labels. */
    public void setCompact(boolean compact) {
        if (!labelled) return;
        setContentDisplay(compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        if (compact && !getStyleClass().contains("tb-icon")) getStyleClass().add("tb-icon");
        if (!compact) getStyleClass().remove("tb-icon");
    }

    public void fire(boolean explainIfBlocked) {
        if (whyNot == null) action.run();
        else if (explainIfBlocked) explain.accept(whyNot);
    }
}
