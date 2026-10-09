package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.common.PageResult;
import com.catshop.dto.product.ProductResponse;
import com.catshop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "商品瀏覽")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "商品列表（分頁、分類篩選、關鍵字搜尋）")
    public ApiResponse<PageResult<ProductResponse>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock) {
        return ApiResponse.ok(productService.search(categoryId, keyword, page, size, sort, brand, minPrice, maxPrice, inStock));
    }

    @GetMapping("/brands")
    public ApiResponse<java.util.List<String>> brands() { return ApiResponse.ok(productService.brands()); }

    @GetMapping("/{id}")
    @Operation(summary = "商品詳情（依 id）")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "商品詳情（依 slug）")
    public ApiResponse<ProductResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<java.util.List<ProductResponse>> related(@PathVariable Long id) {
        return ApiResponse.ok(productService.related(id));
    }
}
