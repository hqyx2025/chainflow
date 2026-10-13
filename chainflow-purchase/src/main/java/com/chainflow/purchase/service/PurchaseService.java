package com.chainflow.purchase.service;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.purchase.api.PurchaseController.CreatePurchaseRequest;
import com.chainflow.purchase.mapper.PurchaseMapper;
import com.chainflow.purchase.model.PurchaseOrder;
import com.chainflow.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;

@Service
public class PurchaseService {
    private final PurchaseMapper mapper;
    private final RestClient inventoryClient;

    @Autowired
    public PurchaseService(PurchaseMapper mapper, RestClient.Builder restClientBuilder,
                           @Value("${chainflow.services.inventory-url:http://localhost:9004}") String inventoryUrl) {
        this(mapper, restClientBuilder.baseUrl(inventoryUrl).build());
    }

    /** Constructor kept small and injectable for unit tests. */
    public PurchaseService(PurchaseMapper mapper, RestClient inventoryClient) {
        this.mapper = mapper;
        this.inventoryClient = inventoryClient;
    }

    /** Backward-compatible constructor for callers that only use create/list. */
    public PurchaseService(PurchaseMapper mapper) {
        this(mapper, RestClient.create("http://localhost:9004"));
    }
    @Transactional
    public PurchaseOrder create(CreatePurchaseRequest request) {
        PurchaseOrder order = new PurchaseOrder(null, request.supplierCode(), request.sku(), request.quantity(), "CREATED", null);
        mapper.insert(order);
        return order;
    }

    @Transactional
    public PurchaseOrder receive(Long purchaseId) {
        PurchaseOrder order = mapper.findByIdForUpdate(purchaseId);
        if (order == null) throw new BusinessException("PUR-404", "采购单不存在");
        if ("RECEIVED".equals(order.status())) return order;
        if (!"CREATED".equals(order.status())) {
            throw new BusinessException("PUR-409", "采购单当前状态不允许入库");
        }

        ApiResponse<?> result;
        try {
            result = inventoryClient.post()
                    .uri("/api/v1/inventory/receipts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InventoryReceiveRequest(order.id(), order.sku(), order.quantity()))
                    .retrieve()
                    .body(ApiResponse.class);
        } catch (RestClientException ex) {
            throw new BusinessException("PUR-002", "库存入库失败");
        }
        if (result == null || !"0".equals(result.code())) {
            throw new BusinessException("PUR-002", "库存入库失败");
        }
        if (mapper.markReceived(purchaseId) == 0) {
            throw new BusinessException("PUR-409", "采购单状态已发生变化");
        }
        return new PurchaseOrder(order.id(), order.supplierCode(), order.sku(), order.quantity(), "RECEIVED", order.createdAt());
    }

    private record InventoryReceiveRequest(Long purchaseId, String sku, Integer quantity) {}
    public List<PurchaseOrder> list() { return mapper.findAll(); }
}
