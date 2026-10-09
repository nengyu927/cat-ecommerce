package com.catshop.dto.order;


import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long skuId,
        String productName,
        String skuSpec,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {}
