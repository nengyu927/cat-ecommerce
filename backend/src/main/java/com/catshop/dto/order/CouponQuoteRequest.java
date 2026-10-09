package com.catshop.dto.order;
import jakarta.validation.constraints.Size;
public record CouponQuoteRequest(@Size(max=32) String code) {}
