package com.chainflow.fulfillment.api;

import com.chainflow.fulfillment.mapper.FulfillmentMapper;
import com.chainflow.fulfillment.model.FulfillmentOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import com.chainflow.common.exception.BusinessException;

class FulfillmentControllerTest {
    private final FulfillmentMapper mapper = mock(FulfillmentMapper.class);
    private final FulfillmentController controller = new FulfillmentController(mapper);

    @Test
    void retryReturnsExistingFulfillmentAndDoesNotCreateAnotherId() {
        var existing = new FulfillmentOrder("FUL-1", "CF-1", "STANDARD", "CREATED", "2026-10-14T00:00:00");
        when(mapper.findByOrderId("CF-1")).thenReturn(existing);

        var response = controller.create(new FulfillmentController.CreateFulfillmentRequest("CF-1", "EXPRESS"));

        assertEquals(existing, response.data());
        verify(mapper).insertIfAbsent(anyString(), eq("CF-1"), eq("EXPRESS"));
        verify(mapper).findByOrderId("CF-1");
        verify(mapper, never()).findById(anyString());
    }

    @Test
    void invalidStatusIsRejectedBeforeDatabaseUpdate() {
        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> controller.updateStatus("FUL-1", "UNKNOWN"));

        assertEquals("FUL-409", error.getCode());
        verify(mapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"CREATED,PICKING", "PICKING,PACKED", "PACKED,SHIPPED", "SHIPPED,COMPLETED",
            "CREATED,CANCELLED", "PICKING,CANCELLED", "PACKED,CANCELLED"})
    void acceptsValidTransition(String from, String target) {
        var before = order(from);
        var after = order(target);
        when(mapper.findById("FUL-1")).thenReturn(before, after);
        when(mapper.updateStatusIfCurrent("FUL-1", from, target)).thenReturn(1);

        assertEquals(after, controller.updateStatus("FUL-1", target).data());
    }

    @ParameterizedTest
    @CsvSource({"CREATED,COMPLETED", "CREATED,SHIPPED", "PICKING,CREATED", "PACKED,PICKING",
            "SHIPPED,CANCELLED", "COMPLETED,PICKING", "CANCELLED,CREATED"})
    void rejectsSkippedAndReverseTransitions(String from, String target) {
        when(mapper.findById("FUL-1")).thenReturn(order(from));

        var error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> controller.updateStatus("FUL-1", target));

        assertEquals("FUL-409", error.getCode());
        verify(mapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString());
    }

    @Test
    void repeatedStatusReturnsCurrentOrderWithoutWriting() {
        var completed = order("COMPLETED");
        when(mapper.findById("FUL-1")).thenReturn(completed);

        assertEquals(completed, controller.updateStatus("FUL-1", "COMPLETED").data());
        verify(mapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString());
    }

    @Test
    void missingFulfillmentIsAReadableFailure() {
        var error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> controller.updateStatus("FUL-missing", "PICKING"));
        assertEquals("FUL-404", error.getCode());
        verify(mapper, never()).updateStatusIfCurrent(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentTransitionCannotOverwriteNewerState() {
        when(mapper.findById("FUL-1")).thenReturn(order("CREATED"), order("CANCELLED"));
        when(mapper.updateStatusIfCurrent("FUL-1", "CREATED", "PICKING")).thenReturn(0);

        var error = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> controller.updateStatus("FUL-1", "PICKING"));
        assertEquals("FUL-409", error.getCode());
    }

    @Test
    void concurrentIdenticalTransitionReturnsPersistedTarget() {
        var picking = order("PICKING");
        when(mapper.findById("FUL-1")).thenReturn(order("CREATED"), picking);
        when(mapper.updateStatusIfCurrent("FUL-1", "CREATED", "PICKING")).thenReturn(0);

        assertEquals(picking, controller.updateStatus("FUL-1", "PICKING").data());
    }

    private FulfillmentOrder order(String status) {
        return new FulfillmentOrder("FUL-1", "CF-1", "STANDARD", status, "2026-10-15T00:00:00");
    }
}
