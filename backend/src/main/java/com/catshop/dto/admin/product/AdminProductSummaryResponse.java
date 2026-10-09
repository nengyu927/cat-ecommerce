package com.catshop.dto.admin.product;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductSummaryResponse(
        Long id,
        String name,
        String slug,
        Long categoryId,
        String categoryName,
        String brand,
        BigDecimal basePrice,
        String status,
        Boolean isActive,
        int skuCount,
        int totalStock,
        String primaryImageUrl,
        LocalDateTime updatedAt
) {}
