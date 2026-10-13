package com.chainflow.purchase.service;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.purchase.mapper.PurchaseMapper;
import com.chainflow.purchase.model.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PurchaseServiceTest {
    private final PurchaseMapper mapper = mock(PurchaseMapper.class);
    private final RestClient inventoryClient = mock(RestClient.class);
    private final PurchaseService service = new PurchaseService(mapper, inventoryClient);

    @Test
    void alreadyReceivedPurchaseIsIdempotentAndDoesNotCallInventory() {
        PurchaseOrder received = new PurchaseOrder(7L, "SUP-1", "SKU-1", 5, "RECEIVED", null);
        when(mapper.findByIdForUpdate(7L)).thenReturn(received);

        PurchaseOrder result = service.receive(7L);

        assertEquals("RECEIVED", result.status());
        verifyNoInteractions(inventoryClient);
        verify(mapper, never()).markReceived(anyLong());
    }

    @Test
    void nonCreatedPurchaseCannotBeReceived() {
        PurchaseOrder cancelled = new PurchaseOrder(8L, "SUP-1", "SKU-1", 5, "CANCELLED", null);
        when(mapper.findByIdForUpdate(8L)).thenReturn(cancelled);

        BusinessException error = assertThrows(BusinessException.class, () -> service.receive(8L));

        assertEquals("PUR-409", error.getCode());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void missingPurchaseReturnsNotFound() {
        when(mapper.findByIdForUpdate(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.receive(99L));

        assertEquals("PUR-404", error.getCode());
    }

    @Test
    void createdPurchaseCallsInventoryAndMovesToReceived() {
        PurchaseOrder created = new PurchaseOrder(10L, "SUP-1", "SKU-1", 3, "CREATED", null);
        when(mapper.findByIdForUpdate(10L)).thenReturn(created);
        when(mapper.markReceived(10L)).thenReturn(1);

        RestClient.RequestBodyUriSpec post = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(inventoryClient.post()).thenReturn(post);
        when(post.uri(anyString())).thenReturn(post);
        when(post.contentType(any(MediaType.class))).thenReturn(post);
        when(post.body(any(Object.class))).thenReturn(post);
        when(post.retrieve()).thenReturn(response);
        when(response.body(ApiResponse.class)).thenReturn(ApiResponse.success(null));

        PurchaseOrder result = service.receive(10L);

        assertEquals("RECEIVED", result.status());
        verify(post).uri("/api/v1/inventory/receipts");
        verify(mapper).markReceived(10L);
    }
}
