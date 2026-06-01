package com.cvagrofarmsstore.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class SaleRecord {

    private final StringProperty  transactionId = new SimpleStringProperty();
    private final StringProperty  timestamp     = new SimpleStringProperty();
    private final StringProperty  productId     = new SimpleStringProperty();
    private final IntegerProperty quantitySold  = new SimpleIntegerProperty();
    private final DoubleProperty  unitPrice     = new SimpleDoubleProperty();
    private final DoubleProperty  totalAmount   = new SimpleDoubleProperty();

    public SaleRecord() {}

    public SaleRecord(String transactionId, String timestamp, String productId,
                      int quantitySold, double unitPrice, double totalAmount) {
        this.transactionId.set(transactionId);
        this.timestamp.set(timestamp);
        this.productId.set(productId);
        this.quantitySold.set(quantitySold);
        this.unitPrice.set(unitPrice);
        this.totalAmount.set(totalAmount);
    }

    public StringProperty  transactionIdProperty() { return transactionId; }
    public StringProperty  timestampProperty()     { return timestamp; }
    public StringProperty  productIdProperty()     { return productId; }
    public IntegerProperty quantitySoldProperty()  { return quantitySold; }
    public DoubleProperty  unitPriceProperty()     { return unitPrice; }
    public DoubleProperty  totalAmountProperty()   { return totalAmount; }

    public String  getTransactionId() { return transactionId.get(); }
    public String  getTimestamp()     { return timestamp.get(); }
    public String  getProductId()     { return productId.get(); }
    public int     getQuantitySold()  { return quantitySold.get(); }
    public double  getUnitPrice()     { return unitPrice.get(); }
    public double  getTotalAmount()   { return totalAmount.get(); }
}
