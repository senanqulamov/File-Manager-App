package com.pmis.docket.desktop.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs server calls in the background and returns to the JavaFX thread with the result. */
public final class Async {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "docket-io");
        t.setDaemon(true);
        return t;
    });

    private Async() { }

    public static <T> void run(Callable<T> work, Consumer<T> onOk, Consumer<Throwable> onError) {
        POOL.submit(() -> {
            try {
                T value = work.call();
                Platform.runLater(() -> onOk.accept(value));
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    public static void run(Runnable work, Runnable onOk, Consumer<Throwable> onError) {
        run(() -> { work.run(); return Boolean.TRUE; }, v -> onOk.run(), onError);
    }
}
