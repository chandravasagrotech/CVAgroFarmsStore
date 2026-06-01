package com.cvagrofarmsstore;

import com.cvagrofarmsstore.db.AppPreferences;
import com.cvagrofarmsstore.db.ExcelDatabaseManager;
import com.cvagrofarmsstore.controllers.MainDashboardController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("CVAgroFarmsStore — Agribusiness Manager");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        if (!AppPreferences.isConfigured()) {
            // ── First launch: show the setup wizard ───────────────────────────
            URL wizardFxml = getClass().getResource("/fxml/setup_wizard.fxml");
            if (wizardFxml == null) throw new IOException("setup_wizard.fxml not found on classpath");

            Scene wizardScene = new Scene(FXMLLoader.load(wizardFxml));
            stage.setTitle("CVAgroFarmsStore — Initial Setup");
            stage.setResizable(true);
            stage.setScene(wizardScene);
            stage.show();
        } else {
            // ── Returning user: initialize DB and go straight to dashboard ────
            ExcelDatabaseManager.getInstance().initializeAndGetFuture()
                    .thenRunAsync(() -> javafx.application.Platform.runLater(() -> {
                        try {
                            launchDashboard(stage);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
        }
    }

    public static void launchDashboard(Stage stage) throws IOException {
        URL fxml = App.class.getResource("/fxml/main_dashboard.fxml");
        if (fxml == null) throw new IOException("main_dashboard.fxml not found on classpath");

        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), 1100, 720);
        stage.setTitle("CVAgroFarmsStore — Agribusiness Manager");
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        // Hook native OS close button confirmation
        MainDashboardController controller = loader.getController();
        controller.hookCloseRequest(stage);
    }

    @Override
    public void stop() {
        ExcelDatabaseManager.getInstance().backupAndShutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
