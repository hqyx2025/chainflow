package com.chainflow.order.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.order.mapper.OrderMapper;
import com.chainflow.order.messaging.OrderEventPublisher;
import com.chainflow.order.model.SalesOrder;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.UUID;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderMapper mapper;
    private final RestClient inventoryClient;
    private final Optional<OrderEventPublisher> eventPublisher;
    public OrderController(OrderMapper mapper, RestClient.Builder restClientBuilder,
                           @Value("${chainflow.services.inventory-url:http://localhost:9004}") String inventoryUrl,
                           Optional<OrderEventPublisher> eventPublisher) {
        this.mapper = mapper;
        this.inventoryClient = restClientBuilder.baseUrl(inventoryUrl).build();
        this.eventPublisher = eventPublisher;
    }
    @PostMapping
    public ApiResponse<SalesOrder> create(@Valid @RequestBody CreateOrderRequest request) {
        String orderId = "CF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        mapper.insert(orderId, request.sku(), request.quantity());
        return ApiResponse.success(mapper.findById(orderId));
    }
    @PostMapping("/{orderId}/pay")
    @GlobalTransactional(name = "chainflow-pay-and-reserve", rollbackFor = Exception.class)
    public ApiResponse<SalesOrder> pay(@PathVariable String orderId) {
        SalesOrder order = mapper.findById(orderId);
        if (order == null) throw new BusinessException("ORD-404", "订单不存在");
        if ("PAID".equals(order.paymentStatus())) return ApiResponse.success(order);
        var result = inventoryClient.post().uri("/api/v1/inventory/reservations").contentType(MediaType.APPLICATION_JSON)
                .body(new ReserveRequest(order.orderId(), order.sku(), order.quantity())).retrieve().body(ApiResponse.class);
        if (result == null || !"0".equals(result.code())) throw new BusinessException("ORD-002", "库存预占失败");
        mapper.markPaid(orderId);
        SalesOrder paid = mapper.findById(orderId);
        eventPublisher.ifPresent(publisher -> publisher.publishPaid(paid));
        return ApiResponse.success(paid);
    }
    @GetMapping("/{orderId}")
    public ApiResponse<SalesOrder> get(@PathVariable String orderId) { return ApiResponse.success(mapper.findById(orderId)); }
    public record CreateOrderRequest(@NotBlank String sku, @Positive Integer quantity) {}
    private record ReserveRequest(String orderId, String sku, Integer quantity) {}
}
