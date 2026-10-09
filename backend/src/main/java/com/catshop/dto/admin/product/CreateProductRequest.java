package com.catshop.dto.admin.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank(message = "商品名稱不可為空") @Size(max = 200) String name,
        @NotBlank(message = "slug 不可為空")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug 僅允許小寫英數字與連字號")
        @Size(max = 220) String slug,
        String description,
        @Size(max = 10000) String ingredients,
        @Size(max = 100) String suitableAge,
        @Size(max = 100) String origin,
        @Size(max = 500) String storageInstructions,
        @NotNull(message = "分類不可為空") Long categoryId,
        @Size(max = 100) String brand,
        @NotNull(message = "售價不可為空") @DecimalMin(value = "0", message = "售價不可為負") BigDecimal basePrice,
        String status,
        Boolean isActive,
        @Valid List<ImageItem> images,
        @Valid List<SkuItem> skus
) {
    public record ImageItem(
            @NotBlank(message = "圖片網址不可為空") @Size(max = 500) String url,
            @Size(max = 200) String altText,
            Integer sortOrder,
            Boolean isPrimary
    ) {}

    public record SkuItem(
            @NotBlank(message = "SKU 編號不可為空") @Size(max = 64) String skuCode,
            @NotBlank(message = "規格名稱不可為空") @Size(max = 200) String specName,
            @NotNull(message = "SKU 價格不可為空") @DecimalMin(value = "0", message = "價格不可為負") BigDecimal price,
            @Min(value = 0, message = "庫存不可為負") Integer stock,
            @Min(value = 0, message = "安全庫存不可為負") Integer safetyStock
    ) {}
}
