package com.chainflow.inventory.model;

public record InventoryItem(Long id, String sku, Integer availableQuantity, Integer reservedQuantity) {}
