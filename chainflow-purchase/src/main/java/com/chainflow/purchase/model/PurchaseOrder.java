package com.chainflow.purchase.model;

public record PurchaseOrder(Long id, String supplierCode, String sku, Integer quantity, String status, String createdAt) {}
