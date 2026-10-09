package com.catshop.dto.admin;
import com.catshop.entity.Coupon;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CouponResponse(Long id,String code,BigDecimal discountAmount,BigDecimal minimumAmount,
 LocalDateTime startsAt,LocalDateTime endsAt,Integer maxUses,Boolean active,Integer version) {
 public static CouponResponse of(Coupon c) {
  return new CouponResponse(c.getId(),c.getCode(),c.getDiscountAmount(),c.getMinimumAmount(),
   c.getStartsAt(),c.getEndsAt(),c.getMaxUses(),c.getActive(),c.getVersion());
 }
}
