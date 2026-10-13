package com.chainflow.purchase.model;

/**
 * Mutable persistence parameter used only for MyBatis generated-key handling.
 *
 * <p>The API model is an immutable record. MyBatis writes an auto-incremented
 * key through a JavaBean setter, so inserts must use this small mutable holder
 * rather than passing the API record directly.</p>
 */
public class PurchaseOrderInsert {
    private Long id;
    private String supplierCode;
    private String sku;
    private Integer quantity;

    public PurchaseOrderInsert(String supplierCode, String sku, Integer quantity) {
        this.supplierCode = supplierCode;
        this.sku = sku;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public String getSku() {
        return sku;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
