package com.catshop.dto.product;


import java.math.BigDecimal;

public record ProductSkuResponse(
        Long id,
        String skuCode,
        String specName,
        BigDecimal price,
        Integer stock,
        Integer safetyStock,
        Boolean isActive
) {}
