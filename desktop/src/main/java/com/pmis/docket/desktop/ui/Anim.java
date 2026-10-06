package com.pmis.docket.desktop.ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

/** The motion language from the design: soft ease-out, short durations. */
public final class Anim {
    public static final Interpolator EASE_OUT = Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0);
    /**
     * Ease-out with a small overshoot (goes slightly past the end, then settles).
     * Interpolator.SPLINE only accepts control points between 0 and 1, so overshoot
     * has to be written as its own curve ("back-out" easing).
     */
    public static final Interpolator SPRING = new Interpolator() {
        private static final double S = 1.2;

        @Override
        protected double curve(double t) {
            double x = t - 1.0;
            return 1.0 + (S + 1.0) * x * x * x + S * x * x;
        }

        @Override
        public String toString() {
            return "Anim.SPRING";
        }
    };

    private Anim() { }

    /** Items in a folder: fade in while rising 8 px. */
    public static void fadeUp(Node n, double delayMs) {
        n.setOpacity(0);
        n.setTranslateY(8);
        FadeTransition f = new FadeTransition(Duration.millis(320), n);
        f.setToValue(1);
        f.setInterpolator(EASE_OUT);
        TranslateTransition t = new TranslateTransition(Duration.millis(320), n);
        t.setToY(0);
        t.setInterpolator(EASE_OUT);
        ParallelTransition p = new ParallelTransition(f, t);
        p.setDelay(Duration.millis(delayMs));
        p.play();
    }

    /** Dialogs, cards: scale up slightly while fading in. */
    public static void pop(Node n) {
        n.setOpacity(0);
        n.setScaleX(0.95);
        n.setScaleY(0.95);
        n.setTranslateY(6);
        FadeTransition f = new FadeTransition(Duration.millis(240), n);
        f.setToValue(1);
        ScaleTransition s = new ScaleTransition(Duration.millis(260), n);
        s.setToX(1);
        s.setToY(1);
        s.setInterpolator(SPRING);
        TranslateTransition t = new TranslateTransition(Duration.millis(260), n);
        t.setToY(0);
        t.setInterpolator(EASE_OUT);
        new ParallelTransition(f, s, t).play();
    }

    /** Context menus: quick scale from 96 %. */
    public static void menuIn(Node n) {
        n.setOpacity(0);
        n.setScaleX(0.96);
        n.setScaleY(0.96);
        FadeTransition f = new FadeTransition(Duration.millis(160), n);
        f.setToValue(1);
        ScaleTransition s = new ScaleTransition(Duration.millis(160), n);
        s.setToX(1);
        s.setToY(1);
        s.setInterpolator(EASE_OUT);
        new ParallelTransition(f, s).play();
    }

    public static void fadeIn(Node n, double ms) {
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        f.setInterpolator(EASE_OUT);
        f.play();
    }

    public static void fadeOut(Node n, double ms, Runnable done) {
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(0);
        f.setOnFinished(e -> { if (done != null) done.run(); });
        f.play();
    }

    /** Toasts: slide up 16 px while fading in. */
    public static void slideUp(Node n) {
        n.setOpacity(0);
        n.setTranslateY(16);
        FadeTransition f = new FadeTransition(Duration.millis(300), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(300), n);
        t.setToY(0);
        t.setInterpolator(SPRING);
        new ParallelTransition(f, t).play();
    }

    /** Wrong password: short horizontal shake. */
    public static void shake(Node n) {
        TranslateTransition t = new TranslateTransition(Duration.millis(60), n);
        t.setFromX(0);
        t.setByX(8);
        t.setCycleCount(6);
        t.setAutoReverse(true);
        t.setOnFinished(e -> n.setTranslateX(0));
        t.play();
    }

    public static RotateTransition spin(Node n) {
        RotateTransition r = new RotateTransition(Duration.millis(800), n);
        r.setByAngle(360);
        r.setCycleCount(Animation.INDEFINITE);
        r.setInterpolator(Interpolator.LINEAR);
        r.play();
        return r;
    }
}
