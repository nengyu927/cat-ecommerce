package com.catshop.dto.cart;


import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long skuId,
        String skuCode,
        String specName,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal,
        Integer stock
) {}
