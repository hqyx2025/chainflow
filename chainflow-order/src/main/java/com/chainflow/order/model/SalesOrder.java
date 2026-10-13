package com.chainflow.order.model;

public record SalesOrder(String orderId, String sku, Integer quantity, String paymentStatus, String fulfillmentStatus, String createdAt) {}
