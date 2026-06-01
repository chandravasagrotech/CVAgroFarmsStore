package com.cvagrofarmsstore.controllers;

import com.cvagrofarmsstore.db.ExcelDatabaseManager;
import com.cvagrofarmsstore.model.Product;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

public class DashboardController implements Initializable {

    @FXML private Label lblTotalProducts;
    @FXML private Label lblCategoryCount;
    @FXML private Label lblLowStock;
    @FXML private Label lblLowStockNames;
    @FXML private Label lblTodayRevenue;
    @FXML private Label lblTodayTxCount;
    @FXML private Label lblTotalRevenue;
    @FXML private Label lblTotalTxCount;

    @FXML private TableView<Product>              tblLowStock;
    @FXML private TableColumn<Product, String>    colLsProdId;
    @FXML private TableColumn<Product, String>    colLsName;
    @FXML private TableColumn<Product, Number>    colLsStock;
    @FXML private TableColumn<Product, Number>    colLsMinAlert;
    @FXML private TableColumn<Product, String>    colLsStatus;

    private final ObservableList<Product> lowStockList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupLowStockTable();
        tblLowStock.setItems(lowStockList);
        loadStatsAsync();
    }

    private void setupLowStockTable() {
        colLsProdId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colLsName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colLsStock.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colLsMinAlert.setCellValueFactory(new PropertyValueFactory<>("minAlertLevel"));

        colLsStatus.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(
                        cd.getValue().getCurrentStock() == 0 ? "🔴 OUT OF STOCK" : "🟡 LOW STOCK"));
        colLsStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.startsWith("🔴")
                        ? "-fx-text-fill: #E57373; -fx-font-weight: bold;"
                        : "-fx-text-fill: #FFB74D; -fx-font-weight: bold;");
            }
        });
    }

    private void loadStatsAsync() {
        ExcelDatabaseManager.getInstance().readAsync(this::readStats)
                .thenAcceptAsync(stats -> Platform.runLater(() -> applyStats(stats)));
    }

    private Stats readStats(Workbook wb) {
        Stats s = new Stats();
        String today = LocalDate.now().toString(); // "yyyy-MM-dd"

        // ── Products ──────────────────────────────────────────────────────────
        Sheet products = wb.getSheet("Products");
        Set<String> categoryIds = new HashSet<>();
        if (products != null) {
            for (int i = 1; i <= products.getLastRowNum(); i++) {
                Row row = products.getRow(i);
                if (row == null) continue;
                String id       = cellStr(row, 0);
                String catId    = cellStr(row, 1);
                String name     = cellStr(row, 2);
                double price    = cellDbl(row, 3);
                int    stock    = (int) cellDbl(row, 4);
                int    minAlert = (int) cellDbl(row, 5);
                if (id.isEmpty()) continue;
                s.totalProducts++;
                if (!catId.isEmpty()) categoryIds.add(catId);
                Product p = new Product(id, catId, name, price, stock, minAlert);
                if (p.isLowStock()) s.lowStockProducts.add(p);
            }
        }
        s.categoryCount = categoryIds.size();

        // ── Sales log ─────────────────────────────────────────────────────────
        Sheet sales = wb.getSheet("SalesLog");
        Set<String> todayTxIds = new HashSet<>();
        Set<String> allTxIds   = new HashSet<>();
        if (sales != null) {
            for (int i = 1; i <= sales.getLastRowNum(); i++) {
                Row row = sales.getRow(i);
                if (row == null) continue;
                String txId      = cellStr(row, 0);
                String timestamp = cellStr(row, 1);
                double total     = cellDbl(row, 5);
                if (txId.isEmpty()) continue;
                allTxIds.add(txId);
                s.totalRevenue += total;
                if (timestamp.startsWith(today)) {
                    todayTxIds.add(txId);
                    s.todayRevenue += total;
                }
            }
        }
        s.totalTxCount = allTxIds.size();
        s.todayTxCount = todayTxIds.size();

        return s;
    }

    private void applyStats(Stats s) {
        lblTotalProducts.setText(String.valueOf(s.totalProducts));
        lblCategoryCount.setText(s.categoryCount + " categor" + (s.categoryCount != 1 ? "ies" : "y"));

        lblLowStock.setText(String.valueOf(s.lowStockProducts.size()));
        if (s.lowStockProducts.isEmpty()) {
            lblLowStockNames.setText("All stock levels OK");
        } else {
            String names = s.lowStockProducts.stream()
                    .limit(3)
                    .map(Product::getName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (s.lowStockProducts.size() > 3) names += "…";
            lblLowStockNames.setText(names);
        }

        lblTodayRevenue.setText(String.format("₱ %.2f", s.todayRevenue));
        lblTodayTxCount.setText(s.todayTxCount + " transaction" + (s.todayTxCount != 1 ? "s" : ""));

        lblTotalRevenue.setText(String.format("₱ %.2f", s.totalRevenue));
        lblTotalTxCount.setText(s.totalTxCount + " transaction" + (s.totalTxCount != 1 ? "s" : ""));

        lowStockList.setAll(s.lowStockProducts);
    }

    // ── Simple stats container ────────────────────────────────────────────────

    private static class Stats {
        int totalProducts  = 0;
        int categoryCount  = 0;
        double totalRevenue = 0;
        double todayRevenue = 0;
        int totalTxCount   = 0;
        int todayTxCount   = 0;
        List<Product> lowStockProducts = new ArrayList<>();
    }

    // ── Cell helpers ──────────────────────────────────────────────────────────

    private static String cellStr(Row row, int col) {
        var cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> cell.toString().trim();
        };
    }

    private static double cellDbl(Row row, int col) {
        var cell = row.getCell(col);
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> { try { yield Double.parseDouble(cell.getStringCellValue()); }
                              catch (NumberFormatException e) { yield 0; } }
            default      -> 0;
        };
    }
}
