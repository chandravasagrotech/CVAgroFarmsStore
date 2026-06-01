package com.cvagrofarmsstore.controllers;

import com.cvagrofarmsstore.db.AppPreferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainDashboardController implements Initializable {

    @FXML private StackPane contentArea;
    @FXML private Label     lblDbStatus;
    @FXML private Label     lblVersion;
    @FXML private Button    btnNavDashboard;
    @FXML private Button    btnNavInventory;
    @FXML private Button    btnNavSales;
    @FXML private HBox      titleBar;

    private Button activeNavButton;
    private double dragOffsetX, dragOffsetY;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        showDashboard();
        updateDbStatus();
    }

    // Hook the native OS close button after the scene is attached
    public void hookCloseRequest(Stage stage) {
        stage.setOnCloseRequest(e -> {
            e.consume(); // prevent default close
            confirmAndClose();
        });
    }

    @FXML
    private void showDashboard() {
        setActiveNav(btnNavDashboard);
        loadView("/fxml/dashboard_view.fxml");
    }

    @FXML
    public void showInventory() {
        setActiveNav(btnNavInventory);
        loadView("/fxml/inventory_view.fxml");
    }

    @FXML
    public void showSales() {
        setActiveNav(btnNavSales);
        loadView("/fxml/sales_view.fxml");
    }

    // ── Window controls ───────────────────────────────────────────────────────

    @FXML
    private void minimizeWindow() {
        getStage().setIconified(true);
    }

    @FXML
    private void maximizeWindow() {
        Stage stage = getStage();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void closeWindow() {
        confirmAndClose();
    }

    private void confirmAndClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Exit CVAgroFarmsStore");
        alert.setHeaderText("Are you sure you want to exit?");
        alert.setContentText("A backup of your database will be saved automatically before closing.");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                getStage().close();
            }
        });
    }

    @FXML
    private void onTitleBarPressed(javafx.scene.input.MouseEvent e) {
        dragOffsetX = e.getScreenX() - getStage().getX();
        dragOffsetY = e.getScreenY() - getStage().getY();
    }

    @FXML
    private void onTitleBarDragged(javafx.scene.input.MouseEvent e) {
        Stage stage = getStage();
        if (!stage.isMaximized()) {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        }
    }

    private Stage getStage() {
        return (Stage) contentArea.getScene().getWindow();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void loadView(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) throw new IOException("FXML not found: " + fxmlPath);
            Node view = FXMLLoader.load(resource);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Label err = new Label("⚠  Failed to load view: " + fxmlPath + "\n" + cause.getMessage());
            err.setStyle("-fx-text-fill: #E57373;");
            err.setWrapText(true);
            contentArea.getChildren().setAll(err);
        }
    }

    private void setActiveNav(Button selected) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
        }
        selected.getStyleClass().add("nav-button-active");
        activeNavButton = selected;
    }

    private void updateDbStatus() {
        // Poll DB file presence on a virtual thread, then update label on FX thread
        Thread.ofVirtual().start(() -> {
            boolean exists = AppPreferences.getDbPath().toFile().exists();
            Platform.runLater(() -> {
                if (exists) {
                    lblDbStatus.setText("● Database: Connected");
                    lblDbStatus.getStyleClass().setAll("status-ok");
                } else {
                    lblDbStatus.setText("● Database: Creating…");
                    lblDbStatus.getStyleClass().setAll("status-warn");
                }
            });
        });
    }
}
