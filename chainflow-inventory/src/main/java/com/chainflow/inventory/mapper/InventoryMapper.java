package com.chainflow.inventory.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryMapper {
    /**
     * Atomically records a purchase receipt. The unique purchase id makes the
     * receive operation idempotent when the caller retries the request.
     */
    @Insert("INSERT IGNORE INTO inventory_receipt (purchase_id, sku, quantity, created_at) VALUES (#{purchaseId}, #{sku}, #{quantity}, NOW())")
    int insertReceiptIfAbsent(Long purchaseId, String sku, int quantity);

    @Update("UPDATE inventory_item SET available_quantity = available_quantity + #{quantity} WHERE sku = #{sku}")
    int receive(String sku, int quantity);

    @Select("SELECT purchase_id FROM inventory_receipt WHERE purchase_id = #{purchaseId}")
    Long findReceipt(Long purchaseId);

    /**
     * Loads the original receipt payload for idempotency validation.  A
     * purchase id is the idempotency key, so a retry is only safe when its
     * sku and quantity are identical to the first request.
     */
    @Select("SELECT purchase_id AS purchaseId, sku, quantity FROM inventory_receipt WHERE purchase_id = #{purchaseId} FOR UPDATE")
    ReceiptView findReceiptDetails(Long purchaseId);

    @Update("UPDATE inventory_item SET available_quantity = available_quantity - #{quantity}, reserved_quantity = reserved_quantity + #{quantity} WHERE sku = #{sku} AND available_quantity >= #{quantity}")
    int reserve(String sku, int quantity);
    @Insert("INSERT INTO inventory_reservation (reservation_id, order_id, sku, quantity, status, created_at) VALUES (#{reservationId}, #{orderId}, #{sku}, #{quantity}, 'RESERVED', NOW())")
    int insertReservation(String reservationId, String orderId, String sku, int quantity);
    @Select("SELECT reservation_id FROM inventory_reservation WHERE order_id = #{orderId} LIMIT 1")
    String findReservationId(String orderId);
    /** Loads the original reservation payload for idempotency validation. */
    @Select("SELECT reservation_id AS reservationId, order_id AS orderId, sku, quantity, status FROM inventory_reservation WHERE order_id = #{orderId} LIMIT 1 FOR UPDATE")
    ReservationView findReservation(String orderId);
    @Select("SELECT id, sku, available_quantity AS availableQuantity, reserved_quantity AS reservedQuantity FROM inventory_item WHERE sku = #{sku}")
    InventoryView findBySku(String sku);
    record InventoryView(Long id, String sku, Integer availableQuantity, Integer reservedQuantity) {}
    record ReceiptView(Long purchaseId, String sku, Integer quantity) {}
    record ReservationView(String reservationId, String orderId, String sku, Integer quantity, String status) {}
}
