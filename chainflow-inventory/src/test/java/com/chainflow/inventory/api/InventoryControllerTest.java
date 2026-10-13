package com.chainflow.inventory.api;

import com.chainflow.common.exception.BusinessException;
import com.chainflow.inventory.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryControllerTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final InventoryController controller = new InventoryController(mapper);

    @Test
    void duplicateOrderReturnsExistingReservationWithoutDecrementingAgain() {
        when(mapper.findReservation("CF-1"))
                .thenReturn(new InventoryMapper.ReservationView("RES-1", "CF-1", "SKU-DEMO-001", 2, "RESERVED"));
        var result = controller.reserve(new InventoryController.ReserveRequest("CF-1", "SKU-DEMO-001", 2));
        assertEquals("RES-1", result.data().reservationId());
        assertEquals("SKU-DEMO-001", result.data().sku());
        assertEquals(2, result.data().quantity());
        verify(mapper, never()).reserve(anyString(), anyInt());
        verify(mapper, never()).insertReservation(anyString(), anyString(), anyString(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"SKU-OTHER, 2", "SKU-DEMO-001, 3"})
    void duplicateOrderWithDifferentPayloadIsRejectedWithoutChangingStock(String sku, int quantity) {
        when(mapper.findReservation("CF-1"))
                .thenReturn(new InventoryMapper.ReservationView("RES-1", "CF-1", "SKU-DEMO-001", 2, "RESERVED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.reserve(new InventoryController.ReserveRequest("CF-1", sku, quantity)));

        assertEquals("INV-409", error.getCode());
        assertTrue(error.getMessage().contains("与首次请求不一致"));
        verify(mapper, never()).reserve(anyString(), anyInt());
        verify(mapper, never()).insertReservation(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void insufficientStockFailsBeforeCreatingReservation() {
        when(mapper.findReservation("CF-2")).thenReturn(null);
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
        when(mapper.findReceiptDetails(7L))
                .thenReturn(new InventoryMapper.ReceiptView(7L, "SKU-DEMO-001", 5));
        when(mapper.findBySku("SKU-DEMO-001"))
                .thenReturn(new InventoryMapper.InventoryView(1L, "SKU-DEMO-001", 105, 0));

        var result = controller.receive(new InventoryController.ReceiveRequest(7L, "SKU-DEMO-001", 5));

        assertEquals("RECEIVED", result.data().status());
        assertEquals(7L, result.data().purchaseId());
        assertEquals("SKU-DEMO-001", result.data().sku());
        assertEquals(5, result.data().quantity());
        verify(mapper, never()).receive(anyString(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"SKU-OTHER, 5", "SKU-DEMO-001, 6"})
    void duplicatePurchaseWithDifferentPayloadIsRejectedWithoutChangingStock(String sku, int quantity) {
        when(mapper.insertReceiptIfAbsent(7L, sku, quantity)).thenReturn(0);
        when(mapper.findReceiptDetails(7L))
                .thenReturn(new InventoryMapper.ReceiptView(7L, "SKU-DEMO-001", 5));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.receive(new InventoryController.ReceiveRequest(7L, sku, quantity)));

        assertEquals("INV-409", error.getCode());
        assertTrue(error.getMessage().contains("与首次请求不一致"));
        verify(mapper, never()).receive(anyString(), anyInt());
        verify(mapper, never()).findBySku(anyString());
    }

    @Test
    void duplicatePurchaseWithoutOriginalReceiptDoesNotReturnSuccess() {
        when(mapper.insertReceiptIfAbsent(7L, "SKU-DEMO-001", 5)).thenReturn(0);
        when(mapper.findReceiptDetails(7L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.receive(new InventoryController.ReceiveRequest(7L, "SKU-DEMO-001", 5)));

        assertEquals("INV-409", error.getCode());
        verify(mapper, never()).receive(anyString(), anyInt());
        verify(mapper, never()).findBySku(anyString());
    }

    @Test
    void receivingUnknownSkuFailsAndDoesNotReturnSuccess() {
        when(mapper.insertReceiptIfAbsent(8L, "UNKNOWN", 1)).thenReturn(1);
        when(mapper.receive("UNKNOWN", 1)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> controller.receive(new InventoryController.ReceiveRequest(8L, "UNKNOWN", 1)));
    }
}
