package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.AppConfig;
import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.ApiException;
import com.pmis.docket.desktop.api.Model;
import com.pmis.docket.desktop.api.Model.NodeInfo;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Docket viewer: a window inside the app that shows any file the right way —
 * pages (PDF, Word, Excel, PowerPoint), pictures, video, audio, text, code, archive contents —
 * or a clear "no preview" card with Open with / Download.
 */
public class ViewerView {
    private static final Set<String> FX_IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final long MAX_TEXT = 2L * 1024 * 1024;

    public interface Actions {
        void openInApp(NodeInfo n);

        void download(NodeInfo n);

        void checkout(NodeInfo n);

        void document(String action, NodeInfo n);

        void extracted(NodeInfo folder);
    }

    private final WindowFrame frame;
    private final ApiClient api;
    private final NodeInfo n;
    private final Actions actions;
    private final StackPane body = new StackPane();
    private final HBox pageControls = new HBox(4);
    private final Label pageLabel = new Label();
    private final Label zoomLabel = new Label("100%");
    private Dialogs.Handle handle;
    private MediaPlayer player;
    private int page = 1, pages = 1;
    private double zoom = 1.0;
    private ImageView pageView;

    private ViewerView(WindowFrame frame, ApiClient api, NodeInfo n, Actions actions) {
        this.frame = frame;
        this.api = api;
        this.n = n;
        this.actions = actions;
    }

    public static void show(WindowFrame frame, ApiClient api, NodeInfo n, Actions actions) {
        new ViewerView(frame, api, n, actions).open();
    }

    private void open() {
        StackPane layer = frame.overlay();

        Label mark = new Label("D");
        mark.getStyleClass().add("logo-mini");
        Label title = new Label(n.fullName());
        title.getStyleClass().add("dialog-strip-text");
        title.setMinWidth(0);
        Label type = new Label(FileKinds.of(n.ext()).label() + " · " + Format.size(n.sizeBytes()) + (n.version() > 1 ? " · version " + n.version() : ""));
        type.getStyleClass().add("viewer-title-chip");
        type.setMinWidth(Region.USE_PREF_SIZE);
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        Button close = new Button();
        close.setGraphic(Icons.of(Icons.CLOSE, 12, 1.5));
        close.getStyleClass().addAll("win-btn", "win-close");
        HBox strip = new HBox(10, mark, title, type, g, close);
        strip.getStyleClass().add("viewer-strip");
        strip.setAlignment(Pos.CENTER_LEFT);

        // Left: page and zoom controls. Right: actions. Every button keeps its full size; names that don't fit become tooltips.
        Button prev = small(Icons.BACK, "Previous page");
        Button next = small(Icons.FORWARD, "Next page");
        Button out = small(Icons.MINUS, "Zoom out");
        Button in = small(Icons.PLUS, "Zoom in");
        prev.setOnAction(e -> showPage(page - 1));
        next.setOnAction(e -> showPage(page + 1));
        out.setOnAction(e -> setZoom(zoom - 0.25));
        in.setOnAction(e -> setZoom(zoom + 0.25));
        pageLabel.getStyleClass().add("page-label");
        zoomLabel.getStyleClass().add("zoom-label");
        pageControls.getChildren().addAll(prev, pageLabel, next, sep(), out, zoomLabel, in);
        pageControls.getStyleClass().add("tool-group");
        pageControls.setAlignment(Pos.CENTER_LEFT);
        pageControls.setMinWidth(Region.USE_PREF_SIZE);
        pageControls.setVisible(false);
        pageControls.setManaged(false);

        Region g2 = new Region();
        HBox.setHgrow(g2, Priority.ALWAYS);
        HBox tools = new HBox(8, pageControls, g2);
        tools.setAlignment(Pos.CENTER_LEFT);
        tools.getStyleClass().add("viewer-tools");
        if (n.canWrite() && n.checkedOutByName() == null && !n.locked()) {
            Button co = new Button("Check out", Icons.of(Icons.PENCIL, 15));
            co.getStyleClass().addAll("btn", "btn-dark", "btn-small");
            co.setTooltip(new Tooltip("Check out to edit in your desktop app"));
            co.setMinWidth(Region.USE_PREF_SIZE);
            co.setOnAction(e -> {
                close();
                actions.checkout(n);
            });
            tools.getChildren().add(co);
        }
        HBox openGroup = new HBox(1, textBtn(Icons.OPEN, "Open in app", () -> actions.openInApp(n)), textBtn(Icons.DOWNLOAD, "Download", () -> actions.download(n)));
        openGroup.getStyleClass().add("tool-group");
        openGroup.setMinWidth(Region.USE_PREF_SIZE);
        HBox docGroup = new HBox(1,
                iconBtn(Icons.CONVERT, "Convert", true, "convert"),
                iconBtn(Icons.SIGN, "Sign", FileKinds.canSign(n.ext()) && n.canWrite() && !n.locked() && !n.checkedOutByOther(), "sign"),
                iconBtn(Icons.LOCK, "Lock with password", n.canWrite() && !n.locked() && !n.checkedOutByOther(), "lock"),
                iconBtn(Icons.STAMP, "Stamp", FileKinds.canStamp(n.ext()) && n.canWrite() && !n.locked() && !n.checkedOutByOther(), "mark"));
        docGroup.getStyleClass().add("tool-group");
        docGroup.setMinWidth(Region.USE_PREF_SIZE);
        tools.getChildren().addAll(openGroup, docGroup);

        VBox banners = new VBox(6);
        banners.setPadding(new Insets(0, 24, 0, 24));
        banners.setAlignment(Pos.TOP_CENTER);
        if (n.checkedOutByOther()) banners.getChildren().add(banner(Icons.PENCIL, "Read only — " + n.checkedOutByName() + " is editing this file right now.", "banner-warn"));
        else if (n.checkedOutByMe()) banners.getChildren().add(banner(Icons.PENCIL, "You have this file checked out. Check it in when you’re done so others see your changes.", "banner-blue"));
        if (n.locked()) banners.getChildren().add(banner(Icons.LOCK, "Protected — a password is needed to open this file outside Docket.", "banner-grey"));
        if (n.isSharedToMe()) banners.getChildren().add(banner(Icons.PEOPLE, "Shared with you by " + n.sharedByName() + " · " + n.shareLevel(), "banner-purple"));

        body.getStyleClass().add("viewer-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        body.getChildren().setAll(spinner("Opening " + n.fullName() + "…"));

        VBox window = new VBox(strip, tools, banners, body);
        window.getStyleClass().add("viewer-window");
        window.setMaxSize(1100, 820);

        StackPane dim = new StackPane(window);
        dim.getStyleClass().add("dim");
        dim.setPadding(new Insets(26));
        layer.getChildren().add(dim);
        handle = new Dialogs.Handle(layer, dim);
        close.setOnAction(e -> close());
        dim.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
            if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.PAGE_DOWN) showPage(page + 1);
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.PAGE_UP) showPage(page - 1);
        });
        Anim.fadeIn(dim, 180);
        Anim.pop(window);
        dim.requestFocus();
        load();
    }

    private void load() {
        Async.run(() -> api.previewInfo(n.id()), info -> {
            switch (info.kind()) {
                case "page", "slides" -> showPages(info.pages());
                case "image" -> showImage();
                case "video", "audio" -> showMedia("video".equals(info.kind()));
                case "text", "code" -> showText("code".equals(info.kind()));
                case "archive" -> showArchive();
                default -> showNone(info.message());
            }
        }, e -> showNone(ApiException.messageOf(e)));
    }

    // ------------------------------------------------------------------ pages

    private void showPages(int count) {
        pages = Math.max(1, count);
        pageControls.setVisible(true);
        pageControls.setManaged(true);
        pageView = new ImageView();
        pageView.setPreserveRatio(true);
        pageView.setSmooth(true);
        StackPane paper = new StackPane(pageView);
        paper.getStyleClass().add("paper-shadow");
        paper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane center = new StackPane(paper);
        center.setPadding(new Insets(16, 24, 30, 24));
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        center.minWidthProperty().bind(Bindings.createDoubleBinding(() -> scroll.getViewportBounds().getWidth(), scroll.viewportBoundsProperty()));
        body.getChildren().setAll(scroll);
        showPage(1);
    }

    private void showPage(int p) {
        if (pageView == null || p < 1 || p > pages) return;
        page = p;
        pageLabel.setText("Page " + page + " of " + pages);
        int dpi = (int) Math.round(96 * Math.max(1, zoom) * 1.25);
        Async.run(() -> api.previewPage(n.id(), page, dpi), bytes -> {
            Image img = new Image(new ByteArrayInputStream(bytes));
            pageView.setImage(img);
            pageView.setFitWidth(Math.min(820, img.getWidth() * 96 / dpi) * zoom);
            Anim.fadeIn(pageView, 160);
        }, e -> Toast.error(frame, ApiException.messageOf(e)));
    }

    private void setZoom(double z) {
        zoom = Math.max(0.5, Math.min(3.0, z));
        zoomLabel.setText(Math.round(zoom * 100) + "%");
        if (pageView != null && pageView.getImage() != null && !body.getChildren().isEmpty() && pages > 0 && pageControls.isVisible() && pageLabel.getText().startsWith("Page")) {
            showPage(page);
        } else if (pageView != null && pageView.getImage() != null) {
            pageView.setFitWidth(Math.min(980, pageView.getImage().getWidth()) * zoom);
        }
    }

    // ------------------------------------------------------------------ pictures

    private void showImage() {
        String e = n.ext() == null ? "" : n.ext().toLowerCase(Locale.ROOT);
        Async.run(() -> {
            if (FX_IMAGES.contains(e)) {
                Path f = temp();
                api.download(n.id(), f, false);
                return Files.readAllBytes(f);
            }
            return api.previewImage(n.id());
        }, bytes -> {
            Image img = new Image(new ByteArrayInputStream(bytes));
            pageView = new ImageView(img);
            pageView.setPreserveRatio(true);
            pageView.setSmooth(true);
            pageView.setFitWidth(Math.min(980, img.getWidth()));
            pages = 0;
            pageLabel.setText("");
            pageControls.getChildren().get(0).setVisible(false);
            pageControls.getChildren().get(2).setVisible(false);
            pageControls.setVisible(true);
            pageControls.setManaged(true);
            StackPane frameBox = new StackPane(pageView);
            frameBox.getStyleClass().add("picture-frame");
            frameBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            Label dims = new Label((int) img.getWidth() + " × " + (int) img.getHeight() + " px · " + Format.size(n.sizeBytes()));
            dims.getStyleClass().add("muted-small");
            VBox box = new VBox(10, frameBox, dims);
            box.setAlignment(Pos.TOP_CENTER);
            box.setPadding(new Insets(16, 24, 30, 24));
            ScrollPane scroll = new ScrollPane(box);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("content-scroll");
            body.getChildren().setAll(scroll);
            Anim.pop(frameBox);
        }, ex -> showNone(ApiException.messageOf(ex)));
    }

    // ------------------------------------------------------------------ video and audio

    private void showMedia(boolean video) {
        body.getChildren().setAll(spinner("Loading " + (video ? "video" : "audio") + "…"));
        Async.run(() -> {
            Path f = temp();
            api.download(n.id(), f, false);
            return f;
        }, f -> {
            try {
                Media media = new Media(f.toUri().toString());
                player = new MediaPlayer(media);
            } catch (Exception ex) {
                showNone("This " + (video ? "video" : "audio") + " format can’t be played inside Docket. Use Open in app.");
                return;
            }
            player.setOnError(() -> showNone("This " + (video ? "video" : "audio") + " format can’t be played inside Docket. Use Open in app."));
            Button play = new Button();
            play.setGraphic(Icons.of(Icons.PLAY, 18));
            play.getStyleClass().add(video ? "play-btn" : "play-btn-dark");
            Slider seek = new Slider(0, 1, 0);
            HBox.setHgrow(seek, Priority.ALWAYS);
            Label time = new Label("0:00");
            time.getStyleClass().add(video ? "media-time" : "strong-small");
            play.setOnAction(e -> {
                if (player.getStatus() == MediaPlayer.Status.PLAYING) player.pause();
                else player.play();
            });
            player.statusProperty().addListener((o, a, s) -> play.setGraphic(Icons.of(s == MediaPlayer.Status.PLAYING ? Icons.PAUSE : Icons.PLAY, 18)));
            player.currentTimeProperty().addListener((o, a, t) -> {
                Duration total = player.getTotalDuration();
                if (total != null && total.toMillis() > 0 && !seek.isValueChanging()) seek.setValue(t.toMillis() / total.toMillis());
                time.setText(fmt(t) + " / " + fmt(total));
            });
            seek.valueChangingProperty().addListener((o, was, now) -> {
                if (!now && player.getTotalDuration() != null) player.seek(player.getTotalDuration().multiply(seek.getValue()));
            });
            player.setOnEndOfMedia(() -> {
                player.stop();
                player.seek(Duration.ZERO);
            });
            HBox controls = new HBox(12, play, seek, time);
            controls.setAlignment(Pos.CENTER_LEFT);
            if (video) {
                MediaView mv = new MediaView(player);
                mv.setPreserveRatio(true);
                mv.fitWidthProperty().bind(body.widthProperty().subtract(80));
                mv.fitHeightProperty().bind(body.heightProperty().subtract(110));
                controls.getStyleClass().add("video-controls");
                VBox box = new VBox(mv, controls);
                box.getStyleClass().add("video-box");
                box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                body.getChildren().setAll(box);
                Anim.pop(box);
            } else {
                HBox bars = new HBox(3);
                bars.setAlignment(Pos.CENTER);
                int[] h = {12, 22, 34, 18, 40, 28, 46, 30, 20, 36, 50, 26, 16, 38, 44, 24, 30, 42, 18, 34, 48, 22, 14, 32, 40, 20, 28, 46, 36, 18};
                for (int v : h) {
                    Region b = new Region();
                    b.setMinSize(6, v);
                    b.setMaxSize(6, v);
                    b.getStyleClass().add("wave-bar");
                    bars.getChildren().add(b);
                }
                Label name = new Label(n.name());
                name.getStyleClass().add("audio-title");
                VBox card = new VBox(18, name, bars, controls);
                card.getStyleClass().add("audio-card");
                card.setMaxSize(620, Region.USE_PREF_SIZE);
                body.getChildren().setAll(card);
                Anim.pop(card);
            }
        }, ex -> showNone(ApiException.messageOf(ex)));
    }

    private static String fmt(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) return "0:00";
        long s = (long) d.toSeconds();
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    // ------------------------------------------------------------------ text and code

    private void showText(boolean code) {
        if (n.sizeBytes() > MAX_TEXT) {
            showNone("This file is too large to show here (" + Format.size(n.sizeBytes()) + "). Use Open in app.");
            return;
        }
        Async.run(() -> {
            Path f = temp();
            api.download(n.id(), f, false);
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        }, text -> {
            TextArea area = new TextArea(text);
            area.setEditable(false);
            area.setWrapText(!code);
            area.getStyleClass().add(code ? "code-area" : "note-area");
            area.setMaxWidth(code ? 900 : 720);
            StackPane box = new StackPane(area);
            box.setPadding(new Insets(10, 24, 24, 24));
            body.getChildren().setAll(box);
            Anim.fadeIn(area, 200);
        }, ex -> showNone(ApiException.messageOf(ex)));
    }

    // ------------------------------------------------------------------ archives

    private void showArchive() {
        Async.run(() -> api.archive(n.id()), entries -> {
            VBox list = new VBox();
            list.getStyleClass().add("table-box");
            for (Model.ArchiveEntry a : entries) {
                Node icon = a.folder() ? FileIcon.folder(18, false) : Icons.of(Icons.LOG, 15);
                Label name = new Label(a.name());
                HBox.setHgrow(name, Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);
                Label size = new Label(a.folder() ? "" : Format.size(a.size()));
                size.getStyleClass().add("muted-small");
                HBox row = new HBox(10, icon, name, size);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("table-row");
                list.getChildren().add(row);
            }
            Label title = new Label("Inside " + n.fullName() + " · " + entries.size() + " items");
            title.getStyleClass().add("strong");
            Region g = new Region();
            HBox.setHgrow(g, Priority.ALWAYS);
            HBox head = new HBox(10, title, g);
            head.setAlignment(Pos.CENTER_LEFT);
            if (n.canWrite() || n.isPersonal() && n.mine()) {
                Button extract = new Button("Extract here");
                extract.getStyleClass().addAll("btn", "btn-primary", "btn-small");
                extract.setOnAction(e -> {
                    extract.setDisable(true);
                    extract.setText("Extracting…");
                    Async.run(() -> api.extract(n.id()), folder -> {
                        close();
                        Toast.show(frame, "Extracted " + n.fullName() + " into the folder “" + folder.name() + "”.");
                        actions.extracted(folder);
                    }, ex -> {
                        extract.setDisable(false);
                        extract.setText("Extract here");
                        Toast.error(frame, ApiException.messageOf(ex));
                    });
                });
                head.getChildren().add(extract);
            }
            Label hint = new Label("Docket shows what’s inside without unpacking. Extract to work with the files.");
            hint.getStyleClass().add("muted-small");
            VBox card = new VBox(10, head, list, hint);
            card.getStyleClass().add("archive-card");
            card.setMaxWidth(720);
            ScrollPane scroll = new ScrollPane(card);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("content-scroll");
            StackPane wrap = new StackPane(scroll);
            wrap.setPadding(new Insets(10, 24, 24, 24));
            body.getChildren().setAll(wrap);
            Anim.fadeIn(card, 200);
        }, ex -> showNone(ApiException.messageOf(ex)));
    }

    // ------------------------------------------------------------------ nothing to show

    private void showNone(String message) {
        FileKinds.Info info = FileKinds.of(n.ext());
        Label icon = new Label("." + (n.ext() == null ? "" : n.ext()));
        icon.getStyleClass().add("none-icon");
        icon.setStyle("-fx-background-color: " + info.color() + ";");
        Label t = new Label("No preview for " + info.label());
        t.getStyleClass().add("none-title");
        Label m = new Label(message == null ? Format.size(n.sizeBytes()) + " · You can open it with a program on this computer or download it." : message);
        m.getStyleClass().add("muted");
        m.setWrapText(true);
        m.setMaxWidth(420);
        VBox card = new VBox(12, icon, t, m);
        if (info.kind() == FileKinds.Kind.APP) {
            Label warn = new Label("Only run programs that IT has approved. Windows will ask before installing.");
            warn.getStyleClass().add("callout-warn");
            warn.setWrapText(true);
            card.getChildren().add(warn);
        }
        Button dl = new Button("Download");
        dl.getStyleClass().addAll("btn", "btn-outline");
        dl.setOnAction(e -> actions.download(n));
        Button op = new Button("Open with…");
        op.getStyleClass().addAll("btn", "btn-primary");
        op.setOnAction(e -> actions.openInApp(n));
        HBox buttons = new HBox(8, dl, op);
        buttons.setAlignment(Pos.CENTER);
        card.getChildren().add(buttons);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("none-card");
        card.setMaxSize(520, Region.USE_PREF_SIZE);
        body.getChildren().setAll(card);
        Anim.pop(card);
    }

    // ------------------------------------------------------------------ helpers

    private void doc(String action) {
        close();
        actions.document(action, n);
    }

    private Button iconBtn(String icon, String name, boolean allowed, String action) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 16));
        b.getStyleClass().addAll("tb", "tb-icon");
        b.setTooltip(new Tooltip(allowed ? name : name + " — " + whyNot(action)));
        if (allowed) b.setOnAction(e -> doc(action));
        else {
            b.getStyleClass().add("off");
            b.setOnAction(e -> Toast.show(frame, whyNot(action)));
        }
        return b;
    }

    private String whyNot(String action) {
        if (n.checkedOutByOther()) return n.checkedOutByName() + " is editing this file.";
        if (n.locked() && !action.equals("convert")) return "Locked files can’t be changed.";
        if (!n.canWrite()) return "You have read-only access to this file.";
        return switch (action) {
            case "sign" -> FileKinds.plural(n.ext()) + " can’t be signed.";
            case "mark" -> FileKinds.plural(n.ext()) + " can’t be stamped.";
            default -> "Not available for this file.";
        };
    }

    private Button textBtn(String icon, String label, Runnable r) {
        Button b = new Button(label, Icons.of(icon, 15));
        b.getStyleClass().add("tb");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> r.run());
        return b;
    }

    private Button small(String icon, String tip) {
        Button b = new Button();
        b.setGraphic(Icons.of(icon, 14));
        b.getStyleClass().addAll("tb", "tb-icon");
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    private Separator sep() {
        Separator s = new Separator(javafx.geometry.Orientation.VERTICAL);
        s.getStyleClass().add("tool-sep");
        return s;
    }

    private HBox banner(String icon, String text, String cls) {
        Label l = new Label(text);
        l.setWrapText(true);
        HBox b = new HBox(8, Icons.of(icon, 14), l);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().addAll("banner", cls);
        b.setMaxWidth(820);
        return b;
    }

    private Node spinner(String text) {
        Region ring = new Region();
        ring.getStyleClass().add("spinner-dark");
        Anim.spin(ring);
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        VBox v = new VBox(12, ring, l);
        v.setAlignment(Pos.CENTER);
        return v;
    }

    private Path temp() {
        return AppConfig.tempDir().resolve("view").resolve(n.id() + "-v" + n.version()).resolve(n.fullName());
    }

    private void close() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
        handle.close();
    }
}
