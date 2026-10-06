package com.pmis.docket.desktop.ui;

import com.pmis.docket.desktop.AppConfig;
import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.ApiException;
import com.pmis.docket.desktop.api.Model;
import javafx.animation.RotateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URI;
import java.util.function.Consumer;

/** Sign-in screen: company account, server status, and a way to change the server address. */
public class LoginView {
    private final WindowFrame frame;
    private final ApiClient api;
    private final AppConfig config;
    private final Consumer<Model.UserInfo> onSignedIn;

    private final StackPane root = new StackPane();
    private final VBox card = new VBox(14);
    private final TextField login = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label error = new Label();
    private final Button signIn = new Button("Sign in");
    private final HBox busy = new HBox(10);
    private final Label serverName = new Label();
    private final Label serverStatus = new Label();
    private RotateTransition spinner;

    public LoginView(WindowFrame frame, ApiClient api, AppConfig config, Consumer<Model.UserInfo> onSignedIn) {
        this.frame = frame;
        this.api = api;
        this.config = config;
        this.onSignedIn = onSignedIn;
        build();
        checkServer();
    }

    public Node root() { return root; }

    private void build() {
        root.getStyleClass().add("login-root");

        Node bigFolder = FileIcon.folder(900, false);
        bigFolder.setOpacity(0.18);
        bigFolder.setMouseTransparent(true);
        StackPane.setAlignment(bigFolder, Pos.BOTTOM_RIGHT);
        bigFolder.setTranslateX(260);
        bigFolder.setTranslateY(260);

        Label mark = new Label("D");
        mark.getStyleClass().add("logo-mark-large");
        Label title = new Label("Sign in");
        title.getStyleClass().add("login-title");
        Label sub = new Label("Use your PMIS network account");
        sub.getStyleClass().add("muted");
        VBox head = new VBox(4, title, sub);

        login.setPromptText("PMIS\\a.karimova");
        login.setText(config.lastLogin());
        login.getStyleClass().add("field");
        password.setPromptText("Password");
        password.getStyleClass().add("field");

        VBox userBox = labelled("Username", login);
        VBox passBox = labelled("Password", password);

        serverName.getStyleClass().add("strong");
        Label serverSub = new Label("Office file server");
        serverSub.getStyleClass().add("muted-small");
        VBox serverText = new VBox(serverName, serverSub);
        Hyperlink change = new Hyperlink("Change");
        change.getStyleClass().add("link");
        change.setOnAction(e -> changeServer());
        Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        HBox server = new HBox(10, Icons.of(Icons.SERVER, 16), serverText, g, serverStatus, change);
        server.getStyleClass().add("server-row");
        server.setAlignment(Pos.CENTER_LEFT);

        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);

        signIn.getStyleClass().addAll("btn", "btn-primary", "btn-wide");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);
        signIn.setOnAction(e -> submit());

        Region ring = new Region();
        ring.getStyleClass().add("spinner-light");
        Label connecting = new Label("Connecting to the server…");
        connecting.getStyleClass().add("busy-text");
        busy.getChildren().addAll(ring, connecting);
        busy.getStyleClass().add("busy-bar");
        busy.setAlignment(Pos.CENTER);
        busy.setManaged(false);
        busy.setVisible(false);
        spinner = Anim.spin(ring);
        spinner.stop();

        Label help = new Label("Forgot your password? Ask your IT administrator.");
        help.getStyleClass().add("muted-small");
        help.setMaxWidth(Double.MAX_VALUE);
        help.setAlignment(Pos.CENTER);

        card.getChildren().addAll(mark, head, userBox, passBox, server, error, signIn, busy, help);
        card.getStyleClass().add("login-card");
        card.setMaxSize(400, Region.USE_PREF_SIZE);
        card.setPadding(new Insets(32));

        root.getChildren().addAll(bigFolder, card);
        Anim.pop(card);
        if (!login.getText().isBlank()) javafx.application.Platform.runLater(password::requestFocus);
    }

    private VBox labelled(String text, Control field) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return new VBox(6, l, field);
    }

    private void checkServer() {
        serverName.setText(hostOf(api.baseUrl()));
        setStatus("Checking…", "status-wait");
        Async.run(api::health, ok -> {
            if (ok) setStatus("● Reachable", "status-ok");
            else setStatus("● Not reachable", "status-bad");
        }, e -> setStatus("● Not reachable", "status-bad"));
    }

    private void setStatus(String text, String cls) {
        serverStatus.setText(text);
        serverStatus.getStyleClass().removeAll("status-ok", "status-bad", "status-wait");
        serverStatus.getStyleClass().add(cls);
    }

    private void changeServer() {
        Dialogs.prompt(frame, "Server address", "Ask IT if you are not sure. Example: https://192.168.1.10:8443",
                "Address", api.baseUrl(), null, "Save", value -> {
                    String v = value.startsWith("http://") || value.startsWith("https://") ? value : "https://" + value;
                    config.setServerUrl(v);
                    api.setBaseUrl(v);
                    checkServer();
                });
    }

    private void submit() {
        String user = login.getText() == null ? "" : login.getText().trim();
        String pass = password.getText() == null ? "" : password.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            showError("Enter your username and password.");
            return;
        }
        setBusy(true);
        Async.run(() -> api.login(user, pass, AppConfig.computerName()), r -> {
            config.setLastLogin(user);
            setBusy(false);
            onSignedIn.accept(r.user());
        }, e -> {
            setBusy(false);
            showError(ApiException.messageOf(e));
            password.clear();
            password.requestFocus();
        });
    }

    private void setBusy(boolean on) {
        signIn.setManaged(!on);
        signIn.setVisible(!on);
        busy.setManaged(on);
        busy.setVisible(on);
        login.setDisable(on);
        password.setDisable(on);
        if (on) {
            error.setManaged(false);
            error.setVisible(false);
            spinner.play();
        } else {
            spinner.stop();
        }
    }

    private void showError(String message) {
        error.setText(message);
        error.setManaged(true);
        error.setVisible(true);
        Anim.shake(card);
    }

    private static String hostOf(String url) {
        try {
            URI u = URI.create(url);
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }
}
