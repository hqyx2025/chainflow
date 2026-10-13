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

    @Test
    void receivingPurchaseAtomicallyIncreasesAvailableStock() {
        when(mapper.insertReceiptIfAbsent(7L, "SKU-DEMO-001", 5)).thenReturn(1);
        when(mapper.receive("SKU-DEMO-001", 5)).thenReturn(1);
        when(mapper.findBySku("SKU-DEMO-001"))
                .thenReturn(new InventoryMapper.InventoryView(1L, "SKU-DEMO-001", 105, 0));

        var result = controller.receive(new InventoryController.ReceiveRequest(7L, "SKU-DEMO-001", 5));

        assertEquals("RECEIVED", result.data().status());
        assertEquals(105, result.data().availableQuantity());
        verify(mapper).receive("SKU-DEMO-001", 5);
    }

    @Test
    void duplicatePurchaseReceiptDoesNotIncreaseStockAgain() {
        when(mapper.insertReceiptIfAbsent(7L, "SKU-DEMO-001", 5)).thenReturn(0);
        when(mapper.findBySku("SKU-DEMO-001"))
                .thenReturn(new InventoryMapper.InventoryView(1L, "SKU-DEMO-001", 105, 0));

        var result = controller.receive(new InventoryController.ReceiveRequest(7L, "SKU-DEMO-001", 5));

        assertEquals("RECEIVED", result.data().status());
        verify(mapper, never()).receive(anyString(), anyInt());
    }

    @Test
    void receivingUnknownSkuFailsAndDoesNotReturnSuccess() {
        when(mapper.insertReceiptIfAbsent(8L, "UNKNOWN", 1)).thenReturn(1);
        when(mapper.receive("UNKNOWN", 1)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> controller.receive(new InventoryController.ReceiveRequest(8L, "UNKNOWN", 1)));
    }
}
