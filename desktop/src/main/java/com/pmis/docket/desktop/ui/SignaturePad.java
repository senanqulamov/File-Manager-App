package com.pmis.docket.desktop.ui;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Draw a signature with the mouse or pen. Exports a transparent PNG. */
public class SignaturePad extends StackPane {
    private final Canvas canvas = new Canvas(440, 150);
    private boolean empty = true;

    public SignaturePad() {
        getStyleClass().add("signature-pad");
        getChildren().add(canvas);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setStroke(Color.web("#1F2E8A"));
        g.setLineWidth(3.2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        canvas.setOnMousePressed(e -> {
            g.beginPath();
            g.moveTo(e.getX(), e.getY());
            g.stroke();
            empty = false;
        });
        canvas.setOnMouseDragged(e -> {
            g.lineTo(e.getX(), e.getY());
            g.stroke();
        });
    }

    public void clear() {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        empty = true;
    }

    public boolean isEmpty() { return empty; }

    public String toPngBase64() {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage img = canvas.snapshot(sp, null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }
}
