package com.catshop.controller.admin;

import com.catshop.common.ApiResponse;
import com.catshop.dto.admin.inventory.LowStockResponse;
import com.catshop.service.admin.AdminInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/inventory") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {
    private final AdminInventoryService service;
    @GetMapping("/movements")
    public ApiResponse<com.catshop.common.PageResult<com.catshop.dto.admin.inventory.StockMovementResponse>> movements(
        @RequestParam(required=false) Long skuId, @RequestParam(required=false) String reason,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.movements(skuId,reason,page,size));
    }
    @GetMapping("/low-stock")
    public ApiResponse<LowStockResponse> lowStock(@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="false") boolean outOfStockOnly,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.lowStock(keyword,outOfStockOnly,page,size));
    }
}
