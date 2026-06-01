package com.cvagrofarmsstore.controllers;

import com.cvagrofarmsstore.db.ExcelDatabaseManager;
import com.cvagrofarmsstore.model.Category;
import com.cvagrofarmsstore.model.Product;
import com.cvagrofarmsstore.model.StockHistoryEntry;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InventoryController implements Initializable {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    // ── TableView ─────────────────────────────────────────────────────────────
    @FXML private TableView<Product>          tblProducts;
    @FXML private TableColumn<Product,String> colProdId;
    @FXML private TableColumn<Product,String> colCatId;
    @FXML private TableColumn<Product,String> colProdName;
    @FXML private TableColumn<Product,Number> colPrice;
    @FXML private TableColumn<Product,Number> colStock;
    @FXML private TableColumn<Product,Number> colMinAlert;
    @FXML private TableColumn<Product,String> colLastUpdated;   // prev update
    @FXML private TableColumn<Product,String> colLatestUpdate;  // latest update
    @FXML private TableColumn<Product,String> colStockStatus;

    // ── Toolbar ───────────────────────────────────────────────────────────────
    @FXML private TextField  txtSearch;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Label      lblProductCount;
    @FXML private Label      lblLowStockWarning;

    // ── Update-stock panel ────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox updateStockPanel;
    @FXML private ComboBox<Product>        cbUpdateProduct;
    @FXML private Label                    lblCurrentStock;
    @FXML private Spinner<Integer>         spnNewStockQty;
    @FXML private Label                    lblNewStockTotal;

    // ── Add-product panel ─────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox addProductPanel;
    @FXML private TextField        txtNewProdId;
    @FXML private ComboBox<String> cbNewCatId;
    @FXML private TextField        txtNewProdName;
    @FXML private TextField        txtNewPrice;
    @FXML private Spinner<Integer> spnNewStock;
    @FXML private Spinner<Integer> spnMinAlert;

    // ── Add-category panel ────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox addCategoryPanel;
    @FXML private TextField txtNewCatId;
    @FXML private TextField txtNewCatName;
    @FXML private TextField txtNewCatDesc;

    private final ObservableList<Product> productList = FXCollections.observableArrayList();
    private FilteredList<Product> filteredProducts;

    // productId -> last two history entries [older, newer]
    private final Map<String, List<StockHistoryEntry>> historyMap = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        spnNewStock.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99999, 0));
        spnMinAlert.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99999, 5));
        spnNewStockQty.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99999, 1));

        // Recalc new total whenever qty spinner changes
        spnNewStockQty.valueProperty().addListener((obs, o, n) -> recalcNewTotal());

        setupColumns();
        setupRowFactory();
        setupSearch();
        loadDataAsync();
    }

    // ── Column setup ──────────────────────────────────────────────────────────

    private void setupColumns() {
        colProdId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCatId.setCellValueFactory(new PropertyValueFactory<>("categoryId"));
        colProdName.setCellValueFactory(new PropertyValueFactory<>("name"));

        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("₱ %.2f", v.doubleValue()));
            }
        });

        colStock.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colMinAlert.setCellValueFactory(new PropertyValueFactory<>("minAlertLevel"));

        // Prev update — second-to-last history entry
        colLastUpdated.setCellValueFactory(cd -> {
            List<StockHistoryEntry> h = historyMap.get(cd.getValue().getId());
            if (h == null || h.size() < 2) return new SimpleStringProperty("—");
            return new SimpleStringProperty(h.get(h.size() - 2).toAuditLabel());
        });

        // Latest update — most recent history entry
        colLatestUpdate.setCellValueFactory(cd -> {
            List<StockHistoryEntry> h = historyMap.get(cd.getValue().getId());
            if (h == null || h.isEmpty()) return new SimpleStringProperty("—");
            return new SimpleStringProperty(h.get(h.size() - 1).toAuditLabel());
        });

        colStockStatus.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().isLowStock() ? "⚠ LOW STOCK" : "✔ OK"));
        colStockStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.startsWith("⚠")
                        ? "-fx-text-fill: #FFB74D; -fx-font-weight: bold;"
                        : "-fx-text-fill: #4CAF50;");
            }
        });
    }

    private void setupRowFactory() {
        tblProducts.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().remove("row-low-stock");
                if (!empty && p != null && p.isLowStock())
                    getStyleClass().add("row-low-stock");
            }
        });
    }

    // ── Search + date filter ──────────────────────────────────────────────────

    private void setupSearch() {
        filteredProducts = new FilteredList<>(productList, p -> true);
        tblProducts.setItems(filteredProducts);
    }

    @FXML private void onSearch()     { applyFilter(); }
    @FXML private void onDateFilter() { applyFilter(); }

    @FXML
    private void clearDateFilter() {
        dpFrom.setValue(null);
        dpTo.setValue(null);
        applyFilter();
    }

    private void applyFilter() {
        String query   = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();

        filteredProducts.setPredicate(p -> {
            boolean matchesText = query.isEmpty()
                    || p.getId().toLowerCase(Locale.ROOT).contains(query)
                    || p.getName().toLowerCase(Locale.ROOT).contains(query)
                    || p.getCategoryId().toLowerCase(Locale.ROOT).contains(query);

            boolean matchesDate = true;
            if (from != null || to != null) {
                // Filter by latest history entry date
                List<StockHistoryEntry> h = historyMap.get(p.getId());
                if (h != null && !h.isEmpty()) {
                    try {
                        LocalDate latest = LocalDate.parse(
                                h.get(h.size() - 1).getDate(), DATE_FMT);
                        if (from != null && latest.isBefore(from)) matchesDate = false;
                        if (to   != null && latest.isAfter(to))    matchesDate = false;
                    } catch (Exception e) {
                        matchesDate = false;
                    }
                } else {
                    matchesDate = false;
                }
            }
            return matchesText && matchesDate;
        });
        updateCountLabel();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadDataAsync() {
        ExcelDatabaseManager.getInstance().readAsync(wb -> {
            List<Product> products = readProducts(wb);
            Map<String, List<StockHistoryEntry>> history = readStockHistory(wb);
            return new Object[]{ products, history };
        }).thenAcceptAsync(result -> Platform.runLater(() -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<Product> products = (List<Product>) result[0];
            @SuppressWarnings("unchecked")
            Map<String, List<StockHistoryEntry>> history =
                    (Map<String, List<StockHistoryEntry>>) result[1];

            historyMap.clear();
            historyMap.putAll(history);
            productList.setAll(products);
            cbUpdateProduct.setItems(productList);
            updateCountLabel();
            boolean hasLow = products.stream().anyMatch(Product::isLowStock);
            lblLowStockWarning.setVisible(hasLow);
            lblLowStockWarning.setManaged(hasLow);
        }));
    }

    private void loadCategoriesAsync() {
        ExcelDatabaseManager.getInstance().readAsync(this::readCategories)
                .thenAcceptAsync(cats -> Platform.runLater(() -> {
                    // Store full Category objects so we can show "ID  Name" in the dropdown
                    ObservableList<Category> catList = FXCollections.observableArrayList(cats);
                    cbNewCatId.setItems(FXCollections.observableArrayList(
                            cats.stream()
                                .map(c -> c.getId() + "  " + c.getName())
                                .toList()));
                }));
    }

    private List<Product> readProducts(Workbook wb) {
        List<Product> list = new ArrayList<>();
        Sheet sheet = wb.getSheet("Products");
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            list.add(new Product(
                    cellStr(row, 0), cellStr(row, 1), cellStr(row, 2),
                    cellDbl(row, 3), (int) cellDbl(row, 4), (int) cellDbl(row, 5),
                    cellStr(row, 6)));
        }
        return list;
    }

    private Map<String, List<StockHistoryEntry>> readStockHistory(Workbook wb) {
        Map<String, List<StockHistoryEntry>> map = new LinkedHashMap<>();
        Sheet sheet = wb.getSheet("StockHistory");
        if (sheet == null) return map;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String pid = cellStr(row, 0);
            if (pid.isEmpty()) continue;
            StockHistoryEntry entry = new StockHistoryEntry(
                    pid,
                    cellStr(row, 1),
                    (int) cellDbl(row, 2),
                    (int) cellDbl(row, 3),
                    (int) cellDbl(row, 4));
            map.computeIfAbsent(pid, k -> new ArrayList<>()).add(entry);
        }
        return map;
    }

    private List<Category> readCategories(Workbook wb) {
        List<Category> list = new ArrayList<>();
        Sheet sheet = wb.getSheet("Categories");
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            list.add(new Category(cellStr(row, 0), cellStr(row, 1), cellStr(row, 2)));
        }
        return list;
    }

    // ── Update Stock panel ────────────────────────────────────────────────────

    @FXML
    private void openUpdateStockPanel() {
        cbUpdateProduct.setValue(null);
        lblCurrentStock.setText("—");
        lblNewStockTotal.setText("—");
        spnNewStockQty.getValueFactory().setValue(1);
        updateStockPanel.setVisible(true);
        updateStockPanel.setManaged(true);
    }

    @FXML
    private void closeUpdateStockPanel() {
        updateStockPanel.setVisible(false);
        updateStockPanel.setManaged(false);
    }

    @FXML
    private void onUpdateProductSelected() {
        Product p = cbUpdateProduct.getValue();
        if (p == null) return;
        lblCurrentStock.setText(String.valueOf(p.getCurrentStock()));
        recalcNewTotal();
    }

    private void recalcNewTotal() {
        Product p = cbUpdateProduct.getValue();
        if (p == null) return;
        int newTotal = p.getCurrentStock() + spnNewStockQty.getValue();
        lblNewStockTotal.setText(String.valueOf(newTotal));
    }

    @FXML
    private void saveStockUpdate() {
        Product p = cbUpdateProduct.getValue();
        if (p == null) {
            showAlert(Alert.AlertType.ERROR, "No Product", "Please select a product to update.");
            return;
        }

        int qtyAdded    = spnNewStockQty.getValue();
        int stockBefore = p.getCurrentStock();
        int stockAfter  = stockBefore + qtyAdded;
        String today    = LocalDate.now().format(DATE_FMT);

        ExcelDatabaseManager.getInstance().writeAsync(wb -> {
            // Update current stock + last updated in Products sheet
            Sheet products = wb.getSheet("Products");
            if (products != null) {
                for (int i = 1; i <= products.getLastRowNum(); i++) {
                    Row row = products.getRow(i);
                    if (row == null) continue;
                    if (p.getId().equals(cellStr(row, 0))) {
                        getOrCreate(row, 4).setCellValue(stockAfter);
                        getOrCreate(row, 6).setCellValue(today);
                        break;
                    }
                }
            }
            // Append to StockHistory sheet
            Sheet history = wb.getSheet("StockHistory");
            if (history != null) {
                Row row = history.createRow(history.getLastRowNum() + 1);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(today);
                row.createCell(2).setCellValue(stockBefore);
                row.createCell(3).setCellValue(qtyAdded);
                row.createCell(4).setCellValue(stockAfter);
            }
        }).thenAcceptAsync(v -> Platform.runLater(() -> {
            // Update in-memory product
            p.setCurrentStock(stockAfter);
            p.setLastUpdated(today);

            // Update in-memory history map
            StockHistoryEntry entry = new StockHistoryEntry(
                    p.getId(), today, stockBefore, qtyAdded, stockAfter);
            historyMap.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(entry);

            tblProducts.refresh();
            updateCountLabel();
            boolean hasLow = productList.stream().anyMatch(Product::isLowStock);
            lblLowStockWarning.setVisible(hasLow);
            lblLowStockWarning.setManaged(hasLow);
            closeUpdateStockPanel();
            showAlert(Alert.AlertType.INFORMATION, "Stock Updated",
                    "\"" + p.getName() + "\"\n"
                    + "Before: " + stockBefore + "  +  Added: " + qtyAdded
                    + "  =  New Stock: " + stockAfter);
        }));
    }

    // ── Add Category panel ────────────────────────────────────────────────────

    @FXML
    private void openAddCategoryPanel() {
        txtNewCatId.setText("CAT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        addCategoryPanel.setVisible(true);
        addCategoryPanel.setManaged(true);
    }

    @FXML
    private void closeAddCategoryPanel() {
        addCategoryPanel.setVisible(false);
        addCategoryPanel.setManaged(false);
        txtNewCatId.clear();
        txtNewCatName.clear();
        txtNewCatDesc.clear();
    }

    @FXML
    private void saveCategory() {
        String id   = txtNewCatId.getText().trim();
        String name = txtNewCatName.getText().trim();
        String desc = txtNewCatDesc.getText().trim();

        if (id.isEmpty() || name.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Category ID and Name are required.");
            return;
        }

        ExcelDatabaseManager.getInstance().writeAsync(wb -> {
            Sheet sheet = wb.getSheet("Categories");
            if (sheet == null) return;
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue(id);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(desc);
        }).thenAcceptAsync(v -> Platform.runLater(() -> {
            cbNewCatId.getItems().add(id + "  " + name);
            cbNewCatId.setValue(id + "  " + name);
            closeAddCategoryPanel();
            showAlert(Alert.AlertType.INFORMATION, "Category Saved", "\"" + name + "\" has been added.");
        }));
    }

    // ── Add Product panel ─────────────────────────────────────────────────────

    @FXML
    private void openAddProductPanel() {
        txtNewProdId.setText("PRD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        loadCategoriesAsync();
        addProductPanel.setVisible(true);
        addProductPanel.setManaged(true);
    }

    @FXML
    private void closeAddProductPanel() {
        addProductPanel.setVisible(false);
        addProductPanel.setManaged(false);
        clearForm();
    }

    @FXML
    private void saveProduct() {
        String id       = txtNewProdId.getText().trim();
        String catRaw   = cbNewCatId.getValue();
        // ComboBox shows "CAT-001  Fertilizers" — extract just the ID before the spaces
        String catId    = catRaw != null ? catRaw.split("  ")[0].trim() : null;
        String name     = txtNewProdName.getText().trim();
        String priceStr = txtNewPrice.getText().trim();

        if (id.isEmpty() || catId == null || catId.isEmpty() || name.isEmpty() || priceStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "All fields are required.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Price", "Price must be a valid positive number.");
            return;
        }

        if (productList.stream().anyMatch(p -> p.getId().equalsIgnoreCase(id))) {
            showAlert(Alert.AlertType.ERROR, "Duplicate ID", "A product with ID \"" + id + "\" already exists.");
            return;
        }

        int stock    = spnNewStock.getValue();
        int minAlert = spnMinAlert.getValue();
        String today = LocalDate.now().format(DATE_FMT);
        Product newProduct = new Product(id, catId, name, price, stock, minAlert, today);

        ExcelDatabaseManager.getInstance().writeAsync(wb -> {
            Sheet sheet = wb.getSheet("Products");
            if (sheet == null) return;
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue(id);
            row.createCell(1).setCellValue(catId);
            row.createCell(2).setCellValue(name);
            row.createCell(3).setCellValue(price);
            row.createCell(4).setCellValue(stock);
            row.createCell(5).setCellValue(minAlert);
            row.createCell(6).setCellValue(today);
            // Record initial stock as first history entry
            Sheet history = wb.getSheet("StockHistory");
            if (history != null) {
                Row hr = history.createRow(history.getLastRowNum() + 1);
                hr.createCell(0).setCellValue(id);
                hr.createCell(1).setCellValue(today);
                hr.createCell(2).setCellValue(0);
                hr.createCell(3).setCellValue(stock);
                hr.createCell(4).setCellValue(stock);
            }
        }).thenAcceptAsync(v -> Platform.runLater(() -> {
            productList.add(newProduct);
            cbUpdateProduct.setItems(productList);
            // Add initial history entry in memory
            StockHistoryEntry entry = new StockHistoryEntry(id, today, 0, stock, stock);
            historyMap.computeIfAbsent(id, k -> new ArrayList<>()).add(entry);
            tblProducts.refresh();
            updateCountLabel();
            closeAddProductPanel();
            showAlert(Alert.AlertType.INFORMATION, "Product Saved",
                    "\"" + name + "\" has been added to inventory.");
        }));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void updateCountLabel() {
        int total = filteredProducts.size();
        long low  = productList.stream().filter(Product::isLowStock).count();
        lblProductCount.setText(total + " product" + (total != 1 ? "s" : "")
                + (low > 0 ? "  |  ⚠ " + low + " low stock" : ""));
    }

    private void clearForm() {
        txtNewProdId.clear();
        cbNewCatId.setValue(null);
        txtNewProdName.clear();
        txtNewPrice.clear();
        spnNewStock.getValueFactory().setValue(0);
        spnMinAlert.getValueFactory().setValue(5);
    }

    private static org.apache.poi.ss.usermodel.Cell getOrCreate(Row row, int col) {
        org.apache.poi.ss.usermodel.Cell c = row.getCell(col);
        return c != null ? c : row.createCell(col);
    }

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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
