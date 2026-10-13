package com.chainflow.purchase.service;

import com.chainflow.purchase.api.PurchaseController.CreatePurchaseRequest;
import com.chainflow.purchase.mapper.PurchaseMapper;
import com.chainflow.purchase.model.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PurchaseService {
    private final PurchaseMapper mapper;
    public PurchaseService(PurchaseMapper mapper) { this.mapper = mapper; }
    @Transactional
    public PurchaseOrder create(CreatePurchaseRequest request) {
        PurchaseOrder order = new PurchaseOrder(null, request.supplierCode(), request.sku(), request.quantity(), "CREATED", null);
        mapper.insert(order);
        return order;
    }
    public List<PurchaseOrder> list() { return mapper.findAll(); }
}
