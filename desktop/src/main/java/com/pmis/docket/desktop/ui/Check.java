package com.pmis.docket.desktop.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/** A checkbox drawn the Docket way: rounded box with a tick, and the text next to it. The whole row is clickable. */
public class Check extends HBox {
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StackPane box = new StackPane();

    public Check(String text) {
        super(10);
        getStyleClass().add("dk-check");
        box.getStyleClass().add("dk-check-box");
        box.setMinSize(20, 20);
        box.setMaxSize(20, 20);
        getChildren().add(box);
        if (text != null && !text.isEmpty()) {
            Label l = new Label(text);
            l.getStyleClass().add("dk-check-text");
            l.setWrapText(true);
            getChildren().add(l);
        }
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(USE_PREF_SIZE);
        selected.addListener((o, a, on) -> refresh());
        setOnMouseClicked(e -> {
            setSelected(!isSelected());
            e.consume();
        });
        refresh();
    }

    private void refresh() {
        box.getChildren().setAll();
        box.getStyleClass().remove("on");
        if (isSelected()) {
            box.getStyleClass().add("on");
            box.getChildren().add(Icons.of("M5 12.5 L10 17 L19 7", 14, 2.4));
        }
    }

    public boolean isSelected() { return selected.get(); }

    public void setSelected(boolean v) { selected.set(v); }

    public BooleanProperty selectedProperty() { return selected; }
}
