package com.cvagrofarmsstore.controllers;

import com.cvagrofarmsstore.db.AppPreferences;
import com.cvagrofarmsstore.db.ExcelDatabaseManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class SetupWizardController {

    @FXML private Label             lblStatus;
    @FXML private ProgressIndicator progressIndicator;

    // ── Option A — Create fresh ───────────────────────────────────────────────

    @FXML
    private void onCreateFresh() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder for your new CVAgroFarms database");

        File chosen = chooser.showDialog(getWindow());
        if (chosen == null) return; // user cancelled

        Path dir = chosen.toPath();
        setWorking("Creating new database in " + dir + " …");

        CompletableFuture.runAsync(() -> AppPreferences.saveDbDirectory(dir))
                .thenComposeAsync(v -> ExcelDatabaseManager.getInstance().initializeAndGetFuture())
                .thenRunAsync(() -> Platform.runLater(this::launchDashboard))
                .exceptionallyAsync(ex -> {
                    Platform.runLater(() -> {
                        clearWorking();
                        showError("Setup Failed",
                                "Could not create the database.\n" + ex.getCause().getMessage());
                    });
                    return null;
                });
    }

    // ── Option B — Import existing ────────────────────────────────────────────

    @FXML
    private void onImportExisting() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select your existing CVAgroFarms database file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));

        File chosen = chooser.showOpenDialog(getWindow());
        if (chosen == null) return; // user cancelled

        Path selectedFile = chosen.toPath();
        setWorking("Validating database structure…");

        CompletableFuture.supplyAsync(() ->
                ExcelDatabaseManager.validateImportedFile(selectedFile)
        ).thenAcceptAsync(error -> Platform.runLater(() -> {
            if (error != null) {
                clearWorking();
                showError("Invalid Database File Structure",
                        "The selected file is not a valid CVAgroFarms database.\n\n"
                        + "Detail: " + error + "\n\n"
                        + "Please select a genuine CVAgroFarms database backup.");
                return;
            }

            // Validation passed — copy file into a user-chosen directory
            // (or keep it in place if the user wants to use it directly)
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Choose the folder where this database will be stored");
            // Pre-select the file's own parent as a sensible default
            dirChooser.setInitialDirectory(selectedFile.getParent().toFile());

            File targetDir = dirChooser.showDialog(getWindow());
            if (targetDir == null) {
                clearWorking();
                return;
            }

            Path dir = targetDir.toPath();
            setWorking("Linking database…");

            CompletableFuture.runAsync(() -> {
                try {
                    Path dest = dir.resolve(AppPreferences.DB_FILE_NAME);
                    if (!selectedFile.toAbsolutePath().equals(dest.toAbsolutePath())) {
                        Files.createDirectories(dir);
                        Files.copy(selectedFile, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    AppPreferences.saveDbDirectory(dir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).thenComposeAsync(v -> ExcelDatabaseManager.getInstance().initializeAndGetFuture())
              .thenRunAsync(() -> Platform.runLater(this::launchDashboard))
              .exceptionallyAsync(ex -> {
                  Platform.runLater(() -> {
                      clearWorking();
                      showError("Import Failed",
                              "Could not link the database file.\n" + ex.getCause().getMessage());
                  });
                  return null;
              });
        }));
    }

    // ── Launch dashboard ──────────────────────────────────────────────────────

    private void launchDashboard() {
        try {
            URL fxml = getClass().getResource("/fxml/main_dashboard.fxml");
            if (fxml == null) throw new IOException("main_dashboard.fxml not found");

            Stage stage = (Stage) lblStatus.getScene().getWindow();
            Scene scene = new Scene(FXMLLoader.load(fxml), 1100, 720);
            stage.setTitle("CVAgroFarmsStore — Agribusiness Manager");
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            clearWorking();
            showError("Launch Failed", "Could not open the main dashboard.\n" + e.getMessage());
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setWorking(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #FFCC80;");
        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
    }

    private void clearWorking() {
        lblStatus.setText("");
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);
    }

    private Window getWindow() {
        return lblStatus.getScene() != null ? lblStatus.getScene().getWindow() : null;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
