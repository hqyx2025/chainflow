package com.chainflow.fulfillment.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.fulfillment.mapper.FulfillmentMapper;
import com.chainflow.fulfillment.model.FulfillmentOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillments")
public class FulfillmentController {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "CREATED", "PICKING", "PACKED", "SHIPPED", "COMPLETED", "CANCELLED");
    private final FulfillmentMapper mapper;
    public FulfillmentController(FulfillmentMapper mapper) { this.mapper = mapper; }
    @PostMapping
    public ApiResponse<FulfillmentOrder> create(@Valid @RequestBody CreateFulfillmentRequest request) {
        String id = "FUL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        // order_id has a unique constraint and is the idempotency key. The
        // upsert keeps concurrent/retried calls from creating a second row.
        mapper.insertIfAbsent(id, request.orderId(), request.carrierCode());
        FulfillmentOrder fulfillment = mapper.findByOrderId(request.orderId());
        if (fulfillment == null) {
            throw new BusinessException("FUL-001", "履约单创建失败");
        }
        return ApiResponse.success(fulfillment);
    }
    @PatchMapping("/{fulfillmentId}/status")
    public ApiResponse<FulfillmentOrder> updateStatus(@PathVariable String fulfillmentId, @RequestParam String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException("FUL-409", "履约状态不合法");
        }
        FulfillmentOrder current = mapper.findById(fulfillmentId);
        if (current == null) {
            throw new BusinessException("FUL-404", "履约单不存在");
        }
        if (status.equals(current.status())) {
            return ApiResponse.success(current);
        }
        if (!canTransition(current.status(), status)) {
            throw new BusinessException("FUL-409", "当前履约状态不允许此操作");
        }
        int updated = mapper.updateStatusIfCurrent(fulfillmentId, current.status(), status);
        FulfillmentOrder latest = mapper.findById(fulfillmentId);
        if (updated < 0 || updated > 1 || latest == null || !status.equals(latest.status())) {
            throw new BusinessException("FUL-409", "履约状态已变化，请刷新后重试");
        }
        return ApiResponse.success(latest);
    }

    private boolean canTransition(String current, String target) {
        return switch (current) {
            case "CREATED" -> target.equals("PICKING") || target.equals("CANCELLED");
            case "PICKING" -> target.equals("PACKED") || target.equals("CANCELLED");
            case "PACKED" -> target.equals("SHIPPED") || target.equals("CANCELLED");
            case "SHIPPED" -> target.equals("COMPLETED");
            default -> false;
        };
    }
    public record CreateFulfillmentRequest(@NotBlank String orderId, @NotBlank String carrierCode) {}
}
