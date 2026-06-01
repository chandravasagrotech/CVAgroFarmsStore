package com.cvagrofarmsstore.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Product {

    private final StringProperty  id             = new SimpleStringProperty();
    private final StringProperty  categoryId     = new SimpleStringProperty();
    private final StringProperty  name           = new SimpleStringProperty();
    private final DoubleProperty  price          = new SimpleDoubleProperty();
    private final IntegerProperty currentStock   = new SimpleIntegerProperty();
    private final IntegerProperty minAlertLevel  = new SimpleIntegerProperty();
    private final StringProperty  lastUpdated    = new SimpleStringProperty("");

    public Product() {}

    public Product(String id, String categoryId, String name,
                   double price, int currentStock, int minAlertLevel) {
        this(id, categoryId, name, price, currentStock, minAlertLevel, "");
    }

    public Product(String id, String categoryId, String name,
                   double price, int currentStock, int minAlertLevel, String lastUpdated) {
        this.id.set(id);
        this.categoryId.set(categoryId);
        this.name.set(name);
        this.price.set(price);
        this.currentStock.set(currentStock);
        this.minAlertLevel.set(minAlertLevel);
        this.lastUpdated.set(lastUpdated);
    }

    public StringProperty  idProperty()            { return id; }
    public StringProperty  categoryIdProperty()    { return categoryId; }
    public StringProperty  nameProperty()          { return name; }
    public DoubleProperty  priceProperty()         { return price; }
    public IntegerProperty currentStockProperty()  { return currentStock; }
    public IntegerProperty minAlertLevelProperty() { return minAlertLevel; }
    public StringProperty  lastUpdatedProperty()   { return lastUpdated; }

    public String  getId()            { return id.get(); }
    public String  getCategoryId()    { return categoryId.get(); }
    public String  getName()          { return name.get(); }
    public double  getPrice()         { return price.get(); }
    public int     getCurrentStock()  { return currentStock.get(); }
    public int     getMinAlertLevel() { return minAlertLevel.get(); }
    public String  getLastUpdated()   { return lastUpdated.get(); }

    public void setId(String v)            { id.set(v); }
    public void setCategoryId(String v)    { categoryId.set(v); }
    public void setName(String v)          { name.set(v); }
    public void setPrice(double v)         { price.set(v); }
    public void setCurrentStock(int v)     { currentStock.set(v); }
    public void setMinAlertLevel(int v)    { minAlertLevel.set(v); }
    public void setLastUpdated(String v)   { lastUpdated.set(v); }

    /** True when stock is at or below the minimum alert threshold. */
    public boolean isLowStock() {
        return currentStock.get() <= minAlertLevel.get();
    }

    @Override
    public String toString() { return name.get(); }
}
