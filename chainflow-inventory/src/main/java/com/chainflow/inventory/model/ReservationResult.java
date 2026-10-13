package com.chainflow.inventory.model;

public record ReservationResult(String reservationId, String sku, Integer quantity, String status) {}
