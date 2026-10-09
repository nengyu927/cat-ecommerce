package com.catshop.dto.product;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String slug,
        String description,
        String ingredients,
        String suitableAge,
        String origin,
        String storageInstructions,
        String brand,
        BigDecimal basePrice,
        String status,
        Boolean isActive,
        String primaryImageUrl,
        List<ProductImageResponse> images,
        List<ProductSkuResponse> skus,
        LocalDateTime createdAt
) {}
