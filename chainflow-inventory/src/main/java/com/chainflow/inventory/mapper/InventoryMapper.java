package com.chainflow.inventory.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface InventoryMapper {
    @Update("UPDATE inventory_item SET available_quantity = available_quantity - #{quantity}, reserved_quantity = reserved_quantity + #{quantity} WHERE sku = #{sku} AND available_quantity >= #{quantity}")
    int reserve(String sku, int quantity);
    @Insert("INSERT INTO inventory_reservation (reservation_id, order_id, sku, quantity, status, created_at) VALUES (#{reservationId}, #{orderId}, #{sku}, #{quantity}, 'RESERVED', NOW())")
    int insertReservation(String reservationId, String orderId, String sku, int quantity);
    @Select("SELECT reservation_id FROM inventory_reservation WHERE order_id = #{orderId} LIMIT 1")
    String findReservationId(String orderId);
    @Select("SELECT id, sku, available_quantity AS availableQuantity, reserved_quantity AS reservedQuantity FROM inventory_item WHERE sku = #{sku}")
    InventoryView findBySku(String sku);
    record InventoryView(Long id, String sku, Integer availableQuantity, Integer reservedQuantity) {}
}
