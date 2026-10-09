package com.catshop.dto.admin.order;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderSummaryResponse(
        Long id,
        String orderNo,
        Long memberId,
        String memberName,
        String memberEmail,
        String status,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        int itemCount,
        String receiverName,
        LocalDateTime createdAt
) {}
