package com.catshop.dto.wishlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** id is the product id, not an exposed member-owned wishlist row id. */
public record WishlistItemResponse(Long id, String name, String brand, BigDecimal basePrice,
        String primaryImageUrl, String status, boolean available, LocalDateTime addedAt) {}
