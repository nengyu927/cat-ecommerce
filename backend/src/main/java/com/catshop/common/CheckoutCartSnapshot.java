package com.catshop.common;

import com.catshop.entity.CartItem;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Canonical SKU/quantity pairs; validated while holding the member's cart write lock. */
public final class CheckoutCartSnapshot {
    private CheckoutCartSnapshot() {}

    public static String prices(List<CartItem> items) {
        return items.stream().sorted(Comparator.comparing(item -> item.getSku().getId()))
                .map(item -> item.getSku().getId() + ":" + item.getQuantity() + ":" +
                        item.getSku().getPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("|"));
    }

    public static String of(List<CartItem> items) {
        return items.stream().sorted(Comparator.comparing(item -> item.getSku().getId()))
                .map(item -> "[" + item.getSku().getId() + "," + item.getQuantity() + "]")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
