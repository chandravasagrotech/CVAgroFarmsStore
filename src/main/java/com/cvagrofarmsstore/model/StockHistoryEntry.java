package com.cvagrofarmsstore.model;

public class StockHistoryEntry {
    private final String productId;
    private final String date;
    private final int    stockBefore;
    private final int    qtyAdded;
    private final int    stockAfter;

    public StockHistoryEntry(String productId, String date,
                             int stockBefore, int qtyAdded, int stockAfter) {
        this.productId   = productId;
        this.date        = date;
        this.stockBefore = stockBefore;
        this.qtyAdded    = qtyAdded;
        this.stockAfter  = stockAfter;
    }

    public String getProductId()   { return productId; }
    public String getDate()        { return date; }
    public int    getStockBefore() { return stockBefore; }
    public int    getQtyAdded()    { return qtyAdded; }
    public int    getStockAfter()  { return stockAfter; }

    /** Short audit label shown in the inventory table column. */
    public String toAuditLabel() {
        return date + "  |  +" + qtyAdded + "  →  " + stockAfter;
    }
}
