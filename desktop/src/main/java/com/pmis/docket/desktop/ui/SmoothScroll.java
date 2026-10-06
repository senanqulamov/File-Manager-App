package com.pmis.docket.desktop.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes every scroll area in the app scroll like a modern Windows app:
 * a wheel notch moves about 110 px (JavaFX moves only a few), movement is animated,
 * and when an inner list reaches its end the outer page scrolls instead.
 */
public final class SmoothScroll {
    private static final double PIXELS_PER_NOTCH = 110;
    private static final Map<ScrollPane, double[]> TARGETS = new WeakHashMap<>();
    private static final Map<ScrollPane, Timeline> RUNNING = new WeakHashMap<>();

    private SmoothScroll() { }

    public static void install(Scene scene) {
        scene.addEventFilter(ScrollEvent.SCROLL, SmoothScroll::onScroll);
    }

    private static void onScroll(ScrollEvent e) {
        if (e.isControlDown() || e.getDeltaY() == 0 || e.isInertia()) return;
        double notches = e.getDeltaY() / (e.getMultiplierY() > 0 ? e.getMultiplierY() : 40);
        double pixels = -notches * PIXELS_PER_NOTCH;
        Node n = e.getTarget() instanceof Node t ? t : null;
        while (n != null) {
            if (n instanceof ScrollPane sp && scrollBy(sp, pixels)) {
                e.consume();
                return;
            }
            n = n.getParent();
        }
    }

    /** Returns false when this area can't move in that direction, so an outer area can take over. */
    private static boolean scrollBy(ScrollPane sp, double pixels) {
        if (sp.getContent() == null) return false;
        double content = sp.getContent().getLayoutBounds().getHeight();
        double view = sp.getViewportBounds().getHeight();
        double range = content - view;
        if (range <= 1) return false;
        double[] target = TARGETS.computeIfAbsent(sp, k -> new double[]{sp.getVvalue()});
        Timeline running = RUNNING.get(sp);
        double from = running != null && running.getStatus() == Timeline.Status.RUNNING ? target[0] : sp.getVvalue();
        double span = sp.getVmax() - sp.getVmin();
        double to = Math.max(sp.getVmin(), Math.min(sp.getVmax(), from + pixels / range * span));
        if (Math.abs(to - sp.getVvalue()) < 1e-6 && Math.abs(to - from) < 1e-6) return false;
        target[0] = to;
        if (running != null) running.stop();
        Timeline t = new Timeline(new KeyFrame(Duration.millis(220), new KeyValue(sp.vvalueProperty(), to, Interpolator.SPLINE(0.25, 0.8, 0.3, 1.0))));
        RUNNING.put(sp, t);
        t.play();
        return true;
    }
}
