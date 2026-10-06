package com.pmis.docket.desktop.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A row of pill buttons where exactly one is selected (formats, levels, expiry …). */
public class Chips extends FlowPane {
    private final List<Button> buttons = new ArrayList<>();
    private String value;
    private Consumer<String> onChange = v -> { };

    public Chips(List<String> options, String initial) {
        super(8, 8);
        getStyleClass().add("chips");
        for (String o : options) {
            Button b = new Button(o);
            b.getStyleClass().add("chip");
            b.setOnAction(e -> select(o));
            buttons.add(b);
            getChildren().add(b);
        }
        select(initial != null ? initial : options.isEmpty() ? null : options.get(0));
    }

    public void select(String v) {
        value = v;
        for (Button b : buttons) {
            b.getStyleClass().remove("on");
            if (b.getText().equals(v)) b.getStyleClass().add("on");
        }
        onChange.accept(v);
    }

    public String value() { return value; }

    public void setOnChange(Consumer<String> c) { this.onChange = c; }
}
