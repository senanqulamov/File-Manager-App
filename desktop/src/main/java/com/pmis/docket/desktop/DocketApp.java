package com.pmis.docket.desktop;

import com.pmis.docket.desktop.api.ApiClient;
import com.pmis.docket.desktop.api.Model;
import com.pmis.docket.desktop.ui.ExplorerView;
import com.pmis.docket.desktop.ui.LoginView;
import com.pmis.docket.desktop.ui.WindowFrame;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.InputStream;

public class DocketApp extends Application {
    private AppConfig config;
    private ApiClient api;
    private WindowFrame frame;

    public static void main(String[] args) {
        launch(DocketApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        loadFonts();
        config = AppConfig.load();
        api = new ApiClient(config.serverUrl());

        frame = new WindowFrame(stage);
        Scene scene = frame.createScene(1280, 820);
        com.pmis.docket.desktop.ui.SmoothScroll.install(scene);
        scene.getStylesheets().add(getClass().getResource("theme.css").toExternalForm());

        stage.setTitle("PMIS Docket");
        InputStream icon = getClass().getResourceAsStream("icon.png");
        if (icon != null) stage.getIcons().add(new Image(icon));
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);

        showLogin();
        stage.centerOnScreen();
        stage.show();
    }

    private void showLogin() {
        frame.setTitleCenter(null);
        frame.setTitleRight(null);
        frame.setContent(new LoginView(frame, api, config, this::showExplorer).root());
    }

    private void showExplorer(Model.UserInfo user) {
        ExplorerView explorer = new ExplorerView(frame, api, config, user, this::showLogin);
        frame.setContent(explorer.root());
        explorer.start();
    }

    /** Loads Urbanist from resources if the font files were added (see fonts/README.txt). */
    private void loadFonts() {
        for (String w : new String[]{"Regular", "Medium", "SemiBold", "Bold", "ExtraBold"}) {
            InputStream in = getClass().getResourceAsStream("fonts/Urbanist-" + w + ".ttf");
            if (in != null) Font.loadFont(in, 13);
        }
    }
}
