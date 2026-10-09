package com.catshop.controller.admin;

import com.catshop.common.ApiResponse;
import com.catshop.common.PageResult;
import com.catshop.dto.admin.product.*;
import com.catshop.service.admin.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Product", description = "後台商品管理（ADMIN）")
public class AdminProductController {

    private final AdminProductService adminProductService;

    @GetMapping("/products")
    @Operation(summary = "AP1 後台商品列表（含下架、含 SKU 庫存摘要）")
    public ApiResponse<PageResult<AdminProductSummaryResponse>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.ok(adminProductService.list(categoryId, keyword, status, isActive, brand, page, size, sort));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "AP2 後台商品詳情（編輯用，含全部 SKU/圖片）")
    public ApiResponse<AdminProductDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminProductService.detail(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "AP3 新增商品（可同時帶入 SKU 與圖片）")
    public ApiResponse<AdminProductDetailResponse> create(@Valid @RequestBody CreateProductRequest req) {
        return ApiResponse.ok(adminProductService.create(req));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "AP4 更新商品主檔")
    public ApiResponse<AdminProductDetailResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateProductRequest req) {
        return ApiResponse.ok(adminProductService.update(id, req));
    }

    @PutMapping("/products/{id}/images")
    @Operation(summary = "只更新商品圖片，含圖片快照衝突檢查")
    public ApiResponse<java.util.List<com.catshop.dto.product.ProductImageResponse>> updateImages(
            @PathVariable Long id, @Valid @RequestBody UpdateProductImagesRequest req) {
        return ApiResponse.ok(adminProductService.updateImages(id, req));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "AP5 下架商品（軟刪除）")
    public ApiResponse<Void> offShelf(@PathVariable Long id) {
        adminProductService.offShelf(id);
        return ApiResponse.ok();
    }

    @PostMapping("/products/{id}/skus")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "AP6 新增商品規格 SKU")
    public ApiResponse<AdminSkuResponse> addSku(@PathVariable Long id,
                                                @Valid @RequestBody CreateSkuRequest req) {
        return ApiResponse.ok(adminProductService.addSku(id, req));
    }

    @PutMapping("/skus/{skuId}")
    @Operation(summary = "AP7 更新 SKU（價格／庫存／安全庫存／啟用）")
    public ApiResponse<AdminSkuResponse> updateSku(@PathVariable Long skuId,
                                                   @Valid @RequestBody UpdateSkuRequest req) {
        return ApiResponse.ok(adminProductService.updateSku(skuId, req));
    }
}
