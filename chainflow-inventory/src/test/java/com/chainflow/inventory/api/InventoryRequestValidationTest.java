package com.chainflow.inventory.api;

import com.chainflow.common.web.GlobalExceptionHandler;
import com.chainflow.inventory.mapper.InventoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryRequestValidationTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InventoryController(mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingOrNullQuantityIsRejectedBeforeAnyStockWrite() throws Exception {
        for (String body : new String[] {
                "{\"orderId\":\"CF-1\",\"sku\":\"SKU-1\"}",
                "{\"orderId\":\"CF-1\",\"sku\":\"SKU-1\",\"quantity\":null}",
                "{\"orderId\":\"CF-1\",\"sku\":\"SKU-1\",\"quantity\":0}"}) {
            mvc.perform(post("/api/v1/inventory/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void receiptRequiresPurchaseIdBeforeAnyStockWrite() throws Exception {
        mvc.perform(post("/api/v1/inventory/receipts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-1\",\"quantity\":2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
        verifyNoInteractions(mapper);
    }

    @Test
    void malformedJsonReturnsClientError() throws Exception {
        mvc.perform(post("/api/v1/inventory/reservations").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));
        verifyNoInteractions(mapper);
    }
}
