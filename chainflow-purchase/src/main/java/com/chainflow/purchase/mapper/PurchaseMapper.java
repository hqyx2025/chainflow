package com.chainflow.purchase.mapper;

import com.chainflow.purchase.model.PurchaseOrder;
import com.chainflow.purchase.model.PurchaseOrderInsert;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PurchaseMapper {
    @Insert("INSERT INTO purchase_order (supplier_code, sku, quantity, status, created_at) VALUES (#{supplierCode}, #{sku}, #{quantity}, 'CREATED', NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseOrderInsert order);
    @Select("SELECT id, supplier_code AS supplierCode, sku, quantity, status, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS createdAt FROM purchase_order ORDER BY id DESC")
    List<PurchaseOrder> findAll();

    @Select("SELECT id, supplier_code AS supplierCode, sku, quantity, status, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') AS createdAt FROM purchase_order WHERE id = #{id} FOR UPDATE")
    PurchaseOrder findByIdForUpdate(Long id);

    @Update("UPDATE purchase_order SET status = 'RECEIVED' WHERE id = #{id} AND status = 'CREATED'")
    int markReceived(Long id);
}
