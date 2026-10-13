package com.chainflow.inventory.api;

import com.chainflow.common.exception.BusinessException;
import com.chainflow.inventory.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryControllerTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final InventoryController controller = new InventoryController(mapper);

    @Test
    void duplicateOrderReturnsExistingReservationWithoutDecrementingAgain() {
        when(mapper.findReservationId("CF-1")).thenReturn("RES-1");
        var result = controller.reserve(new InventoryController.ReserveRequest("CF-1", "SKU-DEMO-001", 2));
        assertEquals("RES-1", result.data().reservationId());
        verify(mapper, never()).reserve(anyString(), anyInt());
    }

    @Test
    void insufficientStockFailsBeforeCreatingReservation() {
        when(mapper.findReservationId("CF-2")).thenReturn(null);
        when(mapper.reserve("SKU-DEMO-001", 200)).thenReturn(0);
        assertThrows(BusinessException.class,
                () -> controller.reserve(new InventoryController.ReserveRequest("CF-2", "SKU-DEMO-001", 200)));
        verify(mapper, never()).insertReservation(anyString(), anyString(), anyString(), anyInt());
    }
}
