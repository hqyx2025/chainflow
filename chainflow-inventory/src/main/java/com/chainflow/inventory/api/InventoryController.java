package com.chainflow.inventory.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.inventory.mapper.InventoryMapper;
import com.chainflow.inventory.model.ReservationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryMapper mapper;
    public InventoryController(InventoryMapper mapper) { this.mapper = mapper; }

    @PostMapping("/reservations")
    @Transactional
    public ApiResponse<ReservationResult> reserve(@Valid @RequestBody ReserveRequest request) {
        InventoryMapper.ReservationView existing = mapper.findReservation(request.orderId());
        if (existing != null) {
            validateRetryPayload(existing.sku(), existing.quantity(), request.sku(), request.quantity(),
                    "同一订单的库存预占 SKU 或数量与首次请求不一致");
            return ApiResponse.success(new ReservationResult(existing.reservationId(), existing.sku(),
                    existing.quantity(), existing.status()));
        }
        if (mapper.reserve(request.sku(), request.quantity()) == 0) throw new BusinessException("INV-001", "库存不足");
        String reservationId = UUID.randomUUID().toString();
        mapper.insertReservation(reservationId, request.orderId(), request.sku(), request.quantity());
        return ApiResponse.success(new ReservationResult(reservationId, request.sku(), request.quantity(), "RESERVED"));
    }

    /**
     * Receives stock for a purchase order. The receipt table has a unique
     * purchase id, so retries with the same payload return successfully
     * without increasing stock a second time.
     */
    @PostMapping({"/receipts", "/receive"})
    @Transactional
    public ApiResponse<ReceiveResult> receive(@Valid @RequestBody ReceiveRequest request) {
        int inserted = mapper.insertReceiptIfAbsent(request.purchaseId(), request.sku(), request.quantity());
        if (inserted == 0) {
            InventoryMapper.ReceiptView receipt = mapper.findReceiptDetails(request.purchaseId());
            if (receipt == null) {
                throw new BusinessException("INV-409", "采购入库记录尚未就绪，请重试");
            }
            validateRetryPayload(receipt.sku(), receipt.quantity(), request.sku(), request.quantity(),
                    "同一采购单的入库 SKU 或数量与首次请求不一致");
            InventoryMapper.InventoryView existing = mapper.findBySku(receipt.sku());
            return ApiResponse.success(new ReceiveResult(receipt.purchaseId(), receipt.sku(), receipt.quantity(),
                    existing == null ? null : existing.availableQuantity(), "RECEIVED"));
        }
        if (mapper.receive(request.sku(), request.quantity()) == 0) {
            throw new BusinessException("INV-404", "库存商品不存在");
        }
        InventoryMapper.InventoryView current = mapper.findBySku(request.sku());
        return ApiResponse.success(new ReceiveResult(request.purchaseId(), request.sku(), request.quantity(),
                current == null ? null : current.availableQuantity(), "RECEIVED"));
    }

    @GetMapping("/{sku}")
    public ApiResponse<InventoryMapper.InventoryView> get(@PathVariable String sku) {
        return ApiResponse.success(mapper.findBySku(sku));
    }

    private static void validateRetryPayload(String originalSku, Integer originalQuantity,
            String requestedSku, Integer requestedQuantity, String message) {
        if (!Objects.equals(originalSku, requestedSku) || !Objects.equals(originalQuantity, requestedQuantity)) {
            throw new BusinessException("INV-409", message);
        }
    }

    public record ReserveRequest(@NotBlank String orderId, @NotBlank String sku, @Positive Integer quantity) {}
    public record ReceiveRequest(@Positive Long purchaseId, @NotBlank String sku, @Positive Integer quantity) {}
    public record ReceiveResult(Long purchaseId, String sku, Integer quantity, Integer availableQuantity, String status) {}
}
