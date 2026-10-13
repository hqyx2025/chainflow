package com.chainflow.order.api;

import com.chainflow.order.mapper.OrderMapper;
import com.chainflow.order.model.SalesOrder;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderControllerTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final RestClient inventoryClient = mock(RestClient.class);
    private final RestClient fulfillmentClient = mock(RestClient.class);

    @Test
    void paidOrderReportsReadableErrorWhenFulfillmentIsUnavailable() {
        var order = new SalesOrder("CF-1", "SKU-1", 1, "PAID", "READY_FOR_FULFILLMENT", "2026-10-14T00:00:00");
        when(mapper.findById("CF-1")).thenReturn(order);
        when(fulfillmentClient.post()).thenThrow(new RestClientException("connection refused"));
        var controller = new OrderController(mapper, inventoryClient, fulfillmentClient, "STANDARD", Optional.empty());

        var error = assertThrows(com.chainflow.common.exception.BusinessException.class, () -> controller.pay("CF-1"));

        assertEquals("ORD-003", error.getCode());
        assertEquals("支付成功但履约服务暂不可用，请稍后重试", error.getMessage());
        verifyNoInteractions(inventoryClient);
        verify(mapper, never()).markFulfilling(anyString());
    }

    @Test
    void alreadyFulfillingOrderIsIdempotentAndDoesNotCallFulfillmentAgain() {
        var order = new SalesOrder("CF-2", "SKU-1", 1, "PAID", "FULFILLING", "2026-10-14T00:00:00");
        when(mapper.findById("CF-2")).thenReturn(order);
        var controller = new OrderController(mapper, inventoryClient, fulfillmentClient, "STANDARD", Optional.empty());

        var response = controller.pay("CF-2");

        assertEquals(order, response.data());
        verifyNoInteractions(inventoryClient, fulfillmentClient);
    }
}
