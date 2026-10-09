package com.catshop.dto.admin.inventory;

import com.catshop.common.PageResult;

public record LowStockResponse(PageResult<Item> items, long lowStockCount, long outOfStockCount) {
    public record Item(Long skuId, Long productId, String productName, String productStatus,
        String skuCode, String specName, String categoryName, int stock, int safetyStock, int shortage) {}
}
