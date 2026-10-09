package com.catshop.dto.admin.product;


import java.math.BigDecimal;

public record AdminSkuResponse(
        Long id,
        String skuCode,
        String specName,
        BigDecimal price,
        Integer stock,
        Integer safetyStock,
        Boolean isActive,
        Integer version
) {}
