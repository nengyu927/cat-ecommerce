package com.catshop.dto.admin.order;

import com.catshop.dto.order.OrderItemResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDetailResponse(
        Long id,
        String orderNo,
        Long memberId,
        String memberName,
        String memberEmail,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
