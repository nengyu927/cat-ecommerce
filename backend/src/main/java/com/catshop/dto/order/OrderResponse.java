package com.catshop.dto.order;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        Long memberId,
        String status,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        String couponCode,
        BigDecimal discountAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String remark,
        List<OrderItemResponse> items,
        LocalDateTime createdAt
) {}
