package com.cvagrofarmsstore.controllers;

import com.cvagrofarmsstore.db.ExcelDatabaseManager;
import com.cvagrofarmsstore.model.Product;
import com.cvagrofarmsstore.model.SaleRecord;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;

public class SalesController implements Initializable {

    // ── Order form ────────────────────────────────────────────────────────────
    @FXML private ComboBox<Product> cbProduct;
    @FXML private Label             lblAvailableStock;
    @FXML private Label             lblUnitPrice;
    @FXML private Spinner<Integer>  spnQuantity;
    @FXML private Label             lblLineTotal;
    @FXML private Label             lblOrderTotal;
    @FXML private Label             lblSaleStatus;

    // ── Order items cart table ────────────────────────────────────────────────
    @FXML private TableView<OrderItem>              tblOrderItems;
    @FXML private TableColumn<OrderItem, String>    colItemName;
    @FXML private TableColumn<OrderItem, Number>    colItemQty;
    @FXML private TableColumn<OrderItem, Number>    colItemUnit;
    @FXML private TableColumn<OrderItem, Number>    colItemTotal;
    @FXML private TableColumn<OrderItem, Void>      colItemRemove;

    // ── Transactions log table ────────────────────────────────────────────────
    @FXML private TableView<SaleRecord>          tblSalesLog;
    @FXML private TableColumn<SaleRecord,String> colTxId;
    @FXML private TableColumn<SaleRecord,String> colTxTime;
    @FXML private TableColumn<SaleRecord,String> colTxProdId;
    @FXML private TableColumn<SaleRecord,Number> colTxQty;
    @FXML private TableColumn<SaleRecord,Double> colTxUnit;
    @FXML private TableColumn<SaleRecord,Double> colTxTotal;
    @FXML private Label                          lblTxCount;

    private final ObservableList<Product>    productList = FXCollections.observableArrayList();
    private final ObservableList<SaleRecord> salesList   = FXCollections.observableArrayList();
    private final ObservableList<OrderItem>  orderItems  = FXCollections.observableArrayList();

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    // ── Inner model for cart rows ─────────────────────────────────────────────

    public static class OrderItem {
        private final Product product;
        private final int     qty;
        private final double  lineTotal;

        OrderItem(Product product, int qty) {
            this.product   = product;
            this.qty       = qty;
            this.lineTotal = product.getPrice() * qty;
        }

        public Product getProduct()   { return product; }
        public String  getName()      { return product.getName(); }
        public int     getQty()       { return qty; }
        public double  getUnitPrice() { return product.getPrice(); }
        public double  getLineTotal() { return lineTotal; }
    }

    // ── Initialization ────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        spnQuantity.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99999, 1));
        setupOrderItemsColumns();
        setupSalesColumns();
        tblOrderItems.setItems(orderItems);
        tblSalesLog.setItems(salesList);
        loadProductsAsync();
        loadSalesLogAsync();
    }

    // ── Order items table columns ─────────────────────────────────────────────

    private void setupOrderItemsColumns() {
        colItemName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        colItemQty.setCellValueFactory(cd  -> new ReadOnlyObjectWrapper<>((Number) cd.getValue().getQty()));
        colItemUnit.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>((Number) cd.getValue().getUnitPrice()));
        colItemUnit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("₱ %.2f", v.doubleValue()));
            }
        });
        colItemTotal.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>((Number) cd.getValue().getLineTotal()));
        colItemTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("₱ %.2f", v.doubleValue()));
            }
        });

        // Remove button column
        colItemRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.getStyleClass().add("btn-danger");
                btn.setOnAction(e -> {
                    OrderItem item = getTableView().getItems().get(getIndex());
                    orderItems.remove(item);
                    refreshOrderTotal();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    // ── Sales log table columns ───────────────────────────────────────────────

    private void setupSalesColumns() {
        colTxId.setCellValueFactory(new PropertyValueFactory<>("transactionId"));
        colTxTime.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colTxProdId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colTxQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));

        colTxUnit.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colTxUnit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("₱ %.2f", v));
            }
        });

        colTxTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        colTxTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("₱ %.2f", v));
            }
        });
    }

    // ── Product picker ────────────────────────────────────────────────────────

    @FXML
    private void onProductSelected() {
        Product p = cbProduct.getValue();
        if (p == null) return;
        lblAvailableStock.setText(String.valueOf(p.getCurrentStock()));
        lblAvailableStock.getStyleClass().setAll(p.isLowStock() ? "status-warn" : "status-ok");
        lblUnitPrice.setText(String.format("₱ %.2f", p.getPrice()));
        recalcLineTotal();
    }

    @FXML
    private void recalcLineTotal() {
        Product p = cbProduct.getValue();
        if (p == null) return;
        double line = p.getPrice() * spnQuantity.getValue();
        lblLineTotal.setText(String.format("₱ %.2f", line));
    }

    // ── Add item to cart ──────────────────────────────────────────────────────

    @FXML
    private void addToOrder() {
        Product p = cbProduct.getValue();
        if (p == null) {
            showAlert(Alert.AlertType.ERROR, "No Product", "Please select a product.");
            return;
        }

        int qty = spnQuantity.getValue();

        // Find existing cart line for this product
        OrderItem existing = orderItems.stream()
                .filter(i -> i.getProduct().getId().equals(p.getId()))
                .findFirst().orElse(null);

        int alreadyInCart = existing == null ? 0 : existing.getQty();

        if (alreadyInCart + qty > p.getCurrentStock()) {
            showAlert(Alert.AlertType.ERROR, "Insufficient Stock",
                    "Total ordered (" + (alreadyInCart + qty) + ") exceeds available stock ("
                    + p.getCurrentStock() + ") for \"" + p.getName() + "\".");
            return;
        }

        if (existing != null) {
            // Merge: replace the existing line with updated qty
            int idx = orderItems.indexOf(existing);
            orderItems.set(idx, new OrderItem(p, alreadyInCart + qty));
        } else {
            orderItems.add(new OrderItem(p, qty));
        }
        refreshOrderTotal();

        // Reset picker for next item
        cbProduct.setValue(null);
        spnQuantity.getValueFactory().setValue(1);
        lblAvailableStock.setText("—");
        lblUnitPrice.setText("—");
        lblLineTotal.setText("₱ 0.00");
    }

    private void refreshOrderTotal() {
        double total = orderItems.stream().mapToDouble(OrderItem::getLineTotal).sum();
        lblOrderTotal.setText(String.format("₱ %.2f", total));
    }

    // ── Complete order ────────────────────────────────────────────────────────

    @FXML
    private void completeOrder() {
        if (orderItems.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Empty Order",
                    "Add at least one product to the order before completing.");
            return;
        }

        String txId      = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String timestamp = LocalDateTime.now().format(TS_FMT);

        // Snapshot items so the lambda captures a stable list
        List<OrderItem> snapshot = new ArrayList<>(orderItems);

        ExcelDatabaseManager.getInstance().writeAsync(wb -> {
            for (OrderItem item : snapshot) {
                SaleRecord r = new SaleRecord(txId, timestamp,
                        item.getProduct().getId(), item.getQty(),
                        item.getUnitPrice(), item.getLineTotal());
                appendSaleRow(wb, r);
                int newStock = item.getProduct().getCurrentStock() - item.getQty();
                updateProductStock(wb, item.getProduct().getId(), newStock);
            }
        }).thenAcceptAsync(v -> Platform.runLater(() -> {
            String timestamp2 = LocalDateTime.now().format(TS_FMT);
            for (OrderItem item : snapshot) {
                int newStock = item.getProduct().getCurrentStock() - item.getQty();
                item.getProduct().setCurrentStock(newStock);
                SaleRecord r = new SaleRecord(txId, timestamp,
                        item.getProduct().getId(), item.getQty(),
                        item.getUnitPrice(), item.getLineTotal());
                salesList.addFirst(r);
            }
            lblTxCount.setText(salesList.size() + " records");
            lblSaleStatus.setText("✔  Order recorded: " + txId
                    + "  (" + snapshot.size() + " item" + (snapshot.size() != 1 ? "s" : "") + ")");
            lblSaleStatus.getStyleClass().setAll("status-ok");
            clearOrder();
        }));
    }

    @FXML
    private void clearOrder() {
        orderItems.clear();
        cbProduct.setValue(null);
        spnQuantity.getValueFactory().setValue(1);
        lblAvailableStock.setText("—");
        lblUnitPrice.setText("—");
        lblLineTotal.setText("₱ 0.00");
        lblOrderTotal.setText("₱ 0.00");
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadProductsAsync() {
        ExcelDatabaseManager.getInstance().readAsync(this::readProducts)
                .thenAcceptAsync(products -> Platform.runLater(() -> {
                    productList.setAll(products);
                    cbProduct.setItems(productList);
                }));
    }

    private void loadSalesLogAsync() {
        ExcelDatabaseManager.getInstance().readAsync(this::readSalesLog)
                .thenAcceptAsync(records -> Platform.runLater(() -> {
                    salesList.setAll(records);
                    lblTxCount.setText(records.size() + " records");
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
                    cellDbl(row, 3), (int) cellDbl(row, 4), (int) cellDbl(row, 5)));
        }
        return list;
    }

    private List<SaleRecord> readSalesLog(Workbook wb) {
        List<SaleRecord> list = new ArrayList<>();
        Sheet sheet = wb.getSheet("SalesLog");
        if (sheet == null) return list;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            list.add(new SaleRecord(
                    cellStr(row, 0), cellStr(row, 1), cellStr(row, 2),
                    (int) cellDbl(row, 3), cellDbl(row, 4), cellDbl(row, 5)));
        }
        java.util.Collections.reverse(list);
        return list;
    }

    // ── Excel write helpers ───────────────────────────────────────────────────

    private void appendSaleRow(Workbook wb, SaleRecord r) {
        Sheet sheet = wb.getSheet("SalesLog");
        if (sheet == null) return;
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        row.createCell(0).setCellValue(r.getTransactionId());
        row.createCell(1).setCellValue(r.getTimestamp());
        row.createCell(2).setCellValue(r.getProductId());
        row.createCell(3).setCellValue(r.getQuantitySold());
        row.createCell(4).setCellValue(r.getUnitPrice());
        row.createCell(5).setCellValue(r.getTotalAmount());
    }

    private void updateProductStock(Workbook wb, String productId, int newStock) {
        Sheet sheet = wb.getSheet("Products");
        if (sheet == null) return;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (productId.equals(cellStr(row, 0))) {
                if (row.getCell(4) != null) row.getCell(4).setCellValue(newStock);
                else row.createCell(4).setCellValue(newStock);
                break;
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

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
