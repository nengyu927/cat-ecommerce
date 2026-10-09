package com.catshop.dto.order;
import java.math.BigDecimal;
public record CouponQuoteResponse(String couponCode,BigDecimal goodsTotal,BigDecimal discountAmount,BigDecimal shippingFee,BigDecimal totalAmount,
                                  String cartSnapshot,String priceSnapshot) {}
