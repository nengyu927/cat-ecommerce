package com.catshop.dto.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Client-confirmed amounts, never trusted as the source of order prices. */
public record CheckoutAmounts(
        @NotNull @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal goodsTotal,
        @NotNull @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal discountAmount,
        @NotNull @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal shippingFee,
        @NotNull @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal totalAmount,
        @NotBlank @Size(max=48000) String priceSnapshot
) {
    public boolean matches(BigDecimal goods,BigDecimal discount,BigDecimal fee,String prices) {
        return goodsTotal != null && discountAmount != null && shippingFee != null && totalAmount != null
                && goodsTotal.compareTo(goods)==0 && discountAmount.compareTo(discount)==0
                && shippingFee.compareTo(fee)==0 && totalAmount.compareTo(goods.subtract(discount).add(fee))==0
                && prices.equals(priceSnapshot);
    }
}
