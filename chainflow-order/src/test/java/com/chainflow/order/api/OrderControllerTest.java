package com.chainflow.order.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.order.mapper.OrderMapper;
import com.chainflow.order.messaging.OrderEventPublisher;
import com.chainflow.order.model.SalesOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
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
    private final OrderEventPublisher publisher = mock(OrderEventPublisher.class);

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

    @Test
    void successfulPaymentVerifiesBothPersistedStatesAndPublishesEvent() {
        var pending = order("PENDING_PAYMENT", "CREATED");
        var paid = order("PAID", "READY_FOR_FULFILLMENT");
        var fulfilling = order("PAID", "FULFILLING");
        when(mapper.findById("CF-1")).thenReturn(pending, paid, fulfilling);
        when(mapper.markPaid("CF-1")).thenReturn(1);
        when(mapper.markFulfilling("CF-1")).thenReturn(1);
        succeeds(inventoryClient);
        succeeds(fulfillmentClient);

        var response = controller().pay("CF-1");

        assertEquals(fulfilling, response.data());
        verify(publisher).publishPaid(fulfilling);
    }

    @ParameterizedTest
    @CsvSource({"0, PENDING_PAYMENT", "1, PENDING_PAYMENT", "2, PAID", "-1, PAID"})
    void failedPaymentStateUpdateDoesNotCreateFulfillmentOrPublishEvent(int updated, String persistedStatus) {
        var pending = order("PENDING_PAYMENT", "CREATED");
        var persisted = order(persistedStatus, "READY_FOR_FULFILLMENT");
        when(mapper.findById("CF-1")).thenReturn(pending, persisted);
        when(mapper.markPaid("CF-1")).thenReturn(updated);
        succeeds(inventoryClient);

        BusinessException error = assertThrows(BusinessException.class, () -> controller().pay("CF-1"));

        assertEquals("ORD-004", error.getCode());
        verifyNoInteractions(fulfillmentClient, publisher);
        verify(mapper, never()).markFulfilling(anyString());
    }

    @Test
    void concurrentPaymentAlreadyReachingPaidCanContinueAfterZeroUpdate() {
        var pending = order("PENDING_PAYMENT", "CREATED");
        var paid = order("PAID", "READY_FOR_FULFILLMENT");
        var fulfilling = order("PAID", "FULFILLING");
        when(mapper.findById("CF-1")).thenReturn(pending, paid, fulfilling);
        when(mapper.markPaid("CF-1")).thenReturn(0);
        when(mapper.markFulfilling("CF-1")).thenReturn(1);
        succeeds(inventoryClient);
        succeeds(fulfillmentClient);

        assertEquals(fulfilling, controller().pay("CF-1").data());

        verify(publisher).publishPaid(fulfilling);
    }

    @Test
    void retryFromReadyStatePublishesPaidEventAfterFulfillmentRecovery() {
        var paid = order("PAID", "READY_FOR_FULFILLMENT");
        var fulfilling = order("PAID", "FULFILLING");
        when(mapper.findById("CF-1")).thenReturn(paid, fulfilling);
        when(mapper.markFulfilling("CF-1")).thenReturn(1);
        succeeds(fulfillmentClient);

        assertEquals(fulfilling, controller().pay("CF-1").data());

        verifyNoInteractions(inventoryClient);
        verify(mapper, never()).markPaid(anyString());
        verify(publisher).publishPaid(fulfilling);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void failedFulfillmentStateUpdateDoesNotPublishOrReportSuccess(int updated) {
        var paid = order("PAID", "READY_FOR_FULFILLMENT");
        when(mapper.findById("CF-1")).thenReturn(paid);
        when(mapper.markFulfilling("CF-1")).thenReturn(updated);
        succeeds(fulfillmentClient);

        BusinessException error = assertThrows(BusinessException.class, () -> controller().pay("CF-1"));

        assertEquals("ORD-004", error.getCode());
        verifyNoInteractions(inventoryClient, publisher);
    }

    @Test
    void inventoryHttpFailureReturnsReadableBusinessErrorBeforeAnyOrderWrite() {
        when(mapper.findById("CF-1")).thenReturn(order("PENDING_PAYMENT", "CREATED"));
        when(inventoryClient.post()).thenThrow(new RestClientException("connection refused"));

        BusinessException error = assertThrows(BusinessException.class, () -> controller().pay("CF-1"));

        assertEquals("ORD-002", error.getCode());
        assertEquals("库存服务暂不可用或预占失败，请稍后重试", error.getMessage());
        verify(mapper, never()).markPaid(anyString());
        verify(mapper, never()).markFulfilling(anyString());
        verifyNoInteractions(fulfillmentClient, publisher);
    }

    @Test
    void inventoryHttpConflictReturnsReservationBusinessErrorInsteadOfInternalError() {
        when(mapper.findById("CF-1")).thenReturn(order("PENDING_PAYMENT", "CREATED"));
        RestClient.ResponseSpec response = succeeds(inventoryClient);
        when(response.body(ApiResponse.class)).thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

        BusinessException error = assertThrows(BusinessException.class, () -> controller().pay("CF-1"));

        assertEquals("ORD-002", error.getCode());
        verify(mapper, never()).markPaid(anyString());
        verify(mapper, never()).markFulfilling(anyString());
        verifyNoInteractions(fulfillmentClient, publisher);
    }

    @Test
    void concurrentFulfillmentAlreadyReachedTargetStateIsAccepted() {
        var paid = order("PAID", "READY_FOR_FULFILLMENT");
        var fulfilling = order("PAID", "FULFILLING");
        when(mapper.findById("CF-1")).thenReturn(paid, fulfilling);
        when(mapper.markFulfilling("CF-1")).thenReturn(0);
        succeeds(fulfillmentClient);

        assertEquals(fulfilling, controller().pay("CF-1").data());

        verify(publisher).publishPaid(fulfilling);
    }

    @Test
    void retryOfCompletedPaymentReattemptsFailedEventPublication() {
        var fulfilling = order("PAID", "FULFILLING");
        when(mapper.findById("CF-1")).thenReturn(fulfilling);
        doThrow(new IllegalStateException("broker unavailable")).doNothing()
                .when(publisher).publishPaid(fulfilling);
        var controller = controller();

        assertThrows(IllegalStateException.class, () -> controller.pay("CF-1"));
        assertEquals(fulfilling, controller.pay("CF-1").data());

        verify(publisher, times(2)).publishPaid(fulfilling);
        verifyNoInteractions(inventoryClient, fulfillmentClient);
        verify(mapper, never()).markPaid(anyString());
        verify(mapper, never()).markFulfilling(anyString());
    }

    private OrderController controller() {
        return new OrderController(mapper, inventoryClient, fulfillmentClient, "STANDARD", Optional.of(publisher));
    }

    private SalesOrder order(String paymentStatus, String fulfillmentStatus) {
        return new SalesOrder("CF-1", "SKU-1", 1, paymentStatus, fulfillmentStatus, "2026-10-14T00:00:00");
    }

    private RestClient.ResponseSpec succeeds(RestClient client) {
        RestClient.RequestBodyUriSpec post = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(post);
        when(post.uri(anyString())).thenReturn(post);
        when(post.contentType(any(MediaType.class))).thenReturn(post);
        when(post.body(any(Object.class))).thenReturn(post);
        when(post.retrieve()).thenReturn(response);
        when(response.body(ApiResponse.class)).thenReturn(ApiResponse.success(null));
        return response;
    }
}
