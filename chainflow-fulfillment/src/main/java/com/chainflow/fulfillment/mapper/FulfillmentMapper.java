package com.chainflow.fulfillment.mapper;

import com.chainflow.fulfillment.model.FulfillmentOrder;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FulfillmentMapper {
    @Insert("INSERT INTO fulfillment_order (fulfillment_id, order_id, carrier_code, status, created_at) VALUES (#{fulfillmentId}, #{orderId}, #{carrierCode}, 'CREATED', NOW())")
    int insert(String fulfillmentId, String orderId, String carrierCode);
    @Update("UPDATE fulfillment_order SET status = #{status} WHERE fulfillment_id = #{fulfillmentId}")
    int updateStatus(String fulfillmentId, String status);
    @Select("SELECT fulfillment_id AS fulfillmentId, order_id AS orderId, carrier_code AS carrierCode, status, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS createdAt FROM fulfillment_order WHERE fulfillment_id = #{fulfillmentId}")
    FulfillmentOrder findById(String fulfillmentId);
}
