package com.chainflow.order.mapper;

import com.chainflow.order.model.SalesOrder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper {
    @Insert("INSERT INTO sales_order (order_id, sku, quantity, payment_status, fulfillment_status, created_at) VALUES (#{orderId}, #{sku}, #{quantity}, 'PENDING_PAYMENT', 'CREATED', NOW())")
    int insert(String orderId, String sku, int quantity);
    @Update("UPDATE sales_order SET payment_status = 'PAID', fulfillment_status = 'READY_FOR_FULFILLMENT' WHERE order_id = #{orderId} AND payment_status = 'PENDING_PAYMENT'")
    int markPaid(String orderId);
    @Select("SELECT order_id AS orderId, sku, quantity, payment_status AS paymentStatus, fulfillment_status AS fulfillmentStatus, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS createdAt FROM sales_order WHERE order_id = #{orderId}")
    SalesOrder findById(String orderId);
}
