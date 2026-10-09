package com.catshop.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        @NotNull(message = "數量不可為空") @Min(value = 1, message = "數量至少為 1") Integer quantity
) {}
