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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryMapper mapper;
    public InventoryController(InventoryMapper mapper) { this.mapper = mapper; }

    @PostMapping("/reservations")
    @Transactional
    public ApiResponse<ReservationResult> reserve(@Valid @RequestBody ReserveRequest request) {
        String existing = mapper.findReservationId(request.orderId());
        if (existing != null) return ApiResponse.success(new ReservationResult(existing, request.sku(), request.quantity(), "RESERVED"));
        if (mapper.reserve(request.sku(), request.quantity()) == 0) throw new BusinessException("INV-001", "库存不足");
        String reservationId = UUID.randomUUID().toString();
        mapper.insertReservation(reservationId, request.orderId(), request.sku(), request.quantity());
        return ApiResponse.success(new ReservationResult(reservationId, request.sku(), request.quantity(), "RESERVED"));
    }

    @GetMapping("/{sku}")
    public ApiResponse<InventoryMapper.InventoryView> get(@PathVariable String sku) {
        return ApiResponse.success(mapper.findBySku(sku));
    }
    public record ReserveRequest(@NotBlank String orderId, @NotBlank String sku, @Positive Integer quantity) {}
}
