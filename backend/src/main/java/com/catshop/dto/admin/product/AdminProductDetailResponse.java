package com.catshop.dto.admin.product;

import com.catshop.dto.product.ProductImageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminProductDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        String ingredients,
        String suitableAge,
        String origin,
        String storageInstructions,
        Long categoryId,
        String categoryName,
        String brand,
        BigDecimal basePrice,
        String status,
        Boolean isActive,
        List<ProductImageResponse> images,
        List<AdminSkuResponse> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
