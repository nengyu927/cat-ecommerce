package com.catshop.dto.admin;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CouponRequest(
 @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,32}") String code,
 @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal discountAmount,
 @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) BigDecimal minimumAmount,
 @NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,
 @NotNull @Min(1) @Max(1000000) Integer maxUses,
 @NotNull Boolean active,Integer version) {}
