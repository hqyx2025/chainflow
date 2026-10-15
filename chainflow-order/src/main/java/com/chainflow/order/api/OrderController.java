package com.chainflow.order.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.order.mapper.OrderMapper;
import com.chainflow.order.messaging.OrderEventPublisher;
import com.chainflow.order.model.SalesOrder;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.UUID;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderMapper mapper;
    private final RestClient inventoryClient;
    private final RestClient fulfillmentClient;
    private final String fulfillmentCarrierCode;
    private final Optional<OrderEventPublisher> eventPublisher;

    @Autowired
    public OrderController(OrderMapper mapper, RestClient.Builder restClientBuilder,
                           @Value("${chainflow.services.inventory-url:http://localhost:9004}") String inventoryUrl,
                           @Value("${chainflow.services.fulfillment-url:http://localhost:9006}") String fulfillmentUrl,
                           @Value("${chainflow.services.fulfillment-carrier-code:STANDARD}") String fulfillmentCarrierCode,
                           Optional<OrderEventPublisher> eventPublisher) {
        this.mapper = mapper;
        this.inventoryClient = restClientBuilder.baseUrl(inventoryUrl).build();
        this.fulfillmentClient = restClientBuilder.baseUrl(fulfillmentUrl).build();
        this.fulfillmentCarrierCode = fulfillmentCarrierCode;
        this.eventPublisher = eventPublisher;
    }

    /** Kept for callers constructing the controller directly in lightweight tests. */
    public OrderController(OrderMapper mapper, RestClient.Builder restClientBuilder,
                           String inventoryUrl, Optional<OrderEventPublisher> eventPublisher) {
        this(mapper, restClientBuilder, inventoryUrl, "http://localhost:9006", "STANDARD", eventPublisher);
    }

    OrderController(OrderMapper mapper, RestClient inventoryClient, RestClient fulfillmentClient,
                    String fulfillmentCarrierCode, Optional<OrderEventPublisher> eventPublisher) {
        this.mapper = mapper;
        this.inventoryClient = inventoryClient;
        this.fulfillmentClient = fulfillmentClient;
        this.fulfillmentCarrierCode = fulfillmentCarrierCode;
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
        if ("PAID".equals(order.paymentStatus()) && "FULFILLING".equals(order.fulfillmentStatus())) {
            // Payment and fulfillment state are durable before the response is
            // returned. A client retry can reattempt publication after a
            // timeout or broker failure; no durable outbox exists yet.
            publishPaidEvent(order);
            return ApiResponse.success(order);
        }

        SalesOrder paid = order;
        if (!"PAID".equals(order.paymentStatus())) {
            reserveInventory(order);
            int paidUpdated = mapper.markPaid(orderId);
            paid = mapper.findById(orderId);
            // A zero update can mean another concurrent payment already won
            // the conditional update.  Accept it only after verifying PAID.
            if ((paidUpdated != 0 && paidUpdated != 1) || paid == null
                    || !"PAID".equals(paid.paymentStatus())) {
                throw new BusinessException("ORD-004", "订单支付状态更新失败，请稍后重试");
            }
        }

        ensureFulfillment(paid);
        int fulfillingUpdated = mapper.markFulfilling(orderId);
        SalesOrder fulfilling = mapper.findById(orderId);
        if ((fulfillingUpdated != 0 && fulfillingUpdated != 1) || fulfilling == null
                || !"PAID".equals(fulfilling.paymentStatus())
                || !"FULFILLING".equals(fulfilling.fulfillmentStatus())) {
            throw new BusinessException("ORD-004", "订单履约状态更新失败，请稍后重试");
        }
        // A retry from READY_FOR_FULFILLMENT can reattempt publication after
        // a prior fulfillment failure. Retries may produce duplicate events;
        // consumers should deduplicate by orderId. Delivery still requires
        // the caller to retry until a durable outbox is implemented.
        publishPaidEvent(fulfilling);
        return ApiResponse.success(fulfilling);
    }

    private void publishPaidEvent(SalesOrder order) {
        eventPublisher.ifPresent(publisher -> publisher.publishPaid(order));
    }

    private void reserveInventory(SalesOrder order) {
        try {
            var result = inventoryClient.post().uri("/api/v1/inventory/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequest(order.orderId(), order.sku(), order.quantity()))
                    .retrieve().body(ApiResponse.class);
            if (result == null || !"0".equals(result.code())) {
                throw new BusinessException("ORD-002", "库存预占失败");
            }
        } catch (RestClientException ex) {
            throw new BusinessException("ORD-002", "库存服务暂不可用或预占失败，请稍后重试");
        }
    }

    private void ensureFulfillment(SalesOrder order) {
        try {
            var result = fulfillmentClient.post().uri("/api/v1/fulfillments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateFulfillmentRequest(order.orderId(), fulfillmentCarrierCode))
                    .retrieve().body(ApiResponse.class);
            if (result == null || !"0".equals(result.code())) {
                throw new BusinessException("ORD-003", "支付成功但履约单创建失败，请稍后重试");
            }
        } catch (RestClientException ex) {
            throw new BusinessException("ORD-003", "支付成功但履约服务暂不可用，请稍后重试");
        }
    }
    @GetMapping("/{orderId}")
    public ApiResponse<SalesOrder> get(@PathVariable String orderId) { return ApiResponse.success(mapper.findById(orderId)); }
    public record CreateOrderRequest(@NotBlank String sku, @NotNull @Positive Integer quantity) {}
    private record ReserveRequest(String orderId, String sku, Integer quantity) {}
    private record CreateFulfillmentRequest(String orderId, String carrierCode) {}
}
