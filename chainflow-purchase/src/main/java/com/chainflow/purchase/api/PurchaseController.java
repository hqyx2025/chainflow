package com.chainflow.purchase.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.purchase.model.PurchaseOrder;
import com.chainflow.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {
    private final PurchaseService service;
    public PurchaseController(PurchaseService service) { this.service = service; }
    @PostMapping
    public ApiResponse<PurchaseOrder> create(@Valid @RequestBody CreatePurchaseRequest request) { return ApiResponse.success(service.create(request)); }
    @GetMapping
    public ApiResponse<List<PurchaseOrder>> list() { return ApiResponse.success(service.list()); }
    @PostMapping("/{purchaseId}/receive")
    public ApiResponse<PurchaseOrder> receive(@PathVariable Long purchaseId) {
        return ApiResponse.success(service.receive(purchaseId));
    }
    public record CreatePurchaseRequest(@NotBlank String supplierCode, @NotBlank String sku, @Positive Integer quantity) {}
}
