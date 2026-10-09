package com.catshop.service;

import com.catshop.repository.ProductImageRepository;
import com.catshop.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Load only list-page summaries, without initializing product image/SKU collections. */
@Component
@RequiredArgsConstructor
public class CatalogSummaryLoader {
    private final ProductImageRepository images;
    private final ProductSkuRepository skus;
    public record Stock(int skuCount, int totalStock) {}

    public Map<Long, String> primaryImages(Collection<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<Long, String> result = new HashMap<>();
        // Preserve the original first-primary rule: sortOrder, then image id; no fallback on list pages.
        for (var batch : com.catshop.common.IdBatches.of(productIds))
            for (var image : images.findPrimaryImages(batch)) result.putIfAbsent(image.getProductId(), image.getUrl());
        return result;
    }

    public Map<Long, Stock> stock(Collection<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<Long, Stock> result = new HashMap<>();
        for (var batch : com.catshop.common.IdBatches.of(productIds))
            for (var row : skus.summarizeStock(batch))
                result.put(row.getProductId(), new Stock(row.getSkuCount().intValue(), row.getTotalStock().intValue()));
        return result;
    }
}
