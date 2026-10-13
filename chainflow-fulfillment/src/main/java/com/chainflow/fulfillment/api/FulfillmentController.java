package com.chainflow.fulfillment.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.fulfillment.mapper.FulfillmentMapper;
import com.chainflow.fulfillment.model.FulfillmentOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillments")
public class FulfillmentController {
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
        mapper.updateStatus(fulfillmentId, status);
        return ApiResponse.success(mapper.findById(fulfillmentId));
    }
    public record CreateFulfillmentRequest(@NotBlank String orderId, @NotBlank String carrierCode) {}
}
