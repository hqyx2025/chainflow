package com.chainflow.fulfillment.api;

import com.chainflow.fulfillment.mapper.FulfillmentMapper;
import com.chainflow.fulfillment.model.FulfillmentOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

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
}
